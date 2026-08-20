package com.shoppingapp.shoppingwebapp.repository;

import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderStatus;
import com.shoppingapp.shoppingwebapp.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The admin order search, against a real PostgreSQL server.
 *
 * <p>This query has the same shape as the one that took {@code /suppliers} down
 * in production: a search term inside {@code lower()} and a nullable enum
 * filter. H2 accepted a null in the first of those and PostgreSQL did not,
 * which is how a page that answered every request with a 500 shipped with a
 * green build. The same mistake in the order search would take the back office
 * with it, so the same guard applies.
 *
 * <p>Skipped unless {@code TEST_POSTGRES_URL} is set; CI sets it against a
 * postgres service container.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_POSTGRES_URL", matches = ".+")
class PostgresOrderSearchTest {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_POSTGRES_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_POSTGRES_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private OrderRepository orders;

    @Autowired
    private UserRepository users;

    private Order saved;

    @BeforeEach
    void setUp() {
        orders.deleteAll();
        users.deleteAll();

        User user = users.save(new User("adaeze@example.test", "hash", "Adaeze Okafor"));
        saved = orders.save(new Order(user, "Adaeze Okafor", "14 Adeola Odeku Street"));
    }

    private PageRequest firstPage() {
        return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "placedAt"));
    }

    /**
     * Every combination of the two filters, because the failure being guarded
     * against is in how a parameter is bound rather than in any one clause.
     */
    @Test
    void everyCombinationOfStatusAndTermRuns() {
        List<OrderStatus> statuses = Arrays.asList(null, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID);
        List<String> terms = List.of("", "adaeze", "OKAFOR", "1", "nothing matches this");

        int combinations = 0;
        for (OrderStatus status : statuses) {
            for (String term : terms) {
                assertThat(orders.searchOrderIds(status, term, firstPage())).isNotNull();
                combinations++;
            }
        }
        assertThat(combinations).isEqualTo(15);
    }

    /** No term at all is the plain list, which is the most-used case of the two. */
    @Test
    void anEmptyTermReturnsEverything() {
        assertThat(orders.searchOrderIds(null, "", firstPage()).getTotalElements()).isEqualTo(1);
    }

    @Test
    void theEmailSearchWorksOnPostgres() {
        assertThat(orders.searchOrderIds(null, "adaeze", firstPage()).getContent())
                .containsExactly(saved.getId());
    }

    /** Case folding is the server's, and the two servers do not agree by default. */
    @Test
    void theNameSearchIgnoresCaseOnPostgres() {
        assertThat(orders.searchOrderIds(null, "okafor", firstPage()).getContent())
                .containsExactly(saved.getId());
    }

    /**
     * The id is compared as text so a number and a name can share one box.
     * That cast is another thing PostgreSQL is stricter about than H2.
     */
    @Test
    void theOrderNumberSearchWorksOnPostgres() {
        assertThat(orders.searchOrderIds(null, String.valueOf(saved.getId()), firstPage()).getContent())
                .containsExactly(saved.getId());
    }

    /** The count query is spelled out separately, so it is worth running too. */
    @Test
    void theCountQueryAgreesWithTheResults() {
        var page = orders.searchOrderIds(OrderStatus.PENDING_PAYMENT, "adaeze", firstPage());

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
    }
}
