package com.example.ordering.security;

import com.example.ordering.auth.TokenService;
import com.example.ordering.couple.CoupleAccess;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.ordering.user.UserAccount;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private CoupleAccess couple = CoupleAccess.legacy();

    @Autowired(required = false)
    void configureCouple(CoupleAccess couple) {
        this.couple = couple;
    }

    public TokenAuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveBearerToken(request.getHeader("Authorization"));
        tokenService.findUserByToken(token)
                .filter(TokenAuthenticationFilter::isUsableAccount)
                .filter(user -> !couple.enabled() || couple.isMember(user) || couple.isDeveloper(user))
                .ifPresent(this::authenticate);
        filterChain.doFilter(request, response);
    }

    private static boolean isUsableAccount(UserAccount user) {
        return user != null && user.isEnabled() && user.getId() != null && user.getId() > 0
                && user.getUsername() != null && !user.getUsername().isBlank()
                && user.getRole() != null;
    }

    private void authenticate(UserAccount user) {
        var role = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
        var authorities = couple.enabled() && couple.isMember(user)
                ? List.of(role, new SimpleGrantedAuthority("COUPLE_MEMBER")) : List.of(role);
        var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String resolveBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }
}
