package com.qingcheng.ordering;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Native presentation only: authentication, requests and screen state belong to the activity. */
final class AnniversaryView extends LinearLayout {
    interface Actions {
        void onBack();
        void onAdd();
        void onRefresh();
        void onSelectDate(String date);
        void onChangeMonth(int delta);
        void onToday();
        default void onEdit(Anniversary record) { }
        default void onDelete(Anniversary record) { }
    }

    private static final int MINT = Color.rgb(33, 115, 94);
    private static final int MUTED = Color.rgb(96, 114, 105);
    private static final int PEACH = Color.rgb(252, 240, 226);
    private static final int LINE = Color.rgb(223, 230, 220);

    private final Actions actions;
    private AnniversaryYearMonthDialog monthPicker;
    private final Button add;
    private final TextView count;
    private final ScrollView scroll;
    private final LinearLayout status;
    private final LinearLayout calendar;
    private final LinearLayout details;
    private final LinearLayout upcoming;
    private final Set<Long> expandedNotes = new HashSet<>();
    private boolean scrollToDetails;
    private Button emptyAdd;
    private final List<Button> mutationButtons = new ArrayList<>();
    private Button upcomingRefresh;
    private TextView calendarTip;
    private final Map<String, CalendarDay> calendarDays = new LinkedHashMap<>();
    private StatusState renderedStatus;
    private CalendarState renderedCalendar;
    private DetailsState renderedDetails;
    private UpcomingState renderedUpcoming;
    private String renderedSelection;

    private record StatusState(boolean loading, String error, boolean hasRecords) { }
    private record CalendarState(List<Anniversary> events, String month, String today, boolean unavailable) { }
    private record CalendarDay(AnniversaryDayView view, String description) { }
    private record DetailsState(List<Anniversary> matches, String selected, String today,
                                boolean emptyRecords, boolean unavailable, boolean staleClock) { }
    private record UpcomingItem(long id, String title, AnniversaryType type, String date, String distance) { }
    private record UpcomingState(List<UpcomingItem> items, boolean unavailable) { }

