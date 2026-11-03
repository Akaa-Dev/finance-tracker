package com.financetracker.recurrence.persistence;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="planned_cashflows")
public class PlannedCashflow { @Id public UUID id; public UUID userId; public UUID accountId; public UUID categoryId; public String name; public String direction;
 @Column(precision=19,scale=2) public BigDecimal amount; public LocalDate expectedDate; public String status;
 public Instant createdAt; public Instant updatedAt; }
