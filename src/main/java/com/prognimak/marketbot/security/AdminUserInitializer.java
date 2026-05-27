package com.prognimak.marketbot.security;

import com.prognimak.marketbot.entity.AppUserEntity;
import com.prognimak.marketbot.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements CommandLineRunner {
    private final AppSecurityProperties properties;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        AppSecurityProperties.Admin admin = properties.admin();
        userRepository.findByUsernameIgnoreCase(admin.username()).ifPresentOrElse(user -> {
            if (user.getRole() != UserRole.ADMIN || !user.isEnabled()) {
                user.setRole(UserRole.ADMIN);
                user.setEnabled(true);
                userRepository.save(user);
            }
        }, () -> {
            AppUserEntity user = new AppUserEntity();
            user.setUsername(admin.username());
            user.setPasswordHash(passwordEncoder.encode(admin.password()));
            user.setDisplayName(admin.displayName());
            user.setEmail(admin.email());
            user.setRole(UserRole.ADMIN);
            user.setEnabled(true);
            user.getMetadata().put("environment", "default");
            userRepository.save(user);
        });
    }
}
