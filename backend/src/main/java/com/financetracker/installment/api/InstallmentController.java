package com.financetracker.installment.api;
import com.financetracker.installment.application.InstallmentService;
import com.financetracker.installment.api.Contracts.*;
import com.financetracker.common.CurrentOwner;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController
@RequestMapping("/api/v1/installments")
public class InstallmentController {
    private final InstallmentService service;private final CurrentOwner current;
    public InstallmentController(InstallmentService service,CurrentOwner current) { this.service=service;this.current=current; }
    @GetMapping public Page list(Principal p,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String search) {
        return service.list(current.id(p),page,size,search);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Plan create(Principal p,@Valid @RequestBody NewPlan request) { return service.create(current.id(p),request); }
    @GetMapping("/{id}") public Plan get(Principal p,@PathVariable UUID id) { return service.get(current.id(p),id); }
    @GetMapping("/{id}/schedule") public List<Due> schedule(Principal p,@PathVariable UUID id) { return get(p,id).schedule(); }
    @GetMapping("/{id}/payments") public List<Payment> history(Principal p,@PathVariable UUID id) { return get(p,id).payments(); }
    @PostMapping(value="/{id}/payments",produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> pay(Principal p,@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody NewPayment request) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.pay(current.id(p),id,key,request));
    }
    @PostMapping("/{id}/payments/{paymentId}/void") public Plan reverse(Principal p,@PathVariable UUID id,@PathVariable UUID paymentId) {
        return service.voidPayment(current.id(p),id,paymentId);
    }
}
