package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.model.CartItem;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.CartItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CartService {

    private final CartItemRepository cartItemRepository;

    public CartService(CartItemRepository cartItemRepository) {
        this.cartItemRepository = cartItemRepository;
    }

    public List<CartItem> itemsFor(User user) {
        return cartItemRepository.findByUser(user);
    }

    public BigDecimal totalFor(User user) {
        return itemsFor(user).stream()
                .map(CartItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int itemCountFor(User user) {
        return itemsFor(user).stream().mapToInt(CartItem::getQuantity).sum();
    }

    /**
     * Adding a product already in the basket increases its quantity rather than
     * creating a second line, which is what the unique constraint on
     * (user, product) enforces at the database level.
     *
     * <p>The quantity is capped at what is on the shelf. The basket used to
     * accept any number and let checkout deliver the bad news, which is the
     * wrong end of the journey to learn that only two of the six panels you
     * chose exist.
     */
    @Transactional
    public AddOutcome add(User user, Product product, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        CartItem item = cartItemRepository.findByUserAndProduct(user, product)
                .orElseGet(() -> new CartItem(user, product, 0));

        int wanted = item.getQuantity() + quantity;
        int allowed = capToStock(product, wanted);
        item.setQuantity(allowed);
        cartItemRepository.save(item);
        return new AddOutcome(allowed, allowed < wanted);
    }

    /**
     * What ended up in the basket, and whether that is less than was asked for.
     * The caller needs the second half to say so — silently adding four when
     * six were requested is the kind of quiet correction people only notice at
     * the checkout.
     */
    public record AddOutcome(int quantityInBasket, boolean limitedByStock) {
    }

    /** A quantity of zero or less removes the line. */
    @Transactional
    public void updateQuantity(User user, Long cartItemId, int quantity) {
        CartItem item = requireOwnedItem(user, cartItemId);
        if (quantity < 1) {
            cartItemRepository.delete(item);
            return;
        }
        item.setQuantity(capToStock(item.getProduct(), quantity));
        cartItemRepository.save(item);
    }

    /**
     * The cap is advisory, not a reservation: stock can still run out between
     * filling a basket and paying, and only {@code OrderService.placeOrder},
     * holding a lock on the row, decides that for real.
     */
    private static int capToStock(Product product, int wanted) {
        if (product.getStock() < 1) {
            throw new IllegalArgumentException(product.getName() + " is out of stock");
        }
        return Math.min(wanted, product.getStock());
    }

    @Transactional
    public void remove(User user, Long cartItemId) {
        cartItemRepository.delete(requireOwnedItem(user, cartItemId));
    }

    @Transactional
    public void clear(User user) {
        cartItemRepository.deleteByUser(user);
    }

    /**
     * Guards against one user mutating another's basket by guessing an id.
     */
    private CartItem requireOwnedItem(User user, Long cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new IllegalArgumentException("No cart item with id " + cartItemId));
        if (!item.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Cart item does not belong to the current user");
        }
        return item;
    }
}