    AnniversaryView(Context context, Actions actions) {
        super(context);
        this.actions = actions;
        setOrientation(VERTICAL);
        setBackgroundColor(Ui.SURFACE_BASE);

        LinearLayout header = row();
        header.setPadding(dp(8), dp(8), dp(12), dp(8));
        Button back = quietButton("‹", "返回智能中心", actions::onBack);
        back.setId(R.id.anniversary_back);
        back.setTextSize(28);
        header.addView(back, new LayoutParams(dp(48), dp(48)));
        LinearLayout heading = column();
        heading.addView(text("青程 · 共享日历", 11, MUTED, false));
        heading.addView(text("纪念日", 24, Ui.TEXT_PRIMARY, true));
        header.addView(heading, new LayoutParams(0, -2, 1));
        add = Ui.primaryButton(context, "+ 添加");
        add.setId(R.id.anniversary_add);
        add.setTextSize(14);
        add.setContentDescription("添加纪念日");
        add.setOnClickListener(ignored -> actions.onAdd());
        header.addView(add, new LayoutParams(dp(84), dp(48)));
        addView(header, new LayoutParams(-1, -2));

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content = column();
        content.setPadding(dp(12), dp(2), dp(12), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        addView(scroll, new LayoutParams(-1, 0, 1));

        HeroFrame hero = new HeroFrame(context);
        hero.setBackground(Ui.stroke(PEACH, dp(20), LINE, dp(1)));
        hero.setClipToOutline(true);
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;
        try {
            image.setImageBitmap(BitmapFactory.decodeResource(getResources(),
                    R.drawable.anniversary_bears_hero, options));
        } catch (Resources.NotFoundException ignored) {
            // The overlaid copy remains meaningful if artwork cannot be loaded.
        }
        hero.addView(image, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout heroCopy = column();
        heroCopy.addView(text("把重要的日子\n放在心上", 15, Ui.TEXT_PRIMARY, true));
        count = text("正在读取…", 11, MUTED, false);
        heroCopy.addView(count, margin(0, 8));
        FrameLayout.LayoutParams copyParams = new FrameLayout.LayoutParams(0, -2, Gravity.START | Gravity.CENTER_VERTICAL);
        copyParams.setMargins(dp(13), dp(10), 0, dp(10));
        hero.addView(heroCopy, copyParams);
        content.addView(hero, margin(14, 0));

        status = column();
        status.setId(R.id.anniversary_status);
        status.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status);
        calendar = card(Ui.SURFACE_RAISED, 8);
        calendar.setId(R.id.anniversary_calendar);
        content.addView(calendar, margin(18, 0));
        details = column();
        details.setId(R.id.anniversary_details);
        content.addView(details, margin(20, 0));
        upcoming = column();
        upcoming.setId(R.id.anniversary_upcoming);
        content.addView(upcoming);
    }

    void render(List<Anniversary> records, String visibleMonth, String selectedDate,
                String today, boolean loading, String error) {
        render(records, visibleMonth, selectedDate, today, loading, error, false);
    }

    void render(List<Anniversary> records, String visibleMonth, String selectedDate,
                String today, boolean loading, String error, boolean staleClock) {
        List<Anniversary> events = records == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(records));
        boolean failed = error != null && !error.trim().isEmpty();
        boolean unavailable = events.isEmpty() && (loading || failed);
        boolean canAdd = !loading && !unavailable;
        String countText = unavailable ? "日子值得珍藏" : "已收藏 " + events.size() + " 个日子";
        if (!countText.contentEquals(count.getText())) count.setText(countText);
        StatusState statusState = new StatusState(loading, error, !events.isEmpty());
        if (!statusState.equals(renderedStatus)) {
            renderStatus(loading, error, !events.isEmpty());
            renderedStatus = statusState;
        }
        renderCalendar(events, visibleMonth, selectedDate, today, unavailable);
        List<Anniversary> matches = Collections.unmodifiableList(eventsOn(events, selectedDate));
        DetailsState detailsState = new DetailsState(matches, selectedDate, today, events.isEmpty(), unavailable, staleClock);
        if (!detailsState.equals(renderedDetails)) {
            details.removeAllViews();
            mutationButtons.clear();
            emptyAdd = null;
            if (!unavailable) renderDetails(matches, events.isEmpty(), selectedDate, today, staleClock);
            renderedDetails = detailsState;
        }
        setCanAdd(canAdd);
        UpcomingState upcomingState = new UpcomingState(upcomingItems(events, today, staleClock), unavailable);
        if (!upcomingState.equals(renderedUpcoming)) {
            upcoming.removeAllViews();
            upcomingRefresh = null;
            if (!unavailable) renderUpcoming(upcomingState.items());
            renderedUpcoming = upcomingState;
        }
        if (upcomingRefresh != null) upcomingRefresh.setEnabled(!loading);
        if (scrollToDetails && !unavailable) {
            scrollToDetails = false;
            revealDetails();
        }
    }

    void setCanAdd(boolean canAdd) {
        for (Button button : mutationButtons) {
            button.setEnabled(canAdd);
            button.setAlpha(canAdd ? 1f : .5f);
        }
        add.setEnabled(canAdd);
        add.setAlpha(canAdd ? 1f : .5f);
        if (emptyAdd != null) {
            emptyAdd.setEnabled(canAdd);
            emptyAdd.setAlpha(canAdd ? 1f : .5f);
        }
    }

    private void renderStatus(boolean loading, String error, boolean hasRecords) {
        status.removeAllViews();
        if (loading) {
            status.addView(text(hasRecords ? "正在更新纪念日…" : "正在加载纪念日…", 14, MUTED, false), margin(12, 0));
        }
        if (error == null || error.trim().isEmpty()) return;
        LinearLayout message = card(PEACH, 12);
        message.addView(text(hasRecords ? "更新失败，仍显示上次读取的记录" : "纪念日暂时无法加载", 15, Ui.TEXT_PRIMARY, true));
        message.addView(text(error, 13, MUTED, false), margin(6, 4));
        Button retry = Ui.secondaryButton(getContext(), "重新加载");
        retry.setId(R.id.anniversary_refresh);
        retry.setEnabled(!loading);
        retry.setContentDescription("重新加载纪念日");
        retry.setOnClickListener(ignored -> actions.onRefresh());
        message.addView(retry, new LayoutParams(-1, dp(48)));
        status.addView(message, margin(12, 0));
    }

    private void renderCalendar(List<Anniversary> events, String month, String selected,
                                String today, boolean unavailable) {
        CalendarState next = new CalendarState(events, month, today, unavailable);
        if (renderedCalendar == null || !month.equals(renderedCalendar.month())) buildCalendar(month);
        if (!next.equals(renderedCalendar)) {
            bindCalendar(events, month, selected, today, unavailable);
        } else if (!Objects.equals(renderedSelection, selected)) {
            updateSelection(renderedSelection, false);
            updateSelection(selected, true);
        }
        renderedCalendar = next;
        renderedSelection = selected;
    }

