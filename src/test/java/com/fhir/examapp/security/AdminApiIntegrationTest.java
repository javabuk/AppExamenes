package com.fhir.examapp.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fhir.examapp.domain.Role;
import com.fhir.examapp.domain.User;
import com.fhir.examapp.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminApiIntegrationTest {

    private static final Path tempDbFile;

    static {
        try {
            tempDbFile = Files.createTempFile("fhir_test_admin_", ".db");
            tempDbFile.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbPath = tempDbFile.toAbsolutePath().toString().replace("\\", "/");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbPath + "?busy_timeout=5000&journal_mode=WAL&foreign_keys=ON");
        registry.add("app.security.admin-username", () -> "admin");
        registry.add("app.security.admin-password", () -> "AdminSecretPass123!");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User normalUser;

    @BeforeEach
    void setUp() {
        normalUser = userRepository.findByUsernameIgnoreCase("student1").orElseGet(() -> {
            User u = new User("student1", "student1@fhir.local", "Student One",
                    passwordEncoder.encode("StudentPass123!"), Role.USER);
            u.setActive(true);
            u.setMustChangePassword(false);
            return userRepository.save(u);
        });
    }

    private Cookie fetchCsrfCookie(MockHttpSession session) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/auth/me")
                        .session(session != null ? session : new MockHttpSession()))
                .andReturn();
        Cookie xsrf = res.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrf, "XSRF-TOKEN cookie must be set");
        return xsrf;
    }

    private MockHttpSession loginAs(String username, String password) throws Exception {
        MockHttpSession initSession = new MockHttpSession();
        Cookie xsrf = fetchCsrfCookie(initSession);
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .session(initSession)
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) res.getRequest().getSession(false);
    }

    @Test
    void testRegularUserCannotAccessAnyAdminEndpoints() throws Exception {
        MockHttpSession userSession = loginAs("student1", "StudentPass123!");
        Cookie xsrf = fetchCsrfCookie(userSession);

        mockMvc.perform(get("/api/admin/users").session(userSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/users")
                        .session(userSession)
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hacker\",\"email\":\"h@fhir.local\",\"displayName\":\"Hacker\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/results/users").session(userSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/results/aggregate").session(userSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/results/export.csv").session(userSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/audit-logs").session(userSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdminUserLifecycleAndLastAdminProtectionRules() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "AdminSecretPass123!");
        Cookie xsrf = fetchCsrfCookie(adminSession);

        // 1. List users -> never exposes passwordHash
        MvcResult listRes = mockMvc.perform(get("/api/admin/users?search=student1")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].username").value("student1"))
                .andReturn();
        assertFalse(listRes.getResponse().getContentAsString().contains("passwordHash"));

        // 2. Create user without specifying password -> generates temp password & mustChangePassword=true
        MvcResult createRes = mockMvc.perform(post("/api/admin/users")
                        .session(adminSession)
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"clinician2\",\"email\":\"clinician2@fhir.local\",\"displayName\":\"Dr. Clinician\",\"role\":\"USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("clinician2"))
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andExpect(jsonPath("$.temporaryPassword", not(emptyOrNullString())))
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createRes.getResponse().getContentAsString());
        long createdUserId = createdJson.get("user").get("id").asLong();

        // 3. Duplicate username or email -> 400 Bad Request
        mockMvc.perform(post("/api/admin/users")
                        .session(adminSession)
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"clinician2\",\"email\":\"other@fhir.local\",\"displayName\":\"Dup\",\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest());

        // 4. Edit user displayName and email
        mockMvc.perform(put("/api/admin/users/" + createdUserId)
                        .session(adminSession)
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Dr. Clinician Updated\",\"email\":\"clinician2.updated@fhir.local\",\"role\":\"USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Dr. Clinician Updated"))
                .andExpect(jsonPath("$.email").value("clinician2.updated@fhir.local"));

        // 5. Reset password -> generates new temporaryPassword and sets mustChangePassword=true
        mockMvc.perform(post("/api/admin/users/" + createdUserId + "/reset-password")
                        .session(adminSession)
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword", not(emptyOrNullString())))
                .andExpect(jsonPath("$.user.mustChangePassword").value(true));

        // 6. Deactivate user -> user cannot login
        mockMvc.perform(put("/api/admin/users/" + createdUserId + "/status")
                        .session(adminSession)
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // 7. Last admin rules: Admin cannot deactivate themselves or remove their own ADMIN role when last active admin
        User adminUser = userRepository.findByUsernameIgnoreCase("admin").orElseThrow();

        mockMvc.perform(put("/api/admin/users/" + adminUser.getId() + "/status")
                        .session(adminSession)
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("administrador")));

        mockMvc.perform(put("/api/admin/users/" + adminUser.getId())
                        .session(adminSession)
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("administrador")));
    }

    @Test
    void testAdminResultsTraceabilityAggregateAndCsvExport() throws Exception {
        // 1. Student1 completes an exam so there is real attempt & answer data
        MockHttpSession studentSession = loginAs("student1", "StudentPass123!");
        Cookie studentXsrf = fetchCsrfCookie(studentSession);

        MvcResult startRes = mockMvc.perform(post("/api/exams/start")
                        .session(studentSession)
                        .cookie(studentXsrf)
                        .header("X-XSRF-TOKEN", studentXsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TUTOR\",\"questionCount\":2}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode startNode = objectMapper.readTree(startRes.getResponse().getContentAsString());
        long attemptId = startNode.get("attemptId").asLong();
        long qId = startNode.get("questions").get(0).get("id").asLong();

        String submitPayload = """
                {
                  "attemptId": %d,
                  "answers": [
                    {
                      "questionId": %d,
                      "selectedOptions": "A",
                      "timeSpentSeconds": 15,
                      "flagged": false
                    }
                  ]
                }
                """.formatted(attemptId, qId);

        mockMvc.perform(post("/api/exams/submit")
                        .session(studentSession)
                        .cookie(studentXsrf)
                        .header("X-XSRF-TOKEN", studentXsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitPayload))
                .andExpect(status().isOk());

        // 2. Admin queries KPIs, user detail, attempt review, aggregate results, CSV export, and audit logs
        MockHttpSession adminSession = loginAs("admin", "AdminSecretPass123!");

        mockMvc.perform(get("/api/admin/results/users").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));

        mockMvc.perform(get("/api/admin/results/users/" + normalUser.getId()).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("student1"))
                .andExpect(jsonPath("$.kpi.totalExams").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.dashboard.topicHeatmaps", hasSize(6)))
                .andExpect(jsonPath("$.attempts", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/api/admin/results/users/" + normalUser.getId() + "/attempts/" + attemptId)
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(attemptId));

        mockMvc.perform(get("/api/admin/results/aggregate").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCompletedExams").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.topicAverages", hasSize(6)))
                .andExpect(jsonPath("$.userRanking", hasSize(greaterThanOrEqualTo(2))));

        MvcResult csvRes = mockMvc.perform(get("/api/admin/results/export.csv?userId=" + normalUser.getId())
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andReturn();

        String csvContent = csvRes.getResponse().getContentAsString();
        assertTrue(csvContent.contains("AttemptId,UserId,Username,DisplayName,Email,Mode"));
        assertTrue(csvContent.contains("student1"));

        mockMvc.perform(get("/api/admin/audit-logs").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));
    }
}
