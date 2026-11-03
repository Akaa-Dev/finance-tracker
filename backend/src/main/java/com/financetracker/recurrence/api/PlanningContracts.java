package com.financetracker.recurrence.api;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
public final class PlanningContracts {
    private PlanningContracts() {}
    public record NewRule(@NotBlank @Size(max=120) String name,UUID accountId,@NotNull UUID categoryId,
        @NotNull @Pattern(regexp="INCOME|EXPENSE") String direction,@NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal amount,
        @NotNull @Pattern(regexp="MONTHLY|YEARLY") String frequency,@NotNull LocalDate anchorDate,@NotNull LocalDate startDate,LocalDate endDate) {}
    public record Active(@NotNull Boolean active) {}
    public record Range(@NotNull LocalDate from,@NotNull LocalDate to) {}
    public record NewPlanned(@NotBlank @Size(max=120) String name,UUID accountId,@NotNull UUID categoryId,
        @NotNull @Pattern(regexp="INCOME|EXPENSE") String direction,@NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal amount,@NotNull LocalDate expectedDate) {}
    public record Status(@NotNull @Pattern(regexp="PLANNED|CANCELLED") String status) {}
    public record Realize(@NotNull UUID transactionId) {}
    public record Rule(UUID id,String name,UUID accountId,UUID categoryId,String direction,BigDecimal amount,String frequency,LocalDate anchorDate,LocalDate startDate,LocalDate endDate,boolean active) {}
    public record Occurrence(UUID id,UUID ruleId,String name,LocalDate occurrenceDate,BigDecimal plannedAmount,UUID transactionId,String status) {}
    public record Planned(UUID id,String name,UUID accountId,UUID categoryId,String direction,BigDecimal amount,LocalDate expectedDate,String status,UUID transactionId) {}
}
