package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.dto.CheckoutForm;
import com.shoppingapp.shoppingwebapp.model.CartItem;
import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderItem;
import com.shoppingapp.shoppingwebapp.model.OrderStatus;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.OrderRepository;
import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;
    private final EmailService emailService;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        CartService cartService,
                        EmailService emailService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.cartService = cartService;
        this.emailService = emailService;
    }

    public List<Order> ordersFor(User user) {
        return orderRepository.findByUserOrderByPlacedAtDesc(user);
    }

    /**
     * Scoped by user so that changing the id in the URL cannot expose someone
     * else's order.
     */
    public Order getForUser(Long orderId, User user) {
        return orderRepository.findByIdAndUser(orderId, user)
                .orElseThrow(() -> new NoSuchElementException("No order " + orderId + " for this user"));
    }

    /**
     * Turns the user's basket into an order: snapshots prices, decrements stock,
     * empties the basket and sends a confirmation.
     *
     * <p>The order is left in {@link OrderStatus#PENDING_PAYMENT}, with the
     * customer's chosen payment method recorded on it. That choice is what a
     * provider needs to be told at capture time; capture itself is where the
     * integration belongs, see {@link #markPaid(Long, User)}.
     */
    @Transactional
    public Order placeOrder(User user, CheckoutForm form) {
        List<CartItem> cartItems = cartService.itemsFor(user);
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cannot place an order with an empty basket");
        }

        Order order = new Order(user, form.getShippingName(), form.getShippingLine1());
        // The optional fields arrive as "" from an untouched input. Store null
        // rather than a blank string: "no postcode" and "a postcode that is
        // empty" should not be two different things in the database.
        order.setShippingLine2(blankToNull(form.getShippingLine2()));
        order.setShippingCity(form.getShippingCity());
        order.setShippingState(form.getShippingState());
        order.setShippingPostcode(blankToNull(form.getShippingPostcode()));
        order.setShippingCountry(form.getShippingCountry());
        order.setPaymentMethod(form.getPaymentMethod());

        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            if (product.getStock() < cartItem.getQuantity()) {
                throw new IllegalStateException(
                        "Not enough stock for " + product.getName()
                                + " (wanted " + cartItem.getQuantity() + ", have " + product.getStock() + ")");
            }
            product.setStock(product.getStock() - cartItem.getQuantity());
            productRepository.save(product);
            order.addItem(new OrderItem(product, cartItem.getQuantity()));
        }

        Order saved = orderRepository.save(order);
        cartService.clear(user);
        emailService.sendOrderConfirmation(saved);
        return saved;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Stands in for capturing payment. Replace the body with a PayPal Orders API
     * capture call, and only flip the status once the provider confirms.
     */
    @Transactional
    public Order markPaid(Long orderId, User user) {
        Order order = getForUser(orderId, user);
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            order.setStatus(OrderStatus.PAID);
        }
        return orderRepository.save(order);
    }
}
