package com.shoppingapp.shoppingwebapp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The pages a customer reads before deciding whether to trust the shop, so
 * every one of them has to work without an account.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LegalPagesTest {

    private static final String[] PAGES = {"/terms", "/returns", "/privacy", "/contact"};

    @Autowired
    private MockMvc mockMvc;

    /**
     * Whitespace collapsed before matching. Prose in a template wraps wherever
     * the line got long, so a sentence is not a single run of text in the
     * source -- asserting on the raw HTML would mean writing the templates to
     * suit the tests rather than to read well.
     */
    private String textOf(String page) throws Exception {
        return mockMvc.perform(get(page))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .replaceAll("\\s+", " ");
    }

    @Test
    void allFourAreReadableWithoutSigningIn() throws Exception {
        for (String page : PAGES) {
            mockMvc.perform(get(page)).andExpect(status().isOk());
        }
    }

    @Test
    void eachPageIsLinkedFromTheFooterOfEveryPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(content().string(containsString("href=\"/terms\"")))
                .andExpect(content().string(containsString("href=\"/returns\"")))
                .andExpect(content().string(containsString("href=\"/privacy\"")))
                .andExpect(content().string(containsString("href=\"/contact\"")));
    }

    /**
     * Nothing here may quietly claim a right the law already gives, or reduce
     * one. These check the substance is actually on the page.
     */
    @Test
    void theTermsCoverPriceCurrencyDeliveryAndFaults() throws Exception {
        assertThat(textOf("/terms"))
                .contains("Nigerian naira")
                .contains("fixed when you place the order")
                .contains("Federal Competition and Consumer Protection Act")
                .contains("never see or store your card number");
    }

    @Test
    void theReturnsPageSeparatesChangingYourMindFromSomethingBeingWrong() throws Exception {
        assertThat(textOf("/returns"))
                .contains("If you change your mind")
                .contains("If something is wrong")
                .contains("We pay the return cost");
    }

    @Test
    void thePrivacyPageNamesTheActAndWhatIsHeld() throws Exception {
        assertThat(textOf("/privacy"))
                .contains("Nigeria Data Protection Act 2023")
                .contains("bcrypt")
                .contains("never hold your card number")
                .contains("Nigeria Data Protection Commission");
    }

    /**
     * The point of the draft notice: an incomplete policy must not be able to
     * pass for a finished one just because it is published.
     */
    @Test
    void anIncompletePolicySaysSo() throws Exception {
        for (String page : PAGES) {
            mockMvc.perform(get(page))
                    .andExpect(content().string(containsString("Draft &mdash; not yet in force")));
        }
    }

    @SpringBootTest(properties = {
            "app.business.legal-name=SolarUpgrade Energy Limited",
            "app.business.registration-number=RC 1234567",
            "app.business.address=12 Ozumba Mbadiwe Avenue, Victoria Island, Lagos",
            "app.business.phone=+234 800 000 0000",
            "app.business.support-email=support@example.test"})
    @AutoConfigureMockMvc
    @org.junit.jupiter.api.Nested
    class OnceTheDetailsAreSupplied {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void theDraftNoticeGoesAndTheRealDetailsAppear() throws Exception {
            mockMvc.perform(get("/terms"))
                    .andExpect(content().string(not(containsString("Draft &mdash; not yet in force"))))
                    .andExpect(content().string(containsString("SolarUpgrade Energy Limited")))
                    .andExpect(content().string(containsString("RC 1234567")))
                    .andExpect(content().string(not(containsString("[RC number]"))));
        }

        @Test
        void theContactPageCarriesEveryWayToReachUs() throws Exception {
            mockMvc.perform(get("/contact"))
                    .andExpect(content().string(containsString("support@example.test")))
                    .andExpect(content().string(containsString("+234 800 000 0000")))
                    .andExpect(content().string(containsString("Ozumba Mbadiwe")));
        }
    }
}
