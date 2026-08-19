package com.shoppingapp.shoppingwebapp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The delivery estimate is one setting, and every page that quotes it reads
 * that setting.
 *
 * <p>It used to be a setting <em>and</em> a sentence typed into the home page,
 * the product page and the dispatch email. Changing it left those telling
 * customers something the terms of sale contradicted -- and a delivery promise
 * that disagrees with itself is the kind that gets argued about.
 *
 * <p>The whole application runs here with a deliberately unmistakable value, so
 * a page that kept its own copy shows up as the default text still being there.
 */
@SpringBootTest(properties = "app.business.delivery-estimate=2 to 3 weeks")
@AutoConfigureMockMvc
class DeliveryEstimateTest {

    private static final String DEFAULT = "5 to 10 working days";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theHomePageQuotesTheConfiguredEstimate() throws Exception {
        String html = body("/");

        assertThat(html).contains("2 to 3 weeks").doesNotContain(DEFAULT);
    }

    @Test
    void theProductPageQuotesTheConfiguredEstimate() throws Exception {
        String html = body("/products/1");

        assertThat(html).contains("2 to 3 weeks").doesNotContain(DEFAULT);
    }

    @Test
    void theTermsQuoteTheConfiguredEstimate() throws Exception {
        String html = body("/terms");

        assertThat(html).contains("2 to 3 weeks").doesNotContain(DEFAULT);
    }

    private String body(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
