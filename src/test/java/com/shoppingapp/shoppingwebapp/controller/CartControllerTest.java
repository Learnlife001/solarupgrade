package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CartControllerTest {

    private static final String EMAIL = "cart-controller@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    private Product product;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail(EMAIL)
                .orElseGet(() -> userRepository.save(new User(EMAIL, "hash", "Cart Tester")));
        product = productRepository.save(
                new Product("Cart Test Panel", "desc", new BigDecimal("99.00"), Category.PANEL, 20, null));
    }

    /**
     * The enhanced path: the header the script sends selects a JSON reply with
     * the new basket size, so the page the customer is reading stays put.
     */
    @Test
    void anAddCarryingTheAjaxHeaderAnswersWithTheNewBasketCount() throws Exception {
        mockMvc.perform(post("/cart/add")
                        .param("productId", product.getId().toString())
                        .param("quantity", "3")
                        .header("X-Requested-With", "fetch")
                        .with(user(EMAIL))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(3))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    /**
     * Without the header -- a browser with JavaScript off -- the same URL still
     * redirects to the basket, so nothing is left dead.
     */
    @Test
    void anAddWithoutTheAjaxHeaderStillRedirectsToTheBasket() throws Exception {
        mockMvc.perform(post("/cart/add")
                        .param("productId", product.getId().toString())
                        .with(user(EMAIL))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));
    }

    @Test
    void theAsyncEndpointIsNotOpenToAnonymousCallers() throws Exception {
        mockMvc.perform(post("/cart/add")
                        .param("productId", product.getId().toString())
                        .header("X-Requested-With", "fetch")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }
}
