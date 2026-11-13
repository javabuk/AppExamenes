package com.fhir.examapp.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fhir.examapp.domain.Question;
import com.fhir.examapp.domain.Role;
import com.fhir.examapp.domain.StudyMaterial;
import com.fhir.examapp.domain.User;
import com.fhir.examapp.repository.QuestionRepository;
import com.fhir.examapp.repository.StudyMaterialRepository;
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
class DataOwnershipAndIdorIntegrationTest {

    private static final Path tempDbFile;

    static {
        try {
            tempDbFile = Files.createTempFile("fhir_test_idor_", ".db");
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
    private QuestionRepository questionRepository;

    @Autowired
    private StudyMaterialRepository studyMaterialRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User userA;
    private User userB;

    @BeforeEach
    void setUpUsers() {
        userA = userRepository.findByUsernameIgnoreCase("alice").orElseGet(() -> {
            User u = new User("alice", "alice@fhir.local", "Alice User",
                    passwordEncoder.encode("AlicePassword123!"), Role.USER);
            u.setActive(true);
            u.setMustChangePassword(false);
            return userRepository.save(u);
        });

        userB = userRepository.findByUsernameIgnoreCase("bob").orElseGet(() -> {
            User u = new User("bob", "bob@fhir.local", "Bob User",
                    passwordEncoder.encode("BobPassword123!"), Role.USER);
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
    void testExamAttemptsAndDashboardAreIsolatedPerUserAndPreventIdor() throws Exception {
        MockHttpSession sessionA = loginAs("alice", "AlicePassword123!");
        Cookie xsrfA = fetchCsrfCookie(sessionA);

        MockHttpSession sessionB = loginAs("bob", "BobPassword123!");
        Cookie xsrfB = fetchCsrfCookie(sessionB);

        MockHttpSession sessionAdmin = loginAs("admin", "AdminSecretPass123!");

        // 1. User A starts an exam
        MvcResult startRes = mockMvc.perform(post("/api/exams/start")
                        .session(sessionA)
                        .cookie(xsrfA)
                        .header("X-XSRF-TOKEN", xsrfA.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TUTOR\",\"questionCount\":2}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode sessionNode = objectMapper.readTree(startRes.getResponse().getContentAsString());
        long attemptId = sessionNode.get("attemptId").asLong();
        long firstQuestionId = sessionNode.get("questions").get(0).get("id").asLong();

        // 2. User B tries to submit User A's attempt -> 403 Forbidden (Anti-IDOR)
        String submitPayload = """
                {
                  "attemptId": %d,
                  "answers": [
                    {
                      "questionId": %d,
                      "selectedOptions": "A",
                      "timeSpentSeconds": 12,
                      "flagged": false
                    }
                  ]
                }
                """.formatted(attemptId, firstQuestionId);

        mockMvc.perform(post("/api/exams/submit")
                        .session(sessionB)
                        .cookie(xsrfB)
                        .header("X-XSRF-TOKEN", xsrfB.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitPayload))
                .andExpect(status().isForbidden());

        // 3. User A submits their own attempt -> 200 OK
        mockMvc.perform(post("/api/exams/submit")
                        .session(sessionA)
                        .cookie(xsrfA)
                        .header("X-XSRF-TOKEN", xsrfA.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitPayload))
                .andExpect(status().isOk());

        // 4. User B tries to view User A's attempt review -> 403 Forbidden (Anti-IDOR)
        mockMvc.perform(get("/api/exams/" + attemptId)
                        .session(sessionB))
                .andExpect(status().isForbidden());

        // 5. User A and Admin can view User A's attempt review -> 200 OK
        mockMvc.perform(get("/api/exams/" + attemptId)
                        .session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(attemptId));

        mockMvc.perform(get("/api/exams/" + attemptId)
                        .session(sessionAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(attemptId));

        // 6. User B's recent attempts and dashboard do NOT include User A's attempt
        mockMvc.perform(get("/api/exams/recent")
                        .session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/api/exams/recent")
                        .session(sessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/dashboard")
                        .session(sessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readinessScore").value(0.0))
                .andExpect(jsonPath("$.recentAttempts", hasSize(0)));
    }

    @Test
    void testStudyCardCommentsAndSrsArePrivatePerUser() throws Exception {
        MockHttpSession sessionA = loginAs("alice", "AlicePassword123!");
        Cookie xsrfA = fetchCsrfCookie(sessionA);

        MockHttpSession sessionB = loginAs("bob", "BobPassword123!");
        Cookie xsrfB = fetchCsrfCookie(sessionB);

        // Find any existing material
        StudyMaterial material = studyMaterialRepository.findAllActive().get(0);
        long materialId = material.getId();

        // 1. User A adds a card comment
        MvcResult commentRes = mockMvc.perform(post("/api/materials/" + materialId + "/comments")
                        .session(sessionA)
                        .cookie(xsrfA)
                        .header("X-XSRF-TOKEN", xsrfA.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardIndex\":1,\"cardHeading\":\"Ficha 1\",\"contentMarkdown\":\"Nota privada de Alice\"}"))
                .andExpect(status().isOk())
                .andReturn();

        long commentId = objectMapper.readTree(commentRes.getResponse().getContentAsString()).get("id").asLong();

        // 2. User A sees their comment; User B sees 0 comments
        mockMvc.perform(get("/api/materials/" + materialId + "/comments")
                        .session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].contentMarkdown").value("Nota privada de Alice"));

        mockMvc.perform(get("/api/materials/" + materialId + "/comments")
                        .session(sessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // 3. User B tries to edit or delete User A's comment -> 403 Forbidden
        mockMvc.perform(put("/api/materials/" + materialId + "/comments/" + commentId)
                        .session(sessionB)
                        .cookie(xsrfB)
                        .header("X-XSRF-TOKEN", xsrfB.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentMarkdown\":\"Intento de modificación por Bob\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/materials/" + materialId + "/comments/" + commentId)
                        .session(sessionB)
                        .cookie(xsrfB)
                        .header("X-XSRF-TOKEN", xsrfB.getValue()))
                .andExpect(status().isForbidden());

        // 4. User A can update and delete their own comment -> 200 OK
        mockMvc.perform(put("/api/materials/" + materialId + "/comments/" + commentId)
                        .session(sessionA)
                        .cookie(xsrfA)
                        .header("X-XSRF-TOKEN", xsrfA.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentMarkdown\":\"Nota actualizada por Alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentMarkdown").value("Nota actualizada por Alice"));

        mockMvc.perform(delete("/api/materials/" + materialId + "/comments/" + commentId)
                        .session(sessionA)
                        .cookie(xsrfA)
                        .header("X-XSRF-TOKEN", xsrfA.getValue()))
                .andExpect(status().isOk());

        // 5. SRS state is isolated per user
        long questionId = questionRepository.findByVerifiedTrue().get(0).getId();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/srs/review/" + questionId)
                            .session(sessionA)
                            .cookie(xsrfA)
                            .header("X-XSRF-TOKEN", xsrfA.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rating\":5}"))
                    .andExpect(status().isOk());
        }

        // User A has 1 mastered question in SRS, while User B still has 0
        mockMvc.perform(get("/api/dashboard")
                        .session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masteredQuestionsCount").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/dashboard")
                        .session(sessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masteredQuestionsCount").value(0));
    }

    @Test
    void testSharedQuestionsAndMaterialsOwnershipAndSoftDelete() throws Exception {
        MockHttpSession sessionA = loginAs("alice", "AlicePassword123!");
        Cookie xsrfA = fetchCsrfCookie(sessionA);

        MockHttpSession sessionB = loginAs("bob", "BobPassword123!");
        Cookie xsrfB = fetchCsrfCookie(sessionB);

        MockHttpSession sessionAdmin = loginAs("admin", "AdminSecretPass123!");
        Cookie xsrfAdmin = fetchCsrfCookie(sessionAdmin);

        // 1. User A creates a question
        String createQuestionPayload = """
                {
                  "topicId": 1,
                  "stem": "Pregunta creada por Alice sobre Patient",
                  "explanation": "Explicación de Alice",
                  "difficulty": 2,
                  "questionType": "SINGLE_CHOICE",
                  "options": [
                    {"optionLabel": "A", "optionText": "Opción A", "correct": true, "distractorReason": "Correcta"},
                    {"optionLabel": "B", "optionText": "Opción B", "correct": false, "distractorReason": "Incorrecta"}
                  ]
                }
                """;

        MvcResult qRes = mockMvc.perform(post("/api/questions")
                        .session(sessionA)
                        .cookie(xsrfA)
                        .header("X-XSRF-TOKEN", xsrfA.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createQuestionPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(userA.getId()))
                .andReturn();

        long questionId = objectMapper.readTree(qRes.getResponse().getContentAsString()).get("id").asLong();

        // 2. User B tries to edit or delete User A's question -> 403 Forbidden
        mockMvc.perform(put("/api/questions/" + questionId)
                        .session(sessionB)
                        .cookie(xsrfB)
                        .header("X-XSRF-TOKEN", xsrfB.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createQuestionPayload.replace("Pregunta creada por Alice", "Modificada por Bob")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/questions/" + questionId)
                        .session(sessionB)
                        .cookie(xsrfB)
                        .header("X-XSRF-TOKEN", xsrfB.getValue()))
                .andExpect(status().isForbidden());

        // 3. User A can edit their own question -> 200 OK
        mockMvc.perform(put("/api/questions/" + questionId)
                        .session(sessionA)
                        .cookie(xsrfA)
                        .header("X-XSRF-TOKEN", xsrfA.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createQuestionPayload.replace("Pregunta creada por Alice", "Pregunta editada por Alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stem").value("Pregunta editada por Alice sobre Patient"));

        // 4. Admin can delete User A's question -> 200 OK (and it is soft-deleted in DB)
        mockMvc.perform(delete("/api/questions/" + questionId)
                        .session(sessionAdmin)
                        .cookie(xsrfAdmin)
                        .header("X-XSRF-TOKEN", xsrfAdmin.getValue()))
                .andExpect(status().isOk());

        Question deletedQuestion = questionRepository.findById(questionId).orElseThrow();
        assertTrue(Boolean.TRUE.equals(deletedQuestion.getDeleted()), "Question must be soft-deleted to preserve historical integrity");

        // 5. User A creates a study material
        MvcResult matRes = mockMvc.perform(post("/api/materials/markdown/text")
                        .session(sessionA)
                        .cookie(xsrfA)
                        .header("X-XSRF-TOKEN", xsrfA.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":1,\"title\":\"Apunte de Alice\",\"content\":\"### Ficha 1\\nContenido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(userA.getId()))
                .andReturn();

        long materialId = objectMapper.readTree(matRes.getResponse().getContentAsString()).get("id").asLong();

        // 6. User B tries to edit or delete User A's material -> 403 Forbidden
        mockMvc.perform(put("/api/materials/" + materialId)
                        .session(sessionB)
                        .cookie(xsrfB)
                        .header("X-XSRF-TOKEN", xsrfB.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hackeado por Bob\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/materials/" + materialId)
                        .session(sessionB)
                        .cookie(xsrfB)
                        .header("X-XSRF-TOKEN", xsrfB.getValue()))
                .andExpect(status().isForbidden());

        // 7. User A can edit and soft-delete their own material -> 200 OK
        mockMvc.perform(put("/api/materials/" + materialId)
                        .session(sessionA)
                        .cookie(xsrfA)
                        .header("X-XSRF-TOKEN", xsrfA.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Apunte editado por Alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Apunte editado por Alice"));

        mockMvc.perform(delete("/api/materials/" + materialId)
                        .session(sessionA)
                        .cookie(xsrfA)
                        .header("X-XSRF-TOKEN", xsrfA.getValue()))
                .andExpect(status().isOk());

        StudyMaterial deletedMaterial = studyMaterialRepository.findById(materialId).orElseThrow();
        assertTrue(Boolean.TRUE.equals(deletedMaterial.getDeleted()), "StudyMaterial must be soft-deleted to preserve historical integrity");
    }
}
