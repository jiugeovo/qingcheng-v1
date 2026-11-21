package com.example.ordering.auth;

import com.example.ordering.user.UserAccount;
import com.example.ordering.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;

@Service
public class TokenService {

    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final Clock clock;

    public TokenService(UserRepository userRepository, AuthTokenRepository authTokenRepository, Clock clock) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public String createToken(UserAccount user) {
        Instant now = Instant.now(clock);
        authTokenRepository.deleteByExpiresAtBefore(now);

        String token = UUID.randomUUID().toString().replace("-", "");
        AuthTokenEntity session = new AuthTokenEntity();
        session.setToken(token);
        session.setUserId(user.getId());
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(TOKEN_TTL));
        authTokenRepository.save(session);
        return token;
    }

    @Transactional
    public Optional<UserAccount> findUserByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return authTokenRepository.findById(token)
                .flatMap(session -> {
                    if (!session.getExpiresAt().isAfter(Instant.now(clock))) {
                        authTokenRepository.delete(session);
                        return Optional.empty();
                    }
                    return userRepository.findById(session.getUserId()).filter(UserAccount::isEnabled);
                });
    }

    @Transactional
    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            authTokenRepository.deleteById(token);
        }
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        authTokenRepository.deleteAllByUserId(userId);
    }
}
