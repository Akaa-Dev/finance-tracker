package com.financetracker.recurrence.persistence;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="actual_links")
public class ActualLink { @Id public UUID id; public UUID userId; public UUID transactionId; public UUID occurrenceId; public UUID plannedCashflowId;
 public Instant linkedAt; public Instant unlinkedAt; @Column(length=500) public String unlinkReason; }
