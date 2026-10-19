package com.routinemachine.core.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 11: a synthetic PDF fixture built in-test with PDFBox itself — never a real M-Pesa
// statement, per the task's explicit instruction. Proves MpesaPdfReader's actual text
// extraction and password handling against real PDF bytes, not just the parser's string
// handling (see MpesaStatementParserTest).
class MpesaPdfReaderTest {

    private byte[] buildPdf(String text, String ownerPassword) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.newLineAtOffset(25, 700);
                for (String line : text.split("\n")) {
                    content.showText(line);
                    content.newLineAtOffset(0, -12);
                }
                content.endText();
            }
            if (ownerPassword != null) {
                AccessPermission permission = new AccessPermission();
                StandardProtectionPolicy policy = new StandardProtectionPolicy(ownerPassword, ownerPassword, permission);
                document.protect(policy);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void extractsTextFromAnUnprotectedPdf() throws Exception {
        byte[] pdf = buildPdf("A synthetic statement line.", null);

        String text = MpesaPdfReader.extractText(pdf, null);

        assertThat(text).contains("A synthetic statement line.");
    }

    @Test
    void extractsTextFromAPasswordProtectedPdfWhenGivenTheRightPassword() throws Exception {
        byte[] pdf = buildPdf("A protected statement line.", "correct-horse");

        String text = MpesaPdfReader.extractText(pdf, "correct-horse");

        assertThat(text).contains("A protected statement line.");
    }

    @Test
    void aPasswordProtectedPdfWithNoPasswordGivenAsksForOne() throws Exception {
        byte[] pdf = buildPdf("A protected statement line.", "correct-horse");

        assertThatThrownBy(() -> MpesaPdfReader.extractText(pdf, null))
                .isInstanceOf(MpesaStatementPasswordRequiredException.class);
    }

    @Test
    void aPasswordProtectedPdfWithTheWrongPasswordAlsoAsksForOne() throws Exception {
        byte[] pdf = buildPdf("A protected statement line.", "correct-horse");

        assertThatThrownBy(() -> MpesaPdfReader.extractText(pdf, "wrong-password"))
                .isInstanceOf(MpesaStatementPasswordRequiredException.class);
    }

    @Test
    void notAPdfAtAllFailsWithAParseException() {
        byte[] notAPdf = "this is just text, not a pdf".getBytes();

        assertThatThrownBy(() -> MpesaPdfReader.extractText(notAPdf, null))
                .isInstanceOf(MpesaStatementParseException.class);
    }
}
