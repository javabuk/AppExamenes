package com.fhir.examapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fhir.examapp.domain.Role;
import com.fhir.examapp.domain.User;
import com.fhir.examapp.dto.ChangePasswordRequest;
import com.fhir.examapp.dto.LoginRequest;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthSecurityIntegrationTest {

    private static final Path TEST_DB_PATH;

    static {
        try {
            TEST_DB_PATH = Files.createTempFile("fhir_test_auth_sec_", ".db");
            TEST_DB_PATH.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + TEST_DB_PATH.toAbsolutePath());
        registry.add("app.security.max-failed-attempts", () -> "3");
        registry.add("app.security.lock-duration-minutes", () -> "15");
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
        normalUser = userRepository.findByUsernameIgnoreCase("testuser").orElseGet(() -> {
            User u = new User("testuser", "testuser@fhir.local", "Usuario Test",
                    passwordEncoder.encode("ValidPass123!"), Role.USER);
            u.setCreatedBy("TEST");
            return u;
        });
        normalUser.setPasswordHash(passwordEncoder.encode("ValidPass123!"));
        normalUser.setActive(true);
        normalUser.setMustChangePassword(false);
        normalUser.setFailedLoginAttempts(0);
        normalUser.setLockedUntil(null);
        normalUser = userRepository.save(normalUser);
    }

    @Test
    void testAnonymousAccessToApiReturns401AndStaticAssetsArePublic() throws Exception {
        // Endpoints de API protegidos -> 401
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/questions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/materials"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        // Estáticos públicos -> 200
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk());
    }

    @Test
    void testLoginSuccessAndMeAndLogout() throws Exception {
        LoginRequest loginReq = new LoginRequest("testuser", "ValidPass123!");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);
        Cookie xsrfCookie = loginResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrfCookie);

        // /api/auth/me con la sesión activa
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"));

        // /api/dashboard con sesión activa
        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk());

        // Logout sin CSRF falla con 403
        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isForbidden());

        // Logout con cookie XSRF-TOKEN + cabecera X-XSRF-TOKEN tiene éxito e invalida la sesión
        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .cookie(xsrfCookie)
                        .header("X-XSRF-TOKEN", xsrfCookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Tras logout, la sesión ya no está autenticada
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testBruteForceLockoutAfterMaxFailedAttempts() throws Exception {
        LoginRequest badReq = new LoginRequest("testuser", "WrongPassword!");

        // Intento 1 fallido -> 401
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Credenciales inválidas"));

        // Intento 2 fallido -> 401
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isUnauthorized());

        // Intento 3 fallido (alcanza max-failed-attempts=3) -> 423 Locked
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.locked").value(true));

        // Incluso con la contraseña correcta ahora está bloqueado -> 423 Locked
        LoginRequest goodReq = new LoginRequest("testuser", "ValidPass123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goodReq)))
                .andExpect(status().isLocked());
    }

    @Test
    void testMustChangePasswordBlocksApiUntilChangedAndValidatesPolicy() throws Exception {
        normalUser.setMustChangePassword(true);
        userRepository.save(normalUser);

        LoginRequest loginReq = new LoginRequest("testuser", "ValidPass123!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);
        Cookie xsrfCookie = loginResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrfCookie);

        // Intentar acceder a /api/dashboard cuando mustChangePassword=true -> 403
        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        // Intentar cambiar a contraseña corta (<10 chars) -> 400 Bad Request
        ChangePasswordRequest shortPass = new ChangePasswordRequest("ValidPass123!", "short123");
        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .cookie(xsrfCookie)
                        .header("X-XSRF-TOKEN", xsrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortPass)))
                .andExpect(status().isBadRequest());

        // Cambiar a contraseña válida (>=10 chars) -> 200 OK y limpia mustChangePassword
        ChangePasswordRequest validChange = new ChangePasswordRequest("ValidPass123!", "NewSecurePass456!");
        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .cookie(xsrfCookie)
                        .header("X-XSRF-TOKEN", xsrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChange)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false));

        // Ahora /api/dashboard responde 200 OK
        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void testDeactivatedUserCannotLoginNorKeepActiveSession() throws Exception {
        LoginRequest loginReq = new LoginRequest("testuser", "ValidPass123!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);

        // Desactivar usuario en BBDD mientras tiene sesión activa
        normalUser.setActive(false);
        userRepository.save(normalUser);

        // La sesión activa es rechazada e invalidada inmediatamente -> 401
        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isUnauthorized());

        // Y tampoco puede volver a iniciar sesión -> 401 con mensaje genérico
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Credenciales inválidas"));
    }

    @Test
    void testCsrfCookieAndHeaderMechanism() throws Exception {
        // 1. Login devuelve sesión
        LoginRequest loginReq = new LoginRequest("testuser", "ValidPass123!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        Cookie xsrfCookie = loginResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrfCookie, "La cookie XSRF-TOKEN debe emitirse en la respuesta");
        assertFalse(xsrfCookie.isHttpOnly(), "La cookie XSRF-TOKEN debe ser legible por el JS de la SPA");

        // 2. Petición mutante enviando cookie XSRF-TOKEN y cabecera X-XSRF-TOKEN funciona
        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .cookie(xsrfCookie)
                        .header("X-XSRF-TOKEN", xsrfCookie.getValue()))
                .andExpect(status().isOk());
    }
}
