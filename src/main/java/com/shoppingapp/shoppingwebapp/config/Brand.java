package com.shoppingapp.shoppingwebapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The name on the shop.
 *
 * <p>It used to be typed into 51 places across 29 files -- every page title,
 * the header, the admin bar, every email subject and body, a hard-coded error
 * page in the rate limiter. Whoever runs this next would have started with a
 * find-and-replace and missed some of them.
 *
 * <p>Now it is one setting. The defaults keep the shop working out of the box,
 * so nothing has to be configured to run it; changing the name is
 * {@code APP_BRAND_NAME} and a restart.
 */
@Component
@ConfigurationProperties(prefix = "app.brand")
public class Brand {

    /** Shown in the header, page titles and every email. */
    private String name = "SolarUpgrade";

    /** The footer line under the links. */
    private String tagline = "Panels, inverters and storage, delivered nationwide.";

    /**
     * The glyph in the header badge and beside the name in emails. A character
     * rather than an image file, so renaming the shop needs no new artwork.
     */
    private String mark = "☀";

    /**
     * Whether to say the catalogue is sample data.
     *
     * <p>True while the shop still shows the seeded catalogue. It has to be
     * switchable rather than baked in: a footer insisting the prices are
     * examples is honest today and a lie the moment somebody loads real stock.
     */
    private boolean demoNotice = true;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTagline() {
        return tagline;
    }

    public void setTagline(String tagline) {
        this.tagline = tagline;
    }

    public String getMark() {
        return mark;
    }

    public void setMark(String mark) {
        this.mark = mark;
    }

    public boolean isDemoNotice() {
        return demoNotice;
    }

    public void setDemoNotice(boolean demoNotice) {
        this.demoNotice = demoNotice;
    }
}
