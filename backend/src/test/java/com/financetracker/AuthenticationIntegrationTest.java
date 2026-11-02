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

    @Autowired com.financetracker.installment.application.InstallmentService service;
    @Autowired tools.jackson.databind.ObjectMapper json;
    void login() throws Exception {
        assertThat(post("/api/v1/auth/login", "username=owner%40example.invalid&password=integration-only-password-12345", true).statusCode()).isEqualTo(204);
    }
    java.util.UUID owner() { return jdbc.queryForObject("select id from app_users",java.util.UUID.class); }
    HttpResponse<String> postJson(String path,String body,String key) throws Exception {
        var token=get("/api/v1/auth/csrf").body();
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path))
            .header("Content-Type","application/json")
            .header(JsonPath.read(token,"$.headerName"),JsonPath.read(token,"$.token"));
        if(key!=null) builder.header("Idempotency-Key",key);
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String id(HttpResponse<String> r) { assertThat(r.statusCode()).isBetween(200,201);return JsonPath.read(r.body(),"$.id"); }
    String account() throws Exception {
        return id(postJson("/accounts","{\"name\":\"Test cash\",\"openingBalance\":1000.00,\"openingDate\":\"2020-01-01\"}",null));
    }
    String plan(String total,int count) throws Exception {
        return id(postJson("/installments","{\"name\":\"Test plan\",\"provider\":\"Test provider\",\"totalPayable\":"+total+",\"installmentCount\":"+count+",\"firstDueDate\":\"2026-01-31\",\"currency\":\"EUR\"}",null));
    }
    String paymentBody(String account,String amount) {
        return "{\"accountId\":\""+account+"\",\"amount\":"+amount+",\"paidOn\":\"2026-02-01\",\"note\":\"Test payment\"}";
    }
    void decimal(String body,String path,String expected) {
        assertThat(new java.math.BigDecimal(JsonPath.read(body,path).toString())).isEqualByComparingTo(expected);
    }
    @Test void installmentAcceptancePartialPaymentsVoidAndReceiptReplay() throws Exception {
        login();var a=account();var p=plan("1200.00",6);var key=java.util.UUID.randomUUID().toString();
        var first=postJson("/installments/"+p+"/payments",paymentBody(a,"80.00"),key);
        assertThat(first.statusCode()).isEqualTo(200);decimal(first.body(),"$.planOutstandingBalance","1120.00");decimal(first.body(),"$.allocations[0].dueRemaining","120.00");
        var replay=postJson("/installments/"+p+"/payments",paymentBody(a,"80.0"),key);
        assertThat(replay.statusCode()).isEqualTo(200);assertThat(replay.body()).isEqualTo(first.body());
        assertThat(postJson("/installments/"+p+"/payments",paymentBody(a,"81.00"),key).statusCode()).isEqualTo(409);
        var second=postJson("/installments/"+p+"/payments",paymentBody(a,"120.00"),java.util.UUID.randomUUID().toString());
        assertThat(second.statusCode()).isEqualTo(200);decimal(second.body(),"$.allocations[0].dueRemaining","0.00");
        var detail=get("/api/v1/installments/"+p);decimal(detail.body(),"$.outstandingBalance","1000.00");
        assertThat((java.util.List<?>)JsonPath.read(detail.body(),"$.payments")).hasSize(2);
        assertThat((String)JsonPath.read(detail.body(),"$.schedule[0].status")).isEqualTo("PAID");
        var voided=postJson("/installments/"+p+"/payments/"+id(first)+"/void","{}",null);
        assertThat(voided.statusCode()).isEqualTo(200);decimal(voided.body(),"$.outstandingBalance","1080.00");decimal(voided.body(),"$.schedule[0].remaining","80.00");
        assertThat((String)JsonPath.read(voided.body(),"$.schedule[0].status")).isEqualTo("OVERDUE");
        assertThat((Boolean)JsonPath.read(voided.body(),"$.schedule[0].partiallyPaid")).isTrue();
        assertThat(postJson("/installments/"+p+"/payments",paymentBody(a,"80.00"),key).body()).isEqualTo(first.body());
        assertThat(jdbc.queryForObject("select count(*) from installment_payments where plan_id=?",Integer.class,java.util.UUID.fromString(p))).isEqualTo(2);
    }
    @Test void advanceMultiDuePaymentRejectsOverpaymentAndBadInput() throws Exception {
        login();var a=account();var p=plan("400.00",2);var path="/installments/"+p+"/payments";
        for(String amount:java.util.List.of("0","-1","1.001","100000000000000000.00")) {
            var invalid=postJson(path,paymentBody(a,amount),java.util.UUID.randomUUID().toString());
            assertThat(invalid.statusCode()).isEqualTo(400);assertThat(invalid.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        }
        assertThat(postJson(path,paymentBody(a,"1.00"),null).statusCode()).isEqualTo(400);
        assertThat(postJson(path,paymentBody(a,"401.00"),java.util.UUID.randomUUID().toString()).statusCode()).isEqualTo(422);
        var r=postJson(path,paymentBody(a,"300.00"),java.util.UUID.randomUUID().toString());assertThat(r.statusCode()).isEqualTo(200);
        decimal(r.body(),"$.allocations[0].amount","200.00");decimal(r.body(),"$.allocations[1].amount","100.00");
        assertThat(postJson(path,paymentBody(a,"101.00"),java.util.UUID.randomUUID().toString()).statusCode()).isEqualTo(422);
        assertThat(jdbc.queryForObject("select count(*) from installment_payments where plan_id=?",Integer.class,java.util.UUID.fromString(p))).isEqualTo(1);
    }
    @Test void invalidDatesSchedulesAndForeignReferencesAreRejected() throws Exception {
        login();var a=account();var p=plan("100.00",1);var other=plan("100.00",1);
        assertThat(postJson("/installments", "{\"name\":\"Tiny\",\"provider\":\"Test\",\"totalPayable\":0.01,\"installmentCount\":2,\"firstDueDate\":\"2026-01-01\",\"currency\":\"EUR\"}",null).statusCode()).isEqualTo(422);
        var body=paymentBody(a,"10.00");
        assertThat(postJson("/installments/"+p+"/payments",body.replace("2026-02-01","2019-12-31"),java.util.UUID.randomUUID().toString()).statusCode()).isEqualTo(422);
        assertThat(postJson("/installments/"+p+"/payments",body.replace("2026-02-01","2999-01-01"),java.util.UUID.randomUUID().toString()).statusCode()).isEqualTo(400);
        assertThat(postJson("/installments/"+p+"/payments",paymentBody(java.util.UUID.randomUUID().toString(),"10.00"),java.util.UUID.randomUUID().toString()).statusCode()).isEqualTo(404);
        var key=java.util.UUID.randomUUID().toString();var r=postJson("/installments/"+p+"/payments",body,key);
        assertThat(postJson("/installments/"+other+"/payments",body,key).statusCode()).isEqualTo(409);
        assertThat(postJson("/installments/"+other+"/payments/"+id(r)+"/void","{}",null).statusCode()).isEqualTo(404);
        assertThatThrownBy(()->service.get(java.util.UUID.randomUUID(),java.util.UUID.fromString(p)))
            .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(()->service.pay(java.util.UUID.randomUUID(),java.util.UUID.fromString(p),java.util.UUID.randomUUID(),
            new com.financetracker.installment.api.Contracts.NewPayment(java.util.UUID.fromString(a),new java.math.BigDecimal("1.00"),java.time.LocalDate.of(2026,2,1),null)))
            .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(404));
    }
    @Test void databasePreventsCrossPlanAllocations() throws Exception {
        login();var a=account();var p=plan("100.00",1);var other=plan("100.00",1);
        var payment=id(postJson("/installments/"+p+"/payments",paymentBody(a,"10.00"),java.util.UUID.randomUUID().toString()));
        var due=jdbc.queryForObject("select id from installment_dues where plan_id=?",java.util.UUID.class,java.util.UUID.fromString(other));
        assertThatThrownBy(()->jdbc.update("insert into payment_allocations(id,user_id,plan_id,payment_id,due_id,amount) values(?,?,?,?,?,?)",
            java.util.UUID.randomUUID(),owner(),java.util.UUID.fromString(p),java.util.UUID.fromString(payment),due,new java.math.BigDecimal("1.00")))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void simultaneousPaymentsCannotOverpay() throws Exception {
        login();var a=java.util.UUID.fromString(account());var p=java.util.UUID.fromString(plan("100.00",1));var owner=owner();
        var request=new com.financetracker.installment.api.Contracts.NewPayment(a,new java.math.BigDecimal("100.00"),java.time.LocalDate.of(2026,2,1),null);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var ready=new java.util.concurrent.CountDownLatch(2);var go=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> pay=()-> { ready.countDown();go.await();try {service.pay(owner,p,java.util.UUID.randomUUID(),request);return 200;}
                catch(org.springframework.web.server.ResponseStatusException e) { return e.getStatusCode().value(); } };
            var one=pool.submit(pay);var two=pool.submit(pay);assertThat(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(java.util.List.of(one.get(10,java.util.concurrent.TimeUnit.SECONDS),two.get(10,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,422);
        }
        assertThat(service.get(owner,p).outstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("select count(*) from installment_payments where plan_id=?",Integer.class,p)).isEqualTo(1);
    }
    @Test void concurrentIdempotentRetriesReturnIdenticalReceipt() throws Exception {
        login();var a=java.util.UUID.fromString(account());var p=java.util.UUID.fromString(plan("100.00",1));var owner=owner();var key=java.util.UUID.randomUUID();
        var request=new com.financetracker.installment.api.Contracts.NewPayment(a,new java.math.BigDecimal("80.00"),java.time.LocalDate.of(2026,2,1),null);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var ready=new java.util.concurrent.CountDownLatch(2);var go=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<String> pay=()-> { ready.countDown();go.await();return service.pay(owner,p,key,request); };
            var one=pool.submit(pay);var two=pool.submit(pay);assertThat(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(one.get(10,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(two.get(10,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertThat(service.get(owner,p).outstandingBalance()).isEqualByComparingTo("20.00");
    }

    @Test void listFilterPaginationAndDerivedStatuses() throws Exception {
        login();var a=java.util.UUID.fromString(account());var owner=owner();var today=java.time.LocalDate.now();
        var name="Status-"+java.util.UUID.randomUUID();
        var p=service.create(owner,new com.financetracker.installment.api.Contracts.NewPlan(name,"Test",null,null,new java.math.BigDecimal("200.00"),2,today,"EUR"));
        assertThat(p.schedule().getFirst().status()).isEqualTo("DUE");assertThat(p.schedule().getLast().status()).isEqualTo("UPCOMING");
        var list=get("/api/v1/installments?search="+name+"&size=1&page=0");assertThat(list.statusCode()).isEqualTo(200);
        assertThat((java.util.List<?>)JsonPath.read(list.body(),"$.items")).hasSize(1);
        assertThat(get("/api/v1/installments?page=-1").statusCode()).isEqualTo(422);
        service.pay(owner,p.id(),java.util.UUID.randomUUID(),new com.financetracker.installment.api.Contracts.NewPayment(a,new java.math.BigDecimal("10.00"),today,null));
        p=service.get(owner,p.id());assertThat(p.schedule().getFirst().status()).isEqualTo("PARTIALLY_PAID");assertThat(p.percentagePaid()).isEqualByComparingTo("5.00");
        service.pay(owner,p.id(),java.util.UUID.randomUUID(),new com.financetracker.installment.api.Contracts.NewPayment(a,new java.math.BigDecimal("90.00"),today,null));
        p=service.get(owner,p.id());assertThat(p.schedule().getFirst().status()).isEqualTo("PAID");assertThat(p.nextDueDate()).isEqualTo(today.plusMonths(1));
        service.pay(owner,p.id(),java.util.UUID.randomUUID(),new com.financetracker.installment.api.Contracts.NewPayment(a,new java.math.BigDecimal("100.00"),today,null));
        p=service.get(owner,p.id());assertThat(p.status()).isEqualTo("PAID");assertThat(p.nextDueDate()).isNull();assertThat(p.percentagePaid()).isEqualByComparingTo("100.00");
    }
}
