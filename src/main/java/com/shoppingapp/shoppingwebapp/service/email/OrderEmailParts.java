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

    /** Total, and the converted charge where one was made. */
    public static String totals(Order order) {
        String font = EmailHtml.fontStack();
        StringBuilder html = new StringBuilder();
        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">")
                .append("<tr>")
                .append("<td style=\"padding:14px 0 0;font:700 16px/1.4 ").append(font)
                .append(";color:").append(EmailHtml.INK).append(";\">Total</td>")
                .append("<td align=\"right\" style=\"padding:14px 0 0;font:700 16px/1.4 ").append(font)
                .append(";color:").append(EmailHtml.INK).append(";\">")
                .append(escape(order.getTotalDisplay())).append("</td>")
                .append("</tr>");
        if (order.isConverted()) {
            html.append("<tr><td colspan=\"2\" style=\"padding:6px 0 0;font:400 13px/1.5 ").append(font)
                    .append(";color:").append(EmailHtml.INK_2).append(";\">")
                    .append("Charged through PayPal as ").append(escape(order.getChargeDisplay()))
                    .append(", converted at ").append(escape(order.getExchangeRateDisplay()))
                    .append(" to &euro;1.</td></tr>");
        }
        return html.append("</table>").toString();
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
}
