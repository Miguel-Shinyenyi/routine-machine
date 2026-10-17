package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.Book;
import com.routinemachine.core.domain.ReadingSource;
import com.routinemachine.core.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReadingNoteApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookRepository bookRepository;

    // One test, not several: @SpringBootTest methods here share one Postgres instance per
    // class with no per-method rollback (see testing.md's decisions log on BookApiIntegrationTest
    // for why this matters), so a second method filtering by bookId would be order-dependent
    // on what this one already wrote.
    @Test
    void postingANoteStoresItAndListsItByBookOrAcrossAllBooks() throws Exception {
        Book book = bookRepository.save(new Book("Atomic Habits", null, 3, 20, ReadingSource.MANUAL));
        Book otherBook = bookRepository.save(new Book("Sapiens", null, null, null, ReadingSource.MANUAL));

        mockMvc.perform(post("/api/reading-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookId": %d,
                                  "noteText": "Habit stacking felt true to my own mornings.",
                                  "chapterPlaced": "Chapter 3",
                                  "ownWordsPercentage": 80,
                                  "ideasConnected": ["Links to willpower idea"],
                                  "quotedPhrases": ["the plateau of latent potential"],
                                  "questionBack": "What would make this automatic for you?"
                                }
                                """.formatted(book.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookId", is(book.getId().intValue())))
                .andExpect(jsonPath("$.bookTitle", is("Atomic Habits")))
                .andExpect(jsonPath("$.ownWordsPercentage", is(80)))
                .andExpect(jsonPath("$.quotedPhrases[0]", is("the plateau of latent potential")));

        mockMvc.perform(post("/api/reading-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookId": %d, "noteText": "An unassessed note."}
                                """.formatted(otherBook.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownWordsPercentage").doesNotExist());

        mockMvc.perform(get("/api/reading-notes").param("bookId", book.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].bookTitle", is("Atomic Habits")));

        mockMvc.perform(get("/api/reading-notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)));
    }

    @Test
    void rejectsABlankNoteText() throws Exception {
        Book book = bookRepository.save(new Book("Some Book", ReadingSource.MANUAL));

        mockMvc.perform(post("/api/reading-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookId": %d, "noteText": ""}
                                """.formatted(book.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnUnknownBookId() throws Exception {
        mockMvc.perform(post("/api/reading-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookId": 999999, "noteText": "Some note."}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnOwnWordsPercentageOutOfRange() throws Exception {
        Book book = bookRepository.save(new Book("Some Book", ReadingSource.MANUAL));

        mockMvc.perform(post("/api/reading-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookId": %d, "noteText": "Some note.", "ownWordsPercentage": 150}
                                """.formatted(book.getId())))
                .andExpect(status().isBadRequest());
    }
}
