package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.model.AdminActionType;
import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.ExportStance;
import com.shoppingapp.shoppingwebapp.model.Role;
import com.shoppingapp.shoppingwebapp.model.Supplier;
import com.shoppingapp.shoppingwebapp.model.SupplierTrade;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.AdminActionRepository;
import com.shoppingapp.shoppingwebapp.repository.SupplierRepository;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Not @Transactional: these go through the request path, which spans
 * transactions, and a transactional test hid a real bug the last time.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminSupplierTest {

    private static final String ADMIN = "supplier-admin@example.test";
    private static final String CUSTOMER = "supplier-customer@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private SupplierRepository suppliers;

    @Autowired
    private AdminActionRepository adminActions;

    @BeforeEach
    void setUp() {
        User admin = users.findByEmail(ADMIN)
                .orElseGet(() -> users.save(new User(ADMIN, "hash", "Supplier Admin")));
        admin.setRole(Role.ADMIN);
        users.save(admin);
        users.findByEmail(CUSTOMER).orElseGet(() -> users.save(new User(CUSTOMER, "hash", "Customer")));
        suppliers.deleteAll();
        adminActions.deleteAll();
    }

    @AfterEach
    void tearDown() {
        suppliers.deleteAll();
        adminActions.deleteAll();
    }

    private Supplier existing() {
        Supplier supplier = new Supplier("Vorhandene GmbH", "Essen");
        supplier.setTrade(SupplierTrade.WHOLESALE);
        supplier.setExportStance(ExportStance.UNKNOWN);
        supplier.setCategories(Set.of(Category.PANEL));
        return suppliers.save(supplier);
    }

    /** The directory is maintained, not crowdsourced. */
    @Test
    void anOrdinaryCustomerCannotReachTheAdminPages() throws Exception {
        mockMvc.perform(get("/admin/suppliers").with(user(CUSTOMER).roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/suppliers")
                        .param("name", "Sneaky").param("city", "Nowhere")
                        .with(user(CUSTOMER).roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminCanAddASupplierAndItIsRecorded() throws Exception {
        mockMvc.perform(post("/admin/suppliers")
                        .param("name", "Neue Solar GmbH")
                        .param("city", "Dortmund")
                        .param("region", "Nordrhein-Westfalen")
                        .param("trade", "WHOLESALE")
                        .param("exportStance", "YES")
                        .param("categories", "PANEL")
                        .param("categories", "INVERTER")
                        .param("minimumOrder", "1 pallet")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Supplier saved = suppliers.findAll().stream()
                .filter(s -> s.getName().equals("Neue Solar GmbH")).findFirst().orElseThrow();
        assertThat(saved.getExportStance()).isEqualTo(ExportStance.YES);
        assertThat(saved.getCategories()).containsExactlyInAnyOrder(Category.PANEL, Category.INVERTER);
        // A new entry has never been checked, whatever else was filled in.
        assertThat(saved.isVerified()).isFalse();

        assertThat(adminActions.findAll())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAction()).isEqualTo(AdminActionType.SUPPLIER_ADDED);
                    assertThat(entry.getActor()).isEqualTo(ADMIN);
                });
    }

    @Test
    void aBlankNameIsRefused() throws Exception {
        mockMvc.perform(post("/admin/suppliers")
                        .param("name", "  ").param("city", "Berlin")
                        .param("trade", "RETAIL").param("exportStance", "UNKNOWN")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());

        assertThat(suppliers.findAll()).isEmpty();
    }

    @Test
    void markingAnEntryCheckedStampsTheDateAndRecordsIt() throws Exception {
        Supplier supplier = existing();

        mockMvc.perform(post("/admin/suppliers/" + supplier.getId() + "/verify")
                        .param("howVerified", "Emailed info@, they replied confirming EXW")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Supplier reloaded = suppliers.findById(supplier.getId()).orElseThrow();
        assertThat(reloaded.isVerified()).isTrue();
        assertThat(reloaded.getHowVerified()).contains("Emailed info@");
        assertThat(adminActions.findAll())
                .anySatisfy(entry ->
                        assertThat(entry.getAction()).isEqualTo(AdminActionType.SUPPLIER_VERIFIED));
    }

    /**
     * The point of the field: a date with no account of what was done is a
     * claim with nothing behind it, and the directory's whole value rests on
     * those claims being worth something.
     */
    @Test
    void aCheckWithNoAccountOfWhatWasDoneIsRefused() throws Exception {
        Supplier supplier = existing();

        mockMvc.perform(post("/admin/suppliers/" + supplier.getId() + "/verify")
                        .param("howVerified", "   ")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(suppliers.findById(supplier.getId()).orElseThrow().isVerified()).isFalse();
        assertThat(adminActions.findAll()).isEmpty();
    }

    @Test
    void removingAnEntryIsRecordedBeforeItGoes() throws Exception {
        Supplier supplier = existing();

        mockMvc.perform(post("/admin/suppliers/" + supplier.getId() + "/delete")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(suppliers.findById(supplier.getId())).isEmpty();
        assertThat(adminActions.findAll())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAction()).isEqualTo(AdminActionType.SUPPLIER_REMOVED);
                    // The name survives the row it described.
                    assertThat(entry.getDetail()).isEqualTo("Vorhandene GmbH");
                });
    }

    /**
     * No field for a contact person anywhere in the form. Leaving it out is
     * what keeps a named individual's details out of the database — stronger
     * than a note asking people not to type one.
     */
    @Test
    void theFormOffersNowhereToPutAContactPerson() throws Exception {
        String form = mockMvc.perform(get("/admin/suppliers/new").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(form)
                .doesNotContain("contactName")
                .doesNotContain("contactPerson")
                .contains("Never a named person");
    }
}
