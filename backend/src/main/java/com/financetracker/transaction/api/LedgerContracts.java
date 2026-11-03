package com.financetracker.transaction.api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public final class LedgerContracts {
    private LedgerContracts() {}
    public record NewCategory(@NotBlank @Size(max=120) String name,@NotNull @Pattern(regexp="INCOME|EXPENSE") String kind) {}
    public record CategoryView(UUID id,String name,String kind) {}
    public record NewActual(@NotNull UUID accountId,@NotNull UUID categoryId,@NotNull @Pattern(regexp="INCOME|EXPENSE") String direction,
        @NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal amount,@NotNull @PastOrPresent LocalDate transactionDate,
        @Size(max=2000) String note,UUID occurrenceId,UUID plannedCashflowId) {}
    public record Reason(@NotBlank @Size(max=500) String reason) {}
    public record Correction(@Valid @NotNull NewActual transaction,@NotBlank @Size(max=500) String reason) {}
    public record Source(UUID occurrenceId,UUID plannedCashflowId) {}
    public record LinkView(UUID id,UUID occurrenceId,UUID plannedCashflowId,Instant linkedAt,Instant unlinkedAt,String unlinkReason) {}
    public record Actual(UUID id,UUID accountId,UUID categoryId,String direction,BigDecimal amount,LocalDate transactionDate,
        String note,Instant createdAt,Instant voidedAt,String voidReason,UUID supersedesId,List<LinkView> links) {}
    public record Page(List<Actual> items,int page,int size,long total) {}
    public record Balance(UUID accountId,LocalDate asOf,boolean openingIncluded,BigDecimal openingBalance,BigDecimal income,
        BigDecimal ordinaryExpenses,BigDecimal installmentRepayments,BigDecimal currentBalance) {}
    public record Summary(LocalDate from,LocalDate to,BigDecimal income,BigDecimal ordinaryExpenses,BigDecimal installmentRepayments,
        BigDecimal netMovement,BigDecimal currentBalance,String warning) {}
}
