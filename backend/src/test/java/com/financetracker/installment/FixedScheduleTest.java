package com.financetracker.installment;
import com.financetracker.installment.domain.FixedSchedule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FixedScheduleTest {
    @Test void exactCentsAndLastRemainder() {
        var dues=FixedSchedule.generate(new BigDecimal("100.00"),3,LocalDate.of(2026,1,31));
        assertThat(dues).extracting(FixedSchedule.Due::amount).containsExactly(new BigDecimal("33.33"),new BigDecimal("33.33"),new BigDecimal("33.34"));
        assertThat(dues.stream().map(FixedSchedule.Due::amount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("100.00");
        assertThat(dues).extracting(FixedSchedule.Due::dueDate).containsExactly(LocalDate.of(2026,1,31),LocalDate.of(2026,2,28),LocalDate.of(2026,3,31));
    }
    @Test void leapYearAndYearBoundaryKeepAnchor() {
        var dues=FixedSchedule.generate(new BigDecimal("1200.00"),6,LocalDate.of(2027,12,31));
        assertThat(dues.get(2).dueDate()).isEqualTo(LocalDate.of(2028,2,29));
        assertThat(dues.get(3).dueDate()).isEqualTo(LocalDate.of(2028,3,31));
    }
    @Test void rejectsFractionalCentsAndSubCentDues() {
        assertThatThrownBy(()->FixedSchedule.generate(new BigDecimal("1.001"),3,LocalDate.now())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->FixedSchedule.generate(new BigDecimal("0.02"),3,LocalDate.now())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void partialAndMultiDueAllocationsAreOldestFirst() {
        var dues=List.of(new BigDecimal("200.00"),new BigDecimal("200.00"));
        assertThat(FixedSchedule.allocate(new BigDecimal("80.00"),dues)).containsExactly(new BigDecimal("80.00"),new BigDecimal("0.00"));
        assertThat(FixedSchedule.allocate(new BigDecimal("300.00"),dues)).containsExactly(new BigDecimal("200.00"),new BigDecimal("100.00"));
        assertThat(FixedSchedule.allocate(new BigDecimal("120.00"),List.of(new BigDecimal("120.00"),new BigDecimal("200.00"))))
            .containsExactly(new BigDecimal("120.00"),new BigDecimal("0.00"));
    }
    @Test void rejectsZeroNegativePrecisionAndOverpayment() {
        for(String amount:List.of("0.00","-1.00","0.001","200.01"))
            assertThatThrownBy(()->FixedSchedule.allocate(new BigDecimal(amount),List.of(new BigDecimal("200.00")))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void largeAmountsStayExact() {
        var total=new BigDecimal("99999999999999999.99");var dues=FixedSchedule.generate(total,600,LocalDate.of(2026,1,31));
        assertThat(dues.stream().map(FixedSchedule.Due::amount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(total);
    }
}
