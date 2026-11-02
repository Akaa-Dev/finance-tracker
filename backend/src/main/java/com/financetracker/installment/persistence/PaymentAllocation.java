package com.financetracker.installment.persistence;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Entity
@Table(name="payment_allocations")
public class PaymentAllocation {
    @Id public UUID id;
    public UUID userId;
    public UUID planId;
    public UUID paymentId;
    public UUID dueId;
    @Column(precision=19,scale=2) public BigDecimal amount;
}
