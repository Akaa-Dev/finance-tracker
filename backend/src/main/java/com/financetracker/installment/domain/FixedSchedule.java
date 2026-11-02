package com.financetracker.installment.domain;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
public final class FixedSchedule {
    private FixedSchedule() {}
    public record Due(int sequenceNumber, LocalDate dueDate, BigDecimal amount) {}
    public static List<Due> generate(BigDecimal total, int count, LocalDate first) {
        if (total == null || first == null || total.scale()>2 || total.signum()<=0 || count<1 || count>600)
            throw new IllegalArgumentException("Invalid fixed schedule.");
        BigDecimal regular=total.divide(BigDecimal.valueOf(count),2,RoundingMode.DOWN);
        if (regular.signum()==0) throw new IllegalArgumentException("Each due must be at least EUR 0.01.");
        List<Due> result=new ArrayList<>();
        for(int i=0;i<count;i++) result.add(new Due(i+1,first.plusMonths(i),
            i==count-1 ? total.subtract(regular.multiply(BigDecimal.valueOf(count-1))).setScale(2) : regular));
        return List.copyOf(result);
    }
    public static List<BigDecimal> allocate(BigDecimal payment, List<BigDecimal> remaining) {
        if (payment.scale()>2 || payment.signum()<=0 || remaining.stream().anyMatch(x->x.signum()<0)
            || payment.compareTo(remaining.stream().reduce(BigDecimal.ZERO,BigDecimal::add))>0)
            throw new IllegalArgumentException("Payment exceeds outstanding or is invalid.");
        List<BigDecimal> result=new ArrayList<>(); BigDecimal left=payment;
        for(var due:remaining) { var amount=left.min(due); result.add(amount); left=left.subtract(amount); }
        if(left.signum()!=0) throw new IllegalStateException("Unallocated payment.");
        return List.copyOf(result);
    }
}
