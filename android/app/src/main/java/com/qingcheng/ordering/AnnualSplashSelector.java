package com.qingcheng.ordering;

import java.util.Calendar;
import java.util.Objects;
import java.util.function.Supplier;

final class AnnualSplashSelector {
    private static final int BIRTHDAY_DAY = 1;

    private final Supplier<Calendar> localCalendarSupplier;

    AnnualSplashSelector() {
        this(Calendar::getInstance);
    }

    AnnualSplashSelector(Supplier<Calendar> localCalendarSupplier) {
        this.localCalendarSupplier = Objects.requireNonNull(localCalendarSupplier);
    }

    int drawableResource() {
        return drawableResource(localCalendarSupplier.get());
    }

    static int drawableResource(Calendar localCalendar) {
        Objects.requireNonNull(localCalendar);
        return localCalendar.get(Calendar.MONTH) == Calendar.JANUARY
                && localCalendar.get(Calendar.DAY_OF_MONTH) == BIRTHDAY_DAY
                ? R.drawable.qingcheng_splash_birthday
                : R.drawable.qingcheng_splash_dog_walk;
    }
}
