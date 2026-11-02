package com.financetracker.account.api;
import com.financetracker.account.persistence.CashAccount;
import com.financetracker.common.CurrentOwner;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.security.Principal;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/v1/accounts")
@Transactional(readOnly=true)
public class AccountController {
    private final EntityManager em; private final CurrentOwner current;
    public AccountController(EntityManager em,CurrentOwner current) { this.em=em;this.current=current; }
    public record NewAccount(@NotBlank @Size(max=120) String name,
        @NotNull @Digits(integer=17,fraction=2) BigDecimal openingBalance,
        @NotNull @PastOrPresent LocalDate openingDate) {}
    public record Account(UUID id,String name,BigDecimal openingBalance,LocalDate openingDate) {}
    private Account dto(CashAccount a) { return new Account(a.id,a.name,a.openingBalance,a.openingDate); }
    @GetMapping public List<Account> list(Principal p) {
        return em.createQuery("from CashAccount where userId=:owner order by createdAt,id",CashAccount.class)
            .setParameter("owner",current.id(p)).getResultList().stream().map(this::dto).toList();
    }
    @GetMapping("/{id}") public Account get(@PathVariable UUID id,Principal p) {
        var a=em.find(CashAccount.class,id);
        if(a==null || !a.userId.equals(current.id(p))) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Account not found.");
        return dto(a);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Account create(@Valid @RequestBody NewAccount request,Principal p) {
        var a=new CashAccount();a.id=UUID.randomUUID();a.userId=current.id(p);a.name=request.name().strip();
        a.openingBalance=request.openingBalance().setScale(2);a.openingDate=request.openingDate();a.createdAt=Instant.now();
        em.persist(a);return dto(a);
    }
}
