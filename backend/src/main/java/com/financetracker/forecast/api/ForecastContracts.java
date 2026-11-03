package com.financetracker.forecast.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ForecastContracts {
    private ForecastContracts() {}

    public record Change(String type, BigDecimal amount, LocalDate date, LocalDate firstDueDate,
                         Integer installmentCount, UUID ruleId) {}
    public record ScenarioRequest(LocalDate from, LocalDate to, List<Change> changes) {}
    public record Event(LocalDate date, String source, UUID sourceId, String name, BigDecimal amount,
                        boolean projected) {}
    public record Day(LocalDate date, BigDecimal income, BigDecimal ordinaryExpenses,
                      BigDecimal installmentRepayments, BigDecimal openingPositions,
                      BigDecimal endingBalance, BigDecimal debtOutstanding) {}
    public record Month(String month, BigDecimal income, BigDecimal ordinaryExpenses,
                        BigDecimal installmentRepayments, BigDecimal openingPositions,
                        BigDecimal endingBalance, BigDecimal lowestDailyBalance,
                        BigDecimal debtOutstanding) {}
    public record AccountBalance(UUID accountId, String name, BigDecimal currentBalance) {}
    public record Forecast(LocalDate from, LocalDate to, LocalDate actualThrough,
                           BigDecimal startingBalance, BigDecimal endingBalance,
                           BigDecimal lowestDailyBalance, BigDecimal startingDebt,
                           BigDecimal endingDebt, List<AccountBalance> accountBalances,
                           List<Event> events, List<Day> days, List<Month> months, String warning) {}
    public record MonthDelta(String month, BigDecimal endingBalanceDelta, BigDecimal lowestBalanceDelta) {}
    public record Scenario(Forecast baseline, Forecast hypothetical, List<MonthDelta> monthlyDeltas,
                           BigDecimal lowestBalanceDelta) {}
}
