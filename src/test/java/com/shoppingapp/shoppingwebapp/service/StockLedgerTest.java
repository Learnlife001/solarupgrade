package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.dto.CheckoutForm;
import com.shoppingapp.shoppingwebapp.model.CancellationReason;
import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.StockMovement;
import com.shoppingapp.shoppingwebapp.model.StockMovementReason;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.CartItemRepository;
import com.shoppingapp.shoppingwebapp.repository.OrderRepository;
import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import com.shoppingapp.shoppingwebapp.repository.StockMovementRepository;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every stock movement is explained.
 *
 * <p>Stock was changed from four places and three of them left no trace, so the
 * figure could only be accounted for by whoever last typed one. "I counted ten
 * onto the shelf and it says three" had no answer, and a stock number nobody
 * can explain is one nobody trusts — which is how a shop ends up with a
 * spreadsheet kept alongside its own system.
 *
 * <p><b>Not</b> {@code @Transactional}, and that matters here. Sharing one
 * transaction with the code under test made a stock take read the product row
 * as it was before the sale in the same test -- {@code refresh()} reloads from
 * the database, and the sale had not been flushed -- so the recorded change was
 * wrong by the size of the sale. In production each of these is its own
 * request and its own transaction. A test that committed nothing would have
 * asserted a figure the shop will never produce.
 */
@SpringBootTest
class StockLedgerTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductRepository products;

    @Autowired
    private UserRepository users;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private CartItemRepository cartItems;

    @Autowired
    private StockMovementRepository movements;

    private Product panel;
    private User buyer;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        panel = products.save(new Product("Ledger Panel " + unique, "A panel.",
                new BigDecimal("1000.00"), Category.PANEL, 10, null));
        buyer = users.save(new User("ledger-" + unique + "@example.test", "hash", "Ledger Buyer"));
    }

    /** Nothing rolls back, so this class clears what it created. */
    @AfterEach
    void tearDown() {
        orders.deleteAll(orders.findByUserOrderByPlacedAtDesc(buyer));
        cartItems.deleteAll(cartItems.findByUser(buyer));
        users.delete(buyer);
        // The ledger points at the product row, so it goes first.
        movements.deleteAll(movements
                .findByProductIdOrderByHappenedAtDescIdDesc(panel.getId(), Limit.of(1000)));
        products.deleteById(panel.getId());
    }

    private List<StockMovement> history() {
        return stockService.historyFor(panel.getId(), 20);
    }

    private Order buy(int quantity) {
        cartService.add(buyer, panel, quantity);
        CheckoutForm form = new CheckoutForm();
        form.setShippingName("Ledger Buyer");
        form.setShippingLine1("1 Test Street");
        form.setShippingCity("Lagos");
        form.setShippingState("Lagos");
        form.setShippingCountry("NG");
        form.setPaymentMethod(PaymentMethod.PAYPAL);
        return orderService.placeOrder(buyer, form);
    }

    @Test
    void aSaleIsRecordedAgainstItsOrder() {
        Order order = buy(3);

        assertThat(history()).singleElement().satisfies(movement -> {
            assertThat(movement.getReason()).isEqualTo(StockMovementReason.SALE);
            assertThat(movement.getChange()).isEqualTo(-3);
            assertThat(movement.getResultingStock()).isEqualTo(7);
        });
        assertThat(products.findById(panel.getId()).orElseThrow().getStock()).isEqualTo(7);
        assertThat(order.getId()).isNotNull();
    }

    @Test
    void aCancellationIsRecordedAndPointsAtTheOrder() {
        Order order = buy(3);
        orderService.cancelUnpaid(order.getId(), CancellationReason.CUSTOMER);

        assertThat(history()).first().satisfies(movement -> {
            assertThat(movement.getReason()).isEqualTo(StockMovementReason.CANCELLATION);
            assertThat(movement.getChange()).isEqualTo(3);
            assertThat(movement.getResultingStock()).isEqualTo(10);
            assertThat(movement.getOrderId()).isEqualTo(order.getId());
        });
    }

    /** A stock take records the difference it made, not just the new figure. */
    @Test
    void aStockTakeRecordsTheDifferenceAndWhoCountedIt() {
        productService.setStock(panel.getId(), 4, "counter@example.test");

        assertThat(history()).first().satisfies(movement -> {
            assertThat(movement.getReason()).isEqualTo(StockMovementReason.STOCK_TAKE);
            assertThat(movement.getChange()).isEqualTo(-6);
            assertThat(movement.getResultingStock()).isEqualTo(4);
            assertThat(movement.getActor()).isEqualTo("counter@example.test");
        });
    }

    /**
     * The history reads as a running figure, so a row can be checked against
     * the one before it without arithmetic.
     */
    @Test
    void theHistoryReadsAsARunningFigureNewestFirst() {
        buy(2);
        productService.setStock(panel.getId(), 20, "counter@example.test");
        buy(5);

        assertThat(history()).extracting(StockMovement::getResultingStock)
                .containsExactly(15, 20, 8);
        assertThat(history()).extracting(StockMovement::getChangeDisplay)
                .containsExactly("-5", "+12", "-2");
    }

    /**
     * The backstop. Every caller checks stock before selling, but a logic error
     * that got past them should refuse rather than write a negative shelf and a
     * movement explaining it.
     */
    @Test
    void stockCannotBeDrivenBelowZero() {
        assertThatThrownBy(() -> stockService.move(panel, -11, StockMovementReason.SALE, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("below zero");

        assertThat(products.findById(panel.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(history()).isEmpty();
    }

    /** A refused stock take leaves neither a changed figure nor a movement. */
    @Test
    void aNegativeStockTakeIsRefusedOutright() {
        assertThatThrownBy(() -> productService.setStock(panel.getId(), -1, "counter@example.test"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(history()).isEmpty();
    }
}
