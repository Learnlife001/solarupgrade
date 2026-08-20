package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.config.Brand;
import com.shoppingapp.shoppingwebapp.config.BusinessDetails;
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

    public NotFoundAdvice(Brand brand, BusinessDetails business) {
        this.brand = brand;
        this.business = business;
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
    public String notFound(Model model) {
        // Spring does not run @ModelAttribute methods for exception handlers,
        // so the page furniture every template expects has to be put back by
        // hand. Without it the 404 page throws while rendering the header, and
        // a missing product becomes a 500 after all.
        model.addAttribute("brand", brand);
        model.addAttribute("business", business);
        model.addAttribute("cartCount", 0);
        return "not-found";
    }
}
