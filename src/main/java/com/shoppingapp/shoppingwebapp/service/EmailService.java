package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderItem;
import com.shoppingapp.shoppingwebapp.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    /**
     * Three transports, tried in order: Resend's HTTP API when an API key is
     * configured, otherwise SMTP when spring.mail.host is set, otherwise
     * nothing but a log line. Both are injected lazily so the app runs with
     * neither configured, which is the default for local development.
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
        // Verification links must be absolute, and the app cannot infer its own
        // public address from behind a proxy.
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * A failure to send must never roll back an order that was already written,
     * so problems here are logged rather than thrown.
     */
    public void sendOrderConfirmation(Order order) {
        send(order.getUser().getEmail(),
                "Your SolarUpgrade order #" + order.getId(),
                buildOrderBody(order),
                "confirmation for order " + order.getId());
    }

    /**
     * The one nudge an unpaid order gets. Sent once per order; see
     * {@link PaymentReminderJob}.
     */
    public void sendPaymentReminder(Order order) {
        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(order.getUser().getFullName()).append(",\n\n")
                .append("Your SolarUpgrade order #").append(order.getId())
                .append(" is still waiting for payment, so we have not dispatched it yet.\n\n")
                .append("Here is what is reserved for you:\n\n");
        for (OrderItem item : order.getItems()) {
            body.append("  ").append(item.getQuantity()).append(" x ")
                    .append(item.getProductName())
                    .append("  £").append(item.getLineTotal())
                    .append('\n');
        }
        body.append("\nTotal: £").append(order.getTotal());
        if (order.getPaymentMethod() != null) {
            body.append("\nYou chose to pay by: ").append(order.getPaymentMethod().getDisplayName());
        }
        body.append("\n\nFinish paying here:\n")
                .append("    ").append(baseUrl).append("/orders/").append(order.getId())
                .append("\n\nIf you have changed your mind, ignore this and the order will lapse. "
                        + "This is the only reminder we will send.\n");

        send(order.getUser().getEmail(),
                "Finish your SolarUpgrade order #" + order.getId(),
                body.toString(),
                "payment reminder for order " + order.getId());
    }

    /**
     * Sends the link that turns a new registration into a usable account.
     *
     * <p>Best-effort like the rest: a send failure leaves the account
     * unverified rather than failing the registration, and the address can
     * request a fresh link.
     */
    public void sendVerification(User user) {
        String code = user.getVerificationCode();
        String body = "Hi " + user.getFullName() + ",\n\n"
                + "Your SolarUpgrade confirmation code is:\n\n"
                + "    " + code + "\n\n"
                + "Enter it at " + baseUrl + "/verify to finish setting up your account.\n\n"
                + "The code expires in 15 minutes. Until it is used you will not be able to sign in.\n\n"
                + "If you did not create this account, ignore this email and nothing will happen. "
                + "Nobody can use this code without also knowing your email address.\n";
        // Subject carries the code too, so it is readable from a notification.
        send(user.getEmail(), code + " is your SolarUpgrade confirmation code", body,
                "verification code for " + user.getEmail());
    }

    private void send(String to, String subject, String body, String description) {
        ResendMailer resend = resendProvider.getIfAvailable();
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();

        if (resend == null && mailSender == null) {
            log.info("Mail is not configured; skipping {}", description);
            return;
        }

        try {
            if (resend != null) {
                resend.send(fromAddress, to, subject, body);
            } else {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
            }
            log.info("Sent {}", description);
        } catch (Exception ex) {
            log.warn("Could not send {}", description, ex);
        }
    }

    private String buildOrderBody(Order order) {
        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(order.getUser().getFullName()).append(",\n\n")
                .append("Thanks for your order. Here is what we have:\n\n");
        for (OrderItem item : order.getItems()) {
            body.append("  ").append(item.getQuantity()).append(" x ")
                    .append(item.getProductName())
                    .append("  £").append(item.getLineTotal())
                    .append('\n');
        }
        body.append("\nTotal: £").append(order.getTotal())
                .append("\nStatus: ").append(order.getStatus().getDisplayName())
                .append("\n\nWe will email again when your order ships.\n");
        return body.toString();
    }
}
