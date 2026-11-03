package com.financetracker.forecast.application;

import com.financetracker.account.persistence.CashAccount;
import com.financetracker.forecast.api.ForecastContracts.*;
import com.financetracker.forecast.domain.ForecastMath;
import com.financetracker.installment.domain.FixedSchedule;
import com.financetracker.installment.persistence.*;
import com.financetracker.recurrence.domain.OccurrenceDates;
import com.financetracker.recurrence.persistence.*;
import com.financetracker.transaction.application.LedgerService;
import com.financetracker.transaction.persistence.ActualTransaction;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.financetracker.common.FinancialWrites.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ForecastService {
    private final EntityManager em;
    private final LedgerService ledger;
    public ForecastService(EntityManager em, LedgerService ledger) { this.em = em; this.ledger = ledger; }

    private record Snapshot(List<CashAccount> accounts, List<InstallmentPlan> plans, List<ActualTransaction> actuals,
                            List<InstallmentPayment> payments, List<InstallmentDue> dues,
                            List<PaymentAllocation> allocations, List<RecurringRule> rules,
                            List<RecurrenceOccurrence> occurrences, List<PlannedCashflow> planned,
                            List<ActualLink> links) {}

    private <T> List<T> owned(Class<T> type, UUID owner) {
        return em.createQuery("from " + type.getSimpleName() + " where userId=:owner", type)
            .setParameter("owner", owner).getResultList();
    }
    private Snapshot snapshot(UUID owner) {
        return new Snapshot(owned(CashAccount.class, owner), owned(InstallmentPlan.class, owner), owned(ActualTransaction.class, owner),
            owned(InstallmentPayment.class, owner), owned(InstallmentDue.class, owner),
            owned(PaymentAllocation.class, owner), owned(RecurringRule.class, owner),
            owned(RecurrenceOccurrence.class, owner), owned(PlannedCashflow.class, owner),
            owned(ActualLink.class, owner));
    }

    private void range(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 366
                || from.isBefore(LocalDate.now().minusMonths(12)) || to.isAfter(LocalDate.now().plusMonths(12)))
            throw invalid("Forecast dates must be ordered and within a 12-month horizon around today.");
    }

    public Forecast cashflow(UUID owner, LocalDate from, LocalDate to) {
        range(from, to);
        return build(owner, snapshot(owner), from, to, List.of());
    }

    public Scenario scenario(UUID owner, ScenarioRequest request) {
        if (request == null) throw invalid("Scenario body is required.");
        range(request.from(), request.to());
        if (request.changes() == null || request.changes().isEmpty() || request.changes().size() > 10)
            throw invalid("Provide 1–10 hypothetical changes.");
        var data = snapshot(owner);
        validateChanges(request.changes(), data, request.from(), request.to());
        var baseline = build(owner, data, request.from(), request.to(), List.of());
        var hypothetical = build(owner, data, request.from(), request.to(), request.changes());
        List<MonthDelta> deltas = new ArrayList<>();
        for (int i = 0; i < baseline.months().size(); i++) {
            var base = baseline.months().get(i); var changed = hypothetical.months().get(i);
            deltas.add(new MonthDelta(base.month(), changed.endingBalance().subtract(base.endingBalance()),
                changed.lowestDailyBalance().subtract(base.lowestDailyBalance())));
        }
        return new Scenario(baseline, hypothetical, List.copyOf(deltas),
            hypothetical.lowestDailyBalance().subtract(baseline.lowestDailyBalance()));
    }

    private void validateChanges(List<Change> changes, Snapshot data, LocalDate from, LocalDate to) {
        Map<UUID, RecurringRule> rules = data.rules.stream().collect(Collectors.toMap(r -> r.id, Function.identity()));
        LocalDate earliest = from.isAfter(LocalDate.now()) ? from : LocalDate.now().plusDays(1);
        for (Change c : changes) {
            if (c == null || c.type() == null) throw invalid("Invalid scenario change.");
            BigDecimal amount = amount(c.amount());
            switch (c.type()) {
                case "ONE_TIME_EXPENSE" -> {
                    if (c.date() == null || c.date().isBefore(earliest) || c.date().isAfter(to)
                            || c.firstDueDate() != null || c.installmentCount() != null || c.ruleId() != null)
                        throw invalid("Hypothetical expense requires one future date within the range.");
                }
                case "NEW_INSTALLMENT" -> {
                    if (c.firstDueDate() == null || c.firstDueDate().isBefore(earliest) || c.firstDueDate().isAfter(to)
                            || c.installmentCount() == null || c.installmentCount() < 1 || c.installmentCount() > 600
                            || c.date() != null || c.ruleId() != null)
                        throw invalid("Hypothetical installment requires a future first due date and valid count.");
                    if (amount.multiply(BigDecimal.valueOf(c.installmentCount())).precision() > 19)
                        throw invalid("Hypothetical total is too large.");
                }
                case "INCOME_REDUCTION" -> {
                    var rule = rules.get(c.ruleId());
                    if (rule == null || !rule.active || !rule.direction.equals("INCOME")
                            || amount.compareTo(rule.amount) > 0 || c.date() == null || c.date().isBefore(earliest)
                            || c.date().isAfter(to) || c.firstDueDate() != null || c.installmentCount() != null)
                        throw invalid("Income reduction requires an owned active income rule and future start date.");
                }
                default -> throw invalid("Unknown hypothetical change type.");
            }
        }
    }

    private Forecast build(UUID owner, Snapshot data, LocalDate from, LocalDate to, List<Change> changes) {
        LocalDate today = LocalDate.now();
        LocalDate timelineStart = from.isAfter(today.plusDays(1)) ? today.plusDays(1) : from;
        LocalDate asOf = timelineStart.minusDays(1);
        BigDecimal initial = ForecastMath.ZERO;
        for (var account : data.accounts) initial = initial.add(ledger.balance(owner, account.id, asOf).currentBalance());
        BigDecimal initialDebt = data.plans.stream().map(p -> p.totalPayable).reduce(ForecastMath.ZERO, BigDecimal::add);
        for (var payment : data.payments) if (payment.voidedAt == null && !payment.paidOn.isAfter(asOf))
            initialDebt = initialDebt.subtract(payment.amount);
        List<AccountBalance> currentAccounts = data.accounts.stream()
            .map(account -> new AccountBalance(account.id, account.name,
                ledger.balance(owner, account.id, today).currentBalance())).toList();
        List<Event> events = new ArrayList<>();
        for (var account : data.accounts) if (!account.openingDate.isBefore(timelineStart) && !account.openingDate.isAfter(to))
            events.add(new Event(account.openingDate, "OPENING_BALANCE", account.id, account.name, account.openingBalance, account.openingDate.isAfter(today)));
        for (var actual : data.actuals) if (actual.voidedAt == null && within(actual.transactionDate, timelineStart, to))
            events.add(new Event(actual.transactionDate, "ACTUAL_" + actual.direction, actual.id,
                actual.note == null || actual.note.isBlank() ? "Ordinary " + actual.direction.toLowerCase(Locale.ROOT) : actual.note,
                actual.amount, false));
        Map<UUID, String> planNames = data.plans.stream().collect(Collectors.toMap(p -> p.id, p -> p.name));
        for (var payment : data.payments) if (payment.voidedAt == null && within(payment.paidOn, timelineStart, to))
            events.add(new Event(payment.paidOn, "ACTUAL_PAYMENT", payment.id,
                "Payment · " + planNames.getOrDefault(payment.planId, "installment plan"), payment.amount, false));
        LocalDate carryStart = today.plusDays(1);
        LocalDate projectionStart = from.isAfter(today) ? from : carryStart;
        if (!carryStart.isAfter(to)) {
            projectedSources(data, changes, carryStart, to, events);
            projectedDues(data, planNames, carryStart, projectionStart, to, events);
            hypothetical(changes, projectionStart, to, events);
        }
        return ForecastMath.project(from, to, to.isBefore(today) ? to : today,
            initial, initialDebt, currentAccounts, events);
    }

    private static boolean within(LocalDate date, LocalDate from, LocalDate to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    private void projectedSources(Snapshot data, List<Change> changes, LocalDate from, LocalDate to, List<Event> events) {
        Set<UUID> validActuals = data.actuals.stream().filter(a -> a.voidedAt == null).map(a -> a.id).collect(Collectors.toSet());
        Set<UUID> realizedOccurrences = new HashSet<>(), realizedPlanned = new HashSet<>();
        for (var link : data.links) if (link.unlinkedAt == null && validActuals.contains(link.transactionId)) {
            if (link.occurrenceId != null) realizedOccurrences.add(link.occurrenceId);
            if (link.plannedCashflowId != null) realizedPlanned.add(link.plannedCashflowId);
        }
        Map<String, RecurrenceOccurrence> occurrences = new HashMap<>();
        for (var occurrence : data.occurrences) occurrences.put(occurrence.ruleId + ":" + occurrence.occurrenceDate, occurrence);
        for (var rule : data.rules) if (rule.active) {
            for (var date : OccurrenceDates.between(rule.frequency, rule.anchorDate, rule.startDate, rule.endDate, from, to)) {
                var persisted = occurrences.get(rule.id + ":" + date);
                if (persisted != null && realizedOccurrences.contains(persisted.id)) continue;
                BigDecimal value = rule.amount;
                for (var change : changes) if ("INCOME_REDUCTION".equals(change.type()) && rule.id.equals(change.ruleId()) && !date.isBefore(change.date()))
                    value = value.subtract(change.amount()).max(ForecastMath.ZERO);
                if (value.signum() > 0) events.add(new Event(date, "RECURRING_" + rule.direction, rule.id, rule.name, value, true));
            }
        }
        for (var planned : data.planned) if ("PLANNED".equals(planned.status) && !realizedPlanned.contains(planned.id)
                && within(planned.expectedDate, from, to))
            events.add(new Event(planned.expectedDate, "PLANNED_" + planned.direction, planned.id, planned.name, planned.amount, true));
    }

    private void projectedDues(Snapshot data, Map<UUID, String> planNames, LocalDate carryStart, LocalDate projectionStart,
                               LocalDate to, List<Event> events) {
        Set<UUID> validPayments = data.payments.stream().filter(p -> p.voidedAt == null).map(p -> p.id).collect(Collectors.toSet());
        Map<UUID, BigDecimal> allocated = new HashMap<>();
        for (var allocation : data.allocations) if (validPayments.contains(allocation.paymentId))
            allocated.merge(allocation.dueId, allocation.amount, BigDecimal::add);
        for (var due : data.dues) {
            BigDecimal remaining = due.amount.subtract(allocated.getOrDefault(due.id, ForecastMath.ZERO));
            if (remaining.signum() < 0) throw new IllegalStateException("Due allocations exceed due amount.");
            if (remaining.signum() == 0) continue;
            LocalDate date = due.dueDate.isBefore(carryStart) ? projectionStart : due.dueDate;
            if (!date.isAfter(to)) events.add(new Event(date, "INSTALLMENT_DUE", due.id,
                "Installment due " + due.sequenceNumber + " · " + planNames.getOrDefault(due.planId, "plan"), remaining, true));
        }
    }

    private void hypothetical(List<Change> changes, LocalDate from, LocalDate to, List<Event> events) {
        for (var change : changes) {
            if ("ONE_TIME_EXPENSE".equals(change.type()))
                events.add(new Event(change.date(), "HYPOTHETICAL_EXPENSE", null, "What-if expense", amount(change.amount()), true));
            if ("NEW_INSTALLMENT".equals(change.type())) {
                BigDecimal total = amount(change.amount()).multiply(BigDecimal.valueOf(change.installmentCount()));
                events.add(new Event(from, "HYPOTHETICAL_OBLIGATION", null,
                    "What-if total installment commitment", total, true));
                for (var due : FixedSchedule.generate(total, change.installmentCount(), change.firstDueDate()))
                    if (within(due.dueDate(), from, to))
                        events.add(new Event(due.dueDate(), "HYPOTHETICAL_INSTALLMENT", null,
                            "What-if installment " + due.sequenceNumber(), due.amount(), true));
            }
        }
    }
}
