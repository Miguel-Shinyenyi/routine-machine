package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static com.routinemachine.core.TestTokens.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Per-user scoping, part 2b: books, reading notes, current reading and learning topics answer
// for exactly one user: the one in the signed token.
// Titles and names are unique to each test: the database is shared across this class.
@SpringBootTest
@AutoConfigureMockMvc
class ScopedReadingAndTopicsApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    private long saveUser(String email) {
        return userRepository.save(new User(email, "irrelevant-hash", null, null)).getId();
    }

    private long idOf(MvcResult result) throws Exception {
        return Long.parseLong(result.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private long createBook(String title, Long userId) throws Exception {
        return idOf(mockMvc.perform(post("/api/books").with(asUser(userId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"" + title + "\"}"))
                .andExpect(status().isCreated()).andReturn());
    }

    private long createGoal(String name, Long userId) throws Exception {
        return idOf(mockMvc.perform(post("/api/goals").with(asUser(userId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn());
    }

    @Test
    void booksAreListedOnlyForTheirOwner() throws Exception {
        long a = saveUser("book-a@example.com");
        long b = saveUser("book-b@example.com");
        createBook("book-A-only", a);
        createBook("book-B-only", b);

        mockMvc.perform(get("/api/books").with(asUser(a)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("book-A-only")))
                .andExpect(jsonPath("$[0].userId", is((int) a)));
    }

    @Test
    void aNewUserHasNoBooksAndNoCurrentBook() throws Exception {
        long newcomer = saveUser("book-new@example.com");
        createBook("book-someone-elses", saveUser("book-other@example.com"));

        mockMvc.perform(get("/api/books").with(asUser(newcomer)))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/books/current").with(asUser(newcomer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theCurrentBookIsTheLatestOfThatUserNotTheLatestOverall() throws Exception {
        long a = saveUser("book-c@example.com");
        long b = saveUser("book-d@example.com");
        createBook("book-C-first", a);
        createBook("book-C-second", a);
        createBook("book-D-newest-overall", b);

        mockMvc.perform(get("/api/books/current").with(asUser(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("book-C-second")));
    }


    @Test
    void readingNotesAreListedOnlyForTheirOwnersAndCannotBeWrittenOnSomeoneElsesBook() throws Exception {
        long a = saveUser("note-a@example.com");
        long b = saveUser("note-b@example.com");
        long asBook = createBook("note-As-book", a);
        long bsBook = createBook("note-Bs-book", b);

        mockMvc.perform(post("/api/reading-notes").with(asUser(a)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": " + asBook + ", \"noteText\": \"note-A-text\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/reading-notes").with(asUser(b)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": " + bsBook + ", \"noteText\": \"note-B-text\"}"))
                .andExpect(status().isCreated());
        // A tries to write on B's book.
        mockMvc.perform(post("/api/reading-notes").with(asUser(a)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": " + bsBook + ", \"noteText\": \"note-intruder\"}"))
                .andExpect(status().isBadRequest());

        String listedForA = mockMvc.perform(get("/api/reading-notes").with(asUser(a)))
                .andReturn().getResponse().getContentAsString();
        assertThat(listedForA).contains("note-A-text").doesNotContain("note-B-text").doesNotContain("note-intruder");
        String listedForB = mockMvc.perform(get("/api/reading-notes").with(asUser(b)))
                .andReturn().getResponse().getContentAsString();
        assertThat(listedForB).contains("note-B-text").doesNotContain("note-intruder");
    }

    @Test
    void askingForNotesOfSomeoneElsesBookReturnsNothing() throws Exception {
        long a = saveUser("note-c@example.com");
        long b = saveUser("note-d@example.com");
        long bsBook = createBook("note-Ds-book", b);
        mockMvc.perform(post("/api/reading-notes").with(asUser(b)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\": " + bsBook + ", \"noteText\": \"note-D-private\"}"));

        mockMvc.perform(get("/api/reading-notes").with(asUser(a)).param("bookId", String.valueOf(bsBook)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aGoodNoteMarksOnlyTheAuthorsReadingRoutineDone() throws Exception {
        long a = saveUser("note-e@example.com");
        long b = saveUser("note-f@example.com");
        for (long user : new long[] {a, b}) {
            mockMvc.perform(post("/api/routine-items").with(asUser(user)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"reading-" + user + "\", \"source\": \"READER_NOTES\"}"))
                    .andExpect(status().isCreated());
        }
        long asBook = createBook("note-Es-book", a);

        mockMvc.perform(post("/api/reading-notes").with(asUser(a)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": " + asBook + ", \"noteText\": \"in my own words\", "
                                + "\"ownWordsPercentage\": 90}"))
                .andExpect(status().isCreated());

        String today = LocalDate.now().toString();
        mockMvc.perform(get("/api/daily-logs").with(asUser(a)).param("date", today))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId", is((int) a)));
        mockMvc.perform(get("/api/daily-logs").with(asUser(b)).param("date", today))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void currentReadingIsPerUser() throws Exception {
        long a = saveUser("cur-a@example.com");
        long b = saveUser("cur-b@example.com");
        long newcomer = saveUser("cur-c@example.com");
        mockMvc.perform(post("/api/current-reading").with(asUser(a)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"cur-A-title\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/current-reading").with(asUser(b)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"cur-B-title\"}")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/current-reading").with(asUser(a)))
                .andExpect(jsonPath("$.title", is("cur-A-title")));
        mockMvc.perform(get("/api/current-reading").with(asUser(newcomer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void learningTopicsAreListedOnlyThroughTheirGoalsOwner() throws Exception {
        long a = saveUser("topic-a@example.com");
        long b = saveUser("topic-b@example.com");
        long asGoal = createGoal("topic-A-goal", a);
        long bsGoal = createGoal("topic-B-goal", b);
        mockMvc.perform(post("/api/learning-topics").with(asUser(a)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"topic-A-topic\", \"goalId\": " + asGoal + "}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/learning-topics").with(asUser(b)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"topic-B-topic\", \"goalId\": " + bsGoal + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/learning-topics").with(asUser(a)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("topic-A-topic")));
    }

    @Test
    void aTopicCannotBeAddedUnderSomeoneElsesGoal() throws Exception {
        long a = saveUser("topic-c@example.com");
        long b = saveUser("topic-d@example.com");
        long bsGoal = createGoal("topic-D-goal", b);

        mockMvc.perform(post("/api/learning-topics").with(asUser(a)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"topic-intruder\", \"goalId\": " + bsGoal + "}"))
                .andExpect(status().isBadRequest());
    }
}
