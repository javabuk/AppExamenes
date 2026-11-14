package com.fhir.examapp.service;

import com.fhir.examapp.domain.Certification;
import com.fhir.examapp.domain.Role;
import com.fhir.examapp.domain.User;
import com.fhir.examapp.domain.UserCertification;
import com.fhir.examapp.dto.AuthUserDTO;
import com.fhir.examapp.dto.ChangePasswordRequest;
import com.fhir.examapp.dto.LoginRequest;
import com.fhir.examapp.dto.UserCertificationSummaryDTO;
import com.fhir.examapp.repository.CertificationRepository;
import com.fhir.examapp.repository.UserCertificationRepository;
import com.fhir.examapp.repository.UserRepository;
import com.fhir.examapp.security.CertificationContextHelper;
import com.fhir.examapp.security.CustomUserDetails;
import com.fhir.examapp.security.SecurityContextHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecurityContextHelper securityContextHelper;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private UserCertificationRepository userCertificationRepository;
    private CertificationRepository certificationRepository;
    private CertificationContextHelper certificationContextHelper;

    @Autowired
    public void setCertificationComponents(@Autowired(required = false) UserCertificationRepository userCertificationRepository,
                                          @Autowired(required = false) CertificationRepository certificationRepository,
                                          @Autowired(required = false) CertificationContextHelper certificationContextHelper) {
        this.userCertificationRepository = userCertificationRepository;
        this.certificationRepository = certificationRepository;
        this.certificationContextHelper = certificationContextHelper;
    }

    @Value("${app.security.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.security.lock-duration-minutes:15}")
    private int lockDurationMinutes;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService,
                       SecurityContextHelper securityContextHelper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.securityContextHelper = securityContextHelper;
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, LockedException.class})
    public AuthUserDTO login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String identifier = request.getUsername() != null ? request.getUsername().trim() : "";
        String rawPassword = request.getPassword() != null ? request.getPassword() : "";
        String clientIp = extractClientIp(httpRequest);

        User user = userRepository.findByUsernameOrEmailIgnoreCase(identifier).orElse(null);

        if (user == null) {
            auditService.logAction(null, identifier.isEmpty() ? "UNKNOWN" : identifier,
                    "LOGIN_FAILED", "USER", null, "Intento de inicio de sesión fallido (usuario no encontrado)", clientIp);
            throw new BadCredentialsException("Credenciales inválidas");
        }

        // Comprobar si la cuenta está temporalmente bloqueada por fuerza bruta
        if (user.isCurrentlyLocked()) {
            auditService.logAction(user.getId(), user.getUsername(),
                    "LOGIN_BLOCKED_LOCKED", "USER", String.valueOf(user.getId()),
                    "Intento de login rechazado: cuenta bloqueada temporalmente hasta " + user.getLockedUntil(), clientIp);
            throw new LockedException("La cuenta se encuentra bloqueada temporalmente por demasiados intentos fallidos. Inténtelo más tarde.");
        }

        // Comprobar si el usuario está desactivado (baja lógica)
        if (!Boolean.TRUE.equals(user.getActive())) {
            auditService.logAction(user.getId(), user.getUsername(),
                    "LOGIN_FAILED_INACTIVE", "USER", String.valueOf(user.getId()),
                    "Intento de login sobre cuenta desactivada", clientIp);
            throw new BadCredentialsException("Credenciales inválidas");
        }

        // Verificar contraseña con BCrypt
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            int attempts = (user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0) + 1;
            user.setFailedLoginAttempts(attempts);

            if (attempts >= maxFailedAttempts) {
                LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(lockDurationMinutes);
                user.setLockedUntilDateTime(lockUntil);
                userRepository.save(user);

                auditService.logAction(user.getId(), user.getUsername(),
                        "USER_LOCKED_BRUTE_FORCE", "USER", String.valueOf(user.getId()),
                        "Cuenta bloqueada temporalmente tras " + attempts + " intentos fallidos hasta " + user.getLockedUntil(), clientIp);
                throw new LockedException("La cuenta se ha bloqueado temporalmente tras múltiples intentos fallidos.");
            } else {
                userRepository.save(user);
                auditService.logAction(user.getId(), user.getUsername(),
                        "LOGIN_FAILED", "USER", String.valueOf(user.getId()),
                        "Contraseña incorrecta (intento fallido " + attempts + "/" + maxFailedAttempts + ")", clientIp);
                throw new BadCredentialsException("Credenciales inválidas");
            }
        }

        // Login exitoso: limpiar contadores de fallo y actualizar fecha de último acceso
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginDateTime(LocalDateTime.now());
        user = userRepository.save(user);

        // Prevenir fijación de sesión invalidando sesión previa si existía
        HttpSession existingSession = httpRequest.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        httpRequest.getSession(true);

        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authToken);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        auditService.logAction(user.getId(), user.getUsername(),
                "LOGIN_SUCCESS", "USER", String.valueOf(user.getId()),
                "Inicio de sesión exitoso", clientIp);

        return enrichAuthUserDto(AuthUserDTO.fromEntity(user), user);
    }

    @Transactional
    public void logout(HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        try {
            User currentUser = securityContextHelper.getCurrentUser();
            if (currentUser != null && currentUser.getId() != null) {
                auditService.logAction(currentUser.getId(), currentUser.getUsername(),
                        "LOGOUT", "USER", String.valueOf(currentUser.getId()),
                        "Cierre de sesión", clientIp);
            }
        } catch (Exception ignored) {}

        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @Transactional(readOnly = true)
    public AuthUserDTO getCurrentUserDto() {
        User user = securityContextHelper.getCurrentUser();
        return enrichAuthUserDto(AuthUserDTO.fromEntity(user), user);
    }

    @Transactional
    public AuthUserDTO changePassword(ChangePasswordRequest request, HttpServletRequest httpRequest) {
        User user = securityContextHelper.getCurrentUser();
        String clientIp = extractClientIp(httpRequest);

        if (request.getCurrentPassword() == null || !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            auditService.logAction(user.getId(), user.getUsername(),
                    "PASSWORD_CHANGE_FAILED", "USER", String.valueOf(user.getId()),
                    "Intento fallido de cambio de contraseña: contraseña actual incorrecta", clientIp);
            throw new IllegalArgumentException("La contraseña actual no es correcta");
        }

        String newPass = request.getNewPassword() != null ? request.getNewPassword().trim() : "";
        if (newPass.length() < 10) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 10 caracteres");
        }

        if (passwordEncoder.matches(newPass, user.getPasswordHash())) {
            throw new IllegalArgumentException("La nueva contraseña debe ser diferente de la contraseña actual");
        }

        user.setPasswordHash(passwordEncoder.encode(newPass));
        user.setMustChangePassword(false);
        user.setUpdatedAt(User.formatNow());
        user = userRepository.save(user);

        // Actualizar el principal en la sesión actual
        CustomUserDetails updatedDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(
                updatedDetails, null, updatedDetails.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(newAuth);
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
        }

        auditService.logAction(user.getId(), user.getUsername(),
                "PASSWORD_CHANGED", "USER", String.valueOf(user.getId()),
                "Contraseña actualizada correctamente por el usuario", clientIp);

        return enrichAuthUserDto(AuthUserDTO.fromEntity(user), user);
    }

    private AuthUserDTO enrichAuthUserDto(AuthUserDTO dto, User user) {
        if (dto == null || user == null) return dto;
        if (userCertificationRepository != null) {
            java.util.List<UserCertification> userCerts = userCertificationRepository.findByUserIdAndActiveTrue(user.getId());
            java.util.List<UserCertificationSummaryDTO> certSummaries = new java.util.ArrayList<>();
            for (UserCertification uc : userCerts) {
                UserCertificationSummaryDTO s = UserCertificationSummaryDTO.from(uc);
                if (s != null) certSummaries.add(s);
            }

            // Si es ADMIN, asegurar que puede acceder a todas las certificaciones activas
            if (user.getRole() == Role.ADMIN && certificationRepository != null) {
                java.util.List<Certification> allActive = certificationRepository.findByActiveTrueOrderBySortOrderAsc();
                for (Certification c : allActive) {
                    boolean alreadyPresent = certSummaries.stream().anyMatch(s -> s.getCertificationId().equals(c.getId()));
                    if (!alreadyPresent) {
                        certSummaries.add(UserCertificationSummaryDTO.from(c));
                    }
                }
            }

            dto.setCertifications(certSummaries);
            if (!certSummaries.isEmpty()) {
                UserCertificationSummaryDTO activeCert = certSummaries.get(0);
                if (certificationContextHelper != null) {
                    try {
                        Certification resolved = certificationContextHelper.resolveActiveCertification();
                        if (resolved != null) {
                            for (UserCertificationSummaryDTO s : certSummaries) {
                                if (s.getCertificationId().equals(resolved.getId())) {
                                    activeCert = s;
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
                dto.setActiveCertificationId(activeCert.getCertificationId());
                dto.setActiveCertificationCode(activeCert.getCode());
                dto.setActiveCertificationName(activeCert.getName());
            }
        }
        return dto;
    }

    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