    private void buildCalendar(String month) {
        View oldFocus = calendar.findFocus();
        Object focusedDate = oldFocus == null ? null : oldFocus.getTag();
        calendar.removeAllViews();
        calendarDays.clear();
        LinearLayout toolbar = row();
        Button title = quietButton(AnniversaryDates.formatMonth(month), "选择年份和月份，当前" + AnniversaryDates.formatMonth(month),
                () -> showMonthPicker(month));
        title.setId(R.id.anniversary_month_picker);
        title.setTextSize(16);
        title.setMaxLines(2);
        title.setPadding(dp(4), 0, dp(4), 0);
        title.setBackground(Ui.round(Color.rgb(235, 246, 239), dp(12)));
        toolbar.addView(title, new LayoutParams(0, -2, 1));
        Button previous = quietButton("‹", "上个月", () -> actions.onChangeMonth(-1));
        previous.setTextSize(24);
        previous.setEnabled(AnniversaryDates.shiftMonth(month, -1) != null);
        toolbar.addView(previous, new LayoutParams(dp(48), dp(48)));
        toolbar.addView(quietButton("今天", "回到今天", actions::onToday), new LayoutParams(dp(48), dp(48)));
        Button next = quietButton("›", "下个月", () -> actions.onChangeMonth(1));
        next.setTextSize(24);
        next.setEnabled(AnniversaryDates.shiftMonth(month, 1) != null);
        toolbar.addView(next, new LayoutParams(dp(48), dp(48)));
        calendar.addView(toolbar);
        calendar.addView(text("点击上方年月，快速选择年份和月份", 11, MUTED, false), margin(4, 6));
        LinearLayout weekdays = row();
        for (String label : new String[]{"一", "二", "三", "四", "五", "六", "日"}) {
            TextView day = text(label, 12, MUTED, false);
            day.setGravity(Gravity.CENTER);
            weekdays.addView(day, new LayoutParams(0, dp(28), 1));
        }
        calendar.addView(weekdays);
        List<String> days = AnniversaryDates.monthDays(month);
        for (int week = 0; week < 6; week++) {
            LinearLayout weekRow = row();
            for (int column = 0; column < 7; column++) {
                String date = days.get(week * 7 + column);
                if (date == null) {
                    weekRow.addView(new View(getContext()), new LayoutParams(0, dp(56), 1));
                    continue;
                }
                AnniversaryDayView day = new AnniversaryDayView(getContext());
                day.setTag("anniversary-day:" + date);
                calendarDays.put(date, new CalendarDay(day, ""));
                day.setFocusable(true);
                // The 56 dp touch target is separate from its centered 34 dp date face.
                day.setOnClickListener(ignored -> actions.onSelectDate(date));
                weekRow.addView(day, new LayoutParams(0, dp(56), 1));
                if (day.getTag().equals(focusedDate)) day.requestFocus();
            }
            calendar.addView(weekRow);
        }
        View rule = new View(getContext());
        rule.setBackgroundColor(LINE);
        LayoutParams ruleParams = new LayoutParams(-1, dp(1));
        ruleParams.setMargins(dp(7), dp(10), dp(7), dp(8));
        calendar.addView(rule, ruleParams);
        LinearLayout legend = row();
        legend.setGravity(Gravity.CENTER);
        legend.addView(markerLabel(AnniversaryType.Marker.HEART, "感情纪念"));
        legend.addView(markerLabel(AnniversaryType.Marker.CAKE, "生日"));
        legend.addView(markerLabel(AnniversaryType.Marker.FLAG, "其他"));
        calendar.addView(legend);
        calendarTip = text("", 11, MUTED, false);
        calendarTip.setGravity(Gravity.CENTER);
        calendar.addView(calendarTip, margin(4, 8));
    }

