package com.financetracker.auth;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "app_users")
public class Owner {
    @Id private UUID id;
    private String email;
    @Column(name="password_hash") private String passwordHash;
    @Column(name="base_currency") private String baseCurrency;
    @Column(name="created_at") private Instant createdAt;
    @Column(name="updated_at") private Instant updatedAt;
    private boolean singleton;
    protected Owner() {}
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
}
