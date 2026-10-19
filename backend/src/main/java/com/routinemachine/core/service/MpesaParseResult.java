package com.routinemachine.core.service;

import java.time.YearMonth;
import java.util.Map;

public record MpesaParseResult(Map<MpesaCategory, Integer> categoryTotals, int transactionsParsed, YearMonth statementMonth) {
}
