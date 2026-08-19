package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.config.Brand;
import com.shoppingapp.shoppingwebapp.config.BusinessDetails;
import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.service.CartService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

/**
 * Supplies the values the shared header needs on every page, so individual
 * controllers do not each have to remember to add them.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final CartService cartService;
    private final CurrentUserSupport currentUser;
    private final Brand brand;
    private final BusinessDetails business;

    public GlobalModelAdvice(CartService cartService, CurrentUserSupport currentUser, Brand brand,
                             BusinessDetails business) {
        this.cartService = cartService;
        this.currentUser = currentUser;
        this.brand = brand;
        this.business = business;
    }

    /**
     * Available to every template, so no page has to be given the name it is
     * supposed to display.
     */
    @ModelAttribute("brand")
    public Brand brand() {
        return brand;
    }

    /**
     * The business settings, on every page rather than only the legal ones.
     *
     * <p>The delivery estimate was typed into the home page and the product
     * page as well as being a setting, so changing it left those two pages
     * telling customers something the terms of sale contradicted.
     */
    @ModelAttribute("business")
    public BusinessDetails business() {
        return business;
    }

    @ModelAttribute("cartCount")
    public int cartCount(Principal principal) {
        if (principal == null) {
            return 0;
        }
        return cartService.itemCountFor(currentUser.require(principal));
    }

    @ModelAttribute("allCategories")
    public Category[] allCategories() {
        return Category.values();
    }
}
