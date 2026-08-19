package com.shoppingapp.shoppingwebapp.service.email;

/**
 * The pieces every transactional email is built from.
 *
 * <p>Tables and inline styles, which looks twenty years out of date and is
 * simply what email is. Outlook renders with Word's engine, Gmail strips
 * anything in a &lt;style&gt; block that it dislikes, and neither supports
 * flexbox or grid. A layout that works everywhere is nested tables with the
 * styling written on each cell.
 *
 * <p>Every value that comes from a person -- a name, an address, a product
 * title -- goes through {@link #escape}. An unescaped apostrophe merely looks
 * wrong; unescaped angle brackets in a shipping address would let whoever
 * typed them write markup into an email we send in our own name.
 */
public final class EmailHtml {

    /* Kept in step with the site's light palette by hand: an email cannot
       reference the stylesheet, so these are the same values written out. */
    public static final String INK = "#0d1a14";
    public static final String INK_2 = "#4d6156";
    public static final String LINE = "#e3ece6";
    public static final String SURFACE = "#ffffff";
    public static final String SURFACE_2 = "#f1f7f3";
    public static final String ACCENT = "#0f8f4f";
    public static final String ACCENT_SOFT = "#e2f7ea";
    public static final String ACCENT_INK = "#06552e";
    public static final String SUN_SOFT = "#fff5e0";
    public static final String SUN_INK = "#7a4d00";

    private static final String FONT =
            "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif";

    private EmailHtml() {
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Wraps the body in the shell: preheader, header bar, card, footer.
     *
     * @param preheader the line inboxes show beside the subject. Left out, a
     *                  client picks the first words of the body instead, which
     *                  is usually "Hi" and the customer's own name.
     */
    public static String document(String brandName, String brandMark, String tagline,
                                  String title, String preheader, String body, String footerNote) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="color-scheme" content="light">
                <meta name="supported-color-schemes" content="light">
                <title>%TITLE%</title>
                </head>
                <body style="margin:0;padding:0;background:%SURFACE_2%;">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0;">%PREHEADER%</div>
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
                       style="background:%SURFACE_2%;padding:24px 12px;">
                  <tr><td align="center">
                    <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0"
                           style="width:600px;max-width:100%;">

                      <tr><td style="padding:0 0 18px 4px;font:700 20px/1.2 %FONT%;color:%INK%;">
                        <span style="color:%ACCENT%;">%MARK%</span> %BRAND%
                      </td></tr>

                      <tr><td style="background:%SURFACE%;border:1px solid %LINE%;border-radius:14px;
                                     padding:28px 28px 24px;">
                        %BODY%
                      </td></tr>

                      <tr><td style="padding:18px 4px 0;font:400 12px/1.6 %FONT%;color:%INK_2%;">
                        %FOOTER%<br>
                        %BRAND% &mdash; %TAGLINE%
                      </td></tr>

                    </table>
                  </td></tr>
                </table>
                </body>
                </html>
                """
                .replace("%BRAND%", escape(brandName))
                .replace("%MARK%", escape(brandMark))
                .replace("%TAGLINE%", escape(tagline))
                .replace("%TITLE%", escape(title))
                .replace("%PREHEADER%", escape(preheader))
                .replace("%BODY%", body)
                .replace("%FOOTER%", footerNote)
                .replace("%FONT%", FONT)
                .replace("%SURFACE_2%", SURFACE_2)
                .replace("%SURFACE%", SURFACE)
                .replace("%LINE%", LINE)
                .replace("%INK%", INK)
                .replace("%INK_2%", INK_2)
                .replace("%ACCENT%", ACCENT);
    }

    public static String heading(String text) {
        return "<h1 style=\"margin:0 0 6px;font:700 24px/1.25 " + FONT + ";color:" + INK + ";\">"
                + escape(text) + "</h1>";
    }

    public static String paragraph(String text) {
        return "<p style=\"margin:0 0 14px;font:400 15px/1.6 " + FONT + ";color:" + INK_2 + ";\">"
                + text + "</p>";
    }

    /** A coloured status chip: paid, on its way, awaiting payment. */
    public static String pill(String text, String background, String colour) {
        return "<p style=\"margin:0 0 16px;\"><span style=\"display:inline-block;padding:4px 12px;"
                + "border-radius:999px;background:" + background + ";color:" + colour + ";"
                + "font:600 12px/1.5 " + FONT + ";letter-spacing:.04em;text-transform:uppercase;\">"
                + escape(text) + "</span></p>";
    }

    /**
     * The call to action.
     *
     * <p>A table rather than a styled anchor, because Outlook ignores padding
     * on inline elements and would render a bare link where the button should
     * be.
     */
    public static String button(String label, String href) {
        return """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:6px 0 18px;">
                  <tr><td style="background:%ACCENT%;border-radius:10px;">
                    <a href="%HREF%" style="display:inline-block;padding:12px 22px;font:600 15px/1 %FONT%;
                       color:#ffffff;text-decoration:none;">%LABEL%</a>
                  </td></tr>
                </table>
                """
                .replace("%ACCENT%", ACCENT)
                .replace("%FONT%", FONT)
                .replace("%HREF%", escape(href))
                .replace("%LABEL%", escape(label));
    }

    public static String divider() {
        return "<div style=\"height:1px;background:" + LINE + ";margin:20px 0;\"></div>";
    }

    public static String sectionTitle(String text) {
        return "<p style=\"margin:0 0 12px;font:600 12px/1.5 " + FONT + ";color:" + INK_2 + ";"
                + "letter-spacing:.08em;text-transform:uppercase;\">" + escape(text) + "</p>";
    }

    /** Monospaced and oversized, for a verification code meant to be typed. */
    public static String code(String value) {
        return "<p style=\"margin:0 0 18px;padding:16px;background:" + SURFACE_2 + ";border-radius:10px;"
                + "text-align:center;font:700 30px/1.2 ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;"
                + "letter-spacing:.22em;color:" + INK + ";\">" + escape(value) + "</p>";
    }

    public static String small(String text) {
        return "<p style=\"margin:0 0 6px;font:400 13px/1.6 " + FONT + ";color:" + INK_2 + ";\">"
                + text + "</p>";
    }

    public static String fontStack() {
        return FONT;
    }
}
