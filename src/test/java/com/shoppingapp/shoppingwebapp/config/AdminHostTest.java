package com.shoppingapp.shoppingwebapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin hostname is a front door, not a lock. Opening it lands on the
 * dashboard; getting past the dashboard is still a matter of holding the role.
 */
@SpringBootTest(properties = "app.admin.host=admin.example.test")
@AutoConfigureMockMvc
class AdminHostTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theAdminHostOpensOnTheDashboard() throws Exception {
        mockMvc.perform(get("/").with(req -> {
                    req.setServerName("admin.example.test");
                    return req;
                }))
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void theShopHostStillOpensOnTheShop() throws Exception {
        mockMvc.perform(get("/").with(req -> {
                    req.setServerName("solarupgrade.onrender.com");
                    return req;
                }))
                .andExpect(status().isOk());
    }

    /**
     * The important half. If the hostname granted anything, an attacker would
     * only need to set a Host header.
     */
    @Test
    void theHostnameGrantsNothingOnItsOwn() throws Exception {
        mockMvc.perform(get("/admin").with(req -> {
                    req.setServerName("admin.example.test");
                    return req;
                }))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/admin")
                        .with(user("nobody@example.test").roles("USER"))
                        .with(req -> {
                            req.setServerName("admin.example.test");
                            return req;
                        }))
                .andExpect(status().isForbidden());
    }
}
