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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Per-user scoping, part 2b: books, reading notes, current reading and learning topics answer
// for exactly one user. userId given = that user's rows; omitted = only unowned rows.
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

    private String userField(Long userId) {
        return userId == null ? "" : ", \"userId\": " + userId;
    }

    private long createBook(String title, Long userId) throws Exception {
        return idOf(mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"" + title + "\"" + userField(userId) + "}"))
                .andExpect(status().isCreated()).andReturn());
    }

    private long createGoal(String name, Long userId) throws Exception {
        return idOf(mockMvc.perform(post("/api/goals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\"" + userField(userId) + "}"))
                .andExpect(status().isCreated()).andReturn());
    }

    @Test
    void booksAreListedOnlyForTheirOwner() throws Exception {
        long a = saveUser("book-a@example.com");
        long b = saveUser("book-b@example.com");
        createBook("book-A-only", a);
        createBook("book-B-only", b);

        mockMvc.perform(get("/api/books").param("userId", String.valueOf(a)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("book-A-only")))
                .andExpect(jsonPath("$[0].userId", is((int) a)));
    }

    @Test
    void aNewUserHasNoBooksAndNoCurrentBook() throws Exception {
        long newcomer = saveUser("book-new@example.com");
        createBook("book-someone-elses", saveUser("book-other@example.com"));

        mockMvc.perform(get("/api/books").param("userId", String.valueOf(newcomer)))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/books/current").param("userId", String.valueOf(newcomer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theCurrentBookIsTheLatestOfThatUserNotTheLatestOverall() throws Exception {
        long a = saveUser("book-c@example.com");
        long b = saveUser("book-d@example.com");
        createBook("book-C-first", a);
        createBook("book-C-second", a);
        createBook("book-D-newest-overall", b);

        mockMvc.perform(get("/api/books/current").param("userId", String.valueOf(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("book-C-second")));
    }

    @Test
    void omittingUserIdShowsOnlyUnownedBooks() throws Exception {
        long a = saveUser("book-e@example.com");
        createBook("book-owned-by-E", a);
        createBook("book-unowned", null);

        String body = mockMvc.perform(get("/api/books")).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("book-unowned").doesNotContain("book-owned-by-E");
    }

    @Test
    void readingNotesAreListedOnlyForTheirOwnersAndCannotBeWrittenOnSomeoneElsesBook() throws Exception {
        long a = saveUser("note-a@example.com");
        long b = saveUser("note-b@example.com");
        long asBook = createBook("note-As-book", a);
        long bsBook = createBook("note-Bs-book", b);

        mockMvc.perform(post("/api/reading-notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": " + asBook + ", \"noteText\": \"note-A-text\", \"userId\": " + a + "}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/reading-notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": " + bsBook + ", \"noteText\": \"note-B-text\", \"userId\": " + b + "}"))
                .andExpect(status().isCreated());
        // A tries to write on B's book.
        mockMvc.perform(post("/api/reading-notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": " + bsBook + ", \"noteText\": \"note-intruder\", \"userId\": " + a + "}"))
                .andExpect(status().isBadRequest());

        String listedForA = mockMvc.perform(get("/api/reading-notes").param("userId", String.valueOf(a)))
                .andReturn().getResponse().getContentAsString();
        assertThat(listedForA).contains("note-A-text").doesNotContain("note-B-text").doesNotContain("note-intruder");
        String listedForB = mockMvc.perform(get("/api/reading-notes").param("userId", String.valueOf(b)))
                .andReturn().getResponse().getContentAsString();
        assertThat(listedForB).contains("note-B-text").doesNotContain("note-intruder");
    }

    @Test
    void askingForNotesOfSomeoneElsesBookReturnsNothing() throws Exception {
        long a = saveUser("note-c@example.com");
        long b = saveUser("note-d@example.com");
        long bsBook = createBook("note-Ds-book", b);
        mockMvc.perform(post("/api/reading-notes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\": " + bsBook + ", \"noteText\": \"note-D-private\", \"userId\": " + b + "}"));

        mockMvc.perform(get("/api/reading-notes").param("userId", String.valueOf(a)).param("bookId", String.valueOf(bsBook)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aGoodNoteMarksOnlyTheAuthorsReadingRoutineDone() throws Exception {
        long a = saveUser("note-e@example.com");
        long b = saveUser("note-f@example.com");
        for (long user : new long[] {a, b}) {
            mockMvc.perform(post("/api/routine-items").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"reading-" + user + "\", \"source\": \"READER_NOTES\", \"userId\": " + user + "}"))
                    .andExpect(status().isCreated());
        }
        long asBook = createBook("note-Es-book", a);

        mockMvc.perform(post("/api/reading-notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": " + asBook + ", \"noteText\": \"in my own words\", "
                                + "\"ownWordsPercentage\": 90, \"userId\": " + a + "}"))
                .andExpect(status().isCreated());

        String today = LocalDate.now().toString();
        mockMvc.perform(get("/api/daily-logs").param("userId", String.valueOf(a)).param("date", today))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId", is((int) a)));
        mockMvc.perform(get("/api/daily-logs").param("userId", String.valueOf(b)).param("date", today))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void currentReadingIsPerUser() throws Exception {
        long a = saveUser("cur-a@example.com");
        long b = saveUser("cur-b@example.com");
        long newcomer = saveUser("cur-c@example.com");
        mockMvc.perform(post("/api/current-reading").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"cur-A-title\", \"userId\": " + a + "}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/current-reading").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"cur-B-title\", \"userId\": " + b + "}")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/current-reading").param("userId", String.valueOf(a)))
                .andExpect(jsonPath("$.title", is("cur-A-title")));
        mockMvc.perform(get("/api/current-reading").param("userId", String.valueOf(newcomer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void learningTopicsAreListedOnlyThroughTheirGoalsOwner() throws Exception {
        long a = saveUser("topic-a@example.com");
        long b = saveUser("topic-b@example.com");
        long asGoal = createGoal("topic-A-goal", a);
        long bsGoal = createGoal("topic-B-goal", b);
        mockMvc.perform(post("/api/learning-topics").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"topic-A-topic\", \"goalId\": " + asGoal + ", \"userId\": " + a + "}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/learning-topics").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"topic-B-topic\", \"goalId\": " + bsGoal + ", \"userId\": " + b + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/learning-topics").param("userId", String.valueOf(a)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("topic-A-topic")));
    }

    @Test
    void aTopicCannotBeAddedUnderSomeoneElsesGoal() throws Exception {
        long a = saveUser("topic-c@example.com");
        long b = saveUser("topic-d@example.com");
        long bsGoal = createGoal("topic-D-goal", b);

        mockMvc.perform(post("/api/learning-topics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"topic-intruder\", \"goalId\": " + bsGoal + ", \"userId\": " + a + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void omittingUserIdShowsOnlyTopicsOfUnownedGoals() throws Exception {
        long a = saveUser("topic-e@example.com");
        long asGoal = createGoal("topic-E-goal", a);
        long unownedGoal = createGoal("topic-unowned-goal", null);
        mockMvc.perform(post("/api/learning-topics").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"topic-owned-by-E\", \"goalId\": " + asGoal + ", \"userId\": " + a + "}"));
        mockMvc.perform(post("/api/learning-topics").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"topic-unowned\", \"goalId\": " + unownedGoal + "}"));

        String body = mockMvc.perform(get("/api/learning-topics")).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("topic-unowned").doesNotContain("topic-owned-by-E");
    }
}
