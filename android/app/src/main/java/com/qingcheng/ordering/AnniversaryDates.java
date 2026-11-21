package com.qingcheng.ordering;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Strict proleptic Gregorian date arithmetic that works on Android API 23 without desugaring. */
final class AnniversaryDates {
    private static final long DAY_MILLIS = 86_400_000L;
    private static final int MIN_YEAR = 1000;
    private static final int MAX_YEAR = 9999;

    private AnniversaryDates() {
    }

    static boolean isValidDate(String date) {
        try {
            parse(date);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static String today(long millis) {
        GregorianCalendar clock = calendar(TimeZone.getTimeZone("Asia/Shanghai"));
        clock.setTimeInMillis(millis);
        return iso(clock);
    }

    static String monthOf(String date) {
        parse(date);
        return date.substring(0, 7);
    }

    static int yearOf(String date) {
        return parse(date).get(Calendar.YEAR);
    }

    static int dayOfMonth(String date) {
        return parse(date).get(Calendar.DAY_OF_MONTH);
    }

    static String shiftMonth(String month, int delta) {
        GregorianCalendar first = parseMonth(month);
        long shifted = (long) first.get(Calendar.YEAR) * 12 + first.get(Calendar.MONTH) + delta;
        if (shifted < MIN_YEAR * 12L || shifted >= (MAX_YEAR + 1L) * 12) {
            return null;
        }
        return String.format(Locale.ROOT, "%04d-%02d", shifted / 12, shifted % 12 + 1);
    }

    /** Monday-first grid; unrepresentable edge dates are explicit, non-clickable null cells. */
    static List<String> monthDays(String month) {
        GregorianCalendar cursor = parseMonth(month);
        int mondayOffset = (cursor.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        cursor.add(Calendar.DAY_OF_MONTH, -mondayOffset);
        List<String> result = new ArrayList<>(42);
        for (int index = 0; index < 42; index++) {
            int year = cursor.get(Calendar.YEAR);
            result.add(year < MIN_YEAR || year > MAX_YEAR ? null : iso(cursor));
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }
        return Collections.unmodifiableList(result);
    }

    static String withYearMonth(String original, String month) {
        if (!isValidDate(original) || !isValidDate(month + "-01"))
            throw new IllegalArgumentException("无效的日期");
        int day = dayOfMonth(original);
        String result;
        do {
            result = month + String.format(Locale.ROOT, "-%02d", day--);
        } while (!isValidDate(result));
        return result;
    }

    static String occurrenceInYear(String date, boolean repeatYearly, int year) {
        GregorianCalendar original = parse(date);
        int originalYear = original.get(Calendar.YEAR);
        if (year < originalYear || year < MIN_YEAR || year > MAX_YEAR
                || (!repeatYearly && year != originalYear)) {
            return null;
        }
        int month = original.get(Calendar.MONTH);
        int day = original.get(Calendar.DAY_OF_MONTH);
        if (month == Calendar.FEBRUARY && day == 29 && !original.isLeapYear(year)) {
            day = 28;
        }
        return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day);
    }

    static long daysBetween(String from, String to) {
        return (parse(to).getTimeInMillis() - parse(from).getTimeInMillis()) / DAY_MILLIS;
    }

    static String nextOccurrence(String date, boolean repeatYearly, String today) {
        parse(date);
        int year = yearOf(today);
        if (date.compareTo(today) >= 0) {
            return date;
        }
        if (!repeatYearly) {
            return null;
        }
        String candidate = occurrenceInYear(date, true, year);
        return candidate.compareTo(today) >= 0 ? candidate : occurrenceInYear(date, true, year + 1);
    }

    /** Keep new annual memories visible in this year, including those already celebrated. */
    static String selectionAfterCreate(Anniversary anniversary, String today) {
        String occurrence = occurrenceInYear(anniversary.date(), anniversary.repeatYearly(), yearOf(today));
        return occurrence == null ? anniversary.date() : occurrence;
    }

    static String formatDate(String date) {
        GregorianCalendar value = parse(date);
        return value.get(Calendar.YEAR) + "年" + (value.get(Calendar.MONTH) + 1) + "月"
                + value.get(Calendar.DAY_OF_MONTH) + "日";
    }

    static String formatMonth(String month) {
        GregorianCalendar value = parseMonth(month);
        return value.get(Calendar.YEAR) + "年" + (value.get(Calendar.MONTH) + 1) + "月";
    }

    private static GregorianCalendar parseMonth(String month) {
        if (month == null || !month.matches("[0-9]{4}-[0-9]{2}")) {
            throw new IllegalArgumentException("月份格式必须为 yyyy-MM");
        }
        return parse(month + "-01");
    }

    private static GregorianCalendar parse(String date) {
        if (date == null || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException("日期格式必须为 yyyy-MM-dd");
        }
        int year = Integer.parseInt(date.substring(0, 4));
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new IllegalArgumentException("日期年份必须在 1000 到 9999 之间");
        }
        GregorianCalendar value = calendar(TimeZone.getTimeZone("UTC"));
        value.set(year, Integer.parseInt(date.substring(5, 7)) - 1,
                Integer.parseInt(date.substring(8, 10)));
        value.getTimeInMillis();
        return value;
    }

    private static GregorianCalendar calendar(TimeZone zone) {
        GregorianCalendar value = new GregorianCalendar(zone, Locale.ROOT);
        value.setGregorianChange(new Date(Long.MIN_VALUE));
        value.clear();
        value.setLenient(false);
        return value;
    }

    private static String iso(GregorianCalendar value) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d", value.get(Calendar.YEAR),
                value.get(Calendar.MONTH) + 1, value.get(Calendar.DAY_OF_MONTH));
    }
}
