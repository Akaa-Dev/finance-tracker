package com.financetracker.common;

import com.financetracker.auth.OwnerRepository;

import java.security.Principal;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class CurrentOwner {
    private final OwnerRepository owners;

    public CurrentOwner(OwnerRepository owners) {
        this.owners = owners;
    }

    public UUID id(Principal principal) {
        return owners.findByEmail(principal.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getId();
    }
}
