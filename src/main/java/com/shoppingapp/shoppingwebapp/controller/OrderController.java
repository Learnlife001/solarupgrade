package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.service.OrderService;
import com.shoppingapp.shoppingwebapp.service.payment.PaymentException;
import com.shoppingapp.shoppingwebapp.service.payment.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@RequestMapping("/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final CurrentUserSupport currentUser;
    private final PaymentService paymentService;

    public OrderController(OrderService orderService,
                           CurrentUserSupport currentUser,
                           PaymentService paymentService) {
        this.orderService = orderService;
        this.currentUser = currentUser;
        this.paymentService = paymentService;
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
        Order order = orderService.getForUser(id, user);
        model.addAttribute("order", order);
        model.addAttribute("justPlaced", placed != null);
        // Drives the button's wording: a live provider means leaving the site,
        // the stand-in does not.
        model.addAttribute("paymentIsLive", paymentService.isLive(order.getPaymentMethod()));
        return "order-summary";
    }

    /**
     * Starts payment for an order.
     *
     * <p>Where a provider is actually configured for the chosen method, this
     * hands off to it and the order is only marked paid once that provider
     * confirms a capture. Where none is, it falls back to the stand-in that
     * flips the status directly — clearly labelled on the page, and the only
     * reason it still exists is that OPay is not wired up yet.
     */
    @PostMapping("/{id}/pay")
    public String pay(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        User user = currentUser.require(principal);
        Order order = orderService.getForUser(id, user);

        if (paymentService.isLive(order.getPaymentMethod())) {
            try {
                return "redirect:" + paymentService.beginPayPal(order);
            } catch (PaymentException ex) {
                log.warn("Could not start PayPal payment for order {}", id, ex);
                redirectAttributes.addFlashAttribute("error",
                        "We could not reach PayPal just now. Nothing has been charged — please try again.");
                return "redirect:/orders/" + id;
            }
        }

        orderService.markPaid(id, user);
        return "redirect:/orders/" + id;
    }
}