    private void bindCalendar(List<Anniversary> events, String month, String selected,
                              String today, boolean unavailable) {
        for (Map.Entry<String, CalendarDay> entry : calendarDays.entrySet()) {
            String date = entry.getKey();
            List<Anniversary> matches = eventsOn(events, date);
            Set<AnniversaryType.Marker> markers = EnumSet.noneOf(AnniversaryType.Marker.class);
            Set<AnniversaryType> types = EnumSet.noneOf(AnniversaryType.class);
            for (Anniversary match : matches) {
                markers.add(match.type().marker());
                types.add(match.type());
            }
            boolean chosen = date.equals(selected);
            boolean isToday = date.equals(today);
            AnniversaryDayView day = entry.getValue().view();
            day.setState(AnniversaryDates.dayOfMonth(date), markers, chosen, isToday,
                    !AnniversaryDates.monthOf(date).equals(month));
            StringBuilder typeLabels = new StringBuilder();
            for (AnniversaryType type : types) typeLabels.append("，").append(type.label());
            String description = AnniversaryDates.formatDate(date) + (isToday ? "，今天" : "")
                    + (unavailable ? "，记录待加载" : "，" + matches.size() + " 个纪念日" + typeLabels);
            entry.setValue(new CalendarDay(day, description));
            updateSelection(date, chosen);
        }
        String tip = unavailable ? "纪念日标记将在加载后显示" : "描边是今天 · 实色是所选日期";
        if (!tip.contentEquals(calendarTip.getText())) calendarTip.setText(tip);
    }

    private void updateSelection(String date, boolean selected) {
        CalendarDay day = calendarDays.get(date);
        if (day == null) return;
        day.view().setDateSelected(selected);
        String description = day.description() + (selected ? "，已选择" : "");
        if (!description.contentEquals(day.view().getContentDescription() == null
                ? "" : day.view().getContentDescription())) day.view().setContentDescription(description);
    }

    private void renderDetails(List<Anniversary> matches, boolean emptyRecords, String selected,
                               String today, boolean staleClock) {
        TextView heading = text(AnniversaryDates.formatDate(selected) + (selected.equals(today) ? " · 今天" : ""), 18, Ui.TEXT_PRIMARY, true);
        heading.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        details.addView(heading, margin(10, 0));
        if (!matches.isEmpty()) {
            for (Anniversary event : matches) details.addView(eventCard(event, today, staleClock), margin(10, 0));
            return;
        }
        LinearLayout empty = card(Ui.SURFACE_RAISED, 16);
        empty.addView(text(emptyRecords ? "故事，从一个日子开始" : "这一天，留白也很好", 17, Ui.TEXT_PRIMARY, true));
        empty.addView(text(emptyRecords ? "从一个值得记住的日子开始，慢慢收藏你们的回忆。"
                : "可以记下这一天，也可以看看月历上有标记的日子。", 14, MUTED, false), margin(12, 6));
        Button record = Ui.secondaryButton(getContext(), emptyRecords ? "添加第一个纪念日" : "记录这一天");
        emptyAdd = record;
        record.setTag("anniversary-empty-add");
        record.setOnClickListener(ignored -> actions.onAdd());
        empty.addView(record, new LayoutParams(-1, dp(48)));
        details.addView(empty);
    }

