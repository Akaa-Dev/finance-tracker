package com.financetracker.forecast.domain;

import com.financetracker.forecast.api.ForecastContracts.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

public final class ForecastMath {
    private ForecastMath() {}
    public static final BigDecimal ZERO = new BigDecimal("0.00");

    public static Forecast project(LocalDate from, LocalDate to, LocalDate actualThrough,
                                   BigDecimal initial, BigDecimal initialDebt,
                                   List<AccountBalance> accountBalances, List<Event> input) {
        List<Event> ordered = input.stream().sorted(Comparator.comparing(Event::date)
                .thenComparing(Event::source).thenComparing(e -> e.sourceId() == null ? "" : e.sourceId().toString()))
            .toList();
        Map<LocalDate, List<Event>> byDate = new HashMap<>();
        ordered.forEach(e -> byDate.computeIfAbsent(e.date(), ignored -> new ArrayList<>()).add(e));
        LocalDate first = ordered.stream().map(Event::date).filter(d -> d.isBefore(from)).min(LocalDate::compareTo).orElse(from);
        BigDecimal balance = initial, visibleStart = initial, low = null;
        BigDecimal debtOutstanding = initialDebt, visibleDebt = initialDebt;
        List<Day> days = new ArrayList<>();
        Map<YearMonth, List<Day>> grouped = new LinkedHashMap<>();
        for (LocalDate date = first; !date.isAfter(to); date = date.plusDays(1)) {
            BigDecimal income = ZERO, expense = ZERO, debtPayment = ZERO, opening = ZERO, newDebt = ZERO;
            for (Event event : byDate.getOrDefault(date, List.of())) {
                switch (event.source()) {
                    case "OPENING_BALANCE" -> opening = opening.add(event.amount());
                    case "ACTUAL_INCOME", "RECURRING_INCOME", "PLANNED_INCOME", "HYPOTHETICAL_INCOME" -> income = income.add(event.amount());
                    case "ACTUAL_EXPENSE", "RECURRING_EXPENSE", "PLANNED_EXPENSE", "HYPOTHETICAL_EXPENSE" -> expense = expense.add(event.amount());
                    case "ACTUAL_PAYMENT", "INSTALLMENT_DUE", "HYPOTHETICAL_INSTALLMENT" -> debtPayment = debtPayment.add(event.amount());
                    case "HYPOTHETICAL_OBLIGATION" -> newDebt = newDebt.add(event.amount());
                    default -> throw new IllegalArgumentException("Unknown forecast source");
                }
            }
            if (date.equals(from)) { visibleStart = balance; visibleDebt = debtOutstanding; }
            balance = balance.add(opening).add(income).subtract(expense).subtract(debtPayment);
            debtOutstanding = debtOutstanding.add(newDebt).subtract(debtPayment);
            if (debtOutstanding.signum() < 0) throw new IllegalStateException("Projected debt fell below zero.");
            if (!date.isBefore(from)) {
                Day day = new Day(date, income, expense, debtPayment, opening, balance, debtOutstanding);
                days.add(day);
                grouped.computeIfAbsent(YearMonth.from(date), ignored -> new ArrayList<>()).add(day);
                low = low == null ? balance : low.min(balance);
            }
        }
        List<Month> months = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            var rows = entry.getValue();
            months.add(new Month(entry.getKey().toString(), sum(rows, Day::income),
                sum(rows, Day::ordinaryExpenses), sum(rows, Day::installmentRepayments),
                sum(rows, Day::openingPositions), rows.getLast().endingBalance(),
                rows.stream().map(Day::endingBalance).min(BigDecimal::compareTo).orElse(ZERO),
                rows.getLast().debtOutstanding()));
        }
        return new Forecast(from, to, actualThrough, visibleStart, balance, low == null ? visibleStart : low,
            visibleDebt, debtOutstanding, List.copyOf(accountBalances),
            ordered.stream().filter(e -> !e.date().isBefore(from)).toList(), List.copyOf(days), List.copyOf(months),
            "Manual records and fixed nominal expectations only. Future amounts are projections, not bank-confirmed balances. Unlinked actuals may overlap expectations.");
    }

    private static BigDecimal sum(List<Day> rows, java.util.function.Function<Day, BigDecimal> field) {
        return rows.stream().map(field).reduce(ZERO, BigDecimal::add);
    }
}
