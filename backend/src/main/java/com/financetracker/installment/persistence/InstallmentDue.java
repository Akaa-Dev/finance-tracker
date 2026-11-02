package com.financetracker.installment.persistence;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Entity
@Table(name="installment_dues")
public class InstallmentDue {
    @Id public UUID id;
    public UUID userId;
    public UUID planId;
    public int sequenceNumber;
    public LocalDate dueDate;
    @Column(precision=19,scale=2) public BigDecimal amount;
}
