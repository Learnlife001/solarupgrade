package com.shoppingapp.shoppingwebapp.dto;

import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.ExportStance;
import com.shoppingapp.shoppingwebapp.model.Supplier;
import com.shoppingapp.shoppingwebapp.model.SupplierTrade;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The admin form for a directory entry.
 *
 * <p>There is no field for a contact person, deliberately. The form is the only
 * way data gets in, so leaving the field out is what keeps a named individual's
 * details out of the database — a stronger guarantee than a note asking people
 * not to type one.
 */
public class SupplierForm {

    private Long id;

    @NotBlank(message = "A company name is required")
    @Size(max = 160)
    private String name;

    @NotBlank(message = "A city is required")
    @Size(max = 120)
    private String city;

    @Size(max = 120)
    private String region = "";

    @Size(max = 255)
    private String website = "";

    @Email(message = "That does not look like an email address")
    @Size(max = 255)
    private String contactEmail = "";

    private Set<Category> categories = new LinkedHashSet<>();

    private SupplierTrade trade = SupplierTrade.WHOLESALE;

    private ExportStance exportStance = ExportStance.UNKNOWN;

    @Size(max = 120)
    private String minimumOrder = "";

    @Size(max = 60)
    private String incoterms = "";

    @Size(max = 120)
    private String languages = "";

    @Min(0)
    @Max(104)
    private Integer leadTimeWeeks;

    @Size(max = 1000)
    private String notes = "";

    public static SupplierForm of(Supplier supplier) {
        SupplierForm form = new SupplierForm();
        form.id = supplier.getId();
        form.name = supplier.getName();
        form.city = supplier.getCity();
        form.region = orEmpty(supplier.getRegion());
        form.website = orEmpty(supplier.getWebsite());
        form.contactEmail = orEmpty(supplier.getContactEmail());
        form.categories = new LinkedHashSet<>(supplier.getCategories());
        form.trade = supplier.getTrade();
        form.exportStance = supplier.getExportStance();
        form.minimumOrder = orEmpty(supplier.getMinimumOrder());
        form.incoterms = orEmpty(supplier.getIncoterms());
        form.languages = orEmpty(supplier.getLanguages());
        form.leadTimeWeeks = supplier.getLeadTimeWeeks();
        form.notes = orEmpty(supplier.getNotes());
        return form;
    }

    /**
     * Copies the form onto the entity, storing null for anything left blank.
     * "No minimum order stated" and "an empty minimum order" must not be two
     * different things in the database.
     */
    public void applyTo(Supplier supplier) {
        supplier.setName(name.trim());
        supplier.setCity(city.trim());
        supplier.setRegion(blankToNull(region));
        supplier.setWebsite(blankToNull(website));
        supplier.setContactEmail(blankToNull(contactEmail));
        supplier.setCategories(categories);
        supplier.setTrade(trade);
        supplier.setExportStance(exportStance);
        supplier.setMinimumOrder(blankToNull(minimumOrder));
        supplier.setIncoterms(blankToNull(incoterms));
        supplier.setLanguages(blankToNull(languages));
        supplier.setLeadTimeWeeks(leadTimeWeeks);
        supplier.setNotes(blankToNull(notes));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public Set<Category> getCategories() {
        return categories;
    }

    public void setCategories(Set<Category> categories) {
        this.categories = categories == null ? new LinkedHashSet<>() : categories;
    }

    public SupplierTrade getTrade() {
        return trade;
    }

    public void setTrade(SupplierTrade trade) {
        this.trade = trade;
    }

    public ExportStance getExportStance() {
        return exportStance;
    }

    public void setExportStance(ExportStance exportStance) {
        this.exportStance = exportStance;
    }

    public String getMinimumOrder() {
        return minimumOrder;
    }

    public void setMinimumOrder(String minimumOrder) {
        this.minimumOrder = minimumOrder;
    }

    public String getIncoterms() {
        return incoterms;
    }

    public void setIncoterms(String incoterms) {
        this.incoterms = incoterms;
    }

    public String getLanguages() {
        return languages;
    }

    public void setLanguages(String languages) {
        this.languages = languages;
    }

    public Integer getLeadTimeWeeks() {
        return leadTimeWeeks;
    }

    public void setLeadTimeWeeks(Integer leadTimeWeeks) {
        this.leadTimeWeeks = leadTimeWeeks;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
