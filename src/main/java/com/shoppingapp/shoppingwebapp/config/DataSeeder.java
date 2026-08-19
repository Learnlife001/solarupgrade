package com.shoppingapp.shoppingwebapp.config;

import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.ExportStance;
import com.shoppingapp.shoppingwebapp.model.Supplier;
import com.shoppingapp.shoppingwebapp.model.SupplierTrade;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.SupplierRepository;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

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
    @Order(StartupOrder.SEED_DEMO_DATA)
    CommandLineRunner seedDemoAccount(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.existsByEmail("demo@solarupgrade.example")) {
                return;
            }
            User demo = new User(
                    "demo@solarupgrade.example",
                    passwordEncoder.encode("sunny-rooftop-42"),
                    "Demo Customer");
            // Pre-verified: there is no inbox to collect a verification link from.
            demo.markEmailVerified();
            userRepository.save(demo);
            log.info("Seeded demo account demo@solarupgrade.example / sunny-rooftop-42");
        };
    }

    /**
     * A few directory entries so the page can be seen working.
     *
     * <p>Every one is named "(example)" and left unverified. Inventing
     * plausible German company names would be worse than leaving the page
     * empty: somebody would eventually email one of them. The real entries are
     * research, not code, and they go in through the admin area.
     */
    @Bean
    @Order(StartupOrder.SEED_DEMO_DATA)
    CommandLineRunner seedExampleSuppliers(SupplierRepository suppliers) {
        return args -> {
            if (suppliers.count() > 0) {
                return;
            }

            suppliers.save(example("Nordlicht Solar (example)", "Hamburg", "Hamburg",
                    SupplierTrade.WHOLESALE, ExportStance.YES,
                    "1 pallet (approx. 30 panels)", "FOB Hamburg", "German, English", 3,
                    Set.of(Category.PANEL, Category.MOUNTING),
                    "Placeholder entry. Replace with a real supplier once contacted."));

            suppliers.save(example("Rheinwatt Großhandel (example)", "Köln", "Nordrhein-Westfalen",
                    SupplierTrade.BOTH, ExportStance.ON_REQUEST,
                    "€5,000", "EXW", "German", 5,
                    Set.of(Category.INVERTER, Category.BATTERY),
                    "Placeholder entry. Replace with a real supplier once contacted."));

            suppliers.save(example("Alpenstrom Handel (example)", "München", "Bayern",
                    SupplierTrade.WHOLESALE, ExportStance.NO,
                    "1 container", null, "German", null,
                    Set.of(Category.PANEL, Category.INVERTER),
                    "Placeholder entry. Listed as EU-only so the filter can be seen working."));

            suppliers.save(example("Ostsee Photovoltaik (example)", "Rostock", "Mecklenburg-Vorpommern",
                    SupplierTrade.RETAIL, ExportStance.UNKNOWN,
                    null, null, null, null,
                    Set.of(Category.PANEL, Category.MONITORING),
                    "Placeholder entry. Nobody has asked them anything yet."));

            log.info("Seeded {} example suppliers, all unverified", suppliers.count());
        };
    }

    private static Supplier example(String name, String city, String region,
                                    SupplierTrade trade, ExportStance stance,
                                    String minimumOrder, String incoterms, String languages,
                                    Integer leadTimeWeeks, Set<Category> categories, String notes) {
        Supplier supplier = new Supplier(name, city);
        supplier.setRegion(region);
        supplier.setTrade(trade);
        supplier.setExportStance(stance);
        supplier.setMinimumOrder(minimumOrder);
        supplier.setIncoterms(incoterms);
        supplier.setLanguages(languages);
        supplier.setLeadTimeWeeks(leadTimeWeeks);
        supplier.setCategories(categories);
        supplier.setNotes(notes);
        // Deliberately never verified: nobody has checked a placeholder.
        return supplier;
    }
}
