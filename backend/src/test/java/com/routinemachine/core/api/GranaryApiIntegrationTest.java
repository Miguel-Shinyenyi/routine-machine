package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GranaryApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @Test
    void settingTheDailyAmountThenStoringASavingUpdatesTheSummary() throws Exception {
        User user = userRepository.save(new User("granary-store@example.com", "irrelevant-hash", null, null));

        mockMvc.perform(put("/api/granary/saving-target-amount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + user.getId() + ", \"amount\": 50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savingTargetAmount", is(50)));

        mockMvc.perform(post("/api/granary/savings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + user.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storedThisMonth", is(50)))
                .andExpect(jsonPath("$.daysStored", is(1)));

        // Storing again the same day doesn't double it.
        mockMvc.perform(post("/api/granary/savings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + user.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storedThisMonth", is(50)))
                .andExpect(jsonPath("$.daysStored", is(1)));
    }

    @Test
    void storingWithNoTargetAmountSetFails() throws Exception {
        User user = userRepository.save(new User("granary-no-target@example.com", "irrelevant-hash", null, null));

        mockMvc.perform(post("/api/granary/savings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + user.getId() + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingAPauseThenDecidingAndReflectingUpdatesItsState() throws Exception {
        User user = userRepository.save(new User("granary-pause@example.com", "irrelevant-hash", null, null));

        MvcResult created = mockMvc.perform(post("/api/granary/pauses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + user.getId() + ", \"what\": \"A token presale\", "
                                + "\"amount\": \"KSh 5,000\", \"why\": \"Everyone says it will 10x\", "
                                + "\"heardFrom\": \"WhatsApp group\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").doesNotExist())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(patch("/api/granary/pauses/" + id + "/decide").param("userId", user.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\": \"DECLINED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome", is("DECLINED")));

        mockMvc.perform(patch("/api/granary/pauses/" + id + "/reflect").param("userId", user.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reflection\": \"Glad I waited.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reflectionThirtyDay", is("Glad I waited.")));

        mockMvc.perform(get("/api/granary/pauses").param("userId", user.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].what", is("A token presale")));
    }

    @Test
    void decidingAnUnknownPauseReturns404() throws Exception {
        mockMvc.perform(patch("/api/granary/pauses/999999/decide").param("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\": \"DECLINED\"}"))
                .andExpect(status().isNotFound());
    }

    private static final String STATEMENT_TEXT = "Receipt No. Completion Time Details Transaction Status Paid In Withdrawn Balance\n"
            + "OEI2AB3C4D 2026-09-02 08:15:22 Pay Bill to NAIVAS SUPERMARKET Completed 0.00 1,200.00 8,800.00\n";

    private byte[] buildPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                content.newLineAtOffset(20, 750);
                for (String line : text.split("\n")) {
                    content.showText(line);
                    content.newLineAtOffset(0, -11);
                }
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void importingAnMpesaStatementReturnsCategoryTotalsAndPersistsThem() throws Exception {
        User user = userRepository.save(new User("mpesa-api@example.com", "irrelevant-hash", null, null));
        MockMultipartFile file = new MockMultipartFile("file", "statement.pdf", "application/pdf", buildPdf(STATEMENT_TEXT));

        mockMvc.perform(multipart("/api/granary/mpesa-statement")
                        .file(file)
                        .param("userId", user.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionsParsed", is(1)))
                .andExpect(jsonPath("$.categoryTotals.FOOD", is(1200)));

        mockMvc.perform(get("/api/granary/mpesa-categories")
                        .param("userId", user.getId().toString())
                        .param("month", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.FOOD", is(1200)));
    }

    @Test
    void importingAStatementWithNoRecognisableTransactionsFailsWithAClearErrorType() throws Exception {
        User user = userRepository.save(new User("mpesa-bad-format@example.com", "irrelevant-hash", null, null));
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.pdf", "application/pdf", buildPdf("Not a statement at all."));

        mockMvc.perform(multipart("/api/granary/mpesa-statement")
                        .file(file)
                        .param("userId", user.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("PARSE_ERROR")));
    }
}
