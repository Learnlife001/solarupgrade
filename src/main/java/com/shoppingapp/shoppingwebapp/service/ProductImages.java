package com.shoppingapp.shoppingwebapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * The artwork a product can be given, discovered rather than typed.
 *
 * <p>The image is a path to a file that ships with the application, so a free
 * text box would mean a typo becoming a broken image on the shop — found by a
 * customer, days later. The form offers a list instead.
 *
 * <p><b>Only images that also exist as a PNG are offered.</b> The site draws
 * products as SVG and no email client renders SVG, so each drawing is also
 * rasterised into {@code static/images/email/}. A product given an SVG with no
 * PNG beside it looks right on the site and arrives with a hole in it in every
 * receipt — which is exactly the kind of fault nobody sees until a customer
 * has. Making it unofferable is cheaper than remembering.
 */
@Component
public class ProductImages {

    private static final Logger log = LoggerFactory.getLogger(ProductImages.class);

    private final List<String> available;

    public ProductImages() {
        this.available = discover();
    }

    /** Paths as a product stores them: {@code /images/panel-450w.svg}. */
    public List<String> getAvailable() {
        return available;
    }

    public boolean isAvailable(String image) {
        return image != null && available.contains(image);
    }

    private static List<String> discover() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            List<String> found = java.util.Arrays.stream(
                            resolver.getResources("classpath:/static/images/*.svg"))
                    .map(Resource::getFilename)
                    .filter(name -> name != null)
                    .filter(name -> hasEmailVersion(resolver, name))
                    .sorted()
                    .map(name -> "/images/" + name)
                    .toList();
            log.info("{} product images available to the admin form", found.size());
            return found;
        } catch (IOException ex) {
            // A shop that cannot list its artwork should still sell things; the
            // form simply offers nothing and the field can be left as it was.
            log.warn("Could not list product images", ex);
            return List.of();
        }
    }

    private static boolean hasEmailVersion(PathMatchingResourcePatternResolver resolver, String svgName) {
        String png = svgName.replace(".svg", ".png");
        return resolver.getResource("classpath:/static/images/email/" + png).exists();
    }
}
