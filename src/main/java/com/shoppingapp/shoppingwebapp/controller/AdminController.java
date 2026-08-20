package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.model.AdminActionType;
import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderStatus;
import com.shoppingapp.shoppingwebapp.service.AuditService;
import com.shoppingapp.shoppingwebapp.service.OrderService;
import com.shoppingapp.shoppingwebapp.service.ProductService;
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

    /** Below this, a product is worth flagging on the dashboard. */
    private static final int LOW_STOCK = 3;

    private final OrderService orderService;
    private final ProductService productService;
    private final AuditService auditService;

    public AdminController(OrderService orderService,
                           ProductService productService,
                           AuditService auditService) {
        this.orderService = orderService;
        this.productService = productService;
        this.auditService = auditService;
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
        model.addAttribute("toShip", orderService.ordersWithStatus(OrderStatus.PAID));
        model.addAttribute("recentActions", auditService.recent(10));
        return "admin/dashboard";
    }

    @GetMapping("/orders")
    public String orders(@RequestParam(required = false) OrderStatus status, Model model) {
        List<Order> orders = status == null
                ? orderService.allOrders()
                : orderService.ordersWithStatus(status);
        model.addAttribute("orders", orders);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", OrderStatus.values());
        return "admin/orders";
    }

    @GetMapping("/orders/{id}")
    public String order(@PathVariable Long id, Model model) {
        model.addAttribute("order", orderService.getAnyOrder(id));
        model.addAttribute("history", auditService.forOrder(id));
        return "admin/order";
    }

    @PostMapping("/orders/{id}/ship")
    public String ship(@PathVariable Long id, Principal principal, RedirectAttributes flash) {
        Order order = orderService.getAnyOrder(id);
        if (orderService.markShipped(id)) {
            log.info("Order {} marked shipped by {}", id, principal.getName());
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

    @PostMapping("/orders/{id}/cancel")
    public String cancel(@PathVariable Long id, Principal principal, RedirectAttributes flash) {
        Order order = orderService.getAnyOrder(id);
        if (orderService.cancelUnpaid(id)) {
            log.info("Order {} cancelled by {}", id, principal.getName());
            auditService.record(principal.getName(), AdminActionType.ORDER_CANCELLED,
                    AuditService.ORDER, id,
                    order.getItemCount() + " item(s) returned to stock; customer notified");
            flash.addFlashAttribute("message",
                    "Order #" + id + " cancelled and its stock returned to the shelf.");
        } else {
            flash.addFlashAttribute("error",
                    "Order #" + id + " is " + order.getStatus().getDisplayName().toLowerCase()
                            + ", so it cannot be cancelled here. Only an unpaid order can — "
                            + "a paid one needs a refund, which this app cannot do yet.");
        }
        return "redirect:/admin/orders/" + id;
    }

}
