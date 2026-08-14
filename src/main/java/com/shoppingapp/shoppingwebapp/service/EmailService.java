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

    public EmailService(ObjectProvider<ResendMailer> resendProvider,
                        ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${app.mail.from:no-reply@solarupgrade.example}") String fromAddress) {
        this.resendProvider = resendProvider;
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress;
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
     * Sent when an account is created. Like the order confirmation this is
     * best-effort: a registration must not fail because email is down.
     */
    public void sendWelcome(User user) {
        String body = "Hi " + user.getFullName() + ",\n\n"
                + "Your SolarUpgrade account is ready. You can now build a basket "
                + "and track your orders.\n\n"
                + "If you did not create this account, please ignore this email.\n";
        send(user.getEmail(), "Welcome to SolarUpgrade", body, "welcome email for " + user.getEmail());
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
