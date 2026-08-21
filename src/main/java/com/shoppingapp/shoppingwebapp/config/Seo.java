package com.shoppingapp.shoppingwebapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * What search engines are told about this shop.
 *
 * <p><b>Indexing is off by default, on purpose.</b> The catalogue ships as
 * sample data with invented prices, and the legal pages carry a "draft — not in
 * force" notice until the business details are set. Being found with that
 * showing is worse than not being found: the prices are wrong, the terms are
 * not binding, and Google keeps a copy of both for weeks after they change.
 *
 * <p>So the tags, the sitemap and the structured data are all built and
 * correct, and one setting decides whether anybody is invited to read them.
 * Turn {@code app.seo.indexable} on when the catalogue is real.
 */
@Component
@ConfigurationProperties(prefix = "app.seo")
public class Seo {

    /** Whether search engines are invited in at all. */
    private boolean indexable = false;

    /**
     * The sentence under the shop's name in a search result, and the preview
     * when a link is pasted into WhatsApp. Pages that can describe themselves
     * better do; this is the fallback.
     */
    private String description = "";

    public boolean isIndexable() {
        return indexable;
    }

    public void setIndexable(boolean indexable) {
        this.indexable = indexable;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
