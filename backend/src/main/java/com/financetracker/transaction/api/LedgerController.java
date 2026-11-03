package com.financetracker.transaction.api;

import com.financetracker.common.CurrentOwner;
import com.financetracker.transaction.api.LedgerContracts.*;
import com.financetracker.transaction.application.LedgerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class LedgerController {
    private final LedgerService ledger;
    private final CurrentOwner current;

    public LedgerController(LedgerService ledger, CurrentOwner current) {
        this.ledger = ledger;
        this.current = current;
    }

    private ResponseEntity<String> json(String body, int status) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @GetMapping("/categories")
    public List<CategoryView> categories(Principal p) {
        return ledger.categories(current.id(p));
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView category(Principal p, @Valid @RequestBody NewCategory r) {
        return ledger.category(current.id(p), r);
    }

    @GetMapping("/transactions")
    public Page list(Principal p, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                     @RequestParam(required = false) UUID accountId, @RequestParam(required = false) UUID categoryId, @RequestParam(required = false) String direction,
                     @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "ALL") String state) {
        return ledger.list(current.id(p), page, size, accountId, categoryId, direction, from, to, state);
    }

    @GetMapping("/transactions/{id}")
    public Actual get(Principal p, @PathVariable UUID id) {
        return ledger.get(current.id(p), id);
    }

    @PostMapping("/transactions")
    public ResponseEntity<String> create(Principal p, @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody NewActual r) {
        return json(ledger.create(current.id(p), key, r), 201);
    }

    @PatchMapping("/transactions/{id}")
    public ResponseEntity<String> correct(Principal p, @PathVariable UUID id, @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody Correction r) {
        return json(ledger.correct(current.id(p), id, key, r), 200);
    }

    @PostMapping("/transactions/{id}/void")
    public Actual reverse(Principal p, @PathVariable UUID id, @Valid @RequestBody Reason r) {
        return ledger.voidActual(current.id(p), id, r.reason());
    }

    @PostMapping("/transactions/{id}/link")
    public ResponseEntity<String> link(Principal p, @PathVariable UUID id, @RequestHeader("Idempotency-Key") UUID key, @RequestBody Source r) {
        return json(ledger.link(current.id(p), id, key, r), 200);
    }

    @PostMapping("/transactions/{id}/unlink")
    public Actual unlink(Principal p, @PathVariable UUID id, @Valid @RequestBody Reason r) {
        return ledger.unlink(current.id(p), id, r.reason());
    }

    @GetMapping("/accounts/{id}/balance")
    public Balance balance(Principal p, @PathVariable UUID id, @RequestParam(required = false) LocalDate asOf) {
        return ledger.balance(current.id(p), id, asOf == null ? LocalDate.now() : asOf);
    }

    @GetMapping("/transactions/summary")
    public Summary summary(Principal p, @RequestParam(required = false) UUID accountId, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return ledger.summary(current.id(p), accountId, from, to);
    }
}
