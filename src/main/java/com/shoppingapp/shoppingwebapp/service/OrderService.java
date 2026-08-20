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
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;
    private final EmailService emailService;
    private final ExchangeRates exchangeRates;

    @PersistenceContext
    private EntityManager entityManager;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        CartService cartService,
                        EmailService emailService,
                        ExchangeRates exchangeRates) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.cartService = cartService;
        this.emailService = emailService;
        this.exchangeRates = exchangeRates;
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
     * integration belongs, see {@link #markPaid(Order)}.
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

        lockStock(cartItems);

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

        // Snapshot what the customer will be charged, in the currency their
        // chosen method can actually take. Done after the lines are added so
        // the total is final, and stored so a later rate move cannot change
        // the figure they agreed to.
        String currency = exchangeRates.currencyFor(form.getPaymentMethod());
        order.recordCharge(currency,
                exchangeRates.convert(order.getTotal(), currency),
                exchangeRates.rateFor(currency));

        Order saved = orderRepository.save(order);
        cartService.clear(user);
        emailService.sendOrderConfirmation(saved);
        return saved;
    }

    /**
     * Cancels an unpaid order and puts its stock back.
     *
     * <p>The counterpart to {@link #placeOrder}, and the reason that method is
     * allowed to hold stock at all: without a way back, every abandoned basket
     * removed panels from the shop permanently. A handful of unpaid orders
     * could take the catalogue to "out of stock" with nothing sold.
     *
     * <p>Takes an id and loads the order itself. It used to take a loaded Order
     * and failed with "Entity not managed" whenever the caller had read it in an
     * earlier transaction — which the admin screens do, and the scheduled job
     * happens not to. Tests missed it entirely because a @Transactional test
     * keeps everything in one transaction, so nothing is ever detached. A
     * method that locks and mutates rows has to load them inside its own
     * transaction.
     *
     * <p>Guarded by the transition, like markPaid: an order that is not
     * PENDING_PAYMENT is returned untouched, so a paid order can never have its
     * stock handed back and a second run cannot return the same units twice.
     *
     * @return true when this call is what cancelled it
     */
    @Transactional
    public boolean cancelUnpaid(Long orderId) {
        Order order = getAnyOrder(orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return false;
        }

        // Same lock and the same ordering as checkout. Returning stock is a
        // read-modify-write too, and it races with the buying of it.
        order.getItems().stream()
                .map(OrderItem::getProduct)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Product::getId))
                .forEach(product -> entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE));

        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            // Null when the product row was deleted after the order was placed.
            // The order still shows what was bought, from its own snapshot;
            // there is simply nowhere to return the stock to.
            if (product != null) {
                product.setStock(product.getStock() + item.getQuantity());
                productRepository.save(product);
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        // Told either way -- an order that vanishes without a word is worse
        // whether a job released it or a person did.
        emailService.sendOrderExpired(saved);
        return true;
    }

    /**
     * Records that a refund went through, and tells the customer.
     *
     * <p>Called by {@code PaymentService} only after the provider has confirmed
     * the money went back. Nothing here contacts a provider: this is the
     * bookkeeping that follows, welded to the email so a refund cannot be
     * recorded silently.
     *
     * <p><b>Stock comes back only if it never left.</b> A refund before dispatch
     * returns the units to the shelf, because they are still on it. A refund
     * after dispatch does not, because the goods are with the customer and
     * inventing them back into stock would sell something twice -- and the
     * second buyer would be the one to discover it. When the parcel returns,
     * the stock figure is set by hand from the admin catalogue, which is a
     * count of a real shelf.
     */
    @Transactional
    public Order markRefunded(Order order, String reference) {
        if (order.getStatus() == OrderStatus.REFUNDED) {
            return order;
        }

        boolean shipped = order.getStatus() == OrderStatus.SHIPPED;
        if (!shipped) {
            returnStock(order);
        }

        order.markRefunded(reference);
        Order saved = orderRepository.save(order);
        emailService.sendOrderRefunded(saved);
        return saved;
    }

    /**
     * Puts an order's units back on the shelf, under the same lock and in the
     * same order as checkout takes them off it. Returning stock is a
     * read-modify-write too, and it races with the buying of it.
     */
    private void returnStock(Order order) {
        order.getItems().stream()
                .map(OrderItem::getProduct)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Product::getId))
                .forEach(product -> entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE));

        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            // Null when the product row was deleted after the order was placed.
            // The order still shows what was bought, from its own snapshot;
            // there is simply nowhere to return the stock to.
            if (product != null) {
                product.setStock(product.getStock() + item.getQuantity());
                productRepository.save(product);
            }
        }
    }

    /**
     * Marks a paid order as dispatched and tells the customer.
     *
     * <p>Only from PAID. Shipping an order nobody paid for is the mistake this
     * guard exists to prevent, and it is an easy one to make from a list where
     * the rows look alike. A second press finds it already shipped and sends
     * nothing, so a double-click does not email twice.
     *
     * @return true when this call is what shipped it
     */
    @Transactional
    public boolean markShipped(Long orderId) {
        Order order = getAnyOrder(orderId);
        if (order.getStatus() != OrderStatus.PAID) {
            return false;
        }
        order.setStatus(OrderStatus.SHIPPED);
        Order saved = orderRepository.save(order);
        emailService.sendOrderShipped(saved);
        return true;
    }

    /** Every order, newest first. Admin only -- see OrderRepository. */
    public List<Order> allOrders() {
        return orderRepository.findAllByOrderByPlacedAtDesc();
    }

    public List<Order> ordersWithStatus(OrderStatus status) {
        return orderRepository.findByStatusOrderByPlacedAtDesc(status);
    }

    /**
     * Any order by id, without the owning-user check {@link #getForUser} makes.
     * Only reachable from the admin area.
     */
    public Order getAnyOrder(Long orderId) {
        return orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new NoSuchElementException("No order " + orderId));
    }

    public long countWithStatus(OrderStatus status) {
        return orderRepository.countByStatus(status);
    }

    /**
     * Takes a write lock on every product in the basket and re-reads its stock
     * from the database.
     *
     * <p>Without this, checkout was a read-modify-write with a gap in the
     * middle. Two customers buying the last panel both read a stock of one,
     * both passed the check, and both wrote zero: two orders, one panel, and
     * nothing in the system aware of it. That needs no unusual timing, only two
     * people in the same second, which is what an advert produces.
     *
     * <p>Refresh rather than a plain lock, and deliberately so. Locking an
     * entity that is already loaded blocks until the other transaction commits
     * and then leaves the stale value sitting in memory, so the second
     * transaction would decrement the figure it read before waiting -- the same
     * oversell with extra steps. Refreshing under the lock re-reads the
     * committed row.
     *
     * <p>Locks are taken in id order. Two baskets holding the same two products
     * in opposite orders would otherwise be able to deadlock, each holding what
     * the other needs next.
     */
    private void lockStock(List<CartItem> cartItems) {
        cartItems.stream()
                .map(CartItem::getProduct)
                .sorted(Comparator.comparing(Product::getId))
                .forEach(product -> entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * The single place an order becomes PAID, whichever route the news arrived
     * by -- a capture on the customer's return, or a webhook.
     *
     * <p>It takes an Order rather than an id on purpose. There was an overload
     * that looked up the order from an id and the signed-in user, and it read
     * as a permission check while being nothing of the sort: the customer is
     * exactly the person who should not decide that their own order is paid.
     * Requiring the loaded order means a caller has to have got it from a
     * provider's answer, which is the only thing that settles anything.
     *
     * <p>The status change and the confirmation email are deliberately welded
     * together here. The alternative, emailing from each caller, is how a
     * customer ends up with two "payment received" messages when a webhook
     * arrives just after they refresh the page.
     *
     * <p>Both are guarded by the transition itself: an order that is already
     * paid returns untouched and sends nothing. That is what makes a replayed
     * webhook harmless.
     */
    @Transactional
    public Order markPaid(Order order) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return order;
        }
        order.setStatus(OrderStatus.PAID);
        Order saved = orderRepository.save(order);
        // Sent inside the transaction, as the order confirmation is. If the
        // commit were to fail afterwards the customer would have been told
        // about a payment the order does not record -- but the money did move,
        // so the email is not a lie, and the provider's webhook retries until
        // the record agrees. EmailService never throws, so a mail outage
        // cannot undo a payment.
        emailService.sendPaymentReceived(saved);
        return saved;
    }
}
