package com.financetracker.common;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="operation_receipts")
public class OperationReceipt { @Id public UUID id; public UUID userId; public UUID idempotencyKey; @Column(length=64) public String fingerprint;
 @Column(columnDefinition="text") public String receipt; public Instant createdAt; }
