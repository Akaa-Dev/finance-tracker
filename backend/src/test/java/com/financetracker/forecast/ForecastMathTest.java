package com.financetracker.forecast;

import com.financetracker.forecast.api.ForecastContracts.Event;
import com.financetracker.forecast.domain.ForecastMath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ForecastMathTest {
    private static LocalDate day(String text) { return LocalDate.parse(text); }
    private static BigDecimal eur(String text) { return new BigDecimal(text); }

    @Test void dailyOrderCentsAndMonthBoundariesAreExact() {
        var result = ForecastMath.project(day("2026-01-31"), day("2026-02-02"), day("2026-01-30"), eur("0.01"), List.of(
            new Event(day("2026-02-01"), "RECURRING_INCOME", null, "Income", eur("0.03"), true),
            new Event(day("2026-01-31"), "INSTALLMENT_DUE", null, "Due", eur("0.02"), true),
            new Event(day("2026-02-02"), "PLANNED_EXPENSE", null, "Expense", eur("0.01"), true)));
        assertThat(result.startingBalance()).isEqualByComparingTo("0.01");
        assertThat(result.days()).extracting(d -> d.endingBalance().toPlainString()).containsExactly("-0.01", "0.02", "0.01");
        assertThat(result.lowestDailyBalance()).isEqualByComparingTo("-0.01");
        assertThat(result.months()).extracting(m -> m.month()).containsExactly("2026-01", "2026-02");
        assertThat(result.months().get(1).endingBalance()).isEqualByComparingTo("0.01");
    }

    @Test void preRangeProjectedEventsCarryIntoVisibleStartingBalance() {
        var result = ForecastMath.project(day("2026-02-02"), day("2026-02-02"), day("2026-01-31"), eur("10.00"), List.of(
            new Event(day("2026-02-01"), "RECURRING_EXPENSE", null, "Rent", eur("3.00"), true)));
        assertThat(result.startingBalance()).isEqualByComparingTo("7.00");
        assertThat(result.endingBalance()).isEqualByComparingTo("7.00");
        assertThat(result.events()).isEmpty();
    }
}
