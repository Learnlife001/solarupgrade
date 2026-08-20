package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.StockMovementReason;
import com.shoppingapp.shoppingwebapp.model.Role;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.AdminActionRepository;
import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import com.shoppingapp.shoppingwebapp.repository.StockMovementRepository;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Running the catalogue from the back office.
 *
 * <p>Not {@code @Transactional}: these post a form and then read what the shop
 * serves on a later request, which is a different transaction. A rollback-per-
 * test would hide anything that only goes wrong once the change is committed —
 * the mistake that let a detached-entity bug reach production earlier in this
 * project.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminProductTest {

    private static final String ADMIN = "product-admin@example.test";
    private static final String CUSTOMER = "product-customer@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository products;

    @Autowired
    private UserRepository users;

    @Autowired
    private AdminActionRepository auditEntries;

    @Autowired
    private StockMovementRepository stockMovements;

    private Product panel;

    /** Marks the rows this class creates, so it can clear its own and no more. */
    private static final String TEST_PREFIX = "ZZ Test ";

    @BeforeEach
    void setUp() {
        auditEntries.deleteAll();
        // Only this class's rows. deleteAll() fails on the seeded catalogue --
        // those products have specification rows pointing at them -- and
        // clearing the whole table would in any case delete data the other
        // tests in this run are using.
        products.findAll().stream()
                .filter(product -> product.getName().startsWith(TEST_PREFIX))
                .forEach(product -> {
                    // The stock ledger points at the product row, so it goes
                    // first. The application archives rather than deletes.
                    stockMovements.deleteAll(stockMovements
                            .findByProductIdOrderByHappenedAtDescIdDesc(product.getId(), Limit.of(1000)));
                    products.delete(product);
                });

        panel = products.save(new Product(TEST_PREFIX + "Panel", "A panel for the test.",
                new BigDecimal("380000.00"), Category.PANEL, 5, "/images/panel-450w.svg"));

        account(ADMIN, Role.ADMIN);
        account(CUSTOMER, Role.USER);
    }

    private void account(String email, Role role) {
        users.findByEmail(email).ifPresentOrElse(existing -> {
        }, () -> {
            User user = new User(email, "hash", "Test Person");
            user.markEmailVerified();
            user.setRole(role);
            users.save(user);
        });
    }

    @Test
    void anAdminCanAddAProduct() throws Exception {
        mockMvc.perform(post("/admin/products")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("name", TEST_PREFIX + "Inverter")
                        .param("description", "An inverter added from the admin area.")
                        .param("price", "1450000.00")
                        .param("category", "INVERTER")
                        .param("stock", "4")
                        .param("imageUrl", "/images/inverter-5kw.svg"))
                .andExpect(redirectedUrl("/admin/products"));

        assertThat(products.findAll())
                .extracting(Product::getName)
                .contains(TEST_PREFIX + "Inverter");
    }

    /** The whole point: a price change without a migration and a deploy. */
    @Test
    void anAdminCanChangeAPrice() throws Exception {
        mockMvc.perform(post("/admin/products/" + panel.getId())
                        .with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("name", panel.getName())
                        .param("description", panel.getDescription())
                        .param("price", "399000.00")
                        .param("category", "PANEL")
                        .param("stock", String.valueOf(panel.getStock()))
                        .param("imageUrl", panel.getImageUrl()))
                .andExpect(redirectedUrl("/admin/products"));

        assertThat(products.findById(panel.getId()).orElseThrow().getPrice())
                .isEqualByComparingTo("399000.00");
    }

    @Test
    void aPriceOfZeroIsRefused() throws Exception {
        mockMvc.perform(post("/admin/products")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("name", TEST_PREFIX + "Free Panel")
                        .param("description", "Should not be accepted.")
                        .param("price", "0")
                        .param("category", "PANEL")
                        .param("stock", "1"))
                // Back to the form, not a redirect: the page has to say why.
                .andExpect(status().isOk());

        assertThat(products.findAll()).extracting(Product::getName).doesNotContain(TEST_PREFIX + "Free Panel");
    }

    /**
     * Archiving is the substitute for deleting, so it has to actually take the
     * product out of the shop.
     */
    @Test
    void anArchivedProductLeavesTheShop() throws Exception {
        mockMvc.perform(post("/admin/products/" + panel.getId() + "/archive")
                .with(user(ADMIN).roles("ADMIN")).with(csrf()));

        String catalogue = mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(catalogue).doesNotContain(TEST_PREFIX + "Panel");
    }

    /** And its page must stop answering, or an old link still sells it. */
    @Test
    void anArchivedProductHasNoPage() throws Exception {
        mockMvc.perform(post("/admin/products/" + panel.getId() + "/archive")
                .with(user(ADMIN).roles("ADMIN")).with(csrf()));

        mockMvc.perform(get("/products/" + panel.getId()))
                .andExpect(status().isNotFound());
    }

    /**
     * Hiding it from the catalogue while still accepting its id in the basket
     * would be a hole rather than a retirement. The id is in the page source of
     * every order that ever contained it.
     *
     * <p>The reply is a sentence rather than a 404: whoever clicked this had a
     * page open from before the product was retired, and deserves to be told
     * why nothing happened.
     */
    @Test
    void anArchivedProductCannotBeAddedToABasket() throws Exception {
        mockMvc.perform(post("/admin/products/" + panel.getId() + "/archive")
                .with(user(ADMIN).roles("ADMIN")).with(csrf()));

        mockMvc.perform(post("/cart/add")
                        .with(user(CUSTOMER)).with(csrf())
                        .param("productId", String.valueOf(panel.getId()))
                        .param("quantity", "1"))
                .andExpect(redirectedUrl("/products"));
    }

    /** Archived is not deleted: the row is still there, and comes back. */
    @Test
    void anArchivedProductCanBeRestored() throws Exception {
        mockMvc.perform(post("/admin/products/" + panel.getId() + "/archive")
                .with(user(ADMIN).roles("ADMIN")).with(csrf()));
        mockMvc.perform(post("/admin/products/" + panel.getId() + "/restore")
                .with(user(ADMIN).roles("ADMIN")).with(csrf()));

        assertThat(products.findById(panel.getId()).orElseThrow().isArchived()).isFalse();

        String catalogue = mockMvc.perform(get("/products"))
                .andReturn().getResponse().getContentAsString();
        assertThat(catalogue).contains(TEST_PREFIX + "Panel");
    }

    /**
     * "Who changed this price, and from what" is the first question asked when
     * a sale goes wrong, so the trail records the old value as well as the new.
     */
    @Test
    void aPriceChangeIsRecordedWithWhatItWas() throws Exception {
        mockMvc.perform(post("/admin/products/" + panel.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf())
                .param("name", panel.getName())
                .param("description", panel.getDescription())
                .param("price", "399000.00")
                .param("category", "PANEL")
                .param("stock", "5")
                .param("imageUrl", panel.getImageUrl()));

        assertThat(auditEntries.findAll())
                .anySatisfy(entry -> {
                    assertThat(entry.getActor()).isEqualTo(ADMIN);
                    assertThat(entry.getDetail()).contains("380,000").contains("399,000");
                });
    }

    /**
     * Every route that moves stock has to leave a movement behind. The edit
     * form set the figure directly at first, so saving a product moved its
     * stock silently while the stock-take control beside it recorded properly
     * -- a figure that is sometimes explained is worse than one that never is,
     * because it gets trusted.
     */
    @Test
    void changingStockOnTheEditFormIsRecordedAsAStockTake() throws Exception {
        mockMvc.perform(post("/admin/products/" + panel.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf())
                .param("name", panel.getName())
                .param("description", panel.getDescription())
                .param("price", panel.getPrice().toPlainString())
                .param("category", "PANEL")
                .param("stock", "42")
                .param("imageUrl", panel.getImageUrl()));

        assertThat(products.findById(panel.getId()).orElseThrow().getStock()).isEqualTo(42);
        assertThat(stockMovements
                .findByProductIdOrderByHappenedAtDescIdDesc(panel.getId(), Limit.of(10)))
                .anySatisfy(movement -> {
                    assertThat(movement.getReason()).isEqualTo(StockMovementReason.STOCK_TAKE);
                    assertThat(movement.getChange()).isEqualTo(37);
                    assertThat(movement.getResultingStock()).isEqualTo(42);
                    assertThat(movement.getActor()).isEqualTo(ADMIN);
                });
    }

    /** A new product's opening figure is a movement, not a quantity from nowhere. */
    @Test
    void aNewProductsOpeningStockIsRecorded() throws Exception {
        mockMvc.perform(post("/admin/products")
                .with(user(ADMIN).roles("ADMIN")).with(csrf())
                .param("name", TEST_PREFIX + "Opening")
                .param("description", "Stock recorded from the first row.")
                .param("price", "1000.00")
                .param("category", "PANEL")
                .param("stock", "8"));

        Product created = products.findAll().stream()
                .filter(product -> product.getName().equals(TEST_PREFIX + "Opening"))
                .findFirst().orElseThrow();

        assertThat(created.getStock()).isEqualTo(8);
        assertThat(stockMovements
                .findByProductIdOrderByHappenedAtDescIdDesc(created.getId(), Limit.of(10)))
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.getReason()).isEqualTo(StockMovementReason.STOCK_TAKE);
                    assertThat(movement.getChange()).isEqualTo(8);
                });
    }

    @Test
    void aCustomerCannotReachTheProductForm() throws Exception {
        mockMvc.perform(get("/admin/products/new").with(user(CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCustomerCannotArchiveAProduct() throws Exception {
        mockMvc.perform(post("/admin/products/" + panel.getId() + "/archive")
                        .with(user(CUSTOMER)).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(products.findById(panel.getId()).orElseThrow().isArchived()).isFalse();
    }
}
