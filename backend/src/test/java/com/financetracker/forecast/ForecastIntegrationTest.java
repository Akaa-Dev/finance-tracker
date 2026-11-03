package com.financetracker.forecast;

import com.financetracker.forecast.application.ForecastService;
import com.financetracker.forecast.api.ForecastContracts.*;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "app.owner-email=forecast@example.invalid", "app.owner-password=forecast-integration-password-12345"
})
class ForecastIntegrationTest {
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
    static { postgres.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired JdbcTemplate db;
    @Autowired ForecastService forecasts;
    @Value("${local.server.port}") int port;
    CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
    private static BigDecimal eur(String text) { return new BigDecimal(text); }
    private static UUID id() { return UUID.randomUUID(); }
    private HttpResponse<String> request(String path, String body, String csrf) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        if (body == null) builder.GET();
        else builder.header("Content-Type", path.endsWith("/login") ? "application/x-www-form-urlencoded" : "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void reconciledIncomePartialDueAndReadOnlyScenario() throws Exception {
        LocalDate today = LocalDate.now(), tomorrow = today.plusDays(1), next = today.plusDays(2), third = today.plusDays(3);
        var owner = db.queryForObject("select id from app_users", UUID.class);
        var account = id(); var income = id(); var expense = id(); var rule = id(); var secondRule = id();
        var actual = id(); var occurrence = id(); var plan = id(); var due = id(); var payment = id(); var planned = id();
        var realizedPlanned = id(); var realizedActual = id();
        db.update("insert into cash_accounts(id,user_id,name,opening_balance,opening_date,created_at) values(?,?,?,0.00,?,now())", account, owner, "Cash", today.minusDays(30));
        db.update("insert into categories(id,user_id,name,kind,created_at) values(?,?,?,'INCOME',now())", income, owner, "Salary");
        db.update("insert into categories(id,user_id,name,kind,created_at) values(?,?,?,'EXPENSE',now())", expense, owner, "Rent");
        db.update("insert into transactions(id,user_id,account_id,category_id,direction,amount,transaction_date,note,created_at) values(?,?,?,?,'INCOME',100.00,?,'Received',now())", actual, owner, account, income, today);
        db.update("insert into transactions(id,user_id,account_id,category_id,direction,amount,transaction_date,note,created_at) values(?,?,?,?,'INCOME',10.00,?,'Received bonus',now())", realizedActual, owner, account, income, today);
        for (var pair : List.of(Map.entry(rule, "100.00"), Map.entry(secondRule, "40.00"))) {
            var anchor = pair.getKey().equals(rule) ? tomorrow : third;
            db.update("insert into recurring_rules(id,user_id,account_id,category_id,name,direction,amount,frequency,anchor_date,start_date,active,created_at,updated_at) values(?,?,?,?,?,'INCOME',?,'MONTHLY',?,?,true,now(),now())",
                pair.getKey(), owner, account, income, pair.getKey().equals(rule) ? "Linked salary" : "Extra income", eur(pair.getValue()), anchor, anchor);
        }
        db.update("insert into recurrence_occurrences(id,user_id,rule_id,occurrence_date,planned_amount) values(?,?,?,?,100.00)", occurrence, owner, rule, tomorrow);
        db.update("insert into actual_links(id,user_id,transaction_id,occurrence_id,linked_at) values(?,?,?,?,now())", id(), owner, actual, occurrence);
        db.update("insert into planned_cashflows(id,user_id,account_id,category_id,name,direction,amount,expected_date,status,created_at,updated_at) values(?,?,?,?,?,'EXPENSE',20.00,?,'PLANNED',now(),now())", planned, owner, account, expense, "Planned rent", next);
        db.update("insert into planned_cashflows(id,user_id,account_id,category_id,name,direction,amount,expected_date,status,created_at,updated_at) values(?,?,?,?,?,'INCOME',10.00,?,'REALIZED',now(),now())", realizedPlanned, owner, account, income, "Realized bonus", next);
        db.update("insert into actual_links(id,user_id,transaction_id,planned_cashflow_id,linked_at) values(?,?,?,?,now())", id(), owner, realizedActual, realizedPlanned);
        db.update("insert into installment_plans(id,user_id,name,provider,currency,total_payable,installment_count,first_due_date,administrative_status,created_at) values(?,?,'Plan','Test','EUR',80.00,1,?,'ACTIVE',now())", plan, owner, today.minusDays(3));
        db.update("insert into installment_dues(id,user_id,plan_id,sequence_number,due_date,amount) values(?,?,?,1,?,80.00)", due, owner, plan, today.minusDays(3));
        db.update("insert into installment_payments(id,user_id,plan_id,account_id,paid_on,amount,idempotency_key,request_fingerprint,receipt,created_at) values(?,?,?,?,?,30.00,?,'test','{}',now())", payment, owner, plan, account, today, id());
        db.update("insert into payment_allocations(id,user_id,plan_id,payment_id,due_id,amount) values(?,?,?,?,?,30.00)", id(), owner, plan, payment, due);

        assertThat(request("/api/v1/forecasts/cashflow?from=" + today + "&to=" + third, null, null).statusCode()).isEqualTo(401);
        String csrf = JsonPath.read(request("/api/v1/auth/csrf", null, null).body(), "$.token");
        assertThat(request("/api/v1/auth/login", "username=forecast%40example.invalid&password=forecast-integration-password-12345", csrf).statusCode()).isEqualTo(204);
        var response = request("/api/v1/forecasts/cashflow?from=" + today + "&to=" + third, null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Extra income", "INSTALLMENT_DUE").doesNotContain("Linked salary");
        assertThat(new BigDecimal(JsonPath.read(response.body(), "$.endingBalance").toString())).isEqualByComparingTo("50.00");
        assertThat(new BigDecimal(JsonPath.read(response.body(), "$.lowestDailyBalance").toString())).isEqualByComparingTo("10.00");
        var baseline = forecasts.cashflow(owner, today, third);
        assertThat(baseline.events()).noneMatch(e -> realizedPlanned.equals(e.sourceId()));
        assertThat(baseline.events().stream().filter(e -> e.source().equals("INSTALLMENT_DUE")).map(Event::amount).toList()).containsExactly(eur("50.00"));
        var futureStart = forecasts.cashflow(owner, third, third);
        assertThat(futureStart.startingBalance()).isEqualByComparingTo("60.00");
        assertThat(futureStart.endingBalance()).isEqualByComparingTo("50.00");
        assertThat(futureStart.events()).noneMatch(e -> planned.equals(e.sourceId()));
        assertThat(futureStart.events()).anyMatch(e -> e.source().equals("INSTALLMENT_DUE") && e.date().equals(third));
        var before = db.queryForObject("select count(*) from transactions", Integer.class);
        csrf = JsonPath.read(request("/api/v1/auth/csrf", null, null).body(), "$.token");
        var body = "{\"from\":\"" + today + "\",\"to\":\"" + third + "\",\"changes\":[{\"type\":\"ONE_TIME_EXPENSE\",\"amount\":5.00,\"date\":\"" + third + "\"}]}";
        var scenario = request("/api/v1/forecasts/scenarios", body, csrf);
        assertThat(scenario.statusCode()).isEqualTo(200);
        assertThat(new BigDecimal(JsonPath.read(scenario.body(), "$.hypothetical.endingBalance").toString())).isEqualByComparingTo("45.00");
        assertThat(db.queryForObject("select count(*) from transactions", Integer.class)).isEqualTo(before);
        assertThat(db.queryForObject("select count(*) from installment_payments", Integer.class)).isEqualTo(1);
        var newPlan = forecasts.scenario(owner, new ScenarioRequest(today, third,
            List.of(new Change("NEW_INSTALLMENT", eur("10.00"), null, next, 3, null))));
        assertThat(newPlan.hypothetical().endingBalance()).isEqualByComparingTo("40.00");
        assertThat(newPlan.hypothetical().events()).anyMatch(e -> e.source().equals("HYPOTHETICAL_INSTALLMENT"));
        var reduction = forecasts.scenario(owner, new ScenarioRequest(today, third,
            List.of(new Change("INCOME_REDUCTION", eur("5.00"), third, null, null, secondRule))));
        assertThat(reduction.hypothetical().endingBalance()).isEqualByComparingTo("45.00");
        assertThat(db.queryForObject("select count(*) from installment_plans", Integer.class)).isEqualTo(1);
        assertThat(request("/api/v1/forecasts/scenarios", body.replace("5.00", "0.001"), csrf).statusCode()).isEqualTo(422);
        assertThatThrownBy(() -> forecasts.scenario(id(), new ScenarioRequest(today, third,
            List.of(new Change("INCOME_REDUCTION", eur("1.00"), tomorrow, null, null, rule)))))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
