package com.shoppingapp.shoppingwebapp.support;

/**
 * Writing values into a CSV file that a spreadsheet will open.
 *
 * <p>Two separate jobs, and the second is the one that gets forgotten.
 *
 * <h2>Quoting</h2>
 * A field holding a comma, a quote or a newline has to be quoted, and quotes
 * inside it doubled, or the row silently gains a column. Addresses hold all
 * three.
 *
 * <h2>Formula injection</h2>
 * Excel, LibreOffice and Google Sheets treat a cell beginning {@code =}, {@code
 * +}, {@code -} or {@code @} as a formula, and will run it on open. The
 * contents of this export are typed by customers -- a name and an address --
 * so a customer who names themselves {@code =HYPERLINK("http://…","Click")}
 * has written a formula into a file the shop's owner opens on their own
 * machine. Quoting does not stop it: the quotes are stripped by the
 * spreadsheet before the cell is evaluated.
 *
 * <p>The fix is a leading apostrophe, which every spreadsheet treats as "this
 * is text" and does not display. Harmless on a value that was never dangerous,
 * so it is applied on the way out rather than being decided per field.
 */
public final class Csv {

    /**
     * Characters that make a spreadsheet treat a cell as a formula. Tab and
     * carriage return are here because a leading one of either is stripped on
     * import, promoting whatever follows to the front of the cell.
     */
    private static final String FORMULA_STARTERS = "=+-@\t\r";

    private Csv() {
    }

    /** One field, quoted and made safe to open. */
    public static String field(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = neutralise(value);
        if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }

    /** One row, already newline-terminated. */
    public static String row(String... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(field(values[i]));
        }
        // CRLF: the line ending the CSV specification names, and the one
        // Windows spreadsheets are least surprised by.
        return row.append("\r\n").toString();
    }

    private static String neutralise(String value) {
        if (!value.isEmpty() && FORMULA_STARTERS.indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }
}
