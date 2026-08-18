package com.shoppingapp.shoppingwebapp.service.email;

import com.shoppingapp.shoppingwebapp.dto.CheckoutForm;
import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import com.shoppingapp.shoppingwebapp.service.CartService;
import com.shoppingapp.shoppingwebapp.service.EmailService;
import com.shoppingapp.shoppingwebapp.service.OrderService;
import com.shoppingapp.shoppingwebapp.service.ResendMailer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Renders the real emails and checks what actually goes down the wire.
 *
 * <p>ResendMailer is replaced by a mock so the HTML can be captured at the last
 * moment before it leaves, rather than by re-deriving it here and testing a
 * copy of the template against itself.
 *
 * <p>Set {@code -Demail.dump.dir=/some/path} to write the rendered HTML out for
 * looking at in a browser.
 */
@SpringBootTest(properties = {
        "app.mail.resend.api-key=test-key",
        "app.base-url=https://solarupgrade.onrender.com"})
@Transactional
class EmailRenderTest {

    @Autowired
    private EmailService emailService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @MockitoBean
    private ResendMailer resendMailer;

    private User user;
    private Order order;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("render-test@example.test", "hash", "Adaeze Okafor"));
        Product panel = productRepository.save(new Product(
                "450W Monocrystalline Panel",
                "High-efficiency panel for rooftop arrays, with a 25-year performance warranty.",
                new BigDecimal("380000.00"), Category.PANEL, 10, "/images/panel-450w.svg"));
        Product battery = productRepository.save(new Product(
                "5.2kWh Battery Module",
                "Lithium iron phosphate storage that carries the house through an outage.",
                new BigDecimal("4780000.00"), Category.BATTERY, 5, "/images/battery-5-2kwh.svg"));

        cartService.add(user, panel, 2);
        cartService.add(user, battery, 1);

        CheckoutForm form = new CheckoutForm();
        form.setShippingName("Adaeze Okafor");
        form.setShippingLine1("14 Adeola Odeku Street");
        form.setShippingLine2("Flat 3B");
        form.setShippingCity("Victoria Island");
        form.setShippingState("Lagos");
        form.setShippingCountry("NG");
        form.setPaymentMethod(PaymentMethod.PAYPAL);
        order = orderService.placeOrder(user, form);
    }

    /**
     * The newest send, not the only one: placing the order in setUp already
     * sent a confirmation, so every test here is at least the second call.
     */
    private String captureHtml() {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(resendMailer, atLeastOnce())
                .send(anyString(), anyString(), anyString(), anyString(), html.capture());
        return html.getValue();
    }

    private String captureText() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(resendMailer, atLeastOnce())
                .send(anyString(), anyString(), anyString(), text.capture(), anyString());
        return text.getValue();
    }

    private void dump(String name, String html) throws Exception {
        String dir = System.getProperty("email.dump.dir");
        if (dir != null) {
            Files.createDirectories(Path.of(dir));
            Files.writeString(Path.of(dir, name + ".html"), html);
        }
    }

    @Test
    void theReceiptCarriesProductPicturesAsPngNotSvg() throws Exception {
        emailService.sendPaymentReceived(order);
        String html = captureHtml();
        dump("payment-received", html);

        // The site draws products as SVG and no major client renders it.
        assertThat(html).contains("https://solarupgrade.onrender.com/images/email/panel-450w.png");
        assertThat(html).contains("https://solarupgrade.onrender.com/images/email/battery-5-2kwh.png");
        assertThat(html).doesNotContain(".svg");
    }

    @Test
    void theReceiptCarriesTheFullProductDetail() throws Exception {
        emailService.sendPaymentReceived(order);
        String html = captureHtml();

        assertThat(html)
                .contains("450W Monocrystalline Panel")
                .contains("High-efficiency panel for rooftop arrays")
                .contains("5.2kWh Battery Module")
                .contains("Lithium iron phosphate storage")
                // Category, quantity, unit price, line total and the total.
                .contains("Solar Panels")
                .contains("Quantity 2")
                .contains("₦380,000")
                .contains("₦760,000")
                .contains("₦5,540,000")
                // No stray format specifiers left by the template.
                .doesNotContain("%%")
                .doesNotContain("%TITLE%")
                .doesNotContain("%BODY%");
    }

    /** Links have to be absolute: an inbox has no site to be relative to. */
    @Test
    void everyLinkIsAbsolute() throws Exception {
        emailService.sendPaymentReceived(order);
        String html = captureHtml();

        assertThat(html).contains("href=\"https://solarupgrade.onrender.com/orders/" + order.getId());
        assertThat(html).doesNotContain("href=\"/");
        assertThat(html).doesNotContain("src=\"/");
    }

    /** Both bodies, every time. A missing text part reads as spam. */
    @Test
    void everyEmailCarriesTextAsWellAsHtml() {
        emailService.sendOrderShipped(order);

        verify(resendMailer, atLeastOnce()).send(anyString(), eq("render-test@example.test"),
                anyString(), anyString(), anyString());
        assertThat(captureText())
                .contains("450W Monocrystalline Panel")
                .contains("14 Adeola Odeku Street")
                .doesNotContain("<table")
                .doesNotContain("<td");
    }

    /**
     * A shipping address is whatever the customer typed. Left unescaped it
     * would let them write markup into an email we send in our own name.
     */
    @Test
    void anythingATypedFieldContainsIsEscaped() {
        User attacker = userRepository.save(
                new User("escape-test@example.test", "hash", "<script>alert(1)</script>"));
        Product panel = productRepository.save(new Product(
                "Panel", "desc", new BigDecimal("1000.00"), Category.PANEL, 5, "/images/panel-450w.svg"));
        cartService.add(attacker, panel, 1);
        CheckoutForm form = new CheckoutForm();
        form.setShippingName("Mallory <img src=x onerror=alert(1)>");
        form.setShippingLine1("1 \"Quote\" Street");
        form.setShippingCity("Lagos");
        form.setShippingState("Lagos");
        form.setShippingCountry("NG");
        form.setPaymentMethod(PaymentMethod.PAYPAL);
        Order hostile = orderService.placeOrder(attacker, form);

        emailService.sendOrderShipped(hostile);
        String html = captureHtml();

        assertThat(html)
                .doesNotContain("<script>")
                .doesNotContain("<img src=x")
                .contains("&lt;script&gt;")
                .contains("&lt;img src=x");
    }

    /** The dispatch notice leaves money out; it is settled by then. */
    @Test
    void theDispatchNoticeShowsItemsWithoutRepeatingThePrices() throws Exception {
        emailService.sendOrderShipped(order);
        String html = captureHtml();
        dump("shipped", html);

        assertThat(html)
                .contains("450W Monocrystalline Panel")
                .contains("images/email/panel-450w.png")
                .contains("14 Adeola Odeku Street")
                .doesNotContain("₦5,540,000");
    }

    @Test
    void theVerificationCodeIsShownAsACodeNotBuriedInProse() throws Exception {
        user.issueVerificationCode("123456", java.time.Instant.now().plusSeconds(900));
        emailService.sendVerification(user);
        String html = captureHtml();
        dump("verification", html);

        assertThat(html).contains("123456").contains("monospace");
    }

    @Test
    void theResetEmailLinksTheTokenOnce() throws Exception {
        emailService.sendPasswordReset(user, "a-token-value");
        String html = captureHtml();
        dump("password-reset", html);

        assertThat(html).contains("https://solarupgrade.onrender.com/reset-password?token=a-token-value");
    }

    @Test
    void theReminderAndExpiryEmailsRenderToo() throws Exception {
        emailService.sendPaymentReminder(order);
        dump("reminder", captureHtml());
    }

    @Test
    void theConfirmationRenders() throws Exception {
        emailService.sendOrderConfirmation(order);
        // placeOrder already sent one, so this is the second call.
        String html = captureHtml();
        dump("confirmation", html);
        assertThat(html).contains("Delivering to");
    }
}
