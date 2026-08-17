package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("products", productService.findAll());
        return "index";
    }

    @GetMapping("/products")
    public String list(@RequestParam(required = false) Category category,
                       @RequestParam(required = false) String q,
                       Model model) {
        if (category != null) {
            model.addAttribute("products", productService.findByCategory(category));
        } else {
            model.addAttribute("products", productService.search(q));
        }
        model.addAttribute("selectedCategory", category);
        model.addAttribute("query", q);
        return "products";
    }

    @GetMapping("/products/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Product product = productService.getWithSpecs(id);
        model.addAttribute("product", product);
        model.addAttribute("pairsWith", productService.pairsWith(product, 3));
        return "product-detail";
    }
}
