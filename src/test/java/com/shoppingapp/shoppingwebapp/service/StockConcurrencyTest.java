package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.dto.CheckoutForm;
import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.CartItemRepository;
import com.shoppingapp.shoppingwebapp.repository.OrderRepository;
import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two customers, one panel left, both pressing "Place order" at the same
 * moment.
 *
 * <p>Not transactional, and it cannot be: the whole point is two transactions
 * running at once, which a single test-managed transaction would collapse into
 * one. The rows are cleaned up by hand instead.
 */
@SpringBootTest
class StockConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    private Product lastOne;
    private final List<User> buyers = new ArrayList<>();

    private static CheckoutForm form() {
        CheckoutForm form = new CheckoutForm();
        form.setShippingName("Race Tester");
        form.setShippingLine1("14 Adeola Odeku Street");
        form.setShippingCity("Victoria Island");
        form.setShippingState("Lagos");
        form.setShippingCountry("NG");
        form.setPaymentMethod(PaymentMethod.PAYPAL);
        return form;
    }

    @BeforeEach
    void setUp() {
        // Exactly one in stock: the interesting number.
        lastOne = productRepository.save(
                new Product("Race Panel", "desc", new BigDecimal("250000.00"), Category.PANEL, 1, null));
        for (int i = 0; i < 2; i++) {
            User buyer = userRepository.save(
                    new User("race-" + i + "-" + System.nanoTime() + "@example.test", "hash", "Race Buyer"));
            buyers.add(buyer);
            cartService.add(buyer, lastOne, 1);
        }
    }

    @AfterEach
    void tearDown() {
        buyers.forEach(buyer -> {
            orderRepository.deleteAll(orderRepository.findByUserOrderByPlacedAtDesc(buyer));
            cartItemRepository.deleteAll(cartItemRepository.findByUser(buyer));
            userRepository.delete(buyer);
        });
        buyers.clear();
        productRepository.deleteById(lastOne.getId());
    }

    @Test
    void onlyOneOfTwoSimultaneousBuyersGetsTheLastUnit() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        // Both threads wait here so that they enter placeOrder together rather
        // than one finishing before the other starts.
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();

        List<Future<Object>> runs = buyers.stream()
                .map(buyer -> pool.submit(() -> {
                    start.await();
                    try {
                        orderService.placeOrder(buyer, form());
                        succeeded.incrementAndGet();
                    } catch (RuntimeException expectedForTheLoser) {
                        // Out of stock, or the database refusing to let two
                        // transactions hold the same row. Either is a refusal,
                        // which is the outcome that matters.
                    }
                    return null;
                }))
                .toList();

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        for (Future<?> run : runs) {
            run.get();
        }

        assertThat(succeeded.get())
                .as("exactly one of two buyers should get the only unit in stock")
                .isEqualTo(1);
        assertThat(productRepository.findById(lastOne.getId()).orElseThrow().getStock())
                .as("stock must never go negative")
                .isZero();
        assertThat(buyers.stream()
                .mapToLong(buyer -> orderRepository.findByUserOrderByPlacedAtDesc(buyer).size())
                .sum())
                .as("one order, not two")
                .isEqualTo(1);
    }
}
