package com.financetracker.auth;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import java.security.Principal;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final OwnerRepository owners;
    public AuthController(OwnerRepository owners) { this.owners = owners; }
    @GetMapping("/csrf") public Token csrf(CsrfToken token) { return new Token(token.getHeaderName(), token.getToken()); }
    @GetMapping("/me") public Session me(Principal principal) {
        Owner owner = owners.findByEmail(principal.getName()).orElseThrow();
        return new Session(owner.getId(), owner.getEmail(), "EUR");
    }
    public record Token(String headerName, String token) {}
    public record Session(UUID id, String email, String currency) {}
}
