package com.fhir.examapp.config;

import com.fhir.examapp.domain.Role;
import com.fhir.examapp.domain.User;
import com.fhir.examapp.repository.UserRepository;
import com.fhir.examapp.service.AuditService;
import com.fhir.examapp.service.StudyMaterialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

@Component
public class AdminBootstrapInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapInitializer.class);
    public static final String BOOTSTRAP_PENDING_HASH = "!BOOTSTRAP_PENDING!";
    private static final String RANDOM_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final StudyMaterialService studyMaterialService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.security.admin-user:}")
    private String configuredAdminUser;

    @Value("${app.security.admin-password:}")
    private String configuredAdminPassword;

    public AdminBootstrapInitializer(UserRepository userRepository,
                                     PasswordEncoder passwordEncoder,
                                     AuditService auditService,
                                     StudyMaterialService studyMaterialService,
                                     JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.studyMaterialService = studyMaterialService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User admin = ensureInitialAdmin();
        studyMaterialService.ensureDefaultStudyCardsDeck();
        reassignOrphanLegacyData(admin);
    }

    public User ensureInitialAdmin() {
        List<User> activeAdmins = userRepository.findByRoleAndActiveTrue(Role.ADMIN);

        // Si ya existe al menos un admin activo cuya contraseña ya fue inicializada (no es el marcador de migración)
        Optional<User> initializedAdmin = activeAdmins.stream()
                .filter(u -> u.getPasswordHash() != null && !BOOTSTRAP_PENDING_HASH.equals(u.getPasswordHash()))
                .findFirst();

        if (initializedAdmin.isPresent()) {
            return initializedAdmin.get();
        }

        // Buscar el registro placeholder creado por V7 (o cualquier admin pendiente de bootstrap)
        User targetAdmin = activeAdmins.stream().findFirst()
                .orElseGet(() -> userRepository.findById(1L).orElse(null));

        String desiredUsername = (configuredAdminUser != null && !configuredAdminUser.trim().isEmpty())
                ? configuredAdminUser.trim()
                : "admin";

        boolean passwordFromConfig = (configuredAdminPassword != null && !configuredAdminPassword.trim().isEmpty());
        String rawPassword;
        boolean mustChange;

        if (passwordFromConfig) {
            rawPassword = configuredAdminPassword.trim();
            if (rawPassword.length() < 10) {
                log.warn("APP_ADMIN_PASSWORD tiene menos de 10 caracteres. Se recomienda usar al menos 10 caracteres.");
            }
            mustChange = false;
        } else {
            rawPassword = generateRandomPassword(16);
            mustChange = true;
        }

        String encodedPassword = passwordEncoder.encode(rawPassword);

        if (targetAdmin == null) {
            targetAdmin = new User(
                    desiredUsername,
                    desiredUsername.contains("@") ? desiredUsername : (desiredUsername + "@fhir.local"),
                    "Administrador Principal",
                    encodedPassword,
                    Role.ADMIN
            );
            targetAdmin.setCreatedBy("SYSTEM_BOOTSTRAP");
        } else {
            // Si el username deseado no colisiona con otro usuario, actualizarlo
            if (!targetAdmin.getUsername().equalsIgnoreCase(desiredUsername)
                    && !userRepository.existsByUsernameIgnoreCase(desiredUsername)) {
                targetAdmin.setUsername(desiredUsername);
            }
            targetAdmin.setPasswordHash(encodedPassword);
            targetAdmin.setRole(Role.ADMIN);
            targetAdmin.setActive(true);
            targetAdmin.setUpdatedAt(User.formatNow());
        }

        targetAdmin.setMustChangePassword(mustChange);
        targetAdmin.setFailedLoginAttempts(0);
        targetAdmin.setLockedUntil(null);
        targetAdmin = userRepository.save(targetAdmin);

        if (!passwordFromConfig) {
            log.warn("\n" +
                    "================================================================================\n" +
                    " [BOOTSTRAP DE SEGURIDAD - FHIR CERT MASTER]\n" +
                    " Se ha inicializado el usuario ADMINISTRADOR por defecto.\n" +
                    " * Usuario:              {}\n" +
                    " * Contraseña temporal:  {}\n" +
                    " IMPORTANTE: Esta contraseña se muestra UNA SOLA VEZ.\n" +
                    " Deberá cambiarla obligatoriamente en el primer inicio de sesión.\n" +
                    "================================================================================",
                    targetAdmin.getUsername(), rawPassword);
        } else {
            log.info("Usuario administrador '{}' inicializado a partir de configuración externa (APP_ADMIN_USER/APP_ADMIN_PASSWORD).",
                    targetAdmin.getUsername());
        }

        auditService.logAction(
                targetAdmin.getId(),
                "SYSTEM_BOOTSTRAP",
                "ADMIN_BOOTSTRAP_INITIALIZED",
                "USER",
                String.valueOf(targetAdmin.getId()),
                "Usuario administrador inicializado (" + (passwordFromConfig ? "credenciales configuradas" : "contraseña aleatoria temporal") + ")",
                "127.0.0.1"
        );

        return targetAdmin;
    }

    private void reassignOrphanLegacyData(User admin) {
        if (admin == null || admin.getId() == null) return;
        Long adminId = admin.getId();
        jdbcTemplate.update("UPDATE exam_attempts SET user_id = ? WHERE user_id IS NULL", adminId);
        jdbcTemplate.update("UPDATE study_card_comments SET user_id = ? WHERE user_id IS NULL", adminId);
        jdbcTemplate.update("UPDATE questions SET owner_id = ?, created_at = COALESCE(created_at, datetime('now')) WHERE owner_id IS NULL", adminId);
        jdbcTemplate.update("UPDATE study_materials SET owner_id = ?, created_at = COALESCE(created_at, datetime('now')) WHERE owner_id IS NULL", adminId);
    }

    private String generateRandomPassword(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM_CHARS.charAt(random.nextInt(RANDOM_CHARS.length())));
        }
        return sb.toString();
    }
}
