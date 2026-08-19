package com.shoppingapp.shoppingwebapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The default brand mark survives being read out of application.properties.
 *
 * <p>It did not. Java reads a {@code .properties} file as ISO-8859-1, so the
 * sun written there as literal UTF-8 was decoded as the three separate bytes it
 * is made of, and the header of every page and the top of every email showed
 * {@code Ã¢Ëœâ‚¬} instead. Nothing failed; it just looked broken to every
 * customer who got an email.
 *
 * <p>The fix is to write it as a {@code \\u2600} escape, which is plain ASCII
 * and therefore immune to how the file is decoded. These tests hold that in
 * place from both ends: the file stays ASCII, and what reaches the page is one
 * character rather than three.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BrandMarkEncodingTest {

    /** BLACK SUN WITH RAYS, the default mark. */
    private static final String SUN = "☀";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Brand brand;

    @Test
    void theDefaultMarkIsOneCharacterNotItsBytes() {
        assertThat(brand.getMark()).isEqualTo(SUN);
        // The mojibake was three characters where one was meant. Length is the
        // shortest way to say "this was not decoded twice".
        assertThat(brand.getMark()).hasSize(1);
    }

    @Test
    void theMarkReachesThePageIntact() throws Exception {
        byte[] page = mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String html = new String(page, StandardCharsets.UTF_8);
        assertThat(html).contains(SUN);
        // "Ã¢" is what the first byte of a UTF-8 sun looks like once it has
        // been through ISO-8859-1 and back out as UTF-8.
        assertThat(html).doesNotContain("Ã¢");
    }

    /**
     * The properties file itself must stay ASCII. A non-ASCII character added
     * to it later is the same bug again, wherever it appears, so this catches
     * it at the source rather than one rendering at a time.
     */
    @Test
    void applicationPropertiesContainsNoNonAsciiCharacters() throws Exception {
        Path properties = Path.of("src/main/resources/application.properties");
        String contents = Files.readString(properties, StandardCharsets.UTF_8);

        for (int i = 0; i < contents.length(); i++) {
            char character = contents.charAt(i);
            assertThat((int) character)
                    .as("non-ASCII '%s' at index %d of application.properties; "
                            + "write it as a \\uXXXX escape, because Java decodes "
                            + "this file as ISO-8859-1", character, i)
                    .isLessThan(128);
        }
    }
}
