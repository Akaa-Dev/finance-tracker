package com.financetracker.auth;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Locale;
import java.util.UUID;
@Component
public class OwnerBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String email, password;
    public OwnerBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder,
            @Value("${app.owner-email}") String email, @Value("${app.owner-password}") String password) {
        this.jdbc = jdbc; this.encoder = encoder; this.email = email; this.password = password;
    }
    public void run(ApplicationArguments args) {
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || normalized.length() > 254
                || password.length() < 16 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Configure a valid OWNER_EMAIL and OWNER_PASSWORD of 16–72 UTF-8 bytes.");
        }
        // Unique singleton constraint and ON CONFLICT make simultaneous bootstraps safe.
        jdbc.update("INSERT INTO app_users (id,email,password_hash,base_currency,created_at,updated_at,singleton) "
                + "VALUES (?, ?, ?, 'EUR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE) ON CONFLICT DO NOTHING",
                UUID.randomUUID(), normalized, encoder.encode(password));
    }
}
