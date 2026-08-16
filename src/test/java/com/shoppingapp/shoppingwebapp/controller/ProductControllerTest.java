package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void catalogueIsBrowsableWithoutSigningIn() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("products"))
                .andExpect(model().attributeExists("products"));
    }

    @Test
    void homePageRenders() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    /**
     * Everything about a product is public. Only doing something with it -- the
     * basket, the checkout -- needs an account. This pins that split, because
     * the easy mistake is to gate the detail page along with the basket.
     */
    @Test
    void aProductPageIsReadableWithoutSigningIn() throws Exception {
        Long id = productRepository.findAll().stream().findFirst().orElseThrow().getId();

        mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("product-detail"))
                .andExpect(model().attributeExists("product"));
    }

    @Test
    void addingToTheBasketRequiresSigningIn() throws Exception {
        mockMvc.perform(post("/cart/add").param("productId", "1").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void basketRequiresSigningIn() throws Exception {
        mockMvc.perform(get("/cart"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void ordersRequireSigningIn() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void checkoutRequiresSigningIn() throws Exception {
        mockMvc.perform(get("/checkout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
