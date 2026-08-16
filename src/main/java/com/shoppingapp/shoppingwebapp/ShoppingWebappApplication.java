package com.shoppingapp.shoppingwebapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Scheduling drives the unpaid-order reminder. The job itself is off unless
// app.payment-reminders.enabled is set, so this starts an idle scheduler and
// nothing else.
@EnableScheduling
@SpringBootApplication
public class ShoppingWebappApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingWebappApplication.class, args);
    }

}
