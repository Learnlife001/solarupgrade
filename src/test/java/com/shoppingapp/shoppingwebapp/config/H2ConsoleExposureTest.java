package com.shoppingapp.shoppingwebapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With the console switched off -- which is every profile that points at a real
 * database -- its path must be treated like any other unknown URL, not waved
 * through.
 *
 * <p>The rules were once unconditional while the console was enabled by
 * default, so a deployment that lost SPRING_PROFILES_ACTIVE would have booted
 * on in-memory H2 with an unauthenticated database console on the internet.
 */
@SpringBootTest(properties = "spring.h2.console.enabled=false")
@AutoConfigureMockMvc
class H2ConsoleExposureTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theConsoleIsNotPubliclyReachableWhenItIsSwitchedOff() throws Exception {
        mockMvc.perform(get("/h2-console/"))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * The CSRF exemption has to go with it. An exemption left behind on a path
     * that later gains a handler is a hole waiting for one.
     */
    @Test
    void theConsolePathIsNotExemptFromCsrfWhenItIsSwitchedOff() throws Exception {
        mockMvc.perform(post("/h2-console/"))
                .andExpect(status().isForbidden());
    }
}
