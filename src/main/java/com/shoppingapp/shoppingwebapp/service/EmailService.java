package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderItem;
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
     * Spring Boot only auto-configures a JavaMailSender when spring.mail.host is
     * set. Injecting it lazily keeps the app runnable with no SMTP configured,
     * which is the default for local development.
     */
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${app.mail.from:no-reply@solarupgrade.example}") String fromAddress) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress;
    }

    /**
     * A failure to send must never roll back an order that was already written,
     * so problems here are logged rather than thrown.
     */
    public void sendOrderConfirmation(Order order) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.info("Mail is not configured; skipping confirmation for order {}", order.getId());
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(order.getUser().getEmail());
            message.setSubject("Your SolarUpgrade order #" + order.getId());
            message.setText(buildBody(order));
            mailSender.send(message);
        } catch (Exception ex) {
            log.warn("Could not send confirmation email for order {}", order.getId(), ex);
        }
    }

    private String buildBody(Order order) {
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
