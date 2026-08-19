package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.dto.SupplierForm;
import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.ExportStance;
import com.shoppingapp.shoppingwebapp.model.Supplier;
import com.shoppingapp.shoppingwebapp.model.SupplierTrade;
import com.shoppingapp.shoppingwebapp.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * The supplier directory.
 *
 * <p>Read by anyone, written only from the admin area. Nothing here accepts a
 * submission from the public: an open directory is a spam target, and an entry
 * nobody checked is worse than a missing one.
 */
@Service
@Transactional(readOnly = true)
public class SupplierService {

    private final SupplierRepository suppliers;

    public SupplierService(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    /** A blank filter is a null filter: an empty query string means "no filter". */
    public List<Supplier> search(Category category, SupplierTrade trade, ExportStance stance, String term) {
        return suppliers.search(category, trade, stance, blankToNull(term));
    }

    public List<Supplier> all() {
        return suppliers.findAll();
    }

    public Supplier get(Long id) {
        return suppliers.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No supplier with id " + id));
    }

    public long countExporting() {
        return suppliers.countByExportStance(ExportStance.YES);
    }

    @Transactional
    public Supplier save(SupplierForm form) {
        Supplier supplier = form.getId() == null
                ? new Supplier(form.getName().trim(), form.getCity().trim())
                : get(form.getId());
        form.applyTo(supplier);
        return suppliers.save(supplier);
    }

    /**
     * Records that somebody checked this entry, and what they did.
     *
     * <p>The account of the check is required rather than optional. A date on
     * its own is a claim with no evidence behind it, and the whole value of the
     * directory rests on those claims being worth something.
     */
    @Transactional
    public Supplier markVerified(Long id, String how) {
        if (how == null || how.isBlank()) {
            throw new IllegalArgumentException("Say how it was checked — a date on its own proves nothing");
        }
        Supplier supplier = get(id);
        supplier.markVerified(how.trim(), Instant.now());
        return suppliers.save(supplier);
    }

    @Transactional
    public void delete(Long id) {
        suppliers.delete(get(id));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
