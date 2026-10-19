package com.routinemachine.core.service;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 11: M-Pesa statement import (docs/product-direction.md). The real M-Pesa statement
// PDF's exact text layout can't be verified without a real statement (the task explicitly
// says test with a synthetic fixture, never a real one) — these tests exercise the parser
// against transaction-row text in the documented shape, not a real extracted PDF. See
// docs/backend.md's open questions for the real-format risk this carries.
class MpesaStatementParserTest {

    private static final String HEADER = "Receipt No. Completion Time Details Transaction Status Paid In Withdrawn Balance\n";

    private String row(String receipt, String date, String time, String details, String status, String paidIn, String withdrawn, String balance) {
        return "%s %s %s %s %s %s %s %s\n".formatted(receipt, date, time, details, status, paidIn, withdrawn, balance);
    }

    @Test
    void sumsWithdrawnAmountsByCategory() {
        String text = HEADER
                + row("OEI2AB3C4D", "2026-09-02", "08:15:22", "Pay Bill to NAIVAS SUPERMARKET", "Completed", "0.00", "1,200.00", "8,800.00")
                + row("OEI2AB3C4E", "2026-09-03", "09:00:11", "Customer Transfer to UBER KENYA", "Completed", "0.00", "450.00", "8,350.00")
                + row("OEI2AB3C4F", "2026-09-10", "19:40:00", "Pay Bill to NAIVAS SUPERMARKET", "Completed", "0.00", "2,100.00", "6,250.00");

        Map<MpesaCategory, Integer> totals = MpesaStatementParser.parse(text).categoryTotals();

        assertThat(totals.get(MpesaCategory.FOOD)).isEqualTo(3300);
        assertThat(totals.get(MpesaCategory.TRANSPORT)).isEqualTo(450);
    }

    @Test
    void onlyWithdrawnAmountsCountNotPaidIn() {
        String text = HEADER
                + row("OEI2AB3C4D", "2026-09-02", "08:15:22", "Funds received from JOHN KAMAU", "Completed", "5,000.00", "0.00", "13,800.00");

        Map<MpesaCategory, Integer> totals = MpesaStatementParser.parse(text).categoryTotals();

        assertThat(totals.values().stream().mapToInt(Integer::intValue).sum()).isZero();
    }

    @Test
    void unmatchedDetailsFallIntoOther() {
        String text = HEADER
                + row("OEI2AB3C4D", "2026-09-02", "08:15:22", "Pay Bill to SOME RANDOM MERCHANT", "Completed", "0.00", "700.00", "8,800.00");

        Map<MpesaCategory, Integer> totals = MpesaStatementParser.parse(text).categoryTotals();

        assertThat(totals.get(MpesaCategory.OTHER)).isEqualTo(700);
    }

    @Test
    void recognisesBettingDataAirtimeLearningAndOverdraftFees() {
        String text = HEADER
                + row("A", "2026-09-01", "08:00:00", "Pay Bill to SPORTPESA", "Completed", "0.00", "500.00", "9,500.00")
                + row("B", "2026-09-01", "09:00:00", "Buy Airtime", "Completed", "0.00", "200.00", "9,300.00")
                + row("C", "2026-09-01", "10:00:00", "Pay Bill to COURSERA LEARNING", "Completed", "0.00", "1,500.00", "7,800.00")
                + row("D", "2026-09-01", "11:00:00", "M-Shwari Fuliza Overdraft Fee", "Completed", "0.00", "30.00", "7,770.00");

        Map<MpesaCategory, Integer> totals = MpesaStatementParser.parse(text).categoryTotals();

        assertThat(totals.get(MpesaCategory.BETTING)).isEqualTo(500);
        assertThat(totals.get(MpesaCategory.DATA_AND_AIRTIME)).isEqualTo(200);
        assertThat(totals.get(MpesaCategory.LEARNING)).isEqualTo(1500);
        assertThat(totals.get(MpesaCategory.OVERDRAFT_FEES)).isEqualTo(30);
    }

    @Test
    void reportsHowManyTransactionsWereParsed() {
        String text = HEADER
                + row("A", "2026-09-01", "08:00:00", "Pay Bill to NAIVAS SUPERMARKET", "Completed", "0.00", "500.00", "9,500.00")
                + row("B", "2026-09-02", "09:00:00", "Customer Transfer to UBER KENYA", "Completed", "0.00", "200.00", "9,300.00");

        MpesaParseResult result = MpesaStatementParser.parse(text);

        assertThat(result.transactionsParsed()).isEqualTo(2);
    }

    @Test
    void amonthIsDerivedFromTheFirstTransactionsCompletionDate() {
        String text = HEADER
                + row("A", "2026-09-15", "08:00:00", "Pay Bill to NAIVAS SUPERMARKET", "Completed", "0.00", "500.00", "9,500.00");

        MpesaParseResult result = MpesaStatementParser.parse(text);

        assertThat(result.statementMonth()).isEqualTo(YearMonth.of(2026, 9));
    }

    @Test
    void textWithNoRecognisableTransactionRowsFailsLoudlyRatherThanReturningEmptyTotals() {
        String text = "This is not an M-Pesa statement at all, just some unrelated text.";

        assertThatThrownBy(() -> MpesaStatementParser.parse(text))
                .isInstanceOf(MpesaStatementParseException.class);
    }
}
