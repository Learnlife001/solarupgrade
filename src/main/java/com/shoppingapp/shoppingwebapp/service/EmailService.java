package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.config.Brand;
import com.shoppingapp.shoppingwebapp.config.BusinessDetails;
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
    private final Brand brand;
    private final BusinessDetails business;
    private final String fromAddress;
    private final String baseUrl;

    public EmailService(ObjectProvider<ResendMailer> resendProvider,
                        ObjectProvider<JavaMailSender> mailSenderProvider,
                        Brand brand,
                        BusinessDetails business,
                        @Value("${app.mail.from:no-reply@example.invalid}") String fromAddress,
                        @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.resendProvider = resendProvider;
        this.mailSenderProvider = mailSenderProvider;
        this.brand = brand;
        this.business = business;
        this.fromAddress = fromAddress;
        // Links in email must be absolute, and the app cannot infer its own
        // public address from behind a proxy.
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String orderUrl(Order order) {
        return baseUrl + "/orders/" + order.getId();
    }

    /**
     * The shop's name, for a subject line or a sentence. One accessor rather
     * than the literal in eighteen places, which is what it was.
     */
    private String shop() {
        return brand.getName();
    }

    /** The HTML shell, already carrying the brand. */
    private String document(String title, String preheader, String body, String footerNote) {
        return document(title, preheader, null, body, footerNote);
    }

    /** The shell with the order reference opposite the brand. */
    private String document(String title, String preheader, String headerNote,
                            String body, String footerNote) {
        return EmailHtml.document(brand.getName(), brand.getMark(), brand.getTagline(),
                title, preheader, headerNote, body, footerNote);
    }

    /**
     * "ORDER #12", sat opposite the brand at the top of every order email.
     *
     * <p>Null when the order has no id yet, so the header simply loses the
     * reference rather than announcing "ORDER #NULL" to a customer. Every order
     * we mail about has been saved, but a header that shouts a Java keyword on
     * the one path where that stops being true is not worth the risk.
     */
    private String orderReference(Order order) {
        return order.getId() == null ? null : "Order #" + order.getId();
    }

    /**
     * How an order is named in a sentence: "order #12", or plain "order" for
     * one with no id yet.
     *
     * <p>Every subject line and title goes through this. Building them by
     * concatenating {@code getId()} put the literal "#null" in a subject line
     * the moment an unsaved order reached the mailer -- which a test found by
     * sending one.
     */
    private String orderName(Order order) {
        return order.getId() == null ? "order" : "order #" + order.getId();
    }

    /** The same, at the start of a sentence. */
    private String capitalisedOrderName(Order order) {
        return order.getId() == null ? "Order" : "Order #" + order.getId();
    }

    /**
     * How to reach a human, in the footer of every order email.
     *
     * <p>The support address appears only once one is configured. A "contact us
     * at" line pointing at an address nobody reads is worse than no line at
     * all, and replying to the message always works.
     */
    private String supportLine() {
        String email = business.getSupportEmail();
        if (email == null || email.isBlank()) {
            return "If you have any questions, just reply to this email.";
        }
        return "If you have any questions, reply to this email or contact us at "
                + "<a href=\"mailto:" + escape(email) + "\" style=\"color:" + EmailHtml.ACCENT
                + ";text-decoration:none;\">" + escape(email) + "</a>.";
    }

    /**
     * The customer's own details, carried back to them: where it is going and
     * how it is being paid. Two columns, the way a receipt is laid out.
     */
    private String customerInformation(Order order) {
        return EmailHtml.sectionHeading("Customer information")
                + EmailHtml.columns(
                        "Delivery address", OrderEmailParts.addressLines(order),
                        "Payment", OrderEmailParts.payment(order));
    }

    /**
     * A failure to send must never roll back an order that was already written,
     * so problems here are logged rather than thrown.
     */
    public void sendOrderConfirmation(Order order) {
        String html = document(
                "Your " + shop() + " " + orderName(order),
                "We have your order. Nothing has been charged yet.",
                orderReference(order),
                EmailHtml.heading("Thanks for your order!")
                        + EmailHtml.pill("Awaiting payment", EmailHtml.SUN_SOFT, EmailHtml.SUN_INK)
                        + EmailHtml.lead("Hi " + escape(order.getUser().getFullName())
                                + ", we have your order and the items are held for you. "
                                + "Nothing has been charged yet &mdash; finish paying and we will get it moving.")
                        + EmailHtml.actions("Finish paying", orderUrl(order),
                                "Visit the shop", baseUrl + "/products")
                        + EmailHtml.divider()
                        + EmailHtml.sectionHeading("Order summary")
                        + OrderEmailParts.itemTable(order, baseUrl, true)
                        + OrderEmailParts.totals(order)
                        + EmailHtml.divider()
                        + customerInformation(order),
                supportLine());

        send(order.getUser().getEmail(),
                "Your " + shop() + " " + orderName(order),
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
        String html = document(
                "Payment received — " + shop() + " " + orderName(order),
                "Payment confirmed. Nothing further is needed from you.",
                orderReference(order),
                EmailHtml.heading("Thank you for your purchase!")
                        + EmailHtml.pill("Paid", EmailHtml.ACCENT_SOFT, EmailHtml.ACCENT_INK)
                        + EmailHtml.lead("Hi " + escape(order.getUser().getFullName())
                                + ", your payment has come through and we are getting your order ready. "
                                + "We will email again when it has been sent.")
                        + EmailHtml.actions("View your order", orderUrl(order),
                                "Visit the shop", baseUrl + "/products")
                        + EmailHtml.divider()
                        + EmailHtml.sectionHeading("Order summary")
                        + OrderEmailParts.itemTable(order, baseUrl, true)
                        + OrderEmailParts.totals(order)
                        + EmailHtml.divider()
                        + customerInformation(order)
                        + EmailHtml.divider()
                        + EmailHtml.small("Keep this email as your receipt."),
                supportLine());

        send(order.getUser().getEmail(),
                "Payment received — " + shop() + " " + orderName(order),
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
        String html = document(
                "Finish your " + shop() + " " + orderName(order),
                "Your order is still waiting for payment.",
                orderReference(order),
                EmailHtml.heading("Still want these?")
                        + EmailHtml.pill("Awaiting payment", EmailHtml.SUN_SOFT, EmailHtml.SUN_INK)
                        + EmailHtml.lead("Hi " + escape(order.getUser().getFullName())
                                + ", this order is still waiting for payment, so we have not dispatched it. "
                                + "The items below are held for you.")
                        + EmailHtml.actions("Finish paying", orderUrl(order),
                                "Visit the shop", baseUrl + "/products")
                        + EmailHtml.divider()
                        + EmailHtml.sectionHeading("Reserved for you")
                        + OrderEmailParts.itemTable(order, baseUrl, true)
                        + OrderEmailParts.totals(order)
                        + EmailHtml.divider()
                        + customerInformation(order)
                        + EmailHtml.divider()
                        + EmailHtml.small("Changed your mind? Ignore this and the order will lapse on its "
                                + "own. This is the only reminder we will send."),
                supportLine());

        send(order.getUser().getEmail(),
                "Finish your " + shop() + " " + orderName(order),
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
        String html = document(
                "Your " + shop() + " " + orderName(order) + " has lapsed",
                "Nothing was charged. The items are back in stock.",
                orderReference(order),
                EmailHtml.heading("This order has lapsed")
                        + EmailHtml.pill("Cancelled", "#fdecea", "#b3261e")
                        + EmailHtml.lead("Hi " + escape(order.getUser().getFullName())
                                + ", this order was never paid for, so we have released it and put the "
                                + "items back in stock. <strong>Nothing has been charged.</strong>")
                        + EmailHtml.actions("Browse the catalogue", baseUrl + "/products", null, null)
                        + EmailHtml.divider()
                        + EmailHtml.sectionHeading("What lapsed")
                        + OrderEmailParts.itemTable(order, baseUrl, true)
                        + EmailHtml.divider()
                        + EmailHtml.small("Prices and stock may have changed since you ordered."),
                supportLine());

        send(order.getUser().getEmail(),
                "Your " + shop() + " " + orderName(order) + " has lapsed",
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
        String html = document(
                "On its way — " + shop() + " " + orderName(order),
                capitalisedOrderName(order) + " has been dispatched.",
                orderReference(order),
                EmailHtml.heading("Your order is on its way!")
                        + EmailHtml.pill("Shipped", EmailHtml.ACCENT_SOFT, EmailHtml.ACCENT_INK)
                        + EmailHtml.lead("Hi " + escape(order.getUser().getFullName())
                                + ", this order has been dispatched. Delivery is usually "
                                + escape(business.getDeliveryEstimate()) + ".")
                        + EmailHtml.actions("View your order", orderUrl(order),
                                "Visit the shop", baseUrl + "/products")
                        + EmailHtml.divider()
                        + EmailHtml.sectionHeading("What was sent")
                        + OrderEmailParts.itemTable(order, baseUrl, false)
                        + EmailHtml.divider()
                        + customerInformation(order)
                        + EmailHtml.divider()
                        + EmailHtml.small("Reply to this email if anything is wrong with the delivery details."),
                supportLine());

        StringBuilder text = new StringBuilder();
        text.append("Hi ").append(order.getUser().getFullName()).append(",\n\n")
                .append(capitalisedOrderName(order))
                .append(" has been dispatched and is on its way to you.\n\nOn its way:\n\n");
        for (OrderItem item : order.getItems()) {
            text.append("  ").append(item.getQuantity()).append(" x ")
                    .append(item.getProductName()).append('\n');
        }
        text.append('\n').append(textAddress(order))
                .append("\nDelivery is usually ").append(business.getDeliveryEstimate())
                .append(" nationwide.\n")
                .append("Your order: ").append(orderUrl(order))
                .append("\n\nReply to this email if anything is wrong with the delivery details.\n");

        send(order.getUser().getEmail(),
                "On its way — " + shop() + " " + orderName(order),
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
        String html = document(
                "Confirm your email",
                code + " is your " + shop() + " confirmation code.",
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
                + "Your " + shop() + " confirmation code is:\n\n"
                + "    " + code + "\n\n"
                + "Enter it at " + baseUrl + "/verify to finish setting up your account.\n\n"
                + "The code expires in 15 minutes. Until it is used you will not be able to sign in.\n\n"
                + "If you did not create this account, ignore this email and nothing will happen. "
                + "Nobody can use this code without also knowing your email address.\n";

        // Subject carries the code too, so it is readable from a notification.
        send(user.getEmail(), code + " is your " + shop() + " confirmation code", text, html,
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
        String html = document(
                "Reset your " + shop() + " password",
                "A link to set a new password. It expires in 30 minutes.",
                EmailHtml.heading("Reset your password")
                        + EmailHtml.paragraph("Hi " + escape(user.getFullName())
                                + ", someone asked to reset the password on your " + shop() + " account.")
                        + EmailHtml.button("Set a new password", link)
                        + EmailHtml.small("The link works once and expires in 30 minutes.")
                        + EmailHtml.small("If this was not you, ignore this email. Your password has not "
                                + "changed, and nobody can use this link without reading this message."),
                "You are receiving this because a password reset was requested for this address.");

        String text = "Hi " + user.getFullName() + ",\n\n"
                + "Someone asked to reset the password on your " + shop() + " account.\n\n"
                + "Set a new password here:\n\n"
                + "    " + link + "\n\n"
                + "The link works once and expires in 30 minutes.\n\n"
                + "If this was not you, ignore this email. Your password has not changed "
                + "and nobody can use this link without reading this message.\n";

        send(user.getEmail(), "Reset your " + shop() + " password", text, html,
                "password reset for " + Redact.email(user.getEmail()));
    }

    /**
     * Sends one message carrying both bodies.
     *
     * <p>Over SMTP that is a multipart/alternative MimeMessage rather than the
     * SimpleMailMessage this used to build, because SimpleMailMessage can only
     * carry one body and it is always plain text.
     */
    /**
     * An operational message to whoever runs the shop, not to a customer.
     *
     * <p>Same transport, different audience: this is how {@code ErrorAlerter}
     * reports a failed request. Kept here so there is still exactly one place
     * that knows how mail leaves this application.
     */
    public void sendOperationalAlert(String to, String subject, String text, String html) {
        send(to, subject, text, html, "error alert to " + Redact.email(to));
    }

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
