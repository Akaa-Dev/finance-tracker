package com.financetracker.recurrence.domain;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
public final class OccurrenceDates {
    private OccurrenceDates() {}
    public static List<LocalDate> between(String frequency,LocalDate anchor,LocalDate start,LocalDate end,LocalDate from,LocalDate to) {
        if(anchor==null || start==null || from==null || to==null || to.isBefore(from) || ChronoUnit.DAYS.between(from,to)>730
            || (end!=null && end.isBefore(start)) || !("DAILY".equals(frequency)||"WEEKLY".equals(frequency)||"MONTHLY".equals(frequency)||"YEARLY".equals(frequency))) throw new IllegalArgumentException("Invalid occurrence range (max 730 days) or rule.");
        List<LocalDate> result=new ArrayList<>();
        var lower=from.isAfter(start) ? from : start;
        var upper=end!=null && end.isBefore(to) ? end : to;
        if(lower.isAfter(upper)) return List.of();
        if(frequency.equals("DAILY")) {
            for(var date=lower;!date.isAfter(upper);date=date.plusDays(1)) result.add(date);
            return List.copyOf(result);
        }
        if(frequency.equals("WEEKLY")) {
            for(var date=lower.with(TemporalAdjusters.nextOrSame(anchor.getDayOfWeek()));!date.isAfter(upper);date=date.plusWeeks(1)) result.add(date);
            return List.copyOf(result);
        }
        for(var month=YearMonth.from(from);!month.isAfter(YearMonth.from(to));month=month.plusMonths(1)) {
            if(frequency.equals("YEARLY") && month.getMonthValue()!=anchor.getMonthValue()) continue;
            var date=month.atDay(Math.min(anchor.getDayOfMonth(),month.lengthOfMonth()));
            if(!date.isBefore(lower) && !date.isAfter(upper)) result.add(date);
        }
        return List.copyOf(result);
    }
}
