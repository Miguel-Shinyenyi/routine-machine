package com.routinemachine.core.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;

// Phase 11: reads a statement PDF's text, trying with no password first, then the caller's
// password if one was given — see docs/product-direction.md's M-Pesa section ("the statement
// PDF may be password-protected... propose how to handle both"). The file's bytes never touch
// disk; GranaryController passes the uploaded MultipartFile's bytes straight through, and
// nothing in this class or its caller writes them anywhere.
public final class MpesaPdfReader {

    private MpesaPdfReader() {
    }

    public static String extractText(byte[] pdfBytes, String password) {
        try (PDDocument document = Loader.loadPDF(pdfBytes, password == null ? "" : password)) {
            return new PDFTextStripper().getText(document);
        } catch (InvalidPasswordException e) {
            throw new MpesaStatementPasswordRequiredException(
                    password == null
                            ? "This statement is password-protected. Enter the password and try again."
                            : "That password didn't open the statement. Check it and try again.");
        } catch (IOException e) {
            throw new MpesaStatementParseException("Could not read this file as a PDF: " + e.getMessage());
        }
    }
}
