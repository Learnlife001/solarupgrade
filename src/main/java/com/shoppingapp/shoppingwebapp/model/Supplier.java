package com.shoppingapp.shoppingwebapp.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A German solar supplier, listed so a Nigerian buyer can judge whether
 * importing directly is worth it.
 *
 * <p><b>Company-level data only.</b> A company name, its general enquiries
 * address and its city are business information; a named salesperson's direct
 * line is personal data under the GDPR, and publishing it without a lawful
 * basis is a real exposure. There is deliberately nowhere here to put one, so
 * the mistake cannot be made later by filling in a field that exists.
 *
 * <p>{@link #verifiedAt} and {@link #howVerified} are the point. Anyone can
 * copy a list of company names; what makes this worth reading is that each
 * entry says when somebody last checked and how. A directory with no dates
 * rots invisibly.
 */
@Entity
@Table(name = "suppliers")
public class Supplier {

    /** After this, an entry is stale enough to say so on the page. */
    private static final int STALE_AFTER_DAYS = 180;

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK).withZone(ZoneId.of("Europe/Berlin"));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 120)
    private String city;

    /** Bundesland, so a buyer can group visits or shipments by region. */
    @Column(length = 120)
    private String region;

    @Column(length = 255)
    private String website;

    /** A general enquiries address only -- never a named person's. */
    @Column(length = 255)
    private String contactEmail;

    /** What they stock, using the same vocabulary as the shop's catalogue. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "supplier_categories", joinColumns = @JoinColumn(name = "supplier_id"))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "category", nullable = false, length = 32)
    private Set<Category> categories = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private SupplierTrade trade = SupplierTrade.WHOLESALE;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "export_stance", nullable = false, length = 32)
    private ExportStance exportStance = ExportStance.UNKNOWN;

    /** In their words: "one pallet", "€5,000", "none". */
    @Column(length = 120)
    private String minimumOrder;

    /** EXW, FOB, CIF -- which decides who pays the freight. */
    @Column(length = 60)
    private String incoterms;

    /** Whether an English email will be answered, which is not a given. */
    @Column(length = 120)
    private String languages;

    /** Null rather than zero when nobody has asked. */
    @Column
    private Integer leadTimeWeeks;

    @Column(length = 1000)
    private String notes;

    /** Null until somebody actually checks. Never defaulted to "now". */
    @Column
    private Instant verifiedAt;

    /** "Emailed 12 Aug, they replied confirming FOB Hamburg." */
    @Column(length = 500)
    private String howVerified;

    protected Supplier() {
        // required by JPA
    }

    public Supplier(String name, String city) {
        this.name = name;
        this.city = city;
    }

    /**
     * Records a check. Both halves together, because a date with no account of
     * what was done is a claim without evidence.
     */
    public void markVerified(String how, Instant when) {
        this.howVerified = how;
        this.verifiedAt = when;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    /** True when the last check is old enough that the page should warn. */
    public boolean isStale() {
        return verifiedAt != null
                && verifiedAt.isBefore(Instant.now().minus(STALE_AFTER_DAYS, ChronoUnit.DAYS));
    }

    public String getVerifiedAtDisplay() {
        return verifiedAt == null ? "Never checked" : DISPLAY_FORMAT.format(verifiedAt);
    }

    /** Berlin time: these are German companies and German business hours. */
    public static int getStaleAfterDays() {
        return STALE_AFTER_DAYS;
    }

    public Long getId() {
        return id;
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
        this.categories = categories == null ? new LinkedHashSet<>() : new LinkedHashSet<>(categories);
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

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public String getHowVerified() {
        return howVerified;
    }
}
