package com.shoppingapp.shoppingwebapp.config;

import com.shoppingapp.shoppingwebapp.model.Role;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import com.shoppingapp.shoppingwebapp.support.Redact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Grants ADMIN to the accounts named in {@code app.admin.emails}.
 *
 * <p>There has to be a first administrator, and every way of making one is
 * uncomfortable. A registration form with an "admin" checkbox is obviously
 * wrong. A seeded account with a default password is worse: it exists on every
 * deployment, and the password is in the repository. So the list is an
 * environment variable, the account has to be registered and verified through
 * the ordinary front door first, and this only changes its role.
 *
 * <p>It runs on every start and is idempotent. Removing an address from the
 * variable also removes the role, so revoking access is editing one setting and
 * redeploying rather than a database session.
 */
@Configuration
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    /**
     * Runs after {@link DataSeeder}; see {@link StartupOrder}.
     *
     * <p>Unordered it ran <em>before</em> the seeder and warned that the account
     * it was asked to promote did not exist -- one log line before the seeder
     * created it. On a fresh install the grant then silently did not happen
     * until the next restart.
     *
     * <p>Both runners carry an explicit number rather than one of them relying
     * on LOWEST_PRECEDENCE: an unordered runner is already treated as lowest, so
     * the two tied and the tie was broken arbitrarily.
     */
    @Bean
    @Order(StartupOrder.GRANT_ADMIN_ROLE)
    ApplicationRunner grantAdminRole(UserRepository users,
                                     @Value("${app.admin.emails:}") String configured) {
        return args -> apply(users, configured);
    }

    @Transactional
    void apply(UserRepository users, String configured) {
        List<String> admins = Arrays.stream(configured.split(","))
                .map(entry -> entry.trim().toLowerCase(Locale.ROOT))
                .filter(entry -> !entry.isBlank())
                .toList();

        if (admins.isEmpty()) {
            log.info("No app.admin.emails configured; nobody has the admin role");
            return;
        }

        for (String email : admins) {
            users.findByEmail(email).ifPresentOrElse(user -> {
                if (user.getRole() != Role.ADMIN) {
                    user.setRole(Role.ADMIN);
                    users.save(user);
                    log.info("Granted admin to {}", Redact.email(email));
                }
            }, () -> log.warn("app.admin.emails names {}, which has no account yet — "
                    + "register and verify it, then restart", Redact.email(email)));
        }

        // Anyone holding the role who is no longer on the list loses it. The
        // variable is the whole truth about who is an administrator; a role
        // left behind in the database would outlive the decision to grant it.
        for (User user : users.findByRole(Role.ADMIN)) {
            if (!admins.contains(user.getEmail().toLowerCase(Locale.ROOT))) {
                user.setRole(Role.USER);
                users.save(user);
                log.info("Revoked admin from {}; not in app.admin.emails", Redact.email(user.getEmail()));
            }
        }
    }
}
