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

    /** Orders per page in a customer's own history. */
    private static final int PAGE_SIZE = 20;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       Principal principal,
                       Model model) {
        // Paged for the same reason the admin list is, if less urgently: a
        // customer's history only grows, and a regular buyer is the one whose
        // page slows down.
        org.springframework.data.domain.Page<Order> orders = orderService.ordersPageFor(
                currentUser.require(principal), OrderService.page(page, PAGE_SIZE));
        model.addAttribute("orders", orders.getContent());
        model.addAttribute("pageNumber", orders.getNumber());
        model.addAttribute("totalPages", orders.getTotalPages());
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
        // Decides whether there is a pay button at all: a method with no
        // provider behind it gets an explanation instead of a control.
        model.addAttribute("paymentIsLive", paymentService.isLive(order.getPaymentMethod()));
        return "order-summary";
    }

    /**
     * Starts payment for an order.
     *
     * <p>This endpoint cannot mark an order paid. It hands off to a provider,
     * and the order becomes PAID only when that provider confirms a capture,
     * on a call we made to them.
     *
     * <p>It used to end with a stand-in that flipped the status directly when
     * no provider was configured. That made the button a way for any buyer to
     * pay for their own order by pressing it: the form was theirs to post, so
     * the goods were theirs to take. A method with nothing behind it now
     * refuses, and says so.
     */
    @PostMapping("/{id}/pay")
    public String pay(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        User user = currentUser.require(principal);
        Order order = orderService.getForUser(id, user);

        if (!paymentService.isLive(order.getPaymentMethod())) {
            log.warn("Payment attempted on order {} with unavailable method {}",
                    id, order.getPaymentMethod());
            redirectAttributes.addFlashAttribute("error",
                    "That payment method is not available yet. Nothing has been charged.");
            return "redirect:/orders/" + id;
        }

        try {
            return "redirect:" + paymentService.begin(order);
        } catch (PaymentException ex) {
            log.warn("Could not start PayPal payment for order {}", id, ex);
            redirectAttributes.addFlashAttribute("error",
                    "We could not reach PayPal just now. Nothing has been charged — please try again.");
            return "redirect:/orders/" + id;
        }
    }
}
