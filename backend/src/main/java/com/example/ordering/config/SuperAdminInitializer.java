package com.example.ordering.config;

import com.example.ordering.auth.ReservedUsernamePolicy;
import com.example.ordering.user.UserAccount;
import com.example.ordering.user.UserRepository;
import com.example.ordering.user.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(name = "app.couple.mode", havingValue = "LEGACY", matchIfMissing = true)
public class SuperAdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String nickname;

    public SuperAdminInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ReservedUsernamePolicy reservedUsernamePolicy,
            @Value("${app.super-admin.password}") String password,
            @Value("${app.super-admin.nickname}") String nickname
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = reservedUsernamePolicy.configuredUsername();
        this.password = password;
        this.nickname = nickname;
    }

    @Override
    public void run(String... args) {
        userRepository.findByUsername(username).ifPresentOrElse(existing -> {
            if (existing.getRole() != UserRole.SUPER_ADMIN) {
                throw new IllegalStateException("Configured superadmin username belongs to a non-superadmin account; "
                        + "refusing automatic privilege elevation");
            }
            userRepository.updateNickname(existing.getId(), nickname);
        }, () -> {
            if (!StringUtils.hasText(password)) {
                throw new IllegalStateException("SUPER_ADMIN_PASSWORD must be configured before creating superadmin");
            }
            UserAccount superAdmin = new UserAccount();
            superAdmin.setUsername(username);
            superAdmin.setPasswordHash(passwordEncoder.encode(password));
            superAdmin.setNickname(nickname);
            superAdmin.setRole(UserRole.SUPER_ADMIN);
            userRepository.save(superAdmin);
        });
    }
}
