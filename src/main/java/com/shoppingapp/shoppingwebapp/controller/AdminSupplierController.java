package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.dto.SupplierForm;
import com.shoppingapp.shoppingwebapp.model.AdminActionType;
import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.ExportStance;
import com.shoppingapp.shoppingwebapp.model.Supplier;
import com.shoppingapp.shoppingwebapp.model.SupplierTrade;
import com.shoppingapp.shoppingwebapp.service.AuditService;
import com.shoppingapp.shoppingwebapp.service.SupplierService;
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

/**
 * Maintaining the directory.
 *
 * <p>Under /admin, so the one ROLE_ADMIN rule in SecurityConfig covers it
 * without a second guard to remember. Every change is recorded in the audit
 * trail: a directory whose entries change with no record of who changed them
 * cannot be trusted by the person maintaining it, let alone by a reader.
 */
@Controller
@RequestMapping("/admin/suppliers")
public class AdminSupplierController {

    private static final Logger log = LoggerFactory.getLogger(AdminSupplierController.class);

    private final SupplierService suppliers;
    private final AuditService auditService;

    public AdminSupplierController(SupplierService suppliers, AuditService auditService) {
        this.suppliers = suppliers;
        this.auditService = auditService;
    }

    private void addFormChoices(Model model) {
        model.addAttribute("allCategories", Category.values());
        model.addAttribute("trades", SupplierTrade.values());
        model.addAttribute("stances", ExportStance.values());
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("suppliers", suppliers.all());
        model.addAttribute("staleAfterDays", Supplier.getStaleAfterDays());
        return "admin/suppliers";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("supplierForm")) {
            model.addAttribute("supplierForm", new SupplierForm());
        }
        addFormChoices(model);
        return "admin/supplier-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("supplierForm")) {
            model.addAttribute("supplierForm", SupplierForm.of(suppliers.get(id)));
        }
        model.addAttribute("supplier", suppliers.get(id));
        addFormChoices(model);
        return "admin/supplier-form";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("supplierForm") SupplierForm supplierForm,
                       BindingResult bindingResult,
                       Principal principal,
                       Model model,
                       RedirectAttributes flash) {
        if (bindingResult.hasErrors()) {
            addFormChoices(model);
            return "admin/supplier-form";
        }

        boolean isNew = supplierForm.getId() == null;
        Supplier saved = suppliers.save(supplierForm);

        auditService.record(principal.getName(),
                isNew ? AdminActionType.SUPPLIER_ADDED : AdminActionType.SUPPLIER_UPDATED,
                AuditService.SUPPLIER, saved.getId(),
                saved.getName() + " — " + saved.getExportStance().getDisplayName());
        log.info("Supplier {} {} by {}", saved.getId(), isNew ? "added" : "updated", Redact.email(principal.getName()));

        flash.addFlashAttribute("message", saved.getName() + (isNew ? " added." : " saved."));
        return "redirect:/admin/suppliers";
    }

    /**
     * Records a check. The account of what was done is required by the service,
     * so this only has to report the refusal.
     */
    @PostMapping("/{id}/verify")
    public String verify(@PathVariable Long id,
                         @RequestParam String howVerified,
                         Principal principal,
                         RedirectAttributes flash) {
        try {
            Supplier verified = suppliers.markVerified(id, howVerified);
            auditService.record(principal.getName(), AdminActionType.SUPPLIER_VERIFIED,
                    AuditService.SUPPLIER, id, verified.getHowVerified());
            flash.addFlashAttribute("message",
                    verified.getName() + " marked as checked today.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/suppliers";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal, RedirectAttributes flash) {
        Supplier supplier = suppliers.get(id);
        String name = supplier.getName();
        // Recorded before the row goes, or there is nothing left to describe.
        auditService.record(principal.getName(), AdminActionType.SUPPLIER_REMOVED,
                AuditService.SUPPLIER, id, name);
        suppliers.delete(id);
        log.info("Supplier {} removed by {}", id, Redact.email(principal.getName()));
        flash.addFlashAttribute("message", name + " removed from the directory.");
        return "redirect:/admin/suppliers";
    }
}
