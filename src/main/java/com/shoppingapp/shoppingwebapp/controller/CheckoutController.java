package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.dto.CheckoutForm;
import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.service.CartService;
import com.shoppingapp.shoppingwebapp.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/checkout")
public class CheckoutController {

    private final CartService cartService;
    private final OrderService orderService;
    private final CurrentUserSupport currentUser;

    public CheckoutController(CartService cartService, OrderService orderService, CurrentUserSupport currentUser) {
        this.cartService = cartService;
        this.orderService = orderService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String form(Principal principal, Model model) {
        User user = currentUser.require(principal);
        if (cartService.itemsFor(user).isEmpty()) {
            return "redirect:/cart";
        }
        if (!model.containsAttribute("checkoutForm")) {
            CheckoutForm form = new CheckoutForm();
            form.setShippingName(user.getFullName());
            model.addAttribute("checkoutForm", form);
        }
        model.addAttribute("items", cartService.itemsFor(user));
        model.addAttribute("total", cartService.totalFor(user));
        model.addAttribute("paymentMethods", PaymentMethod.values());
        return "checkout";
    }

    @PostMapping
    public String submit(@Valid @ModelAttribute("checkoutForm") CheckoutForm checkoutForm,
                         BindingResult bindingResult,
                         Principal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        User user = currentUser.require(principal);

        if (bindingResult.hasErrors()) {
            model.addAttribute("items", cartService.itemsFor(user));
            model.addAttribute("total", cartService.totalFor(user));
            model.addAttribute("paymentMethods", PaymentMethod.values());
            return "checkout";
        }

        Order order;
        try {
            order = orderService.placeOrder(user, checkoutForm);
        } catch (IllegalStateException ex) {
            // Raised when the basket is empty or stock ran out between viewing
            // the basket and submitting.
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/cart";
        }

        return "redirect:/orders/" + order.getId() + "?placed";
    }
}
