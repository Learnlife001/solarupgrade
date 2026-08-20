package com.shoppingapp.shoppingwebapp.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Values written into a file a spreadsheet opens.
 *
 * <p>The formula cases are the point. Everything in the order export that a
 * customer typed — their name, their address — lands in a cell, and Excel,
 * LibreOffice and Sheets all evaluate a cell beginning with {@code =} when the
 * file is opened. Without this, the shop's own export is a way for whoever
 * placed an order to run something on the owner's machine.
 */
class CsvTest {

    @Test
    void ordinaryValuesArePassedThrough() {
        assertThat(Csv.field("Adaeze Okafor")).isEqualTo("Adaeze Okafor");
    }

    @Test
    void aFieldWithACommaIsQuoted() {
        assertThat(Csv.field("Lagos, Nigeria")).isEqualTo("\"Lagos, Nigeria\"");
    }

    /** Quotes are doubled, or the row gains a column. */
    @Test
    void quotesAreDoubledInsideAQuotedField() {
        assertThat(Csv.field("The \"Old\" Mill")).isEqualTo("\"The \"\"Old\"\" Mill\"");
    }

    @Test
    void newlinesStayInsideTheField() {
        assertThat(Csv.field("14 Adeola Odeku\nVictoria Island"))
                .isEqualTo("\"14 Adeola Odeku\nVictoria Island\"");
    }

    /**
     * A formula is defused with a leading apostrophe, which spreadsheets read
     * as "this is text" and do not display.
     */
    @Test
    void aFormulaIsNeutralised() {
        assertThat(Csv.field("=1+1")).isEqualTo("'=1+1");
    }

    @Test
    void everyFormulaStarterIsNeutralised() {
        assertThat(Csv.field("+1234")).isEqualTo("'+1234");
        assertThat(Csv.field("-1+2")).isEqualTo("'-1+2");
        assertThat(Csv.field("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    }

    /**
     * A leading tab or carriage return is stripped on import, which promotes
     * whatever follows to the front of the cell — so those count as starters
     * too.
     */
    @Test
    void aLeadingTabOrCarriageReturnIsNeutralised() {
        assertThat(Csv.field("\t=1+1")).startsWith("'");
        assertThat(Csv.field("\r=1+1")).startsWith("\"'");
    }

    /** Quoting alone does not defuse a formula: the quotes are stripped first. */
    @Test
    void quotingIsNotEnoughOnItsOwn() {
        String written = Csv.field("=HYPERLINK(\"http://evil.example\",\"Click\")");

        assertThat(written).startsWith("\"'=");
    }

    @Test
    void aRowJoinsFieldsAndEndsWithCrLf() {
        assertThat(Csv.row("a", "b,c")).isEqualTo("a,\"b,c\"\r\n");
    }

    @Test
    void nullsAndBlanksBecomeEmptyCells() {
        assertThat(Csv.field(null)).isEmpty();
        assertThat(Csv.field("")).isEmpty();
    }
}
