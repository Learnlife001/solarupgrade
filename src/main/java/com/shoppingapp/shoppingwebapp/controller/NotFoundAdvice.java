package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.config.Brand;
import com.shoppingapp.shoppingwebapp.config.BusinessDetails;
import com.shoppingapp.shoppingwebapp.config.Seo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.NoSuchElementException;

/**
 * A thing that is not there is a 404, not a server error.
 *
 * <p>Every lookup in this application ends in
 * {@code orElseThrow(NoSuchElementException::new)}, and without this each one
 * became a 500: the "Something went wrong" page, a stack trace in the logs, and
 * — now that failures raise alerts — an email. For a mistyped URL or a link to
 * a product that has since been retired.
 *
 * <p>That last case is the reason this arrived with archiving. Retiring a
 * product turns every old link, bookmark and search-engine result for it into a
 * request for something that is deliberately gone. Answering those with "the
 * shop is broken" would be false, and would bury a real outage under alerts
 * about a crawler working through last year's pages.
 */
@ControllerAdvice(assignableTypes = {ProductController.class, SupplierController.class,
        OrderController.class})
public class NotFoundAdvice {

    private final Brand brand;
    private final BusinessDetails business;
    private final Seo seo;
    private final String baseUrl;

    public NotFoundAdvice(Brand brand, BusinessDetails business, Seo seo,
                          @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.brand = brand;
        this.business = business;
        this.seo = seo;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * These controllers answer GETs -- somebody following a link -- so the
     * honest reply is the page that says it is gone, with the status to match
     * so crawlers stop asking.
     *
     * <p>The basket is deliberately not on this list. A POST from a stale tab
     * deserves a sentence explaining what happened, not a 404; see
     * {@code CartController}.
     */
    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(Model model, HttpServletRequest request) {
        // Spring does not run @ModelAttribute methods for exception handlers,
        // so the page furniture every template expects has to be put back by
        // hand. Without it the 404 page throws while rendering the header, and
        // a missing product becomes a 500 after all.
        //
        // This has now caught three separate additions to the shared layout, so
        // the rule is worth stating: anything the layout reads from the model
        // must be added here too. The layout currently needs brand, business,
        // cartCount, seo, canonicalUrl and indexable.
        model.addAttribute("brand", brand);
        model.addAttribute("business", business);
        model.addAttribute("cartCount", 0);
        model.addAttribute("seo", seo);
        model.addAttribute("canonicalUrl", baseUrl + request.getRequestURI());
        // A page that does not exist is never worth indexing, whatever the
        // setting says.
        model.addAttribute("indexable", false);
        return "not-found";
    }
}
