package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.config.Seo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * The few facts every page needs to describe itself to a search engine.
 *
 * <p>Computed here rather than passed by each controller: sixteen templates
 * call the shared head fragment, and a canonical URL that only some pages carry
 * is worse than none — the pages that forget it are the ones that get indexed
 * under whatever address a crawler happened to follow.
 */
@ControllerAdvice
public class SeoModelAdvice {

    /**
     * Pages that must never be indexed, whatever the setting says.
     *
     * <p>A basket, an order, the checkout and the back office. None of them
     * means anything to a stranger, all of them are behind a login, and an
     * indexed URL for somebody's order is an invitation to try the numbers
     * either side of it. Prefix matching, so anything added under these later
     * is covered without a second thought.
     */
    private static final List<String> NEVER_INDEXED =
            List.of("/orders", "/cart", "/checkout", "/admin", "/login", "/register",
                    "/verify", "/forgot-password", "/reset-password", "/payments");

    private final Seo seo;
    private final String baseUrl;

    public SeoModelAdvice(Seo seo, @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.seo = seo;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @ModelAttribute("seo")
    public Seo seo() {
        return seo;
    }

    /**
     * The address this page should be indexed under.
     *
     * <p>Built from the configured base URL and the path only — never the host
     * the request arrived on, and never the query string. Otherwise the same
     * page is a different URL for every filter combination and every hostname
     * pointed at the service, and a search engine treats each as a competing
     * copy of the others.
     */
    @ModelAttribute("canonicalUrl")
    public String canonicalUrl(HttpServletRequest request) {
        String path = request.getRequestURI();
        return baseUrl + ("/".equals(path) ? "" : path);
    }

    /** Whether this particular page may be indexed. */
    @ModelAttribute("indexable")
    public boolean indexable(HttpServletRequest request) {
        if (!seo.isIndexable()) {
            return false;
        }
        String path = request.getRequestURI();
        return NEVER_INDEXED.stream().noneMatch(prefix ->
                path.equals(prefix) || path.startsWith(prefix + "/"));
    }
}
