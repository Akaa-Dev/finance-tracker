package com.financetracker.installment.persistence;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Entity
@Table(name="installment_plans")
public class InstallmentPlan {
    @Id public UUID id;
    public UUID userId;
    public String name;
    public String provider;
    @Column(length=2000) public String description;
    public String currency;
    @Column(precision=19,scale=2) public BigDecimal principalAmount;
    @Column(precision=19,scale=2) public BigDecimal totalPayable;
    public int installmentCount;
    public LocalDate firstDueDate;
    public String administrativeStatus;
    public Instant createdAt;
}
