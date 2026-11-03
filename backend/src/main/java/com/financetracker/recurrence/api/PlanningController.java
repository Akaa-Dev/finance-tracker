package com.financetracker.recurrence.api;
import com.financetracker.recurrence.api.PlanningContracts.*;
import com.financetracker.recurrence.application.PlanningService;
import com.financetracker.transaction.application.LedgerService;
import com.financetracker.transaction.api.LedgerContracts.Source;
import com.financetracker.common.CurrentOwner;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController @RequestMapping("/api/v1")
public class PlanningController {
    private final PlanningService service;private final LedgerService ledger;private final CurrentOwner current;
    public PlanningController(PlanningService service,LedgerService ledger,CurrentOwner current) { this.service=service;this.ledger=ledger;this.current=current; }
    private ResponseEntity<String> json(String body,int status) { return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body); }
    @GetMapping("/recurring-rules") public List<Rule> rules(Principal p) { return service.rules(current.id(p)); }
    @PostMapping("/recurring-rules") public ResponseEntity<String> createRule(Principal p,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody NewRule r) { return json(service.createRule(current.id(p),key,r),201); }
    @PatchMapping("/recurring-rules/{id}") public Rule active(Principal p,@PathVariable UUID id,@Valid @RequestBody Active r) { return service.setActive(current.id(p),id,r.active()); }
    @PostMapping("/recurring-rules/{id}/occurrences") public List<Occurrence> generate(Principal p,@PathVariable UUID id,@Valid @RequestBody Range r) { return service.generate(current.id(p),id,r); }
    @GetMapping("/recurring-rules/{id}/occurrences") public List<Occurrence> occurrences(Principal p,@PathVariable UUID id,@RequestParam LocalDate from,@RequestParam LocalDate to) { return service.occurrences(current.id(p),id,from,to); }
    @GetMapping("/planned-cashflows") public List<Planned> planned(Principal p) { return service.planned(current.id(p)); }
    @PostMapping("/planned-cashflows") public ResponseEntity<String> createPlanned(Principal p,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody NewPlanned r) { return json(service.createPlanned(current.id(p),key,r),201); }
    @PatchMapping("/planned-cashflows/{id}") public Planned status(Principal p,@PathVariable UUID id,@Valid @RequestBody Status r) { return service.status(current.id(p),id,r.status()); }
    @PostMapping("/planned-cashflows/{id}/realize") public ResponseEntity<String> realize(Principal p,@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody Realize r) { return json(ledger.link(current.id(p),r.transactionId(),key,new Source(null,id)),200); }
}
