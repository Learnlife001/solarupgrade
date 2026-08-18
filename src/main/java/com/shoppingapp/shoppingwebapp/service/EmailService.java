package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderItem;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.service.email.EmailHtml;
import com.shoppingapp.shoppingwebapp.service.email.OrderEmailParts;
import com.shoppingapp.shoppingwebapp.support.Redact;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

import static com.shoppingapp.shoppingwebapp.service.email.EmailHtml.escape;

/**
 * Every message the shop sends.
 *
 * <p>Each one goes out as both HTML and plain text in the same message. The
 * HTML is what almost everyone sees; the text part is not a formality. Some
 * clients are configured to prefer it, spam filters read a missing text part as
 * a signal, and a watch or a screen reader may take it in preference. Writing
 * only one of the two is how an email arrives as a wall of markup.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    /**
     * Two transports: Resend's HTTP API when an API key is configured,
     * otherwise SMTP when spring.mail.host is set, otherwise nothing but a log
     * line. Both are injected lazily so the app runs with neither configured,
     * which is the default for local development.
     */
    private final ObjectProvider<ResendMailer> resendProvider;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;
    private final String baseUrl;

    public EmailService(ObjectProvider<ResendMailer> resendProvider,
                        ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${app.mail.from:no-reply@solarupgrade.example}") String fromAddress,
                        @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.resendProvider = resendProvider;
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress;
        // Links in email must be absolute, and the app cannot infer its own
        // public address from behind a proxy.
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String orderUrl(Order order) {
        return baseUrl + "/orders/" + order.getId();
    }

    /**
     * A failure to send must never roll back an order that was already written,
     * so problems here are logged rather than thrown.
     */
    public void sendOrderConfirmation(Order order) {
        String html = EmailHtml.document(
                "Your SolarUpgrade order #" + order.getId(),
                "We have your order. Nothing has been charged yet.",
                EmailHtml.heading("Thanks, " + order.getUser().getFullName())
                        + EmailHtml.pill("Awaiting payment", EmailHtml.SUN_SOFT, EmailHtml.SUN_INK)
                        + EmailHtml.paragraph("We have your order and the items are held for you. "
                                + "Nothing has been charged yet &mdash; finish paying and we will get it moving.")
                        + EmailHtml.button("Finish paying", orderUrl(order))
                        + EmailHtml.divider()
                        + EmailHtml.sectionTitle("Your order #" + order.getId())
                        + OrderEmailParts.itemTable(order, baseUrl, true)
                        + OrderEmailParts.totals(order)
                        + EmailHtml.divider()
                        + EmailHtml.sectionTitle("Delivering to")
                        + OrderEmailParts.address(order),
                "You are receiving this because you placed an order with us.");

        send(order.getUser().getEmail(),
                "Your SolarUpgrade order #" + order.getId(),
                textOrderBody(order, "Thanks for your order. Here is what we have:", true),
                html,
                "confirmation for order " + order.getId());
    }

    /**
     * Confirms that the money arrived and the order is going out.
     *
     * <p>Sent from {@link OrderService#markPaid(Order)} and only on the actual
     * transition, so a replayed webhook or a refreshed browser cannot produce
     * a second copy.
     */
    public void sendPaymentReceived(Order order) {
        String html = EmailHtml.document(
                "Payment received — SolarUpgrade order #" + order.getId(),
                "Payment confirmed. Nothing further is needed from you.",
                EmailHtml.heading("Payment received")
                        + EmailHtml.pill("Paid", EmailHtml.ACCENT_SOFT, EmailHtml.ACCENT_INK)
                        + EmailHtml.paragraph("Hi " + escape(order.getUser().getFullName())
                                + ", your payment has come through and order #" + order.getId()
                                + " is confirmed. Nothing further is needed from you &mdash; "
                                + "we will email again when it ships.")
                        + EmailHtml.button("View your order", orderUrl(order))
                        + EmailHtml.divider()
                        + EmailHtml.sectionTitle("What you bought")
                        + OrderEmailParts.itemTable(order, baseUrl, true)
                        + OrderEmailParts.totals(order)
                        + EmailHtml.divider()
                        + EmailHtml.sectionTitle("Delivering to")
                        + OrderEmailParts.address(order)
                        + EmailHtml.small("Keep this email as your receipt."),
                "This is your receipt for order #" + order.getId() + ".");

        send(order.getUser().getEmail(),
                "Payment received — SolarUpgrade order #" + order.getId(),
                textOrderBody(order, "Your payment has come through and this order is confirmed.", true)
                        + "\nKeep this as your receipt.\n",
                html,
                "payment receipt for order " + order.getId());
    }

    /**
     * The one nudge an unpaid order gets. Sent once per order; see
     * {@link PaymentReminderJob}.
     */
    public void sendPaymentReminder(Order order) {
        String html = EmailHtml.document(
                "Finish your SolarUpgrade order #" + order.getId(),
                "Your order is still waiting for payment.",
                EmailHtml.heading("Still want these?")
                        + EmailHtml.pill("Awaiting payment", EmailHtml.SUN_SOFT, EmailHtml.SUN_INK)
                        + EmailHtml.paragraph("Hi " + escape(order.getUser().getFullName())
                                + ", order #" + order.getId() + " is still waiting for payment, "
                                + "so we have not dispatched it. The items below are held for you.")
                        + EmailHtml.button("Finish paying", orderUrl(order))
                        + EmailHtml.divider()
                        + EmailHtml.sectionTitle("Reserved for you")
                        + OrderEmailParts.itemTable(order, baseUrl, true)
                        + OrderEmailParts.totals(order)
                        + EmailHtml.divider()
                        + EmailHtml.small("Changed your mind? Ignore this and the order will lapse on its "
                                + "own. This is the only reminder we will send."),
                "You placed this order and it has not been paid for.");

        send(order.getUser().getEmail(),
                "Finish your SolarUpgrade order #" + order.getId(),
                textOrderBody(order, "This order is still waiting for payment, so we have not dispatched it.", true)
                        + "\nFinish paying: " + orderUrl(order)
                        + "\n\nIf you have changed your mind, ignore this and the order will lapse. "
                        + "This is the only reminder we will send.\n",
                html,
                "payment reminder for order " + order.getId());
    }

    /**
     * Tells a customer their unpaid order has lapsed and the stock has gone
     * back on the shelf.
     */
    public void sendOrderExpired(Order order) {
        String html = EmailHtml.document(
                "Your SolarUpgrade order #" + order.getId() + " has lapsed",
                "Nothing was charged. The items are back in stock.",
                EmailHtml.heading("Order #" + order.getId() + " has lapsed")
                        + EmailHtml.pill("Cancelled", "#fdecea", "#b3261e")
                        + EmailHtml.paragraph("Hi " + escape(order.getUser().getFullName())
                                + ", this order was never paid for, so we have released it and put the "
                                + "items back in stock. <strong>Nothing has been charged.</strong>")
                        + EmailHtml.button("Browse the catalogue", baseUrl + "/products")
                        + EmailHtml.divider()
                        + EmailHtml.sectionTitle("What lapsed")
                        + OrderEmailParts.itemTable(order, baseUrl, true)
                        + EmailHtml.divider()
                        + EmailHtml.small("Prices and stock may have changed since you ordered."),
                "You placed this order and it was not paid for.");

        send(order.getUser().getEmail(),
                "Your SolarUpgrade order #" + order.getId() + " has lapsed",
                textOrderBody(order, "This order was never paid for, so we have released it and put the "
                                + "items back in stock. Nothing has been charged.", true)
                        + "\nStill want them? " + baseUrl + "/products\n",
                html,
                "expiry notice for order " + order.getId());
    }

    /**
     * Tells a customer their order is on its way.
     *
     * <p>Prices are left off. The money is settled by this point, and putting
     * the figures in front of someone again invites a second look at a number
     * nobody needs to check.
     */
    public void sendOrderShipped(Order order) {
        String html = EmailHtml.document(
                "On its way — SolarUpgrade order #" + order.getId(),
                "Order #" + order.getId() + " has been dispatched.",
                EmailHtml.heading("On its way")
                        + EmailHtml.pill("Shipped", EmailHtml.ACCENT_SOFT, EmailHtml.ACCENT_INK)
                        + EmailHtml.paragraph("Hi " + escape(order.getUser().getFullName())
                                + ", order #" + order.getId()
                                + " has been dispatched. Delivery is usually 5 to 10 working days.")
                        + EmailHtml.button("Track your order", orderUrl(order))
                        + EmailHtml.divider()
                        + EmailHtml.sectionTitle("On its way")
                        + OrderEmailParts.itemTable(order, baseUrl, false)
                        + EmailHtml.divider()
                        + EmailHtml.sectionTitle("Delivering to")
                        + OrderEmailParts.address(order)
                        + EmailHtml.small("Reply to this email if anything is wrong with the delivery details."),
                "Order #" + order.getId() + " has been dispatched.");

        StringBuilder text = new StringBuilder();
        text.append("Hi ").append(order.getUser().getFullName()).append(",\n\n")
                .append("Order #").append(order.getId())
                .append(" has been dispatched and is on its way to you.\n\nOn its way:\n\n");
        for (OrderItem item : order.getItems()) {
            text.append("  ").append(item.getQuantity()).append(" x ")
                    .append(item.getProductName()).append('\n');
        }
        text.append('\n').append(textAddress(order))
                .append("\nDelivery is usually 5 to 10 working days nationwide.\n")
                .append("Your order: ").append(orderUrl(order))
                .append("\n\nReply to this email if anything is wrong with the delivery details.\n");

        send(order.getUser().getEmail(),
                "On its way — SolarUpgrade order #" + order.getId(),
                text.toString(),
                html,
                "dispatch notice for order " + order.getId());
    }

    /**
     * Sends the code that turns a new registration into a usable account.
     *
     * <p>Best-effort like the rest: a send failure leaves the account
     * unverified rather than failing the registration, and the address can
     * request a fresh code.
     */
    public void sendVerification(User user) {
        String code = user.getVerificationCode();
        String html = EmailHtml.document(
                "Confirm your email",
                code + " is your SolarUpgrade confirmation code.",
                EmailHtml.heading("Confirm your email")
                        + EmailHtml.paragraph("Hi " + escape(user.getFullName())
                                + ", here is the code that finishes setting up your account.")
                        + EmailHtml.code(code)
                        + EmailHtml.button("Enter it here", baseUrl + "/verify")
                        + EmailHtml.small("The code expires in 15 minutes. Until it is used you will not "
                                + "be able to sign in.")
                        + EmailHtml.small("If you did not create this account, ignore this email and "
                                + "nothing will happen. Nobody can use this code without also knowing "
                                + "your email address."),
                "You are receiving this because someone signed up with this address.");

        String text = "Hi " + user.getFullName() + ",\n\n"
                + "Your SolarUpgrade confirmation code is:\n\n"
                + "    " + code + "\n\n"
                + "Enter it at " + baseUrl + "/verify to finish setting up your account.\n\n"
                + "The code expires in 15 minutes. Until it is used you will not be able to sign in.\n\n"
                + "If you did not create this account, ignore this email and nothing will happen. "
                + "Nobody can use this code without also knowing your email address.\n";

        // Subject carries the code too, so it is readable from a notification.
        send(user.getEmail(), code + " is your SolarUpgrade confirmation code", text, html,
                "verification code for " + Redact.email(user.getEmail()));
    }

    /**
     * The one-time link that lets someone back into their account.
     *
     * <p>The token is passed in rather than read off the user, because the user
     * only carries its hash. This is the single moment the real token exists
     * outside the customer's inbox.
     */
    public void sendPasswordReset(User user, String token) {
        String link = baseUrl + "/reset-password?token=" + token;
        String html = EmailHtml.document(
                "Reset your SolarUpgrade password",
                "A link to set a new password. It expires in 30 minutes.",
                EmailHtml.heading("Reset your password")
                        + EmailHtml.paragraph("Hi " + escape(user.getFullName())
                                + ", someone asked to reset the password on your SolarUpgrade account.")
                        + EmailHtml.button("Set a new password", link)
                        + EmailHtml.small("The link works once and expires in 30 minutes.")
                        + EmailHtml.small("If this was not you, ignore this email. Your password has not "
                                + "changed, and nobody can use this link without reading this message."),
                "You are receiving this because a password reset was requested for this address.");

        String text = "Hi " + user.getFullName() + ",\n\n"
                + "Someone asked to reset the password on your SolarUpgrade account.\n\n"
                + "Set a new password here:\n\n"
                + "    " + link + "\n\n"
                + "The link works once and expires in 30 minutes.\n\n"
                + "If this was not you, ignore this email. Your password has not changed "
                + "and nobody can use this link without reading this message.\n";

        send(user.getEmail(), "Reset your SolarUpgrade password", text, html,
                "password reset for " + Redact.email(user.getEmail()));
    }

    /**
     * Sends one message carrying both bodies.
     *
     * <p>Over SMTP that is a multipart/alternative MimeMessage rather than the
     * SimpleMailMessage this used to build, because SimpleMailMessage can only
     * carry one body and it is always plain text.
     */
    private void send(String to, String subject, String text, String html, String description) {
        ResendMailer resend = resendProvider.getIfAvailable();
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();

        if (resend == null && mailSender == null) {
            log.info("Mail is not configured; skipping {}", description);
            return;
        }

        try {
            if (resend != null) {
                resend.send(fromAddress, to, subject, text, html);
            } else {
                MimeMessage message = mailSender.createMimeMessage();
                // true = multipart, so both bodies fit in the one message.
                MimeMessageHelper helper =
                        new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
                helper.setFrom(fromAddress);
                helper.setTo(to);
                helper.setSubject(subject);
                // Plain text first, HTML second: the order is the standard, and
                // clients show the last part they understand.
                helper.setText(text, html);
                mailSender.send(message);
            }
            log.info("Sent {}", description);
        } catch (Exception ex) {
            log.warn("Could not send {}", description, ex);
        }
    }

    /** The plain-text half of an order email. */
    private String textOrderBody(Order order, String opening, boolean withPrices) {
        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(order.getUser().getFullName()).append(",\n\n")
                .append(opening).append("\n\n");
        for (OrderItem item : order.getItems()) {
            body.append("  ").append(item.getQuantity()).append(" x ")
                    .append(item.getProductName());
            if (withPrices) {
                body.append("  ").append(item.getLineTotalDisplay());
            }
            body.append('\n');
        }
        if (withPrices) {
            body.append("\nTotal: ").append(order.getTotalDisplay());
            if (order.isConverted()) {
                body.append("\nCharged through PayPal as ").append(order.getChargeDisplay());
            }
            body.append('\n');
        }
        body.append('\n').append(textAddress(order))
                .append("\nYour order: ").append(orderUrl(order)).append('\n');
        return body.toString();
    }

    private String textAddress(Order order) {
        StringBuilder address = new StringBuilder("Delivering to:\n    ");
        address.append(order.getShippingName()).append('\n');
        for (String line : order.getShippingLines()) {
            address.append("    ").append(line).append('\n');
        }
        return address.toString();
    }
}
