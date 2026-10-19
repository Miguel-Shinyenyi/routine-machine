package com.routinemachine.core.api;

import com.routinemachine.core.service.MpesaCategory;
import com.routinemachine.core.service.MpesaParseResult;

import java.time.YearMonth;
import java.util.Map;

public record MpesaImportResponse(YearMonth statementMonth, int transactionsParsed, Map<MpesaCategory, Integer> categoryTotals) {

    public static MpesaImportResponse from(MpesaParseResult result) {
        return new MpesaImportResponse(result.statementMonth(), result.transactionsParsed(), result.categoryTotals());
    }
}
