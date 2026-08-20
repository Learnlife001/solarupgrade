package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.dto.CheckoutForm;
import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.Role;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.AdminActionRepository;
import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import com.shoppingapp.shoppingwebapp.service.CartService;
import com.shoppingapp.shoppingwebapp.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Downloading every order.
 *
 * <p>This file is what an accountant is handed, and the only copy of the shop's
 * trading history outside a database on a free hosting plan. It is also a file
 * built from things customers typed, opened in a spreadsheet — which is why the
 * formula case is tested here as well as in {@code CsvTest}: the guarantee is
 * only worth anything if the export actually uses it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderExportTest {

    private static final String ADMIN = "export-admin@example.test";
    private static final String CUSTOMER = "export-customer@example.test";

    /** What a customer could put in the name box to attack whoever opens this. */
    private static final String MALICIOUS_NAME = "=HYPERLINK(\"http://evil.example\",\"Click me\")";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProductRepository products;

    @Autowired
    private AdminActionRepository auditEntries;

    @BeforeEach
    void setUp() {
        account(ADMIN, Role.ADMIN);
        User customer = account(CUSTOMER, Role.USER);

        String unique = UUID.randomUUID().toString().substring(0, 8);
        Product product = products.save(new Product("Export Panel " + unique, "A panel.",
                new BigDecimal("1250.50"), Category.PANEL, 50, null));
        cartService.add(customer, product, 2);

        CheckoutForm form = new CheckoutForm();
        // The name a customer typed, carried all the way into the file.
        form.setShippingName(MALICIOUS_NAME);
        form.setShippingLine1("14 Adeola Odeku Street");
        form.setShippingCity("Lagos");
        form.setShippingState("Lagos");
        form.setShippingCountry("NG");
        form.setPaymentMethod(PaymentMethod.PAYPAL);
        orderService.placeOrder(customer, form);
    }

    private User account(String email, Role role) {
        return users.findByEmail(email).orElseGet(() -> {
            User created = new User(email, "hash", "Test Person");
            created.markEmailVerified();
            created.setRole(role);
            return users.save(created);
        });
    }

    private String download() throws Exception {
        var started = mockMvc.perform(get("/admin/orders.csv").with(user(ADMIN).roles("ADMIN")))
                .andReturn();
        // Streamed, so the response is only complete after the async dispatch.
        return mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void theFileHasAHeaderRowAndTheOrder() throws Exception {
        String csv = download();

        assertThat(csv).startsWith("order_number,placed_at_utc,status,customer_email");
        assertThat(csv).contains(CUSTOMER);
    }

    /**
     * The whole reason the export escapes anything. Without it, opening this
     * file runs what a customer typed into the name box.
     */
    @Test
    void aNameThatIsAFormulaCannotRunWhenTheFileIsOpened() throws Exception {
        String csv = download();

        assertThat(csv).contains("Click me");
        // Present, but defused: the cell begins with an apostrophe, so the
        // spreadsheet treats it as text.
        assertThat(csv).contains("\"'=HYPERLINK");
        assertThat(csv).doesNotContain(",=HYPERLINK");
    }

    /** The total is a number a spreadsheet can add up, not a formatted string. */
    @Test
    void theTotalIsPlainSoItCanBeSummed() throws Exception {
        String csv = download();

        assertThat(csv).contains("2501.00");
        assertThat(csv).doesNotContain("₦2,501");
    }

    @Test
    void theResponseIsADownloadWithADatedName() throws Exception {
        mockMvc.perform(get("/admin/orders.csv").with(user(ADMIN).roles("ADMIN")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.matchesPattern(
                                "attachment; filename=\"orders-\\d{4}-\\d{2}-\\d{2}\\.csv\"")));
    }

    /**
     * A bulk read of every customer's name and address is worth a line in the
     * record of who did what.
     */
    @Test
    void takingACopyIsRecorded() throws Exception {
        download();

        assertThat(auditEntries.findAll())
                .anySatisfy(entry -> {
                    assertThat(entry.getActor()).isEqualTo(ADMIN);
                    assertThat(entry.getDetail()).contains("Downloaded orders-");
                });
    }

    /** It is every customer's address in one file, so it is admin-only. */
    @Test
    void aCustomerCannotDownloadIt() throws Exception {
        mockMvc.perform(get("/admin/orders.csv").with(user(CUSTOMER)))
                .andExpect(status().isForbidden());
    }
}