    private View eventCard(Anniversary event, String today, boolean staleClock) {
        boolean ended = !event.repeatYearly() && event.date().compareTo(today) < 0;
        LinearLayout card = card(ended ? Color.rgb(238, 238, 231)
                : event.repeatYearly() ? Color.rgb(232, 242, 233) : PEACH, 16);
        card.addView(text(event.title(), 18, Ui.TEXT_PRIMARY, true));
        card.addView(markerLabel(event.type().marker(), event.type().label()), margin(0, 6));
        card.addView(text(AnniversaryDates.formatDate(event.date()) + " · "
                + (event.repeatYearly() ? "每年重复" : "一次性") + (ended ? " · 已结束" : ""), 12, MUTED, false), margin(12, 5));
        LinearLayout metrics = row();
        long elapsed = AnniversaryDates.daysBetween(event.date(), today);
        metrics.addView(metric("已经走过", elapsed < 0 ? "尚未开始" : elapsed + " 天"), new LayoutParams(0, -2, 1));
        View divider = new View(getContext());
        divider.setBackgroundColor(Color.argb(32, 40, 61, 52));
        LayoutParams dividerParams = new LayoutParams(dp(1), -1);
        dividerParams.setMargins(dp(8), 0, dp(12), 0);
        metrics.addView(divider, dividerParams);
        boolean stale = staleClock || (event.nextOccurrence() != null && event.nextOccurrence().compareTo(today) < 0);
        String until = stale ? "待刷新" : event.daysUntil() != null ? event.daysUntil() + " 天"
                : ended ? "已结束" : "暂无下次";
        metrics.addView(metric("距离下次", until), new LayoutParams(0, -2, 1));
        card.addView(metrics, margin(16, 8));
        String next = stale ? (event.nextOccurrence() == null ? "计时待刷新，请重新加载纪念日。"
                : "上次同步 · " + AnniversaryDates.formatDate(event.nextOccurrence()) + "，计时待刷新") : event.nextOccurrence() != null
                ? "下一次 · " + AnniversaryDates.formatDate(event.nextOccurrence())
                    + (Long.valueOf(0).equals(event.daysUntil()) ? "，就是今天" : "")
                : ended ? "这一天已经过去，回忆仍然保留。" : "可计算日期范围内暂无下次纪念日。";
        View rule = new View(getContext());
        rule.setBackgroundColor(Color.argb(24, 40, 61, 52));
        card.addView(rule, new LayoutParams(-1, dp(1)));
        card.addView(text(next, 12, MUTED, false), margin(0, 10));
        if (!event.note().isEmpty()) {
            TextView note = text(event.note(), 14, MUTED, false);
            note.setTextIsSelectable(true);
            note.setLineSpacing(dp(3), 1f);
            note.setVisibility(expandedNotes.contains(event.id()) ? VISIBLE : GONE);
            Button toggle = quietButton(expandedNotes.contains(event.id()) ? "收起备注 −" : "读一读那天的备注 +",
                    "展开或收起" + event.title() + "的备注", () -> { });
            toggle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            toggle.setOnClickListener(ignored -> {
                boolean expanded = !expandedNotes.contains(event.id());
                if (expanded) expandedNotes.add(event.id()); else expandedNotes.remove(event.id());
                note.setVisibility(expanded ? VISIBLE : GONE);
                toggle.setText(expanded ? "收起备注 −" : "读一读那天的备注 +");
            });
            card.addView(toggle, margin(0, 5));
            card.addView(note);
        }
        if (event.canManage()) {
            LinearLayout controls = row();
            Button edit = Ui.secondaryButton(getContext(), "编辑");
            edit.setTag("anniversary-edit:" + event.id());
            edit.setContentDescription("编辑" + event.title());
            edit.setOnClickListener(ignored -> actions.onEdit(event));
            Button delete = Ui.secondaryButton(getContext(), "删除");
            delete.setTextColor(Ui.DANGER);
            delete.setTag("anniversary-delete:" + event.id());
            delete.setContentDescription("删除" + event.title());
            delete.setOnClickListener(ignored -> actions.onDelete(event));
            controls.addView(edit, new LayoutParams(0, dp(48), 1));
            LayoutParams deleteParams = new LayoutParams(0, dp(48), 1);
            deleteParams.setMargins(dp(10), 0, 0, 0);
            controls.addView(delete, deleteParams);
            mutationButtons.add(edit);
            mutationButtons.add(delete);
            card.addView(controls, margin(12, 0));
        }
        return card;
    }

    private View metric(String label, String value) {
        LinearLayout metric = column();
        metric.addView(text(label, 12, MUTED, false));
        metric.addView(text(value, value.length() > 8 ? 19 : 25, MINT, true), margin(0, 5));
        return metric;
    }

    private List<UpcomingItem> upcomingItems(List<Anniversary> events, String today, boolean staleClock) {
        List<Anniversary> sorted = new ArrayList<>();
        for (Anniversary event : events) {
            if (event.nextOccurrence() != null && event.nextOccurrence().compareTo(today) >= 0) sorted.add(event);
        }
        Collections.sort(sorted, (left, right) -> {
            int date = left.nextOccurrence().compareTo(right.nextOccurrence());
            return date != 0 ? date : Long.compare(left.id(), right.id());
        });
        List<UpcomingItem> items = new ArrayList<>();
        for (Anniversary event : sorted.subList(0, Math.min(3, sorted.size()))) {
            String distance = staleClock ? " · 计时待刷新" : event.daysUntil() == null ? "" : Long.valueOf(0).equals(event.daysUntil())
                    ? " · 就是今天" : " · " + event.daysUntil() + " 天后";
            items.add(new UpcomingItem(event.id(), event.title(), event.type(), event.nextOccurrence(), distance));
        }
        return Collections.unmodifiableList(items);
    }

