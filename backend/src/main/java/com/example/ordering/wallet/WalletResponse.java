package com.example.ordering.wallet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WalletResponse(
        Long userId,
        BigDecimal qingchengCoins,
        LocalDate lastCheckInDate,
        boolean checkedInToday,
        BigDecimal dailyReward,
        BigDecimal nextReward,
        int currentStreak,
        LocalDate currentDate,
        String currentDateLabel,
        String currentMonthLabel,
        List<LocalDate> weekCheckInDates,
        List<CheckInDay> checkInWeek,
        List<CalendarDay> monthCalendar,
        List<WalletTransaction> transactions
) {

    public record CheckInDay(
            LocalDate date,
            String weekday,
            boolean checked
    ) {
    }

    public record CalendarDay(
            LocalDate date,
            String weekday,
            int dayOfMonth,
            boolean currentMonth,
            boolean today,
            boolean checked
    ) {
    }
}
