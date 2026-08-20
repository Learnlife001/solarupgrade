package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.model.AdminActionType;
import com.shoppingapp.shoppingwebapp.model.CancellationReason;
import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderStatus;
import com.shoppingapp.shoppingwebapp.service.AuditService;
import com.shoppingapp.shoppingwebapp.service.OrderExportService;
import com.shoppingapp.shoppingwebapp.service.OrderService;
import com.shoppingapp.shoppingwebapp.service.ProductService;
import com.shoppingapp.shoppingwebapp.service.payment.PaymentException;
import com.shoppingapp.shoppingwebapp.service.alerts.ErrorAlerter;
import com.shoppingapp.shoppingwebapp.service.payment.PaymentService;
import com.shoppingapp.shoppingwebapp.support.Redact;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

/**
 * The back office: the only part of the application that reads across
 * customers, and the only part that can change an order after it is placed.
 *
 * <p>Access is entirely a matter of {@code ROLE_ADMIN} in SecurityConfig, not
 * of the URL being unguessable. Every method here is one an ordinary customer
 * must not reach, so the guard belongs in one place that cannot be forgotten
 * per-method.
 *
 * <p>Every action is a POST. A shipment confirmation behind a link would be
 * fired by a browser prefetching it, or by anything that follows links in a
 * page, and it emails a customer.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    /** How many "waiting to ship" rows the dashboard shows before linking on. */
    private static final int DASHBOARD_ROWS = 10;

    /** Below this, a product is worth flagging on the dashboard. */
    private static final int LOW_STOCK = 3;

    private final OrderService orderService;
    private final ProductService productService;
    private final AuditService auditService;
    private final PaymentService paymentService;
    private final OrderExportService orderExportService;
    private final ErrorAlerter alerter;

    public AdminController(OrderService orderService,
                           ProductService productService,
                           AuditService auditService,
                           PaymentService paymentService,
                           OrderExportService orderExportService,
                           ErrorAlerter alerter) {
        this.orderService = orderService;
        this.productService = productService;
        this.auditService = auditService;
        this.paymentService = paymentService;
        this.orderExportService = orderExportService;
        this.alerter = alerter;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("pendingCount", orderService.countWithStatus(OrderStatus.PENDING_PAYMENT));
        model.addAttribute("paidCount", orderService.countWithStatus(OrderStatus.PAID));
        model.addAttribute("shippedCount", orderService.countWithStatus(OrderStatus.SHIPPED));
        model.addAttribute("cancelledCount", orderService.countWithStatus(OrderStatus.CANCELLED));
        model.addAttribute("lowStockCount", productService.countLowStock(LOW_STOCK));
        model.addAttribute("lowStockThreshold", LOW_STOCK);

        // Paid but not yet shipped is the actual to-do list: someone has paid
        // and is waiting. It leads the page for that reason.
        //
        // Capped, and the count beside it says how many there are in total. A
        // dashboard that renders a thousand rows is a dashboard nobody opens,
        // and the full list is one click away.
        Page<Order> toShip = orderService.ordersPage(OrderStatus.PAID, OrderService.page(0, DASHBOARD_ROWS));
        model.addAttribute("toShip", toShip.getContent());
        model.addAttribute("toShipTotal", toShip.getTotalElements());
        model.addAttribute("recentActions", auditService.recent(10));
        return "admin/dashboard";
    }

    /** Orders per page. Enough to work from, few enough to render quickly. */
    private static final int PAGE_SIZE = 25;

    @GetMapping("/orders")
    public String orders(@RequestParam(required = false) OrderStatus status,
                         @RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {
        // Paged rather than every order ever placed. The page looked fixed
        // while the work behind it grew with the shop: at a few thousand orders
        // this query alone would have made the back office unusable, and it is
        // the page whoever runs the shop lives in.
        //
        // Searched, too: a customer writes in about an order and what the shop
        // has is their address, their name or a number from an email. Browsing
        // back through pages for it is not an answer.
        Page<Order> orders = orderService.searchOrders(status, q, OrderService.page(page, PAGE_SIZE));
        model.addAttribute("orders", orders.getContent());
        model.addAttribute("query", q == null ? "" : q.trim());
        model.addAttribute("pageNumber", orders.getNumber());
        model.addAttribute("totalPages", orders.getTotalPages());
        model.addAttribute("totalOrders", orders.getTotalElements());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", OrderStatus.values());
        return "admin/orders";
    }

    /**
     * Every order as a CSV file.
     *
     * <p>What an accountant is handed at the end of a quarter, and the only
     * copy of the shop's trading history that exists anywhere but the database
     * -- which sits on a free plan in a project that can be deleted with one
     * click.
     *
     * <p>Streamed rather than built in memory, and audited: this is a bulk read
     * of every customer's name and address, and a record of who took a copy and
     * when is the least that deserves.
     */
    @GetMapping("/orders.csv")
    public ResponseEntity<StreamingResponseBody> exportOrders(Principal principal) {
        String filename = orderExportService.filename();
        log.info("Order export taken by {}", Redact.email(principal.getName()));
        auditService.record(principal.getName(), AdminActionType.ORDERS_EXPORTED,
                AuditService.ORDER, "Downloaded " + filename);

        StreamingResponseBody body = out -> {
            try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                try {
                    long written = orderExportService.writeTo(writer);
                    log.info("Exported {} orders", written);
                } catch (Exception ex) {
                    // The response is already 200 with rows in it by now, so
                    // there is no status left to change: a failure here hands
                    // back a truncated file that looks complete, which is the
                    // worst possible outcome for something kept as a backup.
                    // Saying so in the file itself is the only way the reader
                    // can tell.
                    log.error("Order export failed part-way through", ex);
                    writer.write("# EXPORT FAILED PART-WAY THROUGH — THIS FILE IS INCOMPLETE\r\n");
                    alerter.jobFailed("Order export", "the CSV download", ex);
                }
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                // UTF-8 named explicitly: without it a spreadsheet guesses, and
                // guesses wrong on any address with an accent in it.
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }

    @GetMapping("/orders/{id}")
    public String order(@PathVariable Long id, Model model) {
        Order order = orderService.getAnyOrder(id);
        model.addAttribute("order", order);
        model.addAttribute("history", auditService.forOrder(id));
        // Asked of the provider rather than assumed from the status: an order
        // paid before the capture id was recorded, or through a provider that
        // cannot refund, must not be offered a button that will fail.
        model.addAttribute("canRefund", paymentService.canRefund(order));
        return "admin/order";
    }

    @PostMapping("/orders/{id}/ship")
    public String ship(@PathVariable Long id, Principal principal, RedirectAttributes flash) {
        Order order = orderService.getAnyOrder(id);
        if (orderService.markShipped(id)) {
            log.info("Order {} marked shipped by {}", id, Redact.email(principal.getName()));
            auditService.record(principal.getName(), AdminActionType.ORDER_SHIPPED,
                    AuditService.ORDER, id, "Customer emailed a dispatch notice");
            flash.addFlashAttribute("message", "Order #" + id + " marked as shipped. The customer has been emailed.");
        } else {
            // Refused rather than forced: the guard is in the service, and the
            // reason is worth showing rather than a silent no-op.
            flash.addFlashAttribute("error",
                    "Order #" + id + " is " + order.getStatus().getDisplayName().toLowerCase()
                            + ", so it cannot be shipped. Only a paid order can.");
        }
        return "redirect:/admin/orders/" + id;
    }

    /**
     * Sends a paid order's money back.
     *
     * <p>The reason is required. A refund is the one action here that moves
     * money outward, and "who refunded this and why" is asked of the record
     * weeks later by somebody reconciling accounts. A date and an amount with
     * no account of why is what makes a trail useless.
     */
    @PostMapping("/orders/{id}/refund")
    public String refund(@PathVariable Long id,
                         @RequestParam String reason,
                         Principal principal,
                         RedirectAttributes flash) {
        Order order = orderService.getAnyOrder(id);
        if (reason == null || reason.isBlank()) {
            flash.addFlashAttribute("error", "Say why this is being refunded before refunding it.");
            return "redirect:/admin/orders/" + id;
        }
        if (!order.isRefundable()) {
            flash.addFlashAttribute("error",
                    "Order #" + id + " is " + order.getStatus().getDisplayName().toLowerCase()
                            + ", so there is nothing to refund.");
            return "redirect:/admin/orders/" + id;
        }

        boolean shipped = order.getStatus() == OrderStatus.SHIPPED;
        try {
            if (paymentService.refund(id)) {
                log.info("Order {} refunded by {}", id, Redact.email(principal.getName()));
                auditService.record(principal.getName(), AdminActionType.ORDER_REFUNDED,
                        AuditService.ORDER, id,
                        order.getTotalDisplay() + " refunded — " + reason.trim()
                                + (shipped ? " (already dispatched, so stock was not returned)"
                                           : " (stock returned)"));
                flash.addFlashAttribute("message", shipped
                        ? "Order #" + id + " refunded and the customer emailed. It had already been "
                                + "dispatched, so its stock was not returned — set that by hand when "
                                + "the goods come back."
                        : "Order #" + id + " refunded, its stock returned, and the customer emailed.");
            } else {
                flash.addFlashAttribute("error",
                        "The provider did not complete the refund. Nothing has been sent back — "
                                + "check the provider's own dashboard before trying again.");
            }
        } catch (PaymentException ex) {
            log.warn("Refund failed for order {}", id, ex);
            flash.addFlashAttribute("error", "Refund failed: " + ex.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    @PostMapping("/orders/{id}/cancel")
    public String cancel(@PathVariable Long id, Principal principal, RedirectAttributes flash) {
        Order order = orderService.getAnyOrder(id);
        if (orderService.cancelUnpaid(id, CancellationReason.ADMIN)) {
            log.info("Order {} cancelled by {}", id, Redact.email(principal.getName()));
            auditService.record(principal.getName(), AdminActionType.ORDER_CANCELLED,
                    AuditService.ORDER, id,
                    order.getItemCount() + " item(s) returned to stock; customer notified");
            flash.addFlashAttribute("message",
                    "Order #" + id + " cancelled and its stock returned to the shelf.");
        } else {
            flash.addFlashAttribute("error",
                    "Order #" + id + " is " + order.getStatus().getDisplayName().toLowerCase()
                            + ", so it cannot be cancelled here. Only an unpaid order can — "
                            + "a paid one is refunded instead.");
        }
        return "redirect:/admin/orders/" + id;
    }

}
