package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.service.ProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ProductController {

    /**
     * Absolute URLs are required in structured data and share previews: a
     * crawler resolving /images/panel.svg against its own host gets nothing.
     */
    private final String baseUrl;

    private final ProductService productService;

    public ProductController(ProductService productService,
                             @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.productService = productService;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
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
        // What a search result and a pasted link show: the product's own
        // sentence rather than the shop's general one.
        model.addAttribute("metaDescription", product.getDescription());
        model.addAttribute("ogType", "product");
        if (product.getImage() != null) {
            model.addAttribute("ogImage", baseUrl + product.getImage());
        }
        return "product-detail";
    }
}
