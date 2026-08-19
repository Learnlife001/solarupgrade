package com.shoppingapp.shoppingwebapp.service.email;

import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderItem;
import com.shoppingapp.shoppingwebapp.model.Product;

import static com.shoppingapp.shoppingwebapp.service.email.EmailHtml.escape;

/**
 * The parts of an email that describe an order: the items, what they cost, and
 * where they are going.
 *
 * <p>Built once and shared by every order email, so a receipt and a dispatch
 * notice show the same lines in the same shape. A customer comparing two of our
 * emails should not have to work out whether they describe the same purchase.
 */
public final class OrderEmailParts {

    private OrderEmailParts() {
    }

    /**
     * Product artwork for email.
     *
     * <p>The site draws products as SVG, which no major email client renders --
     * Gmail and Outlook both drop it. So the same drawings are rasterised to PNG
     * at build time under /images/email/ and referenced here by absolute URL,
     * which is the only kind an inbox can resolve.
     *
     * <p>Returns null when the product row is gone, or when it has an image this
     * mapping does not cover. A missing picture must degrade to a row without
     * one, never to a broken-image icon.
     */
    static String imageUrl(String baseUrl, Product product) {
        if (product == null) {
            return null;
        }
        String image = product.getImage();
        if (image == null || !image.startsWith("/images/") || !image.endsWith(".svg")) {
            return null;
        }
        String file = image.substring("/images/".length()).replace(".svg", ".png");
        return baseUrl + "/images/email/" + file;
    }

    /**
     * One item: picture, name, category, description, and the arithmetic.
     *
     * @param withPrices false on the dispatch notice, where money has already
     *                   been settled and repeating it invites a second look at
     *                   a figure nobody needs to check again
     */
    public static String itemRow(Order order, OrderItem item, String baseUrl, boolean withPrices) {
        Product product = item.getProduct();
        String image = imageUrl(baseUrl, product);
        String font = EmailHtml.fontStack();

        StringBuilder details = new StringBuilder();
        if (product != null) {
            details.append("<p style=\"margin:0 0 4px;font:600 11px/1.5 ").append(font)
                    .append(";color:").append(EmailHtml.INK_2)
                    .append(";letter-spacing:.07em;text-transform:uppercase;\">")
                    .append(escape(product.getCategory().getDisplayName()))
                    .append("</p>");
        }
        details.append("<p style=\"margin:0 0 4px;font:600 15px/1.4 ").append(font)
                .append(";color:").append(EmailHtml.INK).append(";\">")
                .append(escape(item.getProductName()))
                .append("</p>");
        if (product != null && product.getDescription() != null && !product.getDescription().isBlank()) {
            details.append("<p style=\"margin:0 0 6px;font:400 13px/1.55 ").append(font)
                    .append(";color:").append(EmailHtml.INK_2).append(";\">")
                    .append(escape(product.getDescription()))
                    .append("</p>");
        }
        details.append("<p style=\"margin:0;font:400 13px/1.5 ").append(font)
                .append(";color:").append(EmailHtml.INK_2).append(";\">")
                .append("Quantity ").append(item.getQuantity());
        if (withPrices) {
            details.append(" &times; ").append(escape(item.getUnitPriceDisplay()));
        }
        details.append("</p>");

        String priceCell = withPrices
                ? "<td align=\"right\" valign=\"top\" style=\"padding:14px 0;font:700 15px/1.4 " + font
                        + ";color:" + EmailHtml.INK + ";white-space:nowrap;\">"
                        + escape(item.getLineTotalDisplay()) + "</td>"
                : "";

        // width/height on the img so a client that blocks images still lays the
        // row out correctly, and alt text so a blocked image reads as the
        // product name rather than a grey box.
        String imageCell = image == null
                ? ""
                : "<td width=\"96\" valign=\"top\" style=\"padding:14px 14px 14px 0;\">"
                        + "<img src=\"" + escape(image) + "\" width=\"80\" height=\"60\" alt=\""
                        + escape(item.getProductName()) + "\" "
                        + "style=\"display:block;width:80px;height:60px;border-radius:8px;"
                        + "background:" + EmailHtml.SURFACE_2 + ";\"></td>";

        return "<tr>" + imageCell
                + "<td valign=\"top\" style=\"padding:14px 0;border-bottom:1px solid " + EmailHtml.LINE + ";\">"
                + details + "</td>"
                + (priceCell.isEmpty() ? "" : priceCell.replace("padding:14px 0;",
                        "padding:14px 0;border-bottom:1px solid " + EmailHtml.LINE + ";"))
                + "</tr>";
    }

