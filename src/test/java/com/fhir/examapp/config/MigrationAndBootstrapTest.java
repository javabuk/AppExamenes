package com.fhir.examapp.config;

import com.fhir.examapp.domain.AuditLog;
import com.fhir.examapp.domain.Role;
import com.fhir.examapp.domain.User;
import com.fhir.examapp.repository.AuditLogRepository;
import com.fhir.examapp.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MigrationAndBootstrapTest {

    private static final Path TEST_DB_PATH;

    static {
        try {
            TEST_DB_PATH = Files.createTempFile("fhir_test_bootstrap_", ".db");
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
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testBootstrapAdminCreatedAndLegacySeedAssigned() {
        User admin = userRepository.findByUsernameIgnoreCase("admin").orElse(null);
        assertNotNull(admin, "El usuario admin inicial debe existir tras el arranque");
        assertEquals(Role.ADMIN, admin.getRole());
        assertTrue(admin.getActive());
        assertTrue(admin.getMustChangePassword(), "Sin APP_ADMIN_PASSWORD definida, mustChangePassword debe ser true");
        assertNotEquals(AdminBootstrapInitializer.BOOTSTRAP_PENDING_HASH, admin.getPasswordHash());
        assertTrue(admin.getPasswordHash().startsWith("$2a$") || admin.getPasswordHash().startsWith("$2b$"));

        // Verificar que las preguntas y materiales iniciales tienen owner_id = admin.id
        Integer orphanQuestions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM questions WHERE owner_id IS NULL", Integer.class);
        assertEquals(0, orphanQuestions, "Ninguna pregunta debe quedar sin propietario");

        Integer orphanMaterials = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM study_materials WHERE owner_id IS NULL", Integer.class);
        assertEquals(0, orphanMaterials, "Ningún material debe quedar sin propietario");

        Integer srsAssignedToAdmin = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_srs_state WHERE user_id = ?", Integer.class, admin.getId());
        assertTrue(srsAssignedToAdmin != null && srsAssignedToAdmin >= 10,
                "Los estados SRS iniciales deben estar asociados al usuario admin");

        List<AuditLog> logs = auditLogRepository.findTop100ByOrderByIdDesc();
        assertTrue(logs.stream().anyMatch(l -> "ADMIN_BOOTSTRAP_INITIALIZED".equals(l.getAction())),
                "Debe quedar registrado el evento ADMIN_BOOTSTRAP_INITIALIZED en audit_log");
    }

    @Test
    void testFlywayV7MigrationOnCopyOfRealV6Database() throws Exception {
        File realBackup = new File("./data/fhir_exam.v6.bak");
        if (!realBackup.exists()) {
            realBackup = new File("./data/fhir_exam.db");
        }
        if (!realBackup.exists()) {
            return;
        }

        Path copyDb = Files.createTempFile("real_v6_copy_", ".db");
        copyDb.toFile().deleteOnExit();
        Files.copy(realBackup.toPath(), copyDb, StandardCopyOption.REPLACE_EXISTING);

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite:" + copyDb.toAbsolutePath());

        JdbcTemplate copyJdbc = new JdbcTemplate(ds);

        // Contar datos antes de migrar
        Integer questionsBefore = copyJdbc.queryForObject("SELECT COUNT(*) FROM questions", Integer.class);
        Integer materialsBefore = copyJdbc.queryForObject("SELECT COUNT(*) FROM study_materials", Integer.class);
        Integer attemptsBefore = copyJdbc.queryForObject("SELECT COUNT(*) FROM exam_attempts", Integer.class);
        Integer srsBefore = copyJdbc.queryForObject("SELECT COUNT(*) FROM question_srs_state", Integer.class);

        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .validateOnMigrate(false)
                .load();

        flyway.migrate();

        // Contar datos después de migrar a V7
        Integer questionsAfter = copyJdbc.queryForObject("SELECT COUNT(*) FROM questions WHERE owner_id = 1", Integer.class);
        Integer materialsAfter = copyJdbc.queryForObject("SELECT COUNT(*) FROM study_materials WHERE owner_id = 1", Integer.class);
        Integer attemptsAfter = copyJdbc.queryForObject("SELECT COUNT(*) FROM exam_attempts WHERE user_id = 1", Integer.class);
        Integer srsAfter = copyJdbc.queryForObject("SELECT COUNT(*) FROM question_srs_state WHERE user_id = 1", Integer.class);

        assertEquals(questionsBefore, questionsAfter, "Todas las preguntas existentes deben conservarse y asignarse al admin (id=1)");
        assertEquals(materialsBefore, materialsAfter, "Todos los materiales existentes deben conservarse y asignarse al admin (id=1)");
        assertEquals(attemptsBefore, attemptsAfter, "Todos los intentos existentes deben conservarse y asignarse al admin (id=1)");
        assertEquals(srsBefore, srsAfter, "Todo el progreso SRS existente debe conservarse y asignarse al admin (id=1)");
    }
}
