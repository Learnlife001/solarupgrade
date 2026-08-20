package com.shoppingapp.shoppingwebapp.service.alerts;

import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import com.shoppingapp.shoppingwebapp.service.ResendMailer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * One broken page is one email, however many people hit it.
 *
 * <p>Its own class because the cooldown is state held by a single bean for the
 * life of the context: a test that needs the cooldown on cannot share a context
 * with tests that need it off.
 *
 * <p>{@link AlertBudgetTest} covers the rules in detail against a supplied
 * clock. This one proves the wiring uses them at all.
 */
@SpringBootTest(properties = {
        "app.alerts.recipients=ops@example.test",
        "app.alerts.cooldown-minutes=30",
        "app.mail.resend.api-key=test-key"})
@AutoConfigureMockMvc
class ErrorAlertCooldownTest {

    @TestConfiguration
    static class FailingRoute {

        @RestController
        static class Boom {

            @GetMapping("/test-only/repeatedly-broken")
            String boom() {
                throw new IllegalStateException("still broken");
            }
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResendMailer resendMailer;

    @Autowired
    private UserRepository users;

    /**
     * A real account, because the shared page furniture looks one up on every
     * request. Authenticating as a name that exists only in the security
     * context makes every request fail inside the model advice, and the alert
     * under test then describes that failure instead of the intended one --
     * which is how the first version of this test "passed" while proving
     * nothing.
     */
    @BeforeEach
    void createTheSignedInAccount() {
        if (!users.existsByEmail(SIGNED_IN)) {
            User account = new User(SIGNED_IN, "hash", "Test Person");
            account.markEmailVerified();
            users.save(account);
        }
    }

    private static final String SIGNED_IN = "alert-test@example.test";

    @Test
    void theSameFailureIsReportedOnceNotOncePerVisitor() throws Exception {
        for (int visitor = 0; visitor < 5; visitor++) {
            try {
                mockMvc.perform(get("/test-only/repeatedly-broken").with(user(SIGNED_IN)));
            } catch (Exception expected) {
                // Rethrown by MockMvc after the resolvers have looked at it.
            }
        }

        verify(resendMailer, times(1))
                .send(anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
