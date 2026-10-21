package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import static com.routinemachine.core.TestTokens.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Per-user scoping, part 6: the end-to-end proof. Two accounts each create data on every personal
// route; then every route is read as each account (it must show its own and nothing of the other's),
// and as nobody (it must show neither), and every way one account could touch the other's rows is
// tried. The per-route tests already cover each rule; this one fails if any route is ever added
// or changed without respecting them, because it asks the whole API the same question.
//
// What this does not prove: the user id is still claimed by the caller, not proven by a token
// (the access-control stage), so anyone who can reach the backend can still claim any id.
@SpringBootTest
@AutoConfigureMockMvc
class CrossUserIsolationApiIntegrationTest extends AbstractIntegrationTest {

    private static final String H = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    private long a;
    private long b;
    private final long[] goal = new long[2];
    private final long[] routine = new long[2];
    private final long[] template = new long[2];
    private final long[] task = new long[2];
    private final long[] book = new long[2];
    private final long[] pause = new long[2];
    private final long[] evidence = new long[2];
    private final long[] cohort = new long[2];

    private long idOf(MvcResult result) throws Exception {
        return Long.parseLong(result.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private MvcResult send(RequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(request.toString()).isEqualTo(expectedStatus);
        return result;
    }

    private long create(String path, long user, String json) throws Exception {
        return idOf(send(post(path).with(asUser(user)).contentType(H).content(json), 201));
    }

    private static final java.util.Set<String> TOKEN_ROUTES = java.util.Set.of(
            "/api/goals", "/api/routine-items", "/api/learning-topics", "/api/schedule-templates",
            "/api/tasks", "/api/daily-logs");

    private String json(String path, String... params) throws Exception {
        var request = get(path);
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return send(request, 200).getResponse().getContentAsString();
    }

    // A signed-in read: the token says who is asking.
    private String asToken(String path, long userId) throws Exception {
        return send(get(path).with(asUser(userId)), 200).getResponse().getContentAsString();
    }

    private String seed(int who) {
        return who == 0 ? "iso-A" : "iso-B";
    }

    @BeforeEach
    void bothAccountsCreateDataOnEveryPersonalRoute() throws Exception {
        a = userRepository.save(new User("iso-a-" + System.nanoTime() + "@example.com", "irrelevant-hash", null, null)).getId();
        b = userRepository.save(new User("iso-b-" + System.nanoTime() + "@example.com", "irrelevant-hash", null, null)).getId();
        long[] users = {a, b};

        for (int i = 0; i < 2; i++) {
            String s = seed(i) + "-" + System.nanoTime();
            long u = users[i];
            goal[i] = create("/api/goals", u, "{\"name\":\"" + s + "-goal\"}");
            routine[i] = create("/api/routine-items", u, "{\"name\":\"" + s + "-routine\"}");
            create("/api/learning-topics", u, "{\"name\":\"" + s + "-topic\",\"goalId\":" + goal[i] + "}");
            template[i] = create("/api/schedule-templates", u, "{\"dayOfWeek\":\"MONDAY\",\"targetType\":\"LEARNING_SLOT\","
                    + "\"targetDurationMinutes\":30,\"sortOrder\":1,\"label\":\"" + s + "-template\"}");
            task[i] = create("/api/tasks", u, "{\"taskDate\":\"2033-03-0" + (i + 1) + "\",\"label\":\"" + s + "-task\"}");
            create("/api/daily-logs", u, "{\"logDate\":\"2033-03-0" + (i + 1) + "\",\"routineItemId\":" + routine[i] + "}");
            book[i] = create("/api/books", u, "{\"title\":\"" + s + "-book\",\"userId\":" + u + "}");
            create("/api/reading-notes", u, "{\"bookId\":" + book[i] + ",\"noteText\":\"" + s + "-note\",\"userId\":" + u + "}");
            create("/api/current-reading", u, "{\"title\":\"" + s + "-reading\",\"userId\":" + u + "}");
            send(put("/api/granary/saving-target-amount").with(asUser(u)).contentType(H)
                    .content("{\"amount\":" + (i == 0 ? 111 : 222) + "}"), 200);
            send(post("/api/granary/savings").with(asUser(u)), 200);
            pause[i] = idOf(send(post("/api/granary/pauses").with(asUser(u)).contentType(H)
                    .content("{\"what\":\"" + s + "-pause\"}"), 200));
            evidence[i] = create("/api/evidence-events", u, "{\"userId\":" + u + ",\"goalId\":" + goal[i] + ",\"source\":\"MANUAL\","
                    + "\"sourceEventId\":\"" + s + "-evt\",\"occurredAt\":\"2033-03-02T12:00:00Z\"}");
            long org = idOf(send(post("/api/organisations").contentType(H)
                    .content("{\"name\":\"" + s + "-org\",\"foundingAdminUserId\":" + u + "}"), 200));
            cohort[i] = idOf(send(post("/api/organisations/" + org + "/cohorts").contentType(H)
                    .content("{\"requestingUserId\":" + u + ",\"name\":\"" + s + "-cohort\",\"startsOn\":\"2033-03-01\",\"weeks\":4}"), 200));
        }
    }

    // ---- every list route, read as A, as B and as nobody ------------------------------------

    private void assertEachSeesOnlyTheirOwn(String path, String marker, String... extraParams) throws Exception {
        boolean tokenRoute = TOKEN_ROUTES.contains(path);
        String[] withUser = new String[extraParams.length + 2];
        System.arraycopy(extraParams, 0, withUser, 0, extraParams.length);
        withUser[extraParams.length] = "userId";
        for (int i = 0; i < 2; i++) {
            withUser[extraParams.length + 1] = String.valueOf(i == 0 ? a : b);
            String body = tokenRoute ? asToken(path, i == 0 ? a : b) : json(path, withUser);
            String mine = seed(i), theirs = seed(1 - i);
            assertThat(body).as("%s as %s should show its own", path, mine).contains(mine).contains(marker);
            assertThat(body).as("%s as %s must not show %s's", path, mine, theirs).doesNotContain(theirs + "-");
        }
        if (tokenRoute) {
            send(get(path), 401);
            return;
        }
        String asNobody = json(path, extraParams);
        assertThat(asNobody).as("%s with no userId must show neither account", path)
                .doesNotContain("iso-A-").doesNotContain("iso-B-");
    }

    @Test
    void goalsAreIsolated() throws Exception {
        assertEachSeesOnlyTheirOwn("/api/goals", "-goal");
    }

    @Test
    void routineItemsAreIsolated() throws Exception {
        assertEachSeesOnlyTheirOwn("/api/routine-items", "-routine");
    }

    @Test
    void learningTopicsAreIsolated() throws Exception {
        assertEachSeesOnlyTheirOwn("/api/learning-topics", "-topic");
    }

    @Test
    void scheduleTemplatesAreIsolated() throws Exception {
        assertEachSeesOnlyTheirOwn("/api/schedule-templates", "-template");
    }

    @Test
    void tasksAreIsolated() throws Exception {
        assertEachSeesOnlyTheirOwn("/api/tasks", "-task");
    }

    @Test
    void booksAreIsolated() throws Exception {
        assertEachSeesOnlyTheirOwn("/api/books", "-book");
    }

    @Test
    void readingNotesAreIsolated() throws Exception {
        assertEachSeesOnlyTheirOwn("/api/reading-notes", "-note");
    }

    @Test
    void dailyLogsAreIsolated() throws Exception {
        for (int i = 0; i < 2; i++) {
            long me = i == 0 ? a : b;
            String body = asToken("/api/daily-logs", me);
            assertThat(body).contains("\"routineItemId\":" + routine[i]).doesNotContain("\"routineItemId\":" + routine[1 - i]);
        }
        send(get("/api/daily-logs"), 401);
    }

    @Test
    void currentBookAndCurrentReadingAreIsolated() throws Exception {
        for (int i = 0; i < 2; i++) {
            long me = i == 0 ? a : b;
            assertThat(json("/api/books/current", "userId", String.valueOf(me))).contains(seed(i)).doesNotContain(seed(1 - i) + "-");
            assertThat(json("/api/current-reading", "userId", String.valueOf(me))).contains(seed(i)).doesNotContain(seed(1 - i) + "-");
        }
    }

    @Test
    void theGranaryIsIsolated() throws Exception {
        String summaryA = asToken("/api/granary/summary", a);
        String summaryB = asToken("/api/granary/summary", b);
        assertThat(summaryA).contains("\"savingTargetAmount\":111").doesNotContain("222");
        assertThat(summaryB).contains("\"savingTargetAmount\":222").doesNotContain("111");
        assertThat(asToken("/api/granary/pauses", a)).contains("iso-A-").doesNotContain("iso-B-");
        assertThat(asToken("/api/granary/pauses", b)).contains("iso-B-").doesNotContain("iso-A-");
    }

    @Test
    void theCreditLedgerIsIsolated() throws Exception {
        for (int i = 0; i < 2; i++) {
            long me = i == 0 ? a : b;
            String body = json("/api/evidence-events", "userId", String.valueOf(me),
                    "seasonStart", "2033-02-28", "seasonEnd", "2033-03-06");
            assertThat(body).contains(seed(i)).doesNotContain(seed(1 - i) + "-");
        }
    }

    // ---- every way one account could touch the other's rows ---------------------------------

    @Test
    void anAccountCannotChangeAnotherAccountsTask() throws Exception {
        send(patch("/api/tasks/" + task[0]).with(asUser(b)).contentType(H).content("{\"status\":\"DONE\"}"), 404);
        send(patch("/api/tasks/" + task[0]).with(asUser(a)).contentType(H).content("{\"status\":\"DONE\"}"), 200);
    }

    @Test
    void anAccountCannotDecideOrReflectOnAnotherAccountsPause() throws Exception {
        send(patch("/api/granary/pauses/" + pause[0] + "/decide").with(asUser(b))
                .contentType(H).content("{\"outcome\":\"DECLINED\"}"), 404);
        send(patch("/api/granary/pauses/" + pause[0] + "/reflect").with(asUser(b))
                .contentType(H).content("{\"reflection\":\"not yours\"}"), 404);
        send(patch("/api/granary/pauses/" + pause[0] + "/decide").with(asUser(a))
                .contentType(H).content("{\"outcome\":\"DECLINED\"}"), 200);
    }

    @Test
    void anAccountCannotQuestionAnotherAccountsCredit() throws Exception {
        send(patch("/api/evidence-events/" + evidence[0] + "/question").param("userId", String.valueOf(b)), 404);
        send(patch("/api/evidence-events/" + evidence[0] + "/question").param("userId", String.valueOf(a)), 200);
    }

    @Test
    void anAccountCannotLinkItsRowsToAnotherAccountsRows() throws Exception {
        // B tries to build on A's goal, routine item, template, book.
        send(post("/api/learning-topics").with(asUser(b)).contentType(H).content("{\"name\":\"x\",\"goalId\":" + goal[0] + "}"), 400);
        send(post("/api/routine-items").with(asUser(b)).contentType(H).content("{\"name\":\"x\",\"goalId\":" + goal[0] + "}"), 400);
        send(post("/api/daily-logs").with(asUser(b)).contentType(H).content("{\"logDate\":\"2033-04-04\",\"routineItemId\":" + routine[0] + "}"), 400);
        send(post("/api/tasks").with(asUser(b)).contentType(H).content("{\"taskDate\":\"2033-04-04\",\"scheduleTemplateId\":" + template[0] + "}"), 400);
        send(post("/api/tasks").with(asUser(b)).contentType(H).content("{\"taskDate\":\"2033-04-04\",\"label\":\"x\",\"routineItemId\":" + routine[0] + "}"), 400);
        send(post("/api/reading-notes").contentType(H).content("{\"bookId\":" + book[0] + ",\"noteText\":\"x\",\"userId\":" + b + "}"), 400);
        send(post("/api/evidence-events").contentType(H).content("{\"userId\":" + b + ",\"goalId\":" + goal[0] + ",\"source\":\"MANUAL\","
                + "\"sourceEventId\":\"intruder\",\"occurredAt\":\"2033-03-02T12:00:00Z\"}"), 400);
    }

    @Test
    void anAccountCannotReadOrRunAnotherAccountsCohort() throws Exception {
        String other = String.valueOf(b);
        send(get("/api/organisations/cohorts/" + cohort[0] + "/dashboard").param("requestingUserId", other), 403);
        send(get("/api/organisations/cohorts/" + cohort[0] + "/dashboard/by-group").param("requestingUserId", other), 403);
        send(get("/api/organisations/cohorts/" + cohort[0] + "/dashboard/weekly-check-ins").param("requestingUserId", other), 403);
        send(get("/api/organisations/cohorts/" + cohort[0] + "/members").param("requestingUserId", other), 403);
        send(post("/api/organisations/cohorts/" + cohort[0] + "/bulk-invite").contentType(H)
                .content("{\"requestingUserId\":" + b + ",\"csv\":\"x@example.com\"}"), 403);
        send(post("/api/organisations/cohorts/" + cohort[0] + "/survey-response").contentType(H)
                .content("{\"requestingUserId\":" + b + ",\"answer\":\"SUPPORTIVE\"}"), 403);
        send(get("/api/organisations/cohorts/" + cohort[0] + "/dashboard").param("requestingUserId", String.valueOf(a)), 200);
    }

    @Test
    void theWateringHolesOfTheDeploymentAreNotConnectedForAnAccountThatIsNotTheOwner() throws Exception {
        assertThat(json("/api/watering-holes", "userId", String.valueOf(a))).doesNotContain("\"enabled\":true");
    }
}
