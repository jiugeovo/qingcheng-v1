package com.example.ordering.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.ordering.common.BusinessErrorCode;
import com.example.ordering.user.UserAccount;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Objects;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitConfig config;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupMinute = new AtomicLong(Long.MIN_VALUE);

    @Autowired
    public RateLimitFilter(RateLimitConfig config, ObjectMapper objectMapper, ClientIpResolver clientIpResolver,
                          Clock clock) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    RateLimitFilter(RateLimitConfig config, ObjectMapper objectMapper) {
        this(config, objectMapper, new ClientIpResolver(new ClientIpProperties()), Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    /** Test-friendly constructor retained for existing callers. */
    RateLimitFilter(RateLimitConfig config, ObjectMapper objectMapper, Clock clock) {
        this(config, objectMapper, new ClientIpResolver(new ClientIpProperties()), clock);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Limit limit = resolveLimit(request);
        if (limit == null || allow(limit, clientIpResolver.resolve(request))) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", "60");
        response.setHeader("X-Error-Code", BusinessErrorCode.RATE_LIMITED.name());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", "请求太频繁，请稍后再试");
        body.put("data", null);
        objectMapper.writeValue(response.getWriter(), body);
    }

    private Limit resolveLimit(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/auth/login") || uri.startsWith("/api/auth/register")) {
            return new Limit("auth", config.getAuthPerMinute());
        }
        boolean post = "POST".equalsIgnoreCase(request.getMethod());
        if ((post && uri.startsWith("/api/uploads"))
                || (post && uri.startsWith("/api/friends/media"))
                || (post && uri.endsWith("/messages/image"))) {
            return new Limit("upload", config.getUploadPerMinute());
        }
        if (!post && uri.startsWith("/api/friends/media/")) {
            return new Limit("friend-media", config.getFriendMediaPerMinute());
        }
        if (uri.startsWith("/api/ai/")) {
            return new Limit("ai", config.getAiPerMinute());
        }
        if (uri.startsWith("/api/")) {
            return new Limit("api", config.getApiPerMinute());
        }
        return null;
    }

    private boolean allow(Limit limit, String clientIp) {
        Instant current = clock.instant();
        long minute = current.getEpochSecond() / 60;
        cleanup(minute);
        int maximum = Math.max(limit.requestsPerMinute(), 1);
        if (!allow(limit.name() + ":ip:" + clientIp + ":" + minute, minute, maximum)) {
            return false;
        }
        Long userId = authenticatedUserId();
        return userId == null || allow(limit.name() + ":user:" + userId + ":" + minute, minute, maximum);
    }

    private void cleanup(long currentMinute) {
        long last = lastCleanupMinute.get();
        if (currentMinute <= last || !lastCleanupMinute.compareAndSet(last, currentMinute)) {
            return;
        }
        for (Map.Entry<String, Bucket> entry : buckets.entrySet()) {
            if (entry.getValue().minute() <= currentMinute - 1) {
                buckets.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    int trackedBucketCount() {
        return buckets.size();
    }

    private boolean allow(String key, long minute, int maximum) {
        int count = buckets.computeIfAbsent(key, unused -> new Bucket(minute)).count().incrementAndGet();
        return count <= maximum;
    }

    private static Long authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserAccount user)) {
            return null;
        }
        return user.getId();
    }

    private record Limit(String name, int requestsPerMinute) {
    }

    private record Bucket(long minute, AtomicInteger count) {
        private Bucket(long minute) {
            this(minute, new AtomicInteger());
        }
    }
}
