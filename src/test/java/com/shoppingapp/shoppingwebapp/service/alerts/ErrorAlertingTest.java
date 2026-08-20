package com.shoppingapp.shoppingwebapp.service.alerts;

import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import com.shoppingapp.shoppingwebapp.service.ResendMailer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * A page that fails must tell somebody.
 *
 * <p>Driven through a real request rather than by calling the alerter, because
 * the thing worth proving is that the alerter is <em>wired to the requests at
 * all</em>. A unit test of the alerter would have passed just as happily with
 * nothing calling it, which is the state the application was in while
 * {@code /suppliers} returned 500 to every visitor for twenty-five minutes.
 */
@SpringBootTest(properties = {
        "app.alerts.recipients=ops@example.test",
        // No cooldown here. The alerter is one bean for the whole context, so
        // with a cooldown every test after the first would be silenced by the
        // first one's alert and would fail for a reason that has nothing to do
        // with what it is testing. The cooldown has its own class.
        "app.alerts.cooldown-minutes=0",
        "app.mail.resend.api-key=test-key"})
@AutoConfigureMockMvc
class ErrorAlertingTest {

    /**
     * Routes that fail on purpose, registered only for this test.
     *
     * <p>Nested inside a {@code @TestConfiguration}, which Spring picks up on
     * its own -- adding an {@code @Bean} method for it as well registers the
     * same controller twice and the context dies with "ambiguous mapping".
     */
    @TestConfiguration
    static class FailingRoutes {

        @RestController
        static class Boom {

            @GetMapping("/test-only/boom")
            String boom() {
                throw new IllegalStateException("the thing that broke");
            }

            @GetMapping("/test-only/missing")
            String missing() {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no such thing");
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

    private void request(String path) throws Exception {
        try {
            mockMvc.perform(get(path).with(user(SIGNED_IN)));
        } catch (Exception expected) {
            // MockMvc rethrows what the handler threw once the resolvers have
            // had their look. The alert has already been raised by then.
        }
    }

    @Test
    void aFailedRequestIsReported() throws Exception {
        request("/test-only/boom");

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(resendMailer).send(anyString(), anyString(), subject.capture(), anyString(), anyString());

        assertThat(subject.getValue())
                .contains("/test-only/boom")
                .contains("IllegalStateException");
    }

    @Test
    void theAlertSaysWhatBrokeAndWhere() throws Exception {
        request("/test-only/boom");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(resendMailer).send(anyString(), anyString(), anyString(), anyString(), html.capture());

        assertThat(html.getValue())
                .contains("the thing that broke")
                .contains("java.lang.IllegalStateException")
                // The frame that names our own code is the point of the email.
                .contains("com.shoppingapp.shoppingwebapp");
    }

    @Test
    void itGoesToTheConfiguredRecipient() throws Exception {
        request("/test-only/boom");

        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        verify(resendMailer).send(anyString(), to.capture(), anyString(), anyString(), anyString());

        assertThat(to.getValue()).isEqualTo("ops@example.test");
    }

    /**
     * A 404 is the request being wrong, not the shop being broken. Alerting on
     * these would fill the inbox with crawler traffic and other people's typos.
     */
    @Test
    void aMissingPageIsNotAnAlert() throws Exception {
        request("/test-only/missing");

        verify(resendMailer, never())
                .send(anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
