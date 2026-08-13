package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.model.CartItem;
import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.CartItemRepository;
import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CartServiceTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    private User user;
    private Product panel;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("cart-test@example.com", "hash", "Cart Tester"));
        panel = productRepository.save(
                new Product("Test Panel", "desc", new BigDecimal("100.00"), Category.PANEL, 50, null));
    }

    @Test
    void addingTheSameProductTwiceCombinesIntoOneLine() {
        cartService.add(user, panel, 2);
        cartService.add(user, panel, 3);

        assertThat(cartService.itemsFor(user)).hasSize(1);
        assertThat(cartService.itemCountFor(user)).isEqualTo(5);
    }

    @Test
    void totalMultipliesPriceByQuantity() {
        cartService.add(user, panel, 3);

        assertThat(cartService.totalFor(user)).isEqualByComparingTo("300.00");
    }

    @Test
    void addingZeroOrFewerIsRejected() {
        assertThatThrownBy(() -> cartService.add(user, panel, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updatingToZeroRemovesTheLine() {
        cartService.add(user, panel, 2);
        CartItem item = cartService.itemsFor(user).get(0);

        cartService.updateQuantity(user, item.getId(), 0);

        assertThat(cartService.itemsFor(user)).isEmpty();
    }

    @Test
    void aUserCannotTouchAnotherUsersCartItem() {
        cartService.add(user, panel, 1);
        CartItem item = cartService.itemsFor(user).get(0);
        User intruder = userRepository.save(new User("intruder@example.com", "hash", "Intruder"));

        assertThatThrownBy(() -> cartService.remove(intruder, item.getId()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(cartItemRepository.findById(item.getId())).isPresent();
    }

    @Test
    void clearEmptiesTheBasket() {
        cartService.add(user, panel, 2);
        cartService.clear(user);

        assertThat(cartService.itemsFor(user)).isEmpty();
    }
}
