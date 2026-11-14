package com.fhir.examapp.security;

import com.fhir.examapp.domain.Certification;
import com.fhir.examapp.domain.Role;
import com.fhir.examapp.domain.User;
import com.fhir.examapp.domain.UserCertification;
import com.fhir.examapp.repository.CertificationRepository;
import com.fhir.examapp.repository.UserCertificationRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Component
public class CertificationContextHelper {

    public static final String CERTIFICATION_HEADER = "X-Certification";
    public static final String CERTIFICATION_PARAM = "certificationId";

    private final SecurityContextHelper securityContextHelper;
    private final CertificationRepository certificationRepository;
    private final UserCertificationRepository userCertificationRepository;

    public CertificationContextHelper(SecurityContextHelper securityContextHelper,
                                      CertificationRepository certificationRepository,
                                      UserCertificationRepository userCertificationRepository) {
        this.securityContextHelper = securityContextHelper;
        this.certificationRepository = certificationRepository;
        this.userCertificationRepository = userCertificationRepository;
    }

    public Certification resolveActiveCertification() {
        return resolveActiveCertification((Long) null);
    }

    public Certification resolveActiveCertification(Long explicitCertId) {
        User currentUser = securityContextHelper.getCurrentUser();
        Long certId = explicitCertId;

        if (certId == null) {
            certId = resolveCertIdFromRequest();
        }

        Certification cert = null;
        if (certId != null) {
            final Long finalId = certId;
            cert = certificationRepository.findById(finalId).orElse(null);
            if (cert == null && userCertificationRepository != null) {
                cert = userCertificationRepository.findById(finalId)
                        .map(UserCertification::getCertification)
                        .orElse(null);
            }
            if (cert == null && explicitCertId != null) {
                throw new IllegalArgumentException("Certificación no encontrada: " + finalId);
            }
        }
        if (cert == null) {
            String certCode = resolveCertCodeFromRequest();
            if (certCode != null && !certCode.isBlank()) {
                cert = certificationRepository.findByCodeIgnoreCase(certCode).orElse(null);
            }
            if (cert == null && currentUser != null) {
                List<UserCertification> userCerts = userCertificationRepository.findByUserIdAndActiveTrue(currentUser.getId());
                if (!userCerts.isEmpty()) {
                    cert = userCerts.get(0).getCertification();
                } else if (currentUser.getRole() == Role.ADMIN) {
                    cert = certificationRepository.findById(1L)
                            .orElseGet(() -> certificationRepository.findAll().stream().findFirst().orElse(null));
                } else {
                    Certification defCert = certificationRepository.findByActiveTrueOrderBySortOrderAsc()
                            .stream().findFirst().orElseGet(() -> certificationRepository.findById(1L).orElse(null));
                    if (defCert != null) {
                        try {
                            UserCertification autoUc = new UserCertification(currentUser, defCert, "AUTO_ASSIGN");
                            userCertificationRepository.save(autoUc);
                        } catch (Exception ignored) {}
                        cert = defCert;
                    } else {
                        throw new AccessDeniedException("El usuario no tiene ninguna certificación asignada activa.");
                    }
                }
            }
        }

        if (cert == null) {
            throw new AccessDeniedException("No se ha podido resolver ninguna certificación activa.");
        }

        validateAccess(cert, currentUser);
        return cert;
    }

    public Certification resolveActiveCertification(String certHeader, String certParam, User user) {
        User currentUser = (user != null) ? user : securityContextHelper.getCurrentUser();
        Long certId = null;

        if (certHeader != null && !certHeader.isBlank()) {
            try {
                certId = Long.parseLong(certHeader.trim());
            } catch (NumberFormatException ignored) {}
        }
        if (certId == null && certParam != null && !certParam.isBlank()) {
            try {
                certId = Long.parseLong(certParam.trim());
            } catch (NumberFormatException ignored) {}
        }

        if (certId != null) {
            final Long finalId = certId;
            Certification cert = certificationRepository.findById(finalId).orElse(null);
            if (cert == null && userCertificationRepository != null) {
                cert = userCertificationRepository.findById(finalId)
                        .map(UserCertification::getCertification)
                        .orElse(null);
            }
            if (cert != null) {
                validateAccess(cert, currentUser);
                return cert;
            }
        }

        if (certHeader != null && !certHeader.isBlank()) {
            Certification c = certificationRepository.findByCodeIgnoreCase(certHeader.trim()).orElse(null);
            if (c != null) {
                validateAccess(c, currentUser);
                return c;
            }
        }

        return resolveActiveCertification((Long) null);
    }

    public Long resolveActiveCertificationId() {
        Certification cert = resolveActiveCertification();
        return cert != null ? cert.getId() : 1L;
    }

    private void validateAccess(Certification cert, User currentUser) {
        if (currentUser == null) return;
        if (currentUser.getRole() != Role.ADMIN) {
            if (!cert.isActive()) {
                throw new AccessDeniedException("La certificación seleccionada está desactivada.");
            }
            boolean isAssigned = userCertificationRepository.existsByUserIdAndCertificationIdAndActiveTrue(
                    currentUser.getId(), cert.getId());
            if (!isAssigned) {
                // Auto-asignación de conveniencia si el usuario no tiene ninguna fila en user_certifications
                List<UserCertification> allUserCerts = userCertificationRepository.findByUserId(currentUser.getId());
                if (allUserCerts.isEmpty()) {
                    Certification defCert = certificationRepository.findByActiveTrueOrderBySortOrderAsc()
                            .stream().findFirst().orElseGet(() -> certificationRepository.findById(1L).orElse(null));
                    if (defCert != null && defCert.getId().equals(cert.getId())) {
                        try {
                            UserCertification autoUc = new UserCertification(currentUser, defCert, "AUTO_ASSIGN");
                            userCertificationRepository.save(autoUc);
                            isAssigned = true;
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (!isAssigned) {
                throw new AccessDeniedException("No tiene permiso para acceder a la certificación: " + cert.getName());
            }
        }
    }

    private Long resolveCertIdFromRequest() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest req = sra.getRequest();
                String headerVal = req.getHeader(CERTIFICATION_HEADER);
                if (headerVal != null && !headerVal.isBlank()) {
                    try {
                        return Long.parseLong(headerVal.trim());
                    } catch (NumberFormatException ignored) {}
                }
                String paramVal = req.getParameter(CERTIFICATION_PARAM);
                if (paramVal != null && !paramVal.isBlank()) {
                    try {
                        return Long.parseLong(paramVal.trim());
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String resolveCertCodeFromRequest() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest req = sra.getRequest();
                String headerVal = req.getHeader(CERTIFICATION_HEADER);
                if (headerVal != null && !headerVal.isBlank()) {
                    return headerVal.trim();
                }
                String codeParam = req.getParameter("certificationCode");
                if (codeParam != null && !codeParam.isBlank()) {
                    return codeParam.trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
