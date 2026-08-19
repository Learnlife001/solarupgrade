package com.shoppingapp.shoppingwebapp.repository;

import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.ExportStance;
import com.shoppingapp.shoppingwebapp.model.Supplier;
import com.shoppingapp.shoppingwebapp.model.SupplierTrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The supplier search, run against a real PostgreSQL server.
 *
 * <p>This exists because the rest of the suite does not. Every other test runs
 * on H2, and H2 is the more forgiving of the two: it accepted a query that
 * PostgreSQL rejected twice over, so 193 green tests shipped a directory page
 * that answered every request with a 500 in production.
 *
 * <dl>
 *   <dt>{@code function lower(bytea) does not exist}</dt>
 *   <dd>A null bound inside {@code lower(?)} has no type for PostgreSQL to
 *       infer, so the driver sends it as {@code bytea}.</dd>
 *   <dt>{@code for SELECT DISTINCT, ORDER BY expressions must appear in select
 *       list}</dt>
 *   <dd>The category join needed a {@code distinct}, and the ordering is a
 *       {@code case} expression that is not selected.</dd>
 * </dl>
 *
 * <p>Neither is visible in the Java, in review, or on H2 — only in the SQL a
 * real server refuses to plan. So the guard has to be a real server.
 *
 * <p><b>Skipped unless {@code TEST_POSTGRES_URL} is set,</b> so
 * {@code ./gradlew build} still works with nothing installed. CI sets it
 * against a postgres service container, which is what makes this run on every
 * push rather than only when somebody remembers.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_POSTGRES_URL", matches = ".+")
class PostgresSupplierSearchTest {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_POSTGRES_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_POSTGRES_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // The same contract as production: Flyway builds the schema from the
        // postgresql migrations ({vendor} resolves from the URL) and Hibernate
        // only checks the entities still match it.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private SupplierRepository suppliers;

    @BeforeEach
    void setUp() {
        suppliers.deleteAll();

        suppliers.save(supplier("Nordlicht Solar", "Hamburg", "Hamburg",
                SupplierTrade.WHOLESALE, ExportStance.YES,
                Set.of(Category.PANEL, Category.MOUNTING)));
        suppliers.save(supplier("Rheinwatt", "Köln", "Nordrhein-Westfalen",
                SupplierTrade.BOTH, ExportStance.ON_REQUEST,
                Set.of(Category.INVERTER, Category.BATTERY)));
        suppliers.save(supplier("Alpenstrom", "München", "Bayern",
                SupplierTrade.WHOLESALE, ExportStance.NO,
                Set.of(Category.PANEL)));
    }

    /**
     * No filters at all: every parameter null except the term, which is the
     * exact shape of a first visit to /suppliers and the request that failed.
     */
    @Test
    void unfilteredSearchRuns() {
        assertThat(suppliers.search(null, null, null, "")).hasSize(3);
    }

    /**
     * Every combination of the four filters, because the failure was in how a
     * parameter was bound rather than in any one clause -- so any combination
     * could have carried it, and testing the convenient one proves nothing.
     */
    @Test
    void everyCombinationOfFiltersRuns() {
        List<Category> categories = java.util.Arrays.asList(null, Category.PANEL, Category.BATTERY);
        List<SupplierTrade> trades = java.util.Arrays.asList(null, SupplierTrade.WHOLESALE, SupplierTrade.BOTH);
        List<ExportStance> stances = java.util.Arrays.asList(null, ExportStance.YES, ExportStance.NO);
        List<String> terms = List.of("", "hamburg", "Bayern", "nothing matches this");

        int combinations = 0;
        for (Category category : categories) {
            for (SupplierTrade trade : trades) {
                for (ExportStance stance : stances) {
                    for (String term : terms) {
                        // The assertion is that it plans and runs at all: a
                        // query PostgreSQL refuses throws here rather than
                        // returning something wrong.
                        assertThat(suppliers.search(category, trade, stance, term)).isNotNull();
                        combinations++;
                    }
                }
            }
        }
        assertThat(combinations).isEqualTo(108);
    }

    /** The ordering is a case expression, which is what distinct fell over on. */
    @Test
    void confirmedExportersComeFirst() {
        List<Supplier> found = suppliers.search(null, null, null, "");

        assertThat(found).extracting(Supplier::getExportStance)
                .containsExactly(ExportStance.YES, ExportStance.ON_REQUEST, ExportStance.NO);
    }

    /**
     * A supplier stocking two categories must appear once, not once per
     * category. This is what the removed {@code distinct} was hiding.
     */
    @Test
    void aSupplierStockingSeveralCategoriesIsListedOnce() {
        assertThat(suppliers.search(Category.PANEL, null, null, ""))
                .extracting(Supplier::getName)
                .containsExactly("Nordlicht Solar", "Alpenstrom");
    }

    @Test
    void filtersNarrowTheResults() {
        assertThat(suppliers.search(null, null, ExportStance.YES, "")).hasSize(1);
        assertThat(suppliers.search(Category.BATTERY, null, null, "")).hasSize(1);
        assertThat(suppliers.search(null, SupplierTrade.WHOLESALE, null, "")).hasSize(2);
        assertThat(suppliers.search(null, null, null, "hamburg")).hasSize(1);
        assertThat(suppliers.search(null, null, null, "Bayern")).hasSize(1);
        assertThat(suppliers.search(null, null, null, "nothing matches this")).isEmpty();
    }

    /** Searching is case-insensitive on a server that does not fold case for you. */
    @Test
    void searchIgnoresCase() {
        assertThat(suppliers.search(null, null, null, "HAMBURG")).hasSize(1);
        assertThat(suppliers.search(null, null, null, "nordlicht")).hasSize(1);
    }

    private static Supplier supplier(String name, String city, String region,
                                     SupplierTrade trade, ExportStance stance,
                                     Set<Category> categories) {
        Supplier supplier = new Supplier(name, city);
        supplier.setRegion(region);
        supplier.setTrade(trade);
        supplier.setExportStance(stance);
        supplier.setCategories(categories);
        return supplier;
    }
}
