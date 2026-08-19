package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.ExportStance;
import com.shoppingapp.shoppingwebapp.model.Supplier;
import com.shoppingapp.shoppingwebapp.model.SupplierTrade;
import com.shoppingapp.shoppingwebapp.service.SupplierService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * The public directory of German suppliers.
 *
 * <p>Here to be useful about a question customers ask anyway: could I import
 * this myself? The page answers it honestly, including what direct import
 * involves, rather than pretending the option does not exist. Somebody who
 * reads it and decides to import directly was never going to pay a markup for
 * the convenience; somebody who reads it and decides not to has chosen the shop
 * on their own terms, which is a better sale.
 *
 * <p>Read-only and public. There is no route here that writes anything --
 * entries are added from the admin area, because an open directory is a spam
 * target and an unchecked entry is worse than a missing one.
 */
@Controller
public class SupplierController {

    private final SupplierService suppliers;

    public SupplierController(SupplierService suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping("/suppliers")
    public String directory(@RequestParam(required = false) Category category,
                            @RequestParam(required = false) SupplierTrade trade,
                            @RequestParam(required = false) ExportStance stance,
                            @RequestParam(required = false) String q,
                            Model model) {
        List<Supplier> results = suppliers.search(category, trade, stance, q);

        model.addAttribute("suppliers", results);
        model.addAttribute("categories", Category.values());
        model.addAttribute("trades", SupplierTrade.values());
        model.addAttribute("stances", ExportStance.values());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedTrade", trade);
        model.addAttribute("selectedStance", stance);
        model.addAttribute("term", q == null ? "" : q);
        // Whether any filter is on, so an empty result can tell the difference
        // between "nothing matches" and "the directory is empty".
        model.addAttribute("filtered",
                category != null || trade != null || stance != null || (q != null && !q.isBlank()));
        model.addAttribute("staleAfterDays", Supplier.getStaleAfterDays());
        return "suppliers";
    }

    @GetMapping("/suppliers/{id}")
    public String supplier(@PathVariable Long id, Model model) {
        model.addAttribute("supplier", suppliers.get(id));
        model.addAttribute("staleAfterDays", Supplier.getStaleAfterDays());
        return "supplier";
    }
}
