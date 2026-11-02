package com.financetracker.installment.api;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public final class Contracts {
    private Contracts() {}
    public record NewPlan(@NotBlank @Size(max=120) String name, @NotBlank @Size(max=120) String provider,
        @Size(max=2000) String description,
        @DecimalMin("0.00") @Digits(integer=17,fraction=2) BigDecimal principalAmount,
        @NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal totalPayable,
        @Min(1) @Max(600) int installmentCount, @NotNull LocalDate firstDueDate,
        @NotNull @Pattern(regexp="EUR") String currency) {}
    public record NewPayment(@NotNull UUID accountId,
        @NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal amount,
        @NotNull @PastOrPresent LocalDate paidOn, @Size(max=2000) String note) {}
    public record Due(UUID id,int sequenceNumber,LocalDate dueDate,BigDecimal amount,
        BigDecimal allocated,BigDecimal remaining,String status,boolean partiallyPaid) {}
    public record Allocation(UUID dueId,BigDecimal amount,BigDecimal dueRemaining) {}
    public record Payment(UUID id,UUID accountId,LocalDate paidOn,BigDecimal amount,String note,
        Instant createdAt,Instant voidedAt,List<Allocation> allocations) {}
    public record Plan(UUID id,String name,String provider,String description,String currency,
        BigDecimal principalAmount,BigDecimal totalPayable,int installmentCount,LocalDate firstDueDate,
        String administrativeStatus,BigDecimal totalPaid,BigDecimal outstandingBalance,BigDecimal percentagePaid,
        LocalDate nextDueDate,BigDecimal overdueAmount,LocalDate projectedFinalDueDate,String status,
        List<Due> schedule,List<Payment> payments) {}
    public record Page(List<Plan> items,int page,int size,long total) {}
    public record Receipt(UUID id,UUID planId,BigDecimal amount,List<Allocation> allocations,
        BigDecimal totalPaid,BigDecimal planOutstandingBalance) {}
}
