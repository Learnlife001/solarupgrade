package com.shoppingapp.shoppingwebapp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the shop tells a search engine while its catalogue is still sample data.
 *
 * <p>The default is "nothing, please". Prices here are invented and the legal
 * pages say they are drafts; being indexed with that showing is worse than not
 * being found, because a search engine keeps its copy for weeks after either
 * changes. The tags and the sitemap are built and correct all the same, so
 * turning it on later is one setting rather than a project.
 */
@SpringBootTest(properties = "app.base-url=https://solarupgrade.onrender.com")
@AutoConfigureMockMvc
class SeoTest {

    @Autowired
    private MockMvc mockMvc;

    private String body(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void everyPageIsNoindexWhileTheCatalogueIsSampleData() throws Exception {
        assertThat(body("/products")).contains("name=\"robots\" content=\"noindex, nofollow\"");
        assertThat(body("/")).contains("noindex, nofollow");
    }

    /** And nothing is invited in at all. */
    @Test
    void robotsRefusesEverythingWhileNotIndexable() throws Exception {
        String robots = body("/robots.txt");

        assertThat(robots).contains("User-agent: *").contains("Disallow: /");
        assertThat(robots).doesNotContain("Sitemap:");
    }

    /** A crawler cannot sign in, so these two must be reachable without doing so. */
    @Test
    void robotsAndSitemapAreReachableWithoutSigningIn() throws Exception {
        mockMvc.perform(get("/robots.txt")).andExpect(status().isOk());
        mockMvc.perform(get("/sitemap.xml")).andExpect(status().isOk());
    }

    @Test
    void everyPageNamesTheOneAddressItShouldBeIndexedUnder() throws Exception {
        assertThat(body("/products"))
                .contains("rel=\"canonical\" href=\"https://solarupgrade.onrender.com/products\"");
    }

    /** The canonical URL is the path only: a query string is not a new page. */
    @Test
    void aFilteredListingIsStillTheSamePage() throws Exception {
        String filtered = mockMvc.perform(get("/products").param("category", "PANEL"))
                .andReturn().getResponse().getContentAsString();

        assertThat(filtered)
                .contains("rel=\"canonical\" href=\"https://solarupgrade.onrender.com/products\"");
    }

    @Test
    void aPageThatCanDescribeItselfDoes() throws Exception {
        String product = body("/products/1");

        // The product's own sentence, not the shop's general one.
        assertThat(product)
                .contains("name=\"description\"")
                .contains("High-efficiency mono PERC panel");
    }

    /**
     * The tags that decide what a link looks like when it is pasted into a
     * chat. Without them the preview is a bare URL.
     */
    @Test
    void aSharedLinkHasSomethingToShow() throws Exception {
        String product = body("/products/1");

        assertThat(product)
                .contains("property=\"og:title\"")
                .contains("property=\"og:description\"")
                .contains("property=\"og:url\"")
                .contains("property=\"og:image\"")
                .contains("name=\"twitter:card\"");
    }

    /**
     * The structured data that turns a plain result into one showing a price
     * and whether the thing is in stock.
     *
     * <p>Parsed rather than string-matched, which also proves it is valid JSON.
     * A block that does not parse is silently ignored by every consumer of it,
     * so "the text is in the page" is not the thing worth asserting.
     */
    @Test
    void aProductSaysItsPriceAndAvailabilityInStructuredData() throws Exception {
        JsonNode data = structuredData(body("/products/1"));

        assertThat(data.path("@type").asText()).isEqualTo("Product");
        assertThat(data.path("name").asText()).isEqualTo("450W Monocrystalline Panel");
        assertThat(data.path("offers").path("priceCurrency").asText()).isEqualTo("NGN");
        assertThat(data.path("offers").path("price").asDouble()).isEqualTo(380000.00);
        assertThat(data.path("offers").path("availability").asText())
                .isEqualTo("https://schema.org/InStock");
        // Absolute, or a crawler resolves it against its own host and finds
        // nothing.
        assertThat(data.path("image").asText()).startsWith("https://solarupgrade.onrender.com/");
    }

    /** The one ld+json block on a page, parsed. */
    private JsonNode structuredData(String html) throws Exception {
        Matcher matcher = Pattern
                .compile("<script type=\"application/ld\\+json\">(.*?)</script>", Pattern.DOTALL)
                .matcher(html);
        assertThat(matcher.find()).as("the page carries a structured data block").isTrue();
        return new ObjectMapper().readTree(matcher.group(1));
    }

    /** Built from what the shop sells now, not a file somebody remembered to edit. */
    @Test
    void theSitemapListsTheCatalogue() throws Exception {
        String sitemap = body("/sitemap.xml");

        assertThat(sitemap)
                .contains("<loc>https://solarupgrade.onrender.com</loc>")
                .contains("<loc>https://solarupgrade.onrender.com/products</loc>")
                .contains("<loc>https://solarupgrade.onrender.com/products/1</loc>")
                .contains("<loc>https://solarupgrade.onrender.com/suppliers</loc>");
    }

    /** A basket in a sitemap is an invitation to index somebody's shopping. */
    @Test
    void theSitemapLeavesOutEverythingPrivate() throws Exception {
        String sitemap = body("/sitemap.xml");

        assertThat(sitemap)
                .doesNotContain("/cart")
                .doesNotContain("/checkout")
                .doesNotContain("/admin")
                .doesNotContain("/orders");
    }

}
