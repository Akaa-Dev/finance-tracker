package com.financetracker.auth;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OwnerRepository extends JpaRepository<Owner, UUID> {
    Optional<Owner> findByEmail(String email);
}
