package com.financetracker;

import com.financetracker.auth.OwnerBootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;

class OwnerBootstrapTest {
    static class RecordingJdbc extends JdbcTemplate {
        String sql;
        Object[] values;
        @Override public int update(String statement, Object... arguments) {
            sql = statement; values = arguments; return 1;
        }
    }
    @Test void normalizesAndHashesWithoutOverwritingExistingOwner() {
        var jdbc = new RecordingJdbc();
        var encoder = new BCryptPasswordEncoder(4);
        new OwnerBootstrap(jdbc, encoder, " Owner@Example.invalid ", "a-long-local-test-password").run(null);
        assertThat(jdbc.sql).contains("ON CONFLICT DO NOTHING");
        assertThat(jdbc.values[1]).isEqualTo("owner@example.invalid");
        assertThat(encoder.matches("a-long-local-test-password", (String) jdbc.values[2])).isTrue();
    }
    @Test void rejectsShortPasswordBeforeDatabaseAccess() {
        var jdbc = new RecordingJdbc();
        assertThatThrownBy(() -> new OwnerBootstrap(jdbc, new BCryptPasswordEncoder(4),
                "owner@example.invalid", "short").run(null)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.sql).isNull();
    }
    @Test void rejectsPasswordAboveBcryptByteLimit() {
        var jdbc = new RecordingJdbc();
        assertThatThrownBy(() -> new OwnerBootstrap(jdbc, new BCryptPasswordEncoder(4),
                "owner@example.invalid", "€".repeat(25)).run(null)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.sql).isNull();
    }
    @Test void rejectsInvalidEmail() {
        var jdbc = new RecordingJdbc();
        assertThatThrownBy(() -> new OwnerBootstrap(jdbc, new BCryptPasswordEncoder(4),
                "owner example.invalid", "a-long-local-test-password").run(null)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.sql).isNull();
    }
}
