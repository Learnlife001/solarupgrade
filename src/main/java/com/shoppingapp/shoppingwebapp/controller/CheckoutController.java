package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.dto.CheckoutForm;
import com.shoppingapp.shoppingwebapp.dto.Country;
import com.shoppingapp.shoppingwebapp.model.Money;
import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.service.CartService;
import com.shoppingapp.shoppingwebapp.service.ExchangeRates;
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

import java.math.BigDecimal;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/checkout")
public class CheckoutController {

    private final CartService cartService;
    private final OrderService orderService;
    private final CurrentUserSupport currentUser;
    private final ExchangeRates exchangeRates;

    public CheckoutController(CartService cartService,
                              OrderService orderService,
                              CurrentUserSupport currentUser,
                              ExchangeRates exchangeRates) {
        this.cartService = cartService;
        this.orderService = orderService;
        this.currentUser = currentUser;
        this.exchangeRates = exchangeRates;
    }

    /**
     * What each method would actually charge, worked out before the customer
     * commits. Card and transfer settle in naira; PayPal cannot take naira, so
     * it is quoted in euro. Showing the figure per method means nobody picks
     * PayPal and then meets an unexplained number on PayPal's own page.
     */
    private Map<PaymentMethod, String> chargeByMethod(BigDecimal nairaTotal) {
        Map<PaymentMethod, String> charges = new LinkedHashMap<>();
        for (PaymentMethod method : PaymentMethod.offered()) {
            String currency = exchangeRates.currencyFor(method);
            charges.put(method, Money.format(exchangeRates.convert(nairaTotal, currency), currency));
        }
        return charges;
    }

    private void addTotals(Model model, User user) {
        BigDecimal total = cartService.totalFor(user);
        model.addAttribute("items", cartService.itemsFor(user));
        model.addAttribute("totalDisplay", Money.base(total));
        model.addAttribute("chargeByMethod", chargeByMethod(total));
        model.addAttribute("nairaPerEuro", Money.base(exchangeRates.nairaPerEuro()));
        model.addAttribute("paymentMethods", PaymentMethod.offered());
        model.addAttribute("countries", Country.all());
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
        addTotals(model, user);
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
            model.addAttribute("paymentMethods", PaymentMethod.offered());
        model.addAttribute("countries", Country.all());
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
