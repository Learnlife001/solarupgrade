package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.service.OrderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final CurrentUserSupport currentUser;

    public OrderController(OrderService orderService, CurrentUserSupport currentUser) {
        this.orderService = orderService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String list(Principal principal, Model model) {
        model.addAttribute("orders", orderService.ordersFor(currentUser.require(principal)));
        return "orders";
    }

    /**
     * Doubles as the post-checkout confirmation page; the "placed" query
     * parameter is what switches on the thank-you banner.
     */
    @GetMapping("/{id}")
    public String summary(@PathVariable Long id,
                          @RequestParam(required = false) String placed,
                          Principal principal,
                          Model model) {
        User user = currentUser.require(principal);
        model.addAttribute("order", orderService.getForUser(id, user));
        model.addAttribute("justPlaced", placed != null);
        return "order-summary";
    }

    @PostMapping("/{id}/pay")
    public String pay(@PathVariable Long id, Principal principal) {
        orderService.markPaid(id, currentUser.require(principal));
        return "redirect:/orders/" + id;
    }
}
