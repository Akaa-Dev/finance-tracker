package com.financetracker.installment.persistence;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Entity
@Table(name="installment_payments")
public class InstallmentPayment {
    @Id public UUID id;
    public UUID userId;
    public UUID planId;
    public UUID accountId;
    public LocalDate paidOn;
    @Column(precision=19,scale=2) public BigDecimal amount;
    @Column(length=2000) public String note;
    public UUID idempotencyKey;
    @Column(length=64) public String requestFingerprint;
    @Column(columnDefinition="text") public String receipt;
    public Instant voidedAt;
    public Instant createdAt;
}
