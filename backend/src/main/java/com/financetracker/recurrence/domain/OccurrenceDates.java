package com.financetracker.recurrence.domain;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
public final class OccurrenceDates {
    private OccurrenceDates() {}
    public static List<LocalDate> between(String frequency,LocalDate anchor,LocalDate start,LocalDate end,LocalDate from,LocalDate to) {
        if(anchor==null || start==null || from==null || to==null || to.isBefore(from) || ChronoUnit.DAYS.between(from,to)>730
            || (end!=null && end.isBefore(start)) || !("MONTHLY".equals(frequency)||"YEARLY".equals(frequency))) throw new IllegalArgumentException("Invalid occurrence range (max 730 days) or rule.");
        List<LocalDate> result=new ArrayList<>();
        for(var month=YearMonth.from(from);!month.isAfter(YearMonth.from(to));month=month.plusMonths(1)) {
            if(frequency.equals("YEARLY") && month.getMonthValue()!=anchor.getMonthValue()) continue;
            var date=month.atDay(Math.min(anchor.getDayOfMonth(),month.lengthOfMonth()));
            if(!date.isBefore(from) && !date.isAfter(to) && !date.isBefore(start) && (end==null || !date.isAfter(end))) result.add(date);
        }
        return List.copyOf(result);
    }
}
