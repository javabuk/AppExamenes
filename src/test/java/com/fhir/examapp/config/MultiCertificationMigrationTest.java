package com.fhir.examapp.config;

import com.fhir.examapp.domain.Certification;
import com.fhir.examapp.domain.User;
import com.fhir.examapp.domain.UserCertification;
import com.fhir.examapp.repository.CertificationAreaRepository;
import com.fhir.examapp.repository.CertificationRepository;
import com.fhir.examapp.repository.UserCertificationRepository;
import com.fhir.examapp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MultiCertificationMigrationTest {

    private static final Path TEST_DB_PATH;

    static {
        try {
            TEST_DB_PATH = Files.createTempFile("fhir_test_multicert_", ".db");
            TEST_DB_PATH.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + TEST_DB_PATH.toAbsolutePath());
        registry.add("app.security.admin-user", () -> "admin");
        registry.add("app.security.admin-password", () -> "");
    }

    @Autowired
    private CertificationRepository certificationRepository;

    @Autowired
    private CertificationAreaRepository certificationAreaRepository;

    @Autowired
    private UserCertificationRepository userCertificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testV8MigrationCreatesCertificationsAndAssignsUsers() {
        // 1. Verificar existencia de certificaciones semilla
        Certification r4 = certificationRepository.findByCodeIgnoreCase("FHIR_R4").orElse(null);
        assertNotNull(r4, "FHIR_R4 debe existir tras la migración V8");
        assertTrue(r4.getActive(), "FHIR_R4 debe estar activa por defecto");
        assertEquals(50, r4.getExamQuestionCount());
        assertEquals(90, r4.getExamDurationMinutes());
        assertEquals(70.0, r4.getPassingScorePercent());

        Certification r5 = certificationRepository.findByCodeIgnoreCase("FHIR_R5").orElse(null);
        assertNotNull(r5, "FHIR_R5 debe existir tras la migración V8");
        assertFalse(r5.getActive(), "FHIR_R5 debe crearse inactiva como plantilla");

        // 2. Verificar áreas migradas
        var r4Areas = certificationAreaRepository.findByCertificationIdOrderBySortOrderAsc(r4.getId());
        assertEquals(6, r4Areas.size(), "FHIR_R4 debe tener las 6 áreas del syllabus original");
        double sumWeight = r4Areas.stream().mapToDouble(a -> a.getWeightPercent() != null ? a.getWeightPercent() : 0.0).sum();
        assertEquals(100.0, sumWeight, 0.01, "La suma de los pesos del syllabus de FHIR_R4 debe ser 100%");

        // 3. Verificar que los usuarios existentes están asignados a FHIR_R4
        List<User> users = userRepository.findAll();
        assertFalse(users.isEmpty());
        for (User u : users) {
            boolean isEnrolledInR4 = userCertificationRepository.existsByUserIdAndCertificationIdAndActiveTrue(u.getId(), r4.getId());
            assertTrue(isEnrolledInR4, "El usuario " + u.getUsername() + " debe tener asignada la certificación FHIR_R4");
        }

        // 4. Verificar que preguntas, materiales e intentos tienen certification_id = 1
        Integer orphanQuestions = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM questions WHERE certification_id IS NULL", Integer.class);
        assertEquals(0, orphanQuestions, "Todas las preguntas deben tener certification_id");

        Integer orphanMaterials = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM study_materials WHERE certification_id IS NULL", Integer.class);
        assertEquals(0, orphanMaterials, "Todos los materiales deben tener certification_id");
    }
}
