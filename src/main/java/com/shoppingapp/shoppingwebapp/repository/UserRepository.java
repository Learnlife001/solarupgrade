package com.shoppingapp.shoppingwebapp.repository;

import com.shoppingapp.shoppingwebapp.model.Role;
import com.shoppingapp.shoppingwebapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Looked up by the hash, never by the token: the token exists only in the
     * customer's email, and the column holds what it hashes to.
     */
    Optional<User> findByResetTokenHash(String resetTokenHash);

    /** Used by AdminBootstrap to take the role back off anyone no longer listed. */
    List<User> findByRole(Role role);
}
