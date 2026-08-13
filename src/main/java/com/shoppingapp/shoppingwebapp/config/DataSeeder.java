package com.shoppingapp.shoppingwebapp.config;

import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;

/**
 * Populates the in-memory database for local development and tests.
 *
 * <p>Excluded from the "mysql" profile: a real deployment should own its schema
 * through migrations and its data through the admin UI, not through a seeder
 * that ships a known demo password.
 */
@Configuration
@Profile("!mysql")
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner seed(ProductRepository productRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        return args -> {
            if (productRepository.count() == 0) {
                productRepository.saveAll(catalogue());
                log.info("Seeded {} demo products", productRepository.count());
            }
            if (!userRepository.existsByEmail("demo@solarupgrade.example")) {
                userRepository.save(new User(
                        "demo@solarupgrade.example",
                        passwordEncoder.encode("password123"),
                        "Demo Customer"));
                log.info("Seeded demo account demo@solarupgrade.example / password123");
            }
        };
    }

    private List<Product> catalogue() {
        return List.of(
                new Product("450W Monocrystalline Panel",
                        "High-efficiency mono PERC panel with a 25-year performance warranty. "
                                + "Suited to pitched domestic roofs.",
                        new BigDecimal("189.00"), Category.PANEL, 120, null),
                new Product("410W Slimline Panel",
                        "Lower-profile panel for roofs where space is tight, with an all-black frame.",
                        new BigDecimal("164.50"), Category.PANEL, 80, null),
                new Product("5kW Hybrid Inverter",
                        "Single-phase hybrid inverter with battery support and built-in monitoring.",
                        new BigDecimal("1245.00"), Category.INVERTER, 24, null),
                new Product("3.6kW String Inverter",
                        "Entry-level string inverter for smaller arrays. 10-year warranty.",
                        new BigDecimal("749.00"), Category.INVERTER, 31, null),
                new Product("5.2kWh Battery Module",
                        "Stackable LiFePO4 storage module. Pairs with the 5kW hybrid inverter.",
                        new BigDecimal("2390.00"), Category.BATTERY, 15, null),
                new Product("10.4kWh Battery Module",
                        "Double-capacity storage for households running heat pumps or EV charging overnight.",
                        new BigDecimal("4150.00"), Category.BATTERY, 7, null),
                new Product("Pitched Roof Mounting Kit (8 panels)",
                        "Anodised aluminium rails, clamps and roof hooks for a standard 8-panel array.",
                        new BigDecimal("315.00"), Category.MOUNTING, 45, null),
                new Product("Flat Roof Ballast Frame (4 panels)",
                        "Non-penetrating ballasted frame set at 10 degrees for flat roofs.",
                        new BigDecimal("428.00"), Category.MOUNTING, 18, null),
                new Product("7kW Tethered EV Charger",
                        "Smart charger with scheduling, solar-surplus matching and app control.",
                        new BigDecimal("899.00"), Category.EV_CHARGER, 22, null),
                new Product("Consumption Monitoring Kit",
                        "CT clamps and gateway giving real-time generation, export and household usage.",
                        new BigDecimal("179.00"), Category.MONITORING, 60, null));
    }
}
