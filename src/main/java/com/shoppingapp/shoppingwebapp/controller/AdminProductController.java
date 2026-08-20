package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.dto.ProductForm;
import com.shoppingapp.shoppingwebapp.model.AdminActionType;
import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.service.AuditService;
import com.shoppingapp.shoppingwebapp.service.ProductImages;
import com.shoppingapp.shoppingwebapp.service.ProductService;
import com.shoppingapp.shoppingwebapp.service.StockService;
import com.shoppingapp.shoppingwebapp.support.Redact;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.NoSuchElementException;

/**
 * The catalogue, editable.
 *
 * <p>Until now the only thing the back office could change about a product was
 * its stock. A price change or a new line meant writing a migration, committing
 * it and waiting for a deploy — which is not a shop somebody can run, and is
 * the sort of thing that makes an owner keep a spreadsheet of "real" prices
 * beside a website showing the wrong ones.
 *
 * <p><b>Nothing here deletes.</b> {@code order_items} point at products, and an
 * order from last month has to keep saying what it was for. A product that is
 * no longer sold is archived: out of the shop, still in this list, and
 * restorable. Order history that rewrites itself when the catalogue changes
 * would be a far worse bug than a long list here.
 *
 * <p>Every change is written to the audit trail with the price or stock it
 * moved from, because "who changed this price and when" is the first question
 * asked when a sale goes wrong.
 */
@Controller
@RequestMapping("/admin/products")
public class AdminProductController {

    private static final Logger log = LoggerFactory.getLogger(AdminProductController.class);

    /** Below this, a product is worth flagging. */
    private static final int LOW_STOCK = 3;

    private final ProductService productService;
    private final ProductImages productImages;
    private final AuditService auditService;
    private final StockService stockService;

    public AdminProductController(ProductService productService,
                                  ProductImages productImages,
                                  AuditService auditService,
                                  StockService stockService) {
        this.productService = productService;
        this.productImages = productImages;
        this.auditService = auditService;
        this.stockService = stockService;
    }

    @ModelAttribute("categories")
    Category[] categories() {
        return Category.values();
    }

    @ModelAttribute("images")
    java.util.List<String> images() {
        return productImages.getAvailable();
    }

    @GetMapping
    public String products(Model model) {
        model.addAttribute("products", productService.byStockAscending());
        model.addAttribute("lowStockThreshold", LOW_STOCK);
        return "admin/products";
    }

    @GetMapping("/new")
    public String newProduct(Model model) {
        model.addAttribute("productForm", new ProductForm());
        model.addAttribute("heading", "Add a product");
        return "admin/product-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("productForm") ProductForm form,
                         BindingResult binding,
                         Principal principal,
                         Model model,
                         RedirectAttributes flash) {
        if (binding.hasErrors()) {
            model.addAttribute("heading", "Add a product");
            return "admin/product-form";
        }

        // Created empty, then counted up: the ledger's first row is the
        // movement that put the units on the shelf rather than a figure that
        // appeared from nowhere.
        Product saved = productService.save(form.toNewProduct());
        if (form.getStock() > 0) {
            saved = productService.setStock(saved.getId(), form.getStock(), principal.getName());
        }
        log.info("Product {} created by {}", saved.getId(), Redact.email(principal.getName()));
        auditService.record(principal.getName(), AdminActionType.PRODUCT_CREATED,
                AuditService.PRODUCT, saved.getId(),
                saved.getName() + " at " + saved.getPriceDisplay() + ", " + saved.getStock() + " in stock");
        flash.addFlashAttribute("message", saved.getName() + " added to the catalogue.");
        return "redirect:/admin/products";
    }

    /** How many movements the edit page shows before it becomes a wall. */
    private static final int HISTORY_SHOWN = 20;

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        Product product = productService.getById(id);
        model.addAttribute("productForm", ProductForm.of(product));
        model.addAttribute("product", product);
        // Shown here because this is the page somebody is on when they ask why
        // the figure is what it is -- usually while holding a different count.
        model.addAttribute("movements", stockService.historyFor(id, HISTORY_SHOWN));
        model.addAttribute("heading", "Edit " + product.getName());
        return "admin/product-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("productForm") ProductForm form,
                         BindingResult binding,
                         Principal principal,
                         Model model,
                         RedirectAttributes flash) {
        Product product = productService.getById(id);
        if (binding.hasErrors()) {
            model.addAttribute("product", product);
            model.addAttribute("heading", "Edit " + product.getName());
            return "admin/product-form";
        }

        // Read before the change, so the trail says what it moved from rather
        // than only what it is now. "Price changed" answers nothing.
        String before = product.getName() + " at " + product.getPriceDisplay();
        form.applyTo(product);
        Product saved = productService.save(product);

        // Stock is not part of applyTo: changing it here would move the figure
        // with nothing in the ledger to explain it. A different number in the
        // box is a stock take, and is recorded as one.
        if (form.getStock() != saved.getStock()) {
            saved = productService.setStock(saved.getId(), form.getStock(), principal.getName());
        }

        log.info("Product {} updated by {}", id, Redact.email(principal.getName()));
        auditService.record(principal.getName(), AdminActionType.PRODUCT_UPDATED,
                AuditService.PRODUCT, id,
                before + " to " + saved.getName() + " at " + saved.getPriceDisplay());
        flash.addFlashAttribute("message", saved.getName() + " updated.");
        return "redirect:/admin/products";
    }

    /**
     * Absolute rather than a delta, because the person typing it is looking at
     * the actual pile. Kept as its own action beside the edit form so a stock
     * take is one field and one button.
     */
    @PostMapping("/{id}/stock")
    public String setStock(@PathVariable Long id,
                           @RequestParam int stock,
                           Principal principal,
                           RedirectAttributes flash) {
        try {
            int before = productService.getById(id).getStock();
            Product product = productService.setStock(id, stock, principal.getName());
            log.info("Stock for product {} set to {} by {}", id, stock, Redact.email(principal.getName()));
            auditService.record(principal.getName(), AdminActionType.STOCK_SET,
                    AuditService.PRODUCT, id,
                    product.getName() + ": " + before + " to " + stock);
            flash.addFlashAttribute("message", product.getName() + " set to " + stock + " in stock.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/products";
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id, Principal principal, RedirectAttributes flash) {
        Product product = productService.archive(id);
        log.info("Product {} archived by {}", id, Redact.email(principal.getName()));
        auditService.record(principal.getName(), AdminActionType.PRODUCT_ARCHIVED,
                AuditService.PRODUCT, id, product.getName());
        flash.addFlashAttribute("message",
                product.getName() + " archived. It is off the shop but still on every order that bought it.");
        return "redirect:/admin/products";
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id, Principal principal, RedirectAttributes flash) {
        Product product = productService.restore(id);
        log.info("Product {} restored by {}", id, Redact.email(principal.getName()));
        auditService.record(principal.getName(), AdminActionType.PRODUCT_RESTORED,
                AuditService.PRODUCT, id, product.getName());
        flash.addFlashAttribute("message", product.getName() + " is back in the shop.");
        return "redirect:/admin/products";
    }

    /** A product id that is not there is a wrong link, not a server fault. */
    @org.springframework.web.bind.annotation.ExceptionHandler(NoSuchElementException.class)
    public String notFound(RedirectAttributes flash) {
        flash.addFlashAttribute("error", "That product no longer exists.");
        return "redirect:/admin/products";
    }
}
