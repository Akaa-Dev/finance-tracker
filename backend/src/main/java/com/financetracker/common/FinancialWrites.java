package com.financetracker.common;
import com.financetracker.auth.Owner;
import com.financetracker.account.persistence.CashAccount;
import com.financetracker.transaction.persistence.Category;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import java.nio.charset.StandardCharsets;
import java.security.*;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
@Component
public class FinancialWrites {
    private final EntityManager em; private final ObjectMapper json;
    public FinancialWrites(EntityManager em,ObjectMapper json) { this.em=em;this.json=json; }
    public static ResponseStatusException invalid(String reason) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,reason); }
    public static ResponseStatusException conflict(String reason) { return new ResponseStatusException(HttpStatus.CONFLICT,reason); }
    public static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found."); }
    public void lock(UUID owner) { if(em.find(Owner.class,owner,LockModeType.PESSIMISTIC_WRITE)==null) throw missing(); }
    public CashAccount account(UUID owner,UUID id) {
        var a=em.find(CashAccount.class,id);if(a==null || !a.userId.equals(owner)) throw missing();return a;
    }
    public Category category(UUID owner,UUID id,String direction) {
        var c=em.find(Category.class,id);if(c==null || !c.userId.equals(owner)) throw missing();
        if(!c.kind.equals(direction)) throw invalid("Category kind must match direction.");return c;
    }
    public static BigDecimal amount(BigDecimal value) {
        if(value==null || value.signum()<=0 || value.scale()>2 || value.precision()-value.scale()>17) throw invalid("Amount must be positive EUR with up to two decimal places.");
        return value.setScale(2);
    }
    // Must run inside the caller's write transaction. All Phase 2 keys share this owner scope.
    public String once(UUID owner,UUID key,String operation,Object canonical,Supplier<?> mutation) {
        if(key==null) throw invalid("Idempotency key required.");lock(owner);
        String fingerprint;
        try { fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
            json.writeValueAsString(List.of(operation,canonical)).getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        var existing=em.createQuery("from OperationReceipt where userId=:owner and idempotencyKey=:key",OperationReceipt.class)
            .setParameter("owner",owner).setParameter("key",key).getResultList();
        if(!existing.isEmpty()) {
            var prior=existing.getFirst();if(!prior.fingerprint.equals(fingerprint)) throw conflict("Idempotency key already used for a different request.");return prior.receipt;
        }
        String result=json.writeValueAsString(mutation.get());
        var r=new OperationReceipt();r.id=UUID.randomUUID();r.userId=owner;r.idempotencyKey=key;r.fingerprint=fingerprint;r.receipt=result;r.createdAt=Instant.now();em.persist(r);em.flush();return result;
    }
}
