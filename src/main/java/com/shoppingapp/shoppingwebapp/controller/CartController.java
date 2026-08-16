package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.service.CartService;
import com.shoppingapp.shoppingwebapp.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;
    private final ProductService productService;
    private final CurrentUserSupport currentUser;

    public CartController(CartService cartService, ProductService productService, CurrentUserSupport currentUser) {
        this.cartService = cartService;
        this.productService = productService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String view(Principal principal, Model model) {
        User user = currentUser.require(principal);
        model.addAttribute("items", cartService.itemsFor(user));
        model.addAttribute("total", cartService.totalFor(user));
        return "cart";
    }

    /**
     * The no-JavaScript path: add, then land on the basket so the result is
     * visible. {@link #addAsync} handles the enhanced case.
     */
    @PostMapping("/add")
    public String add(@RequestParam Long productId,
                      @RequestParam(defaultValue = "1") int quantity,
                      Principal principal,
                      RedirectAttributes redirectAttributes) {
        User user = currentUser.require(principal);
        cartService.add(user, productService.getById(productId), quantity);
        redirectAttributes.addFlashAttribute("message", "Added to your basket");
        return "redirect:/cart";
    }

    /**
     * Same work, but answers with the new basket size instead of a redirect, so
     * the page the customer is reading stays where it is.
     *
     * <p>Selected by the {@code X-Requested-With} header that app.js sets. A
     * plain form post never carries it, so a browser without JavaScript falls
     * through to {@link #add} and still works.
     */
    @PostMapping(value = "/add", headers = "X-Requested-With=fetch")
    @ResponseBody
    public CartSummary addAsync(@RequestParam Long productId,
                                @RequestParam(defaultValue = "1") int quantity,
                                Principal principal) {
        User user = currentUser.require(principal);
        cartService.add(user, productService.getById(productId), quantity);
        return new CartSummary(cartService.itemCountFor(user), "Added to your basket");
    }

    /** What the enhanced add-to-basket needs back: the number to show, and what to say. */
    public record CartSummary(int itemCount, String message) {
    }

    @PostMapping("/{itemId}/update")
    public String update(@PathVariable Long itemId,
                         @RequestParam int quantity,
                         Principal principal) {
        cartService.updateQuantity(currentUser.require(principal), itemId, quantity);
        return "redirect:/cart";
    }

    @PostMapping("/{itemId}/remove")
    public String remove(@PathVariable Long itemId, Principal principal) {
        cartService.remove(currentUser.require(principal), itemId);
        return "redirect:/cart";
    }
}
