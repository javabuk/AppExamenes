package com.fhir.examapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fhir.examapp.dto.FhirSearchValidationRequest;
import com.fhir.examapp.domain.Certification;
import com.fhir.examapp.domain.CertificationArea;
import com.fhir.examapp.domain.Role;
import com.fhir.examapp.domain.User;
import com.fhir.examapp.domain.UserCertification;
import com.fhir.examapp.repository.CertificationAreaRepository;
import com.fhir.examapp.repository.CertificationRepository;
import com.fhir.examapp.repository.UserCertificationRepository;
import com.fhir.examapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class CertificationIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CertificationRepository certificationRepository;

    @Autowired
    private CertificationAreaRepository areaRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCertificationRepository userCertRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Certification r4Cert;
    private Certification r5Cert;
    private User r4User;
    private User adminUser;

    @BeforeEach
    void setUp() {
        r4Cert = certificationRepository.findByCodeIgnoreCase("FHIR_R4").orElseGet(() -> {
            Certification c = new Certification();
            c.setCode("FHIR_R4");
            c.setName("HL7 FHIR R4 Proficiency");
            c.setStandardVersion("R4");
            c.setActive(true);
            c.setSpecBaseUrl("https://hl7.org/fhir/R4/");
            c.setFeatureFlags("EXAM,FLASHCARDS,MATERIALS,REST_SEARCH_BUILDER,RESOURCE_VALIDATOR");
            return certificationRepository.save(c);
        });

        r5Cert = certificationRepository.findByCodeIgnoreCase("FHIR_R5").orElseGet(() -> {
            Certification c = new Certification();
            c.setCode("FHIR_R5");
            c.setName("HL7 FHIR R5 Proficiency");
            c.setStandardVersion("R5");
            c.setActive(true);
            c.setSpecBaseUrl("https://hl7.org/fhir/R5/");
            return certificationRepository.save(c);
        });
        r5Cert.setFeatureFlags("EXAM,FLASHCARDS,MATERIALS"); // sin REST_SEARCH_BUILDER
        r5Cert = certificationRepository.save(r5Cert);

        r4User = userRepository.findByUsernameIgnoreCase("test_r4_student").orElseGet(() -> {
            User u = new User();
            u.setUsername("test_r4_student");
            u.setEmail("r4student@test.local");
            u.setDisplayName("R4 Student");
            u.setPasswordHash(passwordEncoder.encode("Password123!"));
            u.setRole(Role.USER);
            u.setActive(true);
            u.setMustChangePassword(false);
            return userRepository.save(u);
        });
        r4User.setMustChangePassword(false);
        r4User = userRepository.save(r4User);

        // Asignar a r4User sólo FHIR_R4
        userCertRepository.deleteAll(userCertRepository.findByUserId(r4User.getId()));
        UserCertification uc = new UserCertification();
        uc.setUser(r4User);
        uc.setCertification(r4Cert);
        userCertRepository.save(uc);

        adminUser = userRepository.findByUsernameIgnoreCase("admin").orElseGet(() -> {
            User u = new User();
            u.setUsername("admin");
            u.setEmail("admin@test.local");
            u.setDisplayName("Administrator");
            u.setPasswordHash(passwordEncoder.encode("AdminPass123!"));
            u.setRole(Role.ADMIN);
            u.setActive(true);
            return userRepository.save(u);
        });
    }

    @Test
    @DisplayName("User solo asignado a R4 no puede acceder con header X-Certification a R5")
    @WithMockUser(username = "test_r4_student", roles = {"USER"})
    void userCannotAccessUnassignedCertification() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .header("X-Certification", String.valueOf(r5Cert.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("User asignado a R4 puede acceder a su dashboard con header X-Certification R4")
    @WithMockUser(username = "test_r4_student", roles = {"USER"})
    void userCanAccessAssignedCertification() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .header("X-Certification", String.valueOf(r4Cert.getId())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Admin puede acceder a cualquier certificación aunque no la tenga asignada explícitamente")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminCanAccessAnyCertification() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .header("X-Certification", String.valueOf(r5Cert.getId())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Herramienta técnica denegada si la certificación no tiene el feature flag habilitado")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void technicalToolDeniedWhenFeatureFlagMissing() throws Exception {
        FhirSearchValidationRequest req = new FhirSearchValidationRequest();
        req.setQueryUrl("GET [base]/Patient?name=Smith");

        // R5 no tiene REST_SEARCH_BUILDER habilitado en sus feature flags
        mockMvc.perform(post("/api/fhir/validate-search")
                        .with(csrf())
                        .header("X-Certification", String.valueOf(r5Cert.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin puede duplicar certificación con copia de áreas y estado inactivo")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminCanDuplicateCertification() throws Exception {
        certificationRepository.findByCodeIgnoreCase("FHIR_R4_COPY").ifPresent(c -> {
            areaRepository.deleteAll(areaRepository.findByCertificationIdOrderBySortOrderAsc(c.getId()));
            certificationRepository.delete(c);
        });

        mockMvc.perform(post("/api/admin/certifications/" + r4Cert.getId() + "/duplicate")
                        .with(csrf())
                        .param("newCode", "FHIR_R4_COPY")
                        .param("newName", "HL7 FHIR R4 Clon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FHIR_R4_COPY"))
                .andExpect(jsonPath("$.active").value(false));

        Certification copy = certificationRepository.findByCodeIgnoreCase("FHIR_R4_COPY").orElse(null);
        assertThat(copy).isNotNull();
        List<CertificationArea> areas = areaRepository.findByCertificationIdOrderBySortOrderAsc(copy.getId());
        assertThat(areas).isNotEmpty();
    }

    @Test
    @DisplayName("Certificación sin preguntas devuelve banco vacío y no filtra preguntas de R4")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void emptyCertificationDoesNotLeakR4QuestionsOrStartExam() throws Exception {
        Certification emptyCert = certificationRepository.findByCodeIgnoreCase("FHIR_EMPTY").orElseGet(() -> {
            Certification c = new Certification();
            c.setCode("FHIR_EMPTY");
            c.setName("Certificación Vacía");
            c.setStandardVersion("R5");
            c.setActive(true);
            c.setFeatureFlags("EXAM,FLASHCARDS");
            return certificationRepository.save(c);
        });

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certifications[?(@.code == 'FHIR_R5')].id").value(org.hamcrest.Matchers.contains(r5Cert.getId().intValue())))
                .andExpect(jsonPath("$.certifications[?(@.code == 'FHIR_R5')].certificationId").value(org.hamcrest.Matchers.contains(r5Cert.getId().intValue())));

        mockMvc.perform(get("/api/questions")
                        .header("X-Certification", String.valueOf(emptyCert.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/questions")
                        .param("topicId", "1")
                        .header("X-Certification", String.valueOf(emptyCert.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/exams/start")
                        .with(csrf())
                        .header("X-Certification", String.valueOf(emptyCert.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"PRACTICE\",\"questionCount\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Header con ID de user_certifications o ID obsoleto resuelve de forma segura sin 500")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void headerWithStaleIdResolvesGracefully() throws Exception {
        // Enviar un ID que no existe como certificacion (ej: 99999 o 32)
        mockMvc.perform(get("/api/certifications/areas")
                        .header("X-Certification", "99999"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard")
                        .header("X-Certification", "99999"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Crear pregunta en certificación R5 con areaId no produce error de tema no encontrado")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void createQuestionInR5WithAreaIdSucceeds() throws Exception {
        CertificationArea r5Area = areaRepository.findByCertificationIdOrderBySortOrderAsc(r5Cert.getId())
                .stream().findFirst().orElseGet(() -> {
                    CertificationArea a = new CertificationArea();
                    a.setCertification(r5Cert);
                    a.setCode("FOUNDATIONS_R5");
                    a.setName("Fundamentos R5");
                    a.setWeightPercent(20.0);
                    a.setSortOrder(1);
                    a.setActive(true);
                    return areaRepository.save(a);
                });

        String payload = """
                {
                    "topicId": %d,
                    "areaId": %d,
                    "certificationId": %d,
                    "stem": "¿Pregunta de prueba en FHIR R5?",
                    "explanation": "Explicación FHIR R5",
                    "difficulty": 2,
                    "questionType": "SINGLE_CHOICE",
                    "source": "MANUAL",
                    "options": [
                        {"optionLabel": "A", "optionText": "Opción A correcta", "correct": true},
                        {"optionLabel": "B", "optionText": "Opción B incorrecta", "correct": false}
                    ]
                }
                """.formatted(r5Area.getId(), r5Area.getId(), r5Cert.getId());

        mockMvc.perform(post("/api/questions")
                        .with(csrf())
                        .header("X-Certification", String.valueOf(r5Cert.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.area.id").value(r5Area.getId().intValue()));
    }
}
