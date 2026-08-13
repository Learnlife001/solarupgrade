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
