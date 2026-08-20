package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.support.Csv;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Every order, as a CSV file.
 *
 * <p>Two jobs at once. It is what an accountant is given at the end of a
 * quarter, and it is the only copy of the shop's trading history that lives
 * anywhere other than the database — which sits on a free hosting plan, in a
 * project that can be deleted with one click, holding every order ever placed.
 * A backup that has to be taken by hand is a poor backup, but it beats the
 * absence of one, and this is the version somebody will actually take.
 *
 * <p><b>Written a page at a time.</b> Loading every order to build one string
 * would fail on exactly the shop this is most needed for, and the whole file is
 * streamed to the client as it is built rather than assembled in memory first.
 */
@Service
public class OrderExportService {

    /** Orders read per query. Large enough to be few queries, small enough to hold. */
    private static final int BATCH = 200;

    /**
     * ISO, in UTC. A spreadsheet's own date parsing is a guess at the reader's
     * locale -- 03/04 is two different days on two sides of an ocean -- and an
     * export read a year later by somebody else should not depend on it.
     */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.of("UTC"));

    private static final String[] COLUMNS = {
            "order_number", "placed_at_utc", "status", "customer_email", "customer_name",
            "shipping_name", "shipping_address", "items", "total_naira",
            "payment_method", "charged_amount", "charged_currency",
            "provider_reference", "capture_reference", "refund_reference", "refunded_at_utc"};

    private final OrderService orderService;

    public OrderExportService(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Writes the whole export to a writer.
     *
     * @return how many orders were written, for the log and the audit entry
     */
    public long writeTo(Writer writer) throws IOException {
        writer.write(Csv.row(COLUMNS));

        long written = 0;
        int page = 0;
        Page<Order> batch;
        do {
            batch = orderService.ordersPage(null, OrderService.page(page, BATCH));
            for (Order order : batch.getContent()) {
                writer.write(row(order));
                written++;
            }
            // Flushed per page so a long export starts arriving immediately
            // rather than sitting in a buffer until the last row.
            writer.flush();
            page++;
        } while (page < batch.getTotalPages());

        return written;
    }

    private String row(Order order) {
        return Csv.row(
                String.valueOf(order.getId()),
                TIMESTAMP.format(order.getPlacedAt()),
                order.getStatus().name(),
                order.getUser() == null ? "" : order.getUser().getEmail(),
                order.getUser() == null ? "" : order.getUser().getFullName(),
                order.getShippingName(),
                // One cell, newline-separated, the way the label reads. Csv
                // quotes it, so the newlines stay inside the field.
                String.join("\n", order.getShippingLines()),
                String.valueOf(order.getItemCount()),
                // The plain number, not the formatted one: a spreadsheet cannot
                // add up "₦1,520,000", and this column exists to be added up.
                order.getTotal().toPlainString(),
                order.getPaymentMethod() == null ? "" : order.getPaymentMethod().name(),
                order.getPaymentAmount() == null ? "" : order.getPaymentAmount().toPlainString(),
                order.getPaymentCurrency() == null ? "" : order.getPaymentCurrency(),
                order.getProviderReference() == null ? "" : order.getProviderReference(),
                order.getCaptureReference() == null ? "" : order.getCaptureReference(),
                order.getRefundReference() == null ? "" : order.getRefundReference(),
                order.getRefundedAt() == null ? "" : TIMESTAMP.format(order.getRefundedAt()));
    }

    /** {@code orders-2026-08-20.csv}, so two downloads do not overwrite each other. */
    public String filename() {
        return "orders-" + DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.of("UTC")).format(Instant.now()) + ".csv";
    }
}
