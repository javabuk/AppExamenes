package com.fhir.examapp.repository;

import com.fhir.examapp.domain.Role;
import com.fhir.examapp.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) = LOWER(:identifier) OR LOWER(u.email) = LOWER(:identifier)")
    Optional<User> findByUsernameOrEmailIgnoreCase(@Param("identifier") String identifier);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    long countByRoleAndActiveTrue(Role role);

    List<User> findByRoleAndActiveTrue(Role role);

    @Query("SELECT u FROM User u WHERE " +
           "(:search IS NULL OR :search = '' OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) " +
           " OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
           " OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:role IS NULL OR u.role = :role) " +
           "AND (:active IS NULL OR u.active = :active) " +
           "ORDER BY u.id ASC")
    Page<User> searchUsers(@Param("search") String search,
                           @Param("role") Role role,
                           @Param("active") Boolean active,
                           Pageable pageable);
}
