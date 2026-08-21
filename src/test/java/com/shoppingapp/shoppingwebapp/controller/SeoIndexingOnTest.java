package com.shoppingapp.shoppingwebapp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The shop with indexing switched on, as it will be once the catalogue is real.
 *
 * <p>Its own class because it needs its own setting, and therefore its own
 * application context.
 *
 * <p>What matters here is what stays shut. Turning indexing on must not put a
 * basket, an order or the back office into a search engine: those are noindex
 * on their own account, not merely because the shop-wide setting happened to be
 * off.
 */
@SpringBootTest(properties = {
        "app.seo.indexable=true",
        "app.base-url=https://solarupgrade.onrender.com"})
@AutoConfigureMockMvc
class SeoIndexingOnTest {

    @Autowired
    private MockMvc mockMvc;

    private String body(String path) throws Exception {
        return mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString();
    }

    /**
     * Signed in as an account that actually exists. Authenticating as a name
     * present only in the security context makes the shared page furniture
     * fail while it looks the customer up, and the assertion then reads an
     * empty body.
     */
    private String signedIn(String path) throws Exception {
        return mockMvc.perform(get(path).with(user("demo@solarupgrade.example")))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void theShopIsIndexable() throws Exception {
        assertThat(body("/products")).contains("content=\"index, follow\"");
        assertThat(body("/")).contains("content=\"index, follow\"");
    }

    @Test
    void aBasketIsStillNeverIndexed() throws Exception {
        assertThat(signedIn("/cart")).contains("noindex, nofollow");
    }

    @Test
    void anOrderListIsStillNeverIndexed() throws Exception {
        assertThat(signedIn("/orders")).contains("noindex, nofollow");
    }

    @Test
    void robotsNowInvitesCrawlersAndPointsAtTheSitemap() throws Exception {
        String robots = body("/robots.txt");

        assertThat(robots)
                .contains("Sitemap: https://solarupgrade.onrender.com/sitemap.xml")
                .contains("Disallow: /admin/")
                .contains("Disallow: /cart")
                .contains("Disallow: /orders");
    }
}
