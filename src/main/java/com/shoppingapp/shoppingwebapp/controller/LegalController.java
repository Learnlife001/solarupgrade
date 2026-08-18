package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.config.BusinessDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Terms, returns, privacy and contact.
 *
 * <p>Public, and they have to be: a customer decides whether to trust a shop
 * before they have an account, and a returns policy behind a login is not a
 * policy. Every page is served from the same business details, so an address
 * changed once is changed everywhere.
 */
@Controller
public class LegalController {

    private final BusinessDetails business;

    public LegalController(BusinessDetails business) {
        this.business = business;
    }

    private String page(String view, Model model) {
        model.addAttribute("business", business);
        // Drives the notice at the top of each page while details are missing.
        model.addAttribute("draft", !business.isComplete());
        return view;
    }

    @GetMapping("/terms")
    public String terms(Model model) {
        return page("legal/terms", model);
    }

    @GetMapping("/returns")
    public String returns(Model model) {
        return page("legal/returns", model);
    }

    @GetMapping("/privacy")
    public String privacy(Model model) {
        return page("legal/privacy", model);
    }

    @GetMapping("/contact")
    public String contact(Model model) {
        return page("legal/contact", model);
    }
}
