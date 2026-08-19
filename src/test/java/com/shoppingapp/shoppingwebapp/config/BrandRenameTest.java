package com.shoppingapp.shoppingwebapp.config;

import com.shoppingapp.shoppingwebapp.model.Role;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import com.shoppingapp.shoppingwebapp.service.EmailService;
import com.shoppingapp.shoppingwebapp.service.ResendMailer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The whole point of the brand setting: one variable renames the shop, with
 * nothing of the old name left behind.
 *
 * <p>Every assertion here is paired — the new name is present <em>and</em> the
 * old one is absent. Checking only the first would pass while a stale
 * "SolarUpgrade" sat in a page title or an email subject, which is exactly the
 * failure this replaced.
 */
@SpringBootTest(properties = {
        "app.brand.name=Helios Power",
        "app.brand.tagline=Solar for every rooftop.",
        "app.brand.mark=✦",
        "app.mail.resend.api-key=test-key"})
@AutoConfigureMockMvc
@Transactional
class BrandRenameTest {

    private static final String ADMIN = "brand-admin@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @MockitoBean
    private ResendMailer resendMailer;

    @BeforeEach
    void setUp() {
        User admin = userRepository.findByEmail(ADMIN)
                .orElseGet(() -> userRepository.save(new User(ADMIN, "hash", "Brand Admin")));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
    }

    private String body(String path) throws Exception {
        return mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString();
    }

    @Test
    void theShopFrontCarriesTheNewNameAndNotTheOld() throws Exception {
        String home = body("/");
        assertThat(home).contains("Helios Power").doesNotContain("SolarUpgrade");
        assertThat(home).contains("Solar for every rooftop.");
        assertThat(home).contains("✦");
    }

    /** The title is what shows in a browser tab and a bookmark. */
    @Test
    void everyPageTitleIsRenamed() throws Exception {
        assertThat(body("/products")).contains("<title>Shop · Helios Power</title>");
        assertThat(body("/login")).contains("<title>Sign in · Helios Power</title>");
        assertThat(body("/terms")).contains("<title>Terms of sale · Helios Power</title>");
    }

    @Test
    void theAdminAreaIsRenamedToo() throws Exception {
        String dashboard = mockMvc.perform(get("/admin").with(user(ADMIN).roles("ADMIN")))
                .andReturn().getResponse().getContentAsString();

        assertThat(dashboard).contains("Helios Power").doesNotContain("SolarUpgrade");
        assertThat(dashboard).contains("<title>Dashboard · Helios Power admin</title>");
    }

    /** The legal pages describe the shop, so they carry its name as well. */
    @Test
    void theTermsNameTheShop() throws Exception {
        assertThat(body("/terms")).contains("Helios Power").doesNotContain("SolarUpgrade");
    }

    /**
     * Emails were the worst of it: eighteen literals across subjects and
     * bodies, in the one place a customer definitely reads the name.
     */
    @Test
    void emailSubjectsAndBodiesAreRenamed() throws Exception {
        User customer = userRepository.save(
                new User("brand-customer@example.test", "hash", "Customer"));
        customer.issueVerificationCode("123456", Instant.now().plusSeconds(900));

        emailService.sendVerification(customer);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(resendMailer).send(anyString(), anyString(),
                subject.capture(), text.capture(), html.capture());

        assertThat(subject.getValue()).contains("Helios Power").doesNotContain("SolarUpgrade");
        assertThat(text.getValue()).contains("Helios Power").doesNotContain("SolarUpgrade");
        assertThat(html.getValue())
                .contains("Helios Power")
                .contains("Solar for every rooftop.")
                .contains("✦")
                .doesNotContain("SolarUpgrade");
    }

    @Test
    void theRateLimitPageIsRenamed() throws Exception {
        // Four resend requests: the fourth is refused and renders the filter's
        // own page, which is built before Spring MVC and so cannot read a model.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/forgot-password")
                    .param("email", "nobody@example.test")
                    .with(org.springframework.security.test.web.servlet.request
                            .SecurityMockMvcRequestPostProcessors.csrf()));
        }
        String refused = mockMvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.post("/forgot-password")
                        .param("email", "nobody@example.test")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf()))
                .andReturn().getResponse().getContentAsString();

        assertThat(refused).contains("Too many attempts · Helios Power").doesNotContain("SolarUpgrade");
    }

    /** The sample-data footer has to be able to go when real stock arrives. */
    @SpringBootTest(properties = {
            "app.brand.name=Helios Power",
            "app.brand.demo-notice=false"})
    @AutoConfigureMockMvc
    @org.junit.jupiter.api.Nested
    class WithTheDemoNoticeOff {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void theSampleDataLineIsGone() throws Exception {
            assertThat(mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString())
                    .doesNotContain("sample data")
                    .contains("Helios Power");
        }
    }
}
