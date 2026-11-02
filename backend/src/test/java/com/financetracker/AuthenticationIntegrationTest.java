package com.financetracker;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "app.owner-email=owner@example.invalid", "app.owner-password=integration-only-password-12345"
})
class AuthenticationIntegrationTest {
    // Deliberately fail rather than silently skip when Docker is unavailable.
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
    static { postgres.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
    HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> post(String path, String body, boolean csrf) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
            .header("Content-Type", "application/x-www-form-urlencoded");
        if (csrf) {
            var token = get("/api/v1/auth/csrf").body();
            request.header(JsonPath.read(token, "$.headerName"), JsonPath.read(token, "$.token"));
        }
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void migrationAndSessionLifecycle() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from app_users", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select password_hash from app_users", String.class)).startsWith("$2");
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/installments").statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/auth/me").statusCode()).isEqualTo(401);
        String credentials = "username=owner%40example.invalid&password=integration-only-password-12345";
        assertThat(post("/api/v1/auth/login", credentials, false).statusCode()).isEqualTo(403);
        assertThat(post("/api/v1/auth/login", "username=owner%40example.invalid&password=incorrect", true).statusCode()).isEqualTo(401);
        assertThat(post("/api/v1/auth/login", credentials, true).statusCode()).isEqualTo(204);
        assertThat(get("/api/v1/auth/me").statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/auth/me").body()).contains("owner@example.invalid").doesNotContain("passwordHash");
        assertThat(cookies.getCookieStore().getCookies()).anySatisfy(cookie -> {
            assertThat(cookie.getName()).isEqualTo("JSESSIONID"); assertThat(cookie.isHttpOnly()).isTrue();
        });
        assertThat(post("/api/v1/auth/logout", "", false).statusCode()).isEqualTo(403);
        assertThat(post("/api/v1/auth/logout", "", true).statusCode()).isEqualTo(204);
        assertThat(get("/api/v1/auth/me").statusCode()).isEqualTo(401);
    }
    @Test void databaseRejectsSecondOwner() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO app_users (id,email,password_hash,created_at,updated_at) VALUES (gen_random_uuid(),'second@example.invalid','unused',now(),now())"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
