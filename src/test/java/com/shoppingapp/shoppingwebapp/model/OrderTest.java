package com.shoppingapp.shoppingwebapp.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    private static Product product(String name, String price) {
        return new Product(name, "desc", new BigDecimal(price), Category.PANEL, 100, null);
    }

    private static Order order() {
        User user = new User("a@b.example", "hash", "A B");
        return new Order(user, "A B", "1 Test Street");
    }

    @Test
    void totalSumsLineTotals() {
        Order order = order();
        order.addItem(new OrderItem(product("Panel", "189.00"), 2));
        order.addItem(new OrderItem(product("Inverter", "1245.00"), 1));

        assertThat(order.getTotal()).isEqualByComparingTo("1623.00");
    }

    @Test
    void totalOfEmptyOrderIsZero() {
        assertThat(order().getTotal()).isEqualByComparingTo("0");
    }

    @Test
    void itemCountSumsQuantitiesNotLines() {
        Order order = order();
        order.addItem(new OrderItem(product("Panel", "189.00"), 8));
        order.addItem(new OrderItem(product("Mounting", "315.00"), 1));

        assertThat(order.getItemCount()).isEqualTo(9);
    }

    @Test
    void addingItemSetsBothSidesOfTheRelationship() {
        Order order = order();
        OrderItem item = new OrderItem(product("Panel", "189.00"), 1);
        order.addItem(item);

        assertThat(order.getItems()).containsExactly(item);
        assertThat(item.getOrder()).isSameAs(order);
    }

    @Test
    void lineTotalUsesThePriceSnapshotNotTheLiveProductPrice() {
        Product panel = product("Panel", "189.00");
        OrderItem item = new OrderItem(panel, 2);

        // The catalogue price changes after the order was placed.
        panel.setPrice(new BigDecimal("249.00"));

        assertThat(item.getUnitPrice()).isEqualByComparingTo("189.00");
        assertThat(item.getLineTotal()).isEqualByComparingTo("378.00");
    }
}
