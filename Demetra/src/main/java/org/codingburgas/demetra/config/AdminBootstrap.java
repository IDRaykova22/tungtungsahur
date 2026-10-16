package org.codingburgas.demetra.config;

import org.codingburgas.demetra.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Public registration never grants ADMIN, so an administrator comes from configuration
 * (DEMETRA_ADMIN_EMAIL / DEMETRA_ADMIN_PASSWORD), applied at every startup. Skipped when unset.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserService userService;
    private final String name;
    private final String email;
    private final String password;

    public AdminBootstrap(UserService userService,
                          @Value("${demetra.admin.name:}") String name,
                          @Value("${demetra.admin.email:}") String email,
                          @Value("${demetra.admin.password:}") String password) {
        this.userService = userService;
        // Default kept in Java: application.properties is read as ISO-8859-1 and would garble Cyrillic.
        this.name = name.isBlank() ? "Администратор" : name;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        if (!UserService.meetsPasswordRules(password)) {
            log.warn("The configured administrator password does not meet the password rules; use it only locally.");
        }
        try {
            log.info("Administrator account ready: {}", userService.ensureAdministrator(name, email, password).getEmail());
        } catch (IllegalArgumentException ex) {
            log.warn("Administrator was not created from configuration: {}", ex.getMessage());
        }
    }
}
