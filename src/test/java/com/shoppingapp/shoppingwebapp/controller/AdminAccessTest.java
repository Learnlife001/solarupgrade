package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.model.Role;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin area is protected by the role and nothing else — not by the URL
 * being obscure, and not by the hostname it is reached on. These check that,
 * because it is the assumption everything else in the back office rests on.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminAccessTest {

    private static final String CUSTOMER = "admin-access-customer@example.test";
    private static final String ADMIN = "admin-access-admin@example.test";

    /** Every page and action in the back office. */
    private static final String[] PAGES = {
            "/admin", "/admin/orders", "/admin/orders/1", "/admin/products"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail(CUSTOMER)
                .orElseGet(() -> userRepository.save(new User(CUSTOMER, "hash", "Customer")));
        User admin = userRepository.findByEmail(ADMIN)
                .orElseGet(() -> userRepository.save(new User(ADMIN, "hash", "Admin")));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
    }

    @Test
    void signedOutVisitorsAreSentToSignIn() throws Exception {
        for (String page : PAGES) {
            mockMvc.perform(get(page)).andExpect(status().is3xxRedirection());
        }
    }

    /**
     * The case that matters: a perfectly ordinary signed-in customer, who is
     * exactly who must not see other people's orders.
     */
    @Test
    void anOrdinaryCustomerIsRefused() throws Exception {
        for (String page : PAGES) {
            mockMvc.perform(get(page).with(user(CUSTOMER).roles("USER")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void aCustomerCannotShipOrCancelOrChangeStockEither() throws Exception {
        mockMvc.perform(post("/admin/orders/1/ship").with(user(CUSTOMER).roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/orders/1/cancel").with(user(CUSTOMER).roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/products/1/stock").param("stock", "99")
                        .with(user(CUSTOMER).roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminGetsIn() throws Exception {
        mockMvc.perform(get("/admin").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/orders").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/products").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk());
    }

    /** State-changing admin actions are posts, and CSRF applies to them. */
    @Test
    void adminActionsStillRequireACsrfToken() throws Exception {
        mockMvc.perform(post("/admin/orders/1/ship").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isForbidden());
    }
}
