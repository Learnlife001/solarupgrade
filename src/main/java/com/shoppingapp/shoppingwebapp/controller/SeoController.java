package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.config.Seo;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.Supplier;
import com.shoppingapp.shoppingwebapp.service.ProductService;
import com.shoppingapp.shoppingwebapp.service.SupplierService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * The two files a search engine looks for before it reads anything else.
 *
 * <p>Both are generated rather than kept as static files, for the same reason:
 * a sitemap that lists products is a copy of the catalogue, and a copy goes
 * stale. A hand-written one would list products that have since been archived
 * and miss every product added afterwards — and a sitemap full of 404s is worse
 * than none, because it is a direct statement about what the shop sells.
 */
@Controller
public class SeoController {

    private final Seo seo;
    private final ProductService products;
    private final SupplierService suppliers;
    private final String baseUrl;

    public SeoController(Seo seo,
                         ProductService products,
                         SupplierService suppliers,
                         @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.seo = seo;
        this.products = products;
        this.suppliers = suppliers;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * What crawlers may read.
     *
     * <p>While indexing is off this refuses everything, which is the honest
     * answer for a shop showing sample prices under legal pages marked draft.
     * When it is on, the private parts stay closed: a basket and an order mean
     * nothing to a stranger, and an indexed order URL invites somebody to try
     * the numbers either side of it.
     */
    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robots() {
        if (!seo.isIndexable()) {
            return """
                    # This shop is not ready to be indexed: the catalogue is sample
                    # data and the legal pages are marked draft. Set APP_SEO_INDEXABLE
                    # to true once that is no longer so.
                    User-agent: *
                    Disallow: /
                    """;
        }
        return """
                User-agent: *
                Disallow: /admin/
                Disallow: /orders
                Disallow: /cart
                Disallow: /checkout
                Disallow: /payments/
                Disallow: /login
                Disallow: /register
                Disallow: /verify
                Disallow: /forgot-password
                Disallow: /reset-password

                Sitemap: %BASE%/sitemap.xml
                """.replace("%BASE%", baseUrl);
    }

    /**
     * Every page worth finding, built from what the shop actually sells now.
     *
     * <p>Served even when indexing is off — robots.txt is what withholds the
     * invitation — so that turning indexing on needs no second change, and so
     * the file can be checked before anybody is invited to read it.
     */
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // The pages a stranger might land on, in the order they matter.
        url(xml, "/", "1.0");
        url(xml, "/products", "0.9");
        url(xml, "/suppliers", "0.7");

        // Archived products are excluded because findAll() excludes them: their
        // pages answer 404, and a sitemap listing 404s is a statement about the
        // shop that happens to be false.
        for (Product product : products.findAll()) {
            url(xml, "/products/" + product.getId(), "0.8");
        }
        for (Supplier supplier : suppliers.all()) {
            url(xml, "/suppliers/" + supplier.getId(), "0.5");
        }

        // Low priority, but a customer searching for the returns policy of a
        // shop they bought from should find it.
        url(xml, "/terms", "0.3");
        url(xml, "/returns", "0.3");
        url(xml, "/privacy", "0.3");
        url(xml, "/contact", "0.4");

        return xml.append("</urlset>\n").toString();
    }

    private void url(StringBuilder xml, String path, String priority) {
        xml.append("  <url><loc>").append(escape(baseUrl + ("/".equals(path) ? "" : path)))
                .append("</loc><priority>").append(priority).append("</priority></url>\n");
    }

    /**
     * XML, not HTML. A product name with an ampersand in it would otherwise end
     * the document early and take the rest of the catalogue with it.
     */
    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /** Exposed for the sitemap test, which counts what a crawler would find. */
    List<String> indexablePaths() {
        return List.of("/", "/products", "/suppliers", "/terms", "/returns", "/privacy", "/contact");
    }
}
