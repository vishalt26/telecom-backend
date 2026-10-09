package com.telecom.service.repo;

import com.telecom.service.domain.Role;
import com.telecom.service.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByRole(Role role);

    Optional<User> findByIdAndRole(Long id, Role role);

    @Query("""
            select u from User u
            where u.role = :role
              and (:q = '' or lower(u.name) like lower(concat('%', :q, '%'))
                   or lower(u.email) like lower(concat('%', :q, '%'))
                   or u.phone like concat('%', :q, '%'))
            """)
    Page<User> search(@Param("role") Role role, @Param("q") String q, Pageable pageable);
}
