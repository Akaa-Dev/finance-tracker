package com.financetracker.account.persistence;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Entity
@Table(name="cash_accounts")
public class CashAccount {
    @Id public UUID id;
    public UUID userId;
    public String name;
    @Column(precision=19,scale=2) public BigDecimal openingBalance;
    public LocalDate openingDate;
    public Instant createdAt;
}
