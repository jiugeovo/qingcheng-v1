package com.example.ordering.wallet;

import com.example.ordering.user.UserAccount;
import com.example.ordering.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;

@Service
public class WalletService {

    public static final BigDecimal DAILY_REWARD = new BigDecimal("5.00");
    private static final int MAX_STREAK_BONUS_DAYS = 6;
    private static final String[] WEEKDAY_LABELS = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    private final UserRepository userRepository;
    private final WalletLedgerService walletLedgerService;
    private final WalletTransactionRepository transactionRepository;
    private final Clock clock;

    public WalletService(
            UserRepository userRepository,
            WalletLedgerService walletLedgerService,
            WalletTransactionRepository transactionRepository
    ) {
        this(userRepository, walletLedgerService, transactionRepository, Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WalletService(
            UserRepository userRepository,
            WalletLedgerService walletLedgerService,
            WalletTransactionRepository transactionRepository,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.walletLedgerService = walletLedgerService;
        this.transactionRepository = transactionRepository;
        this.clock = Objects.requireNonNull(clock, "clock").withZone(ZoneId.of("Asia/Shanghai"));
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(UserAccount user) {
        UserAccount current = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User does not exist"));
        return toResponse(current);
    }

    @Transactional
    public WalletResponse checkIn(UserAccount user) {
        UserAccount current = userRepository.findLockedById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User does not exist"));
        LocalDate today = LocalDate.now(clock);
        if (today.equals(current.getLastCheckInDate())) {
            throw new IllegalArgumentException("今天已经签到过了");
        }
        int previousStreak = currentStreak(current.getId(), current.getLastCheckInDate(), today.minusDays(1));
        int newStreak = previousStreak + 1;
        BigDecimal reward = rewardForStreak(newStreak);
        current.setQingchengCoins(current.getQingchengCoins().add(reward));
        current.setLastCheckInDate(today);
        UserAccount saved = userRepository.save(current);
        walletLedgerService.record(
                saved,
                WalletTransactionType.CHECK_IN,
                reward,
                "签到奖励",
                "连续签到 " + newStreak + " 天",
                today.toString()
        );
        return toResponse(saved);
    }

    private WalletResponse toResponse(UserAccount user) {
        LocalDate today = LocalDate.now(clock);
        List<WalletTransactionEntity> checkIns = transactionRepository.findTop120ByUserIdAndTypeOrderByCreatedAtDesc(
                user.getId(),
                WalletTransactionType.CHECK_IN
        );
        Set<LocalDate> checkInDates = checkInDates(checkIns, user.getLastCheckInDate());
        boolean checkedInToday = today.equals(user.getLastCheckInDate());
        int currentStreak = currentStreak(checkInDates, checkedInToday ? today : today.minusDays(1), user.getLastCheckInDate());
        BigDecimal todayReward = checkedInToday ? rewardForStreak(currentStreak) : rewardForStreak(currentStreak + 1);
        BigDecimal nextReward = rewardForStreak(checkedInToday ? currentStreak + 1 : currentStreak + 2);
        List<WalletResponse.CheckInDay> week = currentWeek(checkInDates, today);
        return new WalletResponse(
                user.getId(),
                user.getQingchengCoins(),
                user.getLastCheckInDate(),
                checkedInToday,
                todayReward,
                nextReward,
                currentStreak,
                today,
                currentDateLabel(today),
                today.getYear() + "年" + today.getMonthValue() + "月",
                week.stream().filter(WalletResponse.CheckInDay::checked).map(WalletResponse.CheckInDay::date).toList(),
                week,
                currentMonth(checkInDates, today),
                transactionRepository.findTop80ByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                        .map(WalletTransaction::from)
                        .toList()
        );
    }

    private int currentStreak(Long userId, LocalDate fallbackDate, LocalDate anchor) {
        List<WalletTransactionEntity> checkIns = transactionRepository.findTop120ByUserIdAndTypeOrderByCreatedAtDesc(
                userId,
                WalletTransactionType.CHECK_IN
        );
        return currentStreak(checkInDates(checkIns, fallbackDate), anchor, fallbackDate);
    }

    private Set<LocalDate> checkInDates(List<WalletTransactionEntity> checkIns, LocalDate fallbackDate) {
        Set<LocalDate> dates = new HashSet<>();
        for (WalletTransactionEntity transaction : checkIns) {
            LocalDate date = parseDate(transaction.getReferenceId());
            if (date == null && transaction.getCreatedAt() != null) {
                date = transaction.getCreatedAt().toLocalDate();
            }
            if (date != null) {
                dates.add(date);
            }
        }
        if (fallbackDate != null) {
            dates.add(fallbackDate);
        }
        return dates;
    }

    private int currentStreak(Set<LocalDate> checkInDates, LocalDate anchor, LocalDate fallbackDate) {
        if ((checkInDates == null || checkInDates.isEmpty()) && fallbackDate != null) {
            return 1;
        }
        LocalDate cursor = anchor;
        int streak = 0;
        while (checkInDates != null && checkInDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private BigDecimal rewardForStreak(int streak) {
        int bonusDays = Math.max(0, Math.min(streak - 1, MAX_STREAK_BONUS_DAYS));
        return DAILY_REWARD.add(new BigDecimal(bonusDays + ".00"));
    }

    private List<WalletResponse.CheckInDay> currentWeek(Set<LocalDate> checkInDates, LocalDate today) {
        LocalDate start = today.minusDays(today.getDayOfWeek().getValue() % 7L);
        List<WalletResponse.CheckInDay> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = start.plusDays(i);
            days.add(new WalletResponse.CheckInDay(
                    date,
                    WEEKDAY_LABELS[i],
                    checkInDates.contains(date)
            ));
        }
        return days;
    }

    private List<WalletResponse.CalendarDay> currentMonth(Set<LocalDate> checkInDates, LocalDate today) {
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate start = firstDay.minusDays(firstDay.getDayOfWeek().getValue() % 7L);
        List<WalletResponse.CalendarDay> days = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
            LocalDate date = start.plusDays(i);
            days.add(new WalletResponse.CalendarDay(
                    date,
                    WEEKDAY_LABELS[i % 7],
                    date.getDayOfMonth(),
                    date.getMonthValue() == today.getMonthValue() && date.getYear() == today.getYear(),
                    date.equals(today),
                    checkInDates.contains(date)
            ));
        }
        return days;
    }

    private String currentDateLabel(LocalDate date) {
        return date.getYear() + "年" + date.getMonthValue() + "月" + date.getDayOfMonth() + "日 " + WEEKDAY_LABELS[date.getDayOfWeek().getValue() % 7];
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }
}