    /** Every item, wrapped in the table that holds them. */
    public static String itemTable(Order order, String baseUrl, boolean withPrices) {
        StringBuilder rows = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            rows.append(itemRow(order, item, baseUrl, withPrices));
        }
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">"
                + rows + "</table>";
    }

    /**
     * The money, right-aligned under the items.
     *
     * <p>Only lines that are true of this shop. There is no discount row
     * because nothing here issues discounts, and delivery says "included"
     * because nothing here charges for it -- an invented "Shipping: 0" line
     * would be a promise the checkout never made. Whoever adds either feature
     * adds the row with it.
     */
    public static String totals(Order order) {
        String font = EmailHtml.fontStack();
        int items = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();

        StringBuilder html = new StringBuilder();
        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">")
                .append("<tr><td width=\"50%\"></td><td>")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">");

        html.append(row(font, items == 1 ? "Subtotal (1 item)" : "Subtotal (" + items + " items)",
                escape(order.getTotalDisplay()), false));
        html.append(row(font, "Delivery", "Included", false));

        html.append("<tr><td colspan=\"2\" style=\"padding:10px 0 0;\">")
                .append("<div style=\"height:1px;background:").append(EmailHtml.LINE).append(";\"></div>")
                .append("</td></tr>");

        html.append(row(font, "Total", escape(order.getTotalDisplay()), true));

        html.append("</table></td></tr>");

        if (order.isConverted()) {
            html.append("<tr><td colspan=\"2\" align=\"right\" style=\"padding:8px 0 0;font:400 13px/1.5 ")
                    .append(font).append(";color:").append(EmailHtml.INK_2).append(";\">")
                    .append("Charged through PayPal as ").append(escape(order.getChargeDisplay()))
                    .append(", converted at ").append(escape(order.getExchangeRateDisplay()))
                    .append(" to &euro;1.</td></tr>");
        }
        return html.append("</table>").toString();
    }

    /** One label-and-figure line; the last one is the one that matters, so it is bigger. */
    private static String row(String font, String label, String value, boolean emphasis) {
        String labelStyle = emphasis
                ? "padding:10px 0 0;font:700 17px/1.4 " + font + ";color:" + EmailHtml.INK + ";"
                : "padding:5px 0;font:400 14px/1.5 " + font + ";color:" + EmailHtml.INK_2 + ";";
        String valueStyle = emphasis
                ? "padding:10px 0 0;font:700 17px/1.4 " + font + ";color:" + EmailHtml.INK + ";white-space:nowrap;"
                : "padding:5px 0;font:400 14px/1.5 " + font + ";color:" + EmailHtml.INK + ";white-space:nowrap;";
        return "<tr><td style=\"" + labelStyle + "\">" + escape(label) + "</td>"
                + "<td align=\"right\" style=\"" + valueStyle + "\">" + value + "</td></tr>";
    }

    /**
     * How the order is being paid, and where it stands.
     *
     * <p>Never any card detail: nothing in this application sees a card number,
     * a last four or an expiry, because the payment provider takes them on its
     * own pages. An email that showed them would mean we had stored them.
     *
     * <p>No status either. The pill at the top of the message already says
     * whether the order is paid, and stating it twice from two places is how a
     * receipt headed "Thank you for your purchase" came to carry the words
     * "Pending payment" further down when it was rendered. One statement,
     * one source.
     */
    public static String payment(Order order) {
        StringBuilder html = new StringBuilder();
        html.append("<strong style=\"color:").append(EmailHtml.INK).append(";\">")
                .append(escape(order.getPaymentMethod().getDisplayName())).append("</strong>");
        if (order.isConverted()) {
            html.append("<br>").append(escape(order.getChargeDisplay()));
        }
        return html.toString();
    }

    /** The delivery label, laid out the way it would be written on a parcel. */
    public static String address(Order order) {
        String font = EmailHtml.fontStack();
        StringBuilder lines = new StringBuilder();
        lines.append("<strong style=\"color:").append(EmailHtml.INK).append(";\">")
                .append(escape(order.getShippingName())).append("</strong><br>");
        for (String line : order.getShippingLines()) {
            lines.append(escape(line)).append("<br>");
        }
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">"
                + "<tr><td style=\"background:" + EmailHtml.SURFACE_2 + ";border-radius:10px;padding:16px 18px;"
                + "font:400 14px/1.7 " + font + ";color:" + EmailHtml.INK_2 + ";\">"
                + lines + "</td></tr></table>";
    }

    /** The same address with no box around it, for use inside a column. */
    public static String addressLines(Order order) {
        StringBuilder lines = new StringBuilder();
        lines.append("<strong style=\"color:").append(EmailHtml.INK).append(";\">")
                .append(escape(order.getShippingName())).append("</strong><br>");
        for (String line : order.getShippingLines()) {
            lines.append(escape(line)).append("<br>");
        }
        return lines.toString();
    }
}