    private void renderUpcoming(List<UpcomingItem> items) {
        LinearLayout heading = row();
        heading.addView(text("接下来，值得期待", 18, Ui.TEXT_PRIMARY, true), new LayoutParams(0, -2, 1));
        upcomingRefresh = quietButton("刷新", "刷新纪念日", actions::onRefresh);
        heading.addView(upcomingRefresh, new LayoutParams(dp(56), dp(48)));
        upcoming.addView(heading, margin(8, 0));
        if (items.isEmpty()) {
            upcoming.addView(text("暂时没有即将到来的纪念日。", 14, MUTED, false));
        }
        for (UpcomingItem event : items) {
            LinearLayout item = card(Ui.SURFACE_RAISED, 14);
            item.addView(text(event.title(), 16, Ui.TEXT_PRIMARY, true));
            item.addView(markerLabel(event.type().marker(), event.type().label()), margin(0, 5));
            item.addView(text(AnniversaryDates.formatDate(event.date()) + event.distance() + "  ›", 13, MUTED, false), margin(0, 4));
            item.setContentDescription("查看" + event.title() + "，" + event.type().label() + "，"
                    + AnniversaryDates.formatDate(event.date()) + event.distance());
            item.setFocusable(true);
            item.setMinimumHeight(dp(64));
            item.setOnClickListener(ignored -> {
                if (event.date().equals(renderedSelection)) revealDetails();
                else scrollToDetails = true;
                actions.onSelectDate(event.date());
            });
            upcoming.addView(item, margin(8, 0));
        }
    }

    private void revealDetails() {
        scroll.post(() -> {
            int target = Math.max(0, details.getTop() - dp(12));
            if (MotionPolicy.systemAnimatorsEnabled(getContext())) scroll.smoothScrollTo(0, target);
            else scroll.scrollTo(0, target);
        });
    }

    private List<Anniversary> eventsOn(List<Anniversary> events, String date) {
        List<Anniversary> matches = new ArrayList<>();
        for (Anniversary event : events) {
            if (date.equals(AnniversaryDates.occurrenceInYear(event.date(), event.repeatYearly(),
                    AnniversaryDates.yearOf(date)))) matches.add(event);
        }
        Collections.sort(matches, (left, right) -> {
            int dateOrder = left.date().compareTo(right.date());
            return dateOrder != 0 ? dateOrder : Long.compare(left.id(), right.id());
        });
        return matches;
    }

    private void showMonthPicker(String month) {
        if (monthPicker != null && monthPicker.isShowing()) return;
        AnniversaryYearMonthDialog picker = new AnniversaryYearMonthDialog(getContext(), month, true,
                selected -> actions.onSelectDate(selected + "-01"));
        monthPicker = picker;
        picker.setOnDismissListener(ignored -> { if (monthPicker == picker) monthPicker = null; });
        picker.show();
    }

    @Override protected void onDetachedFromWindow() {
        if (monthPicker != null) monthPicker.dismiss();
        super.onDetachedFromWindow();
    }

    private Button quietButton(String label, String description, Runnable action) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setTextColor(MINT);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setMinimumWidth(0);
        button.setMinimumHeight(dp(48));
        button.setPadding(0, 0, 0, 0);
        button.setBackground(Ui.round(Color.TRANSPARENT, dp(12)));
        button.setContentDescription(description);
        button.setOnClickListener(ignored -> action.run());
        return button;
    }

    private TextView markerLabel(AnniversaryType.Marker marker, String label) {
        TextView text = text(label, 11, AnniversaryMarkerDrawable.colorFor(marker), false);
        AnniversaryMarkerDrawable icon = new AnniversaryMarkerDrawable(marker);
        icon.setBounds(0, 0, dp(16), dp(16));
        text.setCompoundDrawablesRelative(icon, null, null, null);
        text.setCompoundDrawablePadding(dp(3));
        text.setPadding(dp(5), 0, dp(5), 0);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    private LinearLayout card(int color, int padding) {
        LinearLayout card = column();
        card.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        card.setBackground(Ui.stroke(color, dp(20), LINE, dp(1)));
        card.setClipToOutline(true);
        return card;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        return Ui.text(getContext(), value, size, color, bold ? Typeface.BOLD : Typeface.NORMAL);
    }

    private LayoutParams margin(int bottom, int top) {
        LayoutParams params = new LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(top), 0, dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Ui.dp(getContext(), value);
    }

    private static final class HeroFrame extends FrameLayout {
        HeroFrame(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            View copy = getChildAt(1);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) copy.getLayoutParams();
            params.width = width * 39 / 100;
            copy.measure(MeasureSpec.makeMeasureSpec(params.width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int height = Math.max(width * 10 / 27, copy.getMeasuredHeight() + params.topMargin + params.bottomMargin);
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }
}
