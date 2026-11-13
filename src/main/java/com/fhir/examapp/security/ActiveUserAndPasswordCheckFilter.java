package com.fhir.examapp.security;

import com.fhir.examapp.domain.User;
import com.fhir.examapp.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

public class ActiveUserAndPasswordCheckFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public ActiveUserAndPasswordCheckFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            User dbUser = resolveDbUser(auth);

            if (dbUser != null) {
                // 1. Verificar que el usuario sigue activo y no bloqueado en BBDD
                if (!Boolean.TRUE.equals(dbUser.getActive()) || dbUser.isCurrentlyLocked()) {
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        session.invalidate();
                    }
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"La cuenta ha sido desactivada o bloqueada temporalmente.\",\"status\":401}");
                    return;
                }

                // 2. Si mustChangePassword es true, solo permitir endpoints de cambio de contraseña, /me y /logout
                if (Boolean.TRUE.equals(dbUser.getMustChangePassword())) {
                    boolean allowed = path.equals("/api/auth/change-password")
                            || path.equals("/api/auth/me")
                            || path.equals("/api/auth/logout")
                            || path.equals("/api/auth/login");
                    if (!allowed) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"error\":\"Debe cambiar su contraseña temporal antes de continuar.\",\"mustChangePassword\":true,\"status\":403}");
                        return;
                    }
                }
            } else if (auth.getPrincipal() instanceof CustomUserDetails) {
                // El usuario fue eliminado o no existe en BBDD
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Sesión inválida.\",\"status\":401}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private User resolveDbUser(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails customDetails && customDetails.getUserId() != null) {
            return userRepository.findById(customDetails.getUserId()).orElse(null);
        }
        if (principal instanceof UserDetails userDetails) {
            Optional<User> u = userRepository.findByUsernameOrEmailIgnoreCase(userDetails.getUsername());
            return u.orElse(null);
        }
        return null;
    }
}
