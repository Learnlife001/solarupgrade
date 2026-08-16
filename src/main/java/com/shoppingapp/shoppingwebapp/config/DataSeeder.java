package com.shoppingapp.shoppingwebapp.config;

import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates the demo account for local development and tests.
 *
 * <p>Only the account lives here. The product catalogue is reference data and
 * ships as a Flyway migration instead, so a real deployment has something to
 * sell -- an earlier version gated both behind this flag and left production
 * with an empty shop.
 *
 * <p>This account's password is published in the README, so it must never
 * reach a real database. {@code app.seed-demo-data} is true only in the
 * default in-memory configuration and false in every profile that points at a
 * real one. An opt-in flag fails closed when a new profile is added.
 */
@Configuration
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner seedDemoAccount(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.existsByEmail("demo@solarupgrade.example")) {
                return;
            }
            User demo = new User(
                    "demo@solarupgrade.example",
                    passwordEncoder.encode("password123"),
                    "Demo Customer");
            // Pre-verified: there is no inbox to collect a verification link from.
            demo.markEmailVerified();
            userRepository.save(demo);
            log.info("Seeded demo account demo@solarupgrade.example / password123");
        };
    }
}
