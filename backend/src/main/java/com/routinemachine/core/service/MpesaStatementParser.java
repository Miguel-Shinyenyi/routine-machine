package com.routinemachine.core.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Phase 11: parses M-Pesa statement text (already extracted from the PDF — see
// MpesaPdfReader) into category totals, per docs/product-direction.md's granary section.
// Only "Withdrawn" amounts count: these totals answer "where did spending go," not "where did
// money come from." Deliberately keeps only the totals, never the individual transaction
// rows, matching "keep only the totals" exactly — nothing here stores a receipt number, a
// counterparty name, or a timestamp past the single pass that computes the month.
//
// The real M-Pesa statement's exact text layout once extracted from its PDF hasn't been
// verified against a real statement (the task explicitly says test with a synthetic fixture,
// never a real one — see docs/backend.md's open questions). This regex is a best-effort
// match for the row shape M-Pesa's own statement documents (receipt no., completion
// time, free-text details, a status word, three decimal amounts); if the real format differs,
// parsing fails loudly (MpesaStatementParseException) rather than silently returning empty or
// wrong totals.
public final class MpesaStatementParser {

    private static final Pattern TRANSACTION_ROW = Pattern.compile(
            "(?<receipt>\\S+)\\s+"
                    + "(?<date>\\d{4}-\\d{2}-\\d{2})\\s+"
                    + "(?<time>\\d{2}:\\d{2}:\\d{2})\\s+"
                    + "(?<details>.+?)\\s+"
                    + "(?:Completed|Pending|Failed)\\s+"
                    + "(?<paidIn>[\\d,]+\\.\\d{2})\\s+"
                    + "(?<withdrawn>[\\d,]+\\.\\d{2})\\s+"
                    + "(?<balance>[\\d,]+\\.\\d{2})\\s*$");

    private static final Map<MpesaCategory, List<String>> CATEGORY_KEYWORDS = new EnumMap<>(Map.of(
            MpesaCategory.FOOD, List.of("SUPERMARKET", "RESTAURANT", "BUTCHERY", "GROCERY", "NAIVAS", "CARREFOUR"),
            MpesaCategory.TRANSPORT, List.of("UBER", "BOLT", "MATATU", "BODA", "TRANSPORT"),
            MpesaCategory.FAMILY_SUPPORT, List.of("FAMILY SUPPORT", "SCHOOL FEES"),
            MpesaCategory.DATA_AND_AIRTIME, List.of("AIRTIME", "DATA BUNDLE", "SAFARICOM DATA", "BUY AIRTIME"),
            MpesaCategory.LEARNING, List.of("LEARNING", "COURSERA", "UDEMY", "COURSE", "TUITION"),
            MpesaCategory.BETTING, List.of("SPORTPESA", "BETIKA", "BETTING", "ODIBET", "MOZZART"),
            MpesaCategory.OVERDRAFT_FEES, List.of("FULIZA", "OVERDRAFT")));

    private MpesaStatementParser() {
    }

    public static MpesaParseResult parse(String extractedText) {
        Map<MpesaCategory, Integer> totals = new LinkedHashMap<>();
        int transactionsParsed = 0;
        LocalDate firstDate = null;

        for (String line : extractedText.split("\\R")) {
            Matcher matcher = TRANSACTION_ROW.matcher(line.trim());
            if (!matcher.matches()) {
                continue;
            }
            transactionsParsed++;
            if (firstDate == null) {
                firstDate = LocalDate.parse(matcher.group("date"));
            }

            int withdrawn = parseAmount(matcher.group("withdrawn"));
            if (withdrawn == 0) {
                continue;
            }
            MpesaCategory category = categorize(matcher.group("details"));
            totals.merge(category, withdrawn, Integer::sum);
        }

        if (transactionsParsed == 0) {
            throw new MpesaStatementParseException(
                    "No recognisable transaction rows found. The statement's format may have changed.");
        }

        return new MpesaParseResult(totals, transactionsParsed, YearMonth.from(firstDate));
    }

    private static int parseAmount(String raw) {
        return (int) Math.round(Double.parseDouble(raw.replace(",", "")));
    }

    private static MpesaCategory categorize(String details) {
        String upper = details.toUpperCase();
        for (Map.Entry<MpesaCategory, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (upper.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return MpesaCategory.OTHER;
    }
}
