package com.financetracker.recurrence;
import com.financetracker.recurrence.domain.OccurrenceDates;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class OccurrenceDatesTest {
    private LocalDate d(String s) { return LocalDate.parse(s); }
    @Test void monthlyAnchorDoesNotDrift() {
        assertThat(OccurrenceDates.between("MONTHLY",d("2026-01-31"),d("2026-01-01"),null,d("2026-01-01"),d("2026-04-30")))
            .containsExactly(d("2026-01-31"),d("2026-02-28"),d("2026-03-31"),d("2026-04-30"));
    }
    @Test void yearlyLeapAnchorReturnsToFebruary29() {
        assertThat(OccurrenceDates.between("YEARLY",d("2024-02-29"),d("2024-01-01"),null,d("2027-01-01"),d("2028-12-30")))
            .containsExactly(d("2027-02-28"),d("2028-02-29"));
    }
    @Test void respectsInclusiveStartEndAndRangeBoundaries() {
        assertThat(OccurrenceDates.between("MONTHLY",d("2026-01-15"),d("2026-02-15"),d("2026-04-15"),d("2026-01-01"),d("2026-05-31")))
            .containsExactly(d("2026-02-15"),d("2026-03-15"),d("2026-04-15"));
    }
    @Test void anchorYearIsTemplateNotOccurrenceLowerBound() {
        assertThat(OccurrenceDates.between("YEARLY",d("2030-12-31"),d("2026-01-01"),null,d("2026-01-01"),d("2026-12-31")))
            .containsExactly(d("2026-12-31"));
    }
    @Test void dailyIncludesEveryEligibleDay() {
        assertThat(OccurrenceDates.between("DAILY",d("2026-01-01"),d("2026-02-02"),d("2026-02-04"),d("2026-02-01"),d("2026-02-05")))
            .containsExactly(d("2026-02-02"),d("2026-02-03"),d("2026-02-04"));
    }
    @Test void weeklyKeepsAnchorWeekdayAcrossMonths() {
        assertThat(OccurrenceDates.between("WEEKLY",d("2026-01-05"),d("2026-01-01"),null,d("2026-01-28"),d("2026-02-11")))
            .containsExactly(d("2026-02-02"),d("2026-02-09"));
    }
    @Test void rejectsUnboundedAndInvalidRanges() {
        assertThatThrownBy(()->OccurrenceDates.between("MONTHLY",d("2026-01-01"),d("2026-01-01"),null,d("2026-01-01"),d("2030-01-01"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->OccurrenceDates.between("BOGUS",d("2026-01-01"),d("2026-01-01"),null,d("2026-01-01"),d("2026-02-01"))).isInstanceOf(IllegalArgumentException.class);
    }
}
