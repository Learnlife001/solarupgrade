package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.ExportStance;
import com.shoppingapp.shoppingwebapp.model.Supplier;
import com.shoppingapp.shoppingwebapp.model.SupplierTrade;
import com.shoppingapp.shoppingwebapp.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SupplierDirectoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SupplierRepository suppliers;

    private Supplier exporter;

    @BeforeEach
    void setUp() {
        suppliers.deleteAll();

        exporter = new Supplier("Hansa Solar Test", "Bremen");
        exporter.setRegion("Bremen");
        exporter.setTrade(SupplierTrade.WHOLESALE);
        exporter.setExportStance(ExportStance.YES);
        exporter.setCategories(Set.of(Category.PANEL));
        exporter.setMinimumOrder("1 pallet");
        exporter.markVerified("Emailed export@, they confirmed FOB Bremerhaven", Instant.now());
        exporter = suppliers.save(exporter);

        Supplier euOnly = new Supplier("Binnenland Test", "Leipzig");
        euOnly.setTrade(SupplierTrade.RETAIL);
        euOnly.setExportStance(ExportStance.NO);
        euOnly.setCategories(Set.of(Category.BATTERY));
        suppliers.save(euOnly);
    }

    private String body(String path) throws Exception {
        return mockMvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().replaceAll("\\s+", " ");
    }

    /** People read this before deciding to buy, so before they have an account. */
    @Test
    void theDirectoryIsPublic() throws Exception {
        mockMvc.perform(get("/suppliers")).andExpect(status().isOk());
        mockMvc.perform(get("/suppliers/" + exporter.getId())).andExpect(status().isOk());
    }

    @Test
    void theHonestPanelAboutDirectImportIsOnThePage() throws Exception {
        assertThat(body("/suppliers"))
                .contains("What direct import actually involves")
                .contains("Minimum orders")
                .contains("Duty and clearing")
                .contains("four to eight weeks")
                // ...and the alternative, stated plainly rather than hidden.
                .contains("our catalogue");
    }

    @Test
    void filteringByExportStanceNarrowsTheList() throws Exception {
        assertThat(body("/suppliers?stance=YES"))
                .contains("Hansa Solar Test")
                .doesNotContain("Binnenland Test");
    }

    @Test
    void filteringByCategoryNarrowsTheList() throws Exception {
        assertThat(body("/suppliers?category=BATTERY"))
                .contains("Binnenland Test")
                .doesNotContain("Hansa Solar Test");
    }

    @Test
    void searchMatchesNameAndCity() throws Exception {
        assertThat(body("/suppliers?q=bremen")).contains("Hansa Solar Test");
        assertThat(body("/suppliers?q=binnen")).contains("Binnenland Test");
    }

    /**
     * The two empty states say different things. "Nothing matches" when the
     * directory is simply empty makes the site look broken rather than honest.
     */
    @Test
    void anEmptyDirectorySaysSoDifferentlyFromAnEmptyFilter() throws Exception {
        assertThat(body("/suppliers?q=nothingmatchesthis"))
                .contains("Nothing matches that");

        suppliers.deleteAll();
        assertThat(body("/suppliers"))
                .contains("Nothing listed yet")
                .doesNotContain("Nothing matches that");
    }

    /** The verification date is what separates this from a scraped list. */
    @Test
    void everyEntryShowsWhenItWasLastChecked() throws Exception {
        String page = body("/suppliers");
        assertThat(page).contains("Checked");
        assertThat(page).contains("Not yet checked");
    }

    @Test
    void anEntryNobodyCheckedSaysSoOnItsOwnPage() throws Exception {
        Supplier unchecked = suppliers.findAll().stream()
                .filter(s -> !s.isVerified()).findFirst().orElseThrow();

        assertThat(body("/suppliers/" + unchecked.getId()))
                .contains("Not checked yet")
                .contains("Nobody has confirmed these details");
    }

    /**
     * An entry checked a year ago must warn before its terms are read, not
     * after — somebody is about to email a company on the strength of them.
     */
    @Test
    void aStaleEntryWarnsAtTheTop() throws Exception {
        exporter.markVerified("Emailed long ago", Instant.now().minus(400, ChronoUnit.DAYS));
        suppliers.save(exporter);

        assertThat(body("/suppliers/" + exporter.getId()))
                .contains("may be out of date");
    }

    @Test
    void theDetailPageCarriesTheTermsThatMatter() throws Exception {
        assertThat(body("/suppliers/" + exporter.getId()))
                .contains("Exports to Nigeria")
                .contains("1 pallet")
                .contains("Emailed export@, they confirmed FOB Bremerhaven")
                // The shop, offered as the alternative rather than concealed.
                .contains("Or skip all of it");
    }

    /** No affiliation, and no pretence that the terms are guaranteed. */
    @Test
    void theDirectoryDisclaimsAffiliation() throws Exception {
        assertThat(body("/suppliers"))
                .contains("not affiliated")
                .contains("earn nothing from listing them");
    }
}
