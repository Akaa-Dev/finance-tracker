package com.financetracker.transaction.persistence;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="transactions")
public class ActualTransaction { @Id public UUID id; public UUID userId; public UUID accountId; public UUID categoryId; public String direction;
 @Column(precision=19,scale=2) public BigDecimal amount; public LocalDate transactionDate;
 @Column(length=2000) public String note; public Instant createdAt; public Instant voidedAt;
 @Column(length=500) public String voidReason; public UUID supersedesId; }
