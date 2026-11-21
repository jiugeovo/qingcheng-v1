package com.qingcheng.ordering;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Map;

/** A recoverable draft editor. Saving and transport errors are controlled by the host. */
final class AnniversaryEditorDialog extends AlertDialog {
    interface Listener {
        void onSave(AnniversaryDraft draft);
        void onDismiss(AnniversaryDraft draft);
    }

    private final Listener listener;
    private final EditText title;
    private final EditText date;
    private final EditText note;
    private final CheckBox repeat;
    private final Button pickDate;
    private final Button pickYear;
    private AnniversaryYearMonthDialog yearPicker;
    private final TextView error;
    private final AnniversaryTypePicker typePicker;
    private final AnniversaryDayView typePreview;
    private final TextView typeHint;
    private DatePickerDialog datePicker;
    private boolean busy;
    private boolean submissionBlocked;

    private final boolean editing;

    AnniversaryEditorDialog(Context context, AnniversaryDraft initial, Listener listener) {
        this(context, initial, listener, false);
    }

    AnniversaryEditorDialog(Context context, AnniversaryDraft initial, Listener listener, boolean editing) {
        super(context);
        this.editing = editing;
        this.listener = listener;
        AnniversaryDraft draft = initial == null
                ? new AnniversaryDraft("", AnniversaryDates.today(System.currentTimeMillis()), "", true) : initial;
        setTitle(editing ? "编辑纪念日" : "添加纪念日");
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(12));
        form.addView(copy("记下一个值得放在心上的日子，所有登录用户都能看到。", 14, Ui.TEXT_SECONDARY), margin(16));

        TextView typeLabel = copy("想珍藏哪一种日子？", 14, Ui.TEXT_PRIMARY);
        typeLabel.setLabelFor(R.id.anniversary_editor_type);
        form.addView(typeLabel, margin(5));
        typePicker = new AnniversaryTypePicker(context, draft.type(), ignored -> updateTypePreview());
        form.addView(typePicker, margin(10));
        LinearLayout previewRow = new LinearLayout(context);
        previewRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        previewRow.setPadding(dp(10), dp(6), dp(10), dp(6));
        previewRow.setBackground(Ui.round(Color.rgb(243, 240, 231), dp(12)));
        typePreview = new AnniversaryDayView(context);
        typePreview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        previewRow.addView(typePreview, new LinearLayout.LayoutParams(dp(44), dp(56)));
        typeHint = copy("", 12, Ui.TEXT_SECONDARY);
        typeHint.setId(R.id.anniversary_editor_type_hint);
        typeHint.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(0, -2, 1);
        hintParams.setMargins(dp(10), 0, 0, 0);
        previewRow.addView(typeHint, hintParams);
        form.addView(previewRow, margin(18));

        title = field("例如：第一次见面", R.id.anniversary_editor_title);
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        title.setSingleLine(true);
        title.setText(draft.title());
        addField(form, "纪念日名称 *", title);
        form.addView(copy("最多 100 个字", 12, Ui.TEXT_SECONDARY), margin(12));

        date = field("YYYY-MM-DD", R.id.anniversary_editor_date);
        date.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        date.setSingleLine(true);
        date.setText(draft.date());
        addField(form, "开始日期 *", date);
        pickDate = Ui.secondaryButton(context, "选择日期");
        pickDate.setOnClickListener(ignored -> showDatePicker());
        LinearLayout dateActions = new LinearLayout(context);
        dateActions.addView(pickDate, new LinearLayout.LayoutParams(0, dp(48), 1));
        pickYear = Ui.secondaryButton(context, "选择年份");
        pickYear.setId(R.id.anniversary_editor_year);
        pickYear.setOnClickListener(ignored -> showYearPicker());
        LinearLayout.LayoutParams yearParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        yearParams.setMargins(dp(8), 0, 0, 0);
        dateActions.addView(pickYear, yearParams);
        form.addView(dateActions, margin(6));
        form.addView(copy("可手动输入，年份范围 1000–9999", 12, Ui.TEXT_SECONDARY), margin(12));

        repeat = new CheckBox(context);
        repeat.setId(R.id.anniversary_editor_repeat);
        repeat.setText("每年重复");
        repeat.setTextSize(15);
        repeat.setTextColor(Ui.TEXT_PRIMARY);
        repeat.setMinHeight(dp(48));
        repeat.setChecked(draft.repeatYearly());
        form.addView(repeat, margin(2));
        form.addView(copy("关闭后，只在开始日期当天显示；回忆仍然保留。", 12, Ui.TEXT_SECONDARY), margin(14));

        note = field("想留给这一天的话（选填）", R.id.anniversary_editor_note);
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note.setSingleLine(false);
        note.setMinLines(3);
        note.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        note.setPadding(dp(12), dp(12), dp(12), dp(12));
        note.setText(draft.note());
        addField(form, "备注", note);
        form.addView(copy("最多 1000 个字", 12, Ui.TEXT_SECONDARY), margin(10));
        error = copy("", 14, Ui.DANGER);
        error.setId(R.id.anniversary_editor_error);
        error.setVisibility(View.GONE);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);
        form.addView(error, margin(8));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(form, new ScrollView.LayoutParams(-1, -2));
        setView(scroll);
        setButton(BUTTON_POSITIVE, editing ? "保存修改" : "保存纪念日", (OnClickListener) null);
        setButton(BUTTON_NEGATIVE, "暂时收起", (OnClickListener) null);
        setOnDismissListener(ignored -> listener.onDismiss(getDraft()));
        clearErrorWhenEditing(title);
        clearErrorWhenEditing(date);
        clearErrorWhenEditing(note);
        updateTypePreview();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getButton(BUTTON_POSITIVE).setOnClickListener(ignored -> save());
        getButton(BUTTON_NEGATIVE).setOnClickListener(ignored -> {
            if (!busy) dismiss();
        });
        if (getWindow() != null) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        applyBusy();
    }

    AnniversaryDraft getDraft() {
        return new AnniversaryDraft(title.getText().toString(), date.getText().toString(),
                note.getText().toString(), repeat.isChecked(), typePicker.selectedType());
    }

    void setBusy(boolean saving) {
        busy = saving;
        applyBusy();
    }

    void setSubmissionBlocked(boolean blocked) {
        submissionBlocked = blocked;
        applyBusy();
    }

    void showError(String message) {
        setBusy(false);
        error.setText(message == null || message.trim().isEmpty() ? "保存失败，请稍后重试。" : message);
        error.setVisibility(View.VISIBLE);
        error.post(() -> error.requestRectangleOnScreen(new android.graphics.Rect(0, 0,
                error.getWidth(), error.getHeight()), false));
    }

    private void save() {
        if (busy || submissionBlocked) return;
        title.setError(null);
        date.setError(null);
        note.setError(null);
        AnniversaryDraft draft = getDraft().normalized();
        Map<String, String> errors = draft.validate();
        if (!errors.isEmpty()) {
            EditText first = null;
            for (String key : new String[]{"title", "date", "note"}) {
                if (!errors.containsKey(key)) continue;
                EditText field = "title".equals(key) ? title : "date".equals(key) ? date : note;
                field.setError(errors.get(key));
                if (first == null) first = field;
            }
            if (first != null) first.requestFocus();
            return;
        }
        error.setVisibility(View.GONE);
        setBusy(true);
        listener.onSave(draft);
    }

    private void applyBusy() {
        title.setEnabled(!busy);
        date.setEnabled(!busy);
        note.setEnabled(!busy);
        repeat.setEnabled(!busy);
        pickDate.setEnabled(!busy);
        pickYear.setEnabled(!busy);
        typePicker.setEnabled(!busy);
        setCancelable(!busy);
        setCanceledOnTouchOutside(!busy);
        if (getButton(BUTTON_POSITIVE) != null) {
            getButton(BUTTON_POSITIVE).setEnabled(!busy && !submissionBlocked);
            getButton(BUTTON_POSITIVE).setText(busy ? "正在保存…" : submissionBlocked ? "请先关闭并刷新" : editing ? "保存修改" : "保存纪念日");
            getButton(BUTTON_NEGATIVE).setEnabled(!busy);
        }
    }

    private void showYearPicker() {
        if (busy || (yearPicker != null && yearPicker.isShowing())) return;
        String current = date.getText().toString().trim();
        if (!AnniversaryDates.isValidDate(current)) current = AnniversaryDates.today(System.currentTimeMillis());
        final String original = current;
        AnniversaryYearMonthDialog picker = new AnniversaryYearMonthDialog(getContext(), current.substring(0, 7), false,
                selected -> date.setText(AnniversaryDates.withYearMonth(original, selected)));
        yearPicker = picker;
        picker.setOnDismissListener(ignored -> { if (yearPicker == picker) yearPicker = null; });
        picker.show();
    }

    private void showDatePicker() {
        if (busy) return;
        if (datePicker != null && datePicker.isShowing()) return;
        String current = date.getText().toString().trim();
        if (!AnniversaryDates.isValidDate(current)) current = AnniversaryDates.today(System.currentTimeMillis());
        int year = AnniversaryDates.yearOf(current);
        int month = Integer.parseInt(current.substring(5, 7));
        int day = AnniversaryDates.dayOfMonth(current);
        DatePickerDialog picker = new DatePickerDialog(getContext(), (ignored, pickedYear, pickedMonth, pickedDay) ->
                date.setText(String.format(Locale.ROOT, "%04d-%02d-%02d", pickedYear, pickedMonth + 1, pickedDay)),
                year, month - 1, day);
        datePicker = picker;
        picker.setOnDismissListener(ignored -> {
            if (datePicker == picker) datePicker = null;
        });
        picker.getDatePicker().setMinDate(pickerBoundary(1000, Calendar.JANUARY, 1));
        picker.getDatePicker().setMaxDate(pickerBoundary(9999, Calendar.DECEMBER, 31));
        // Some platform delegates clamp the constructor date before custom bounds are set.
        picker.updateDate(year, month - 1, day);
        picker.show();
    }

    @Override public void dismiss() {
        AnniversaryYearMonthDialog year = yearPicker;
        yearPicker = null;
        if (year != null && year.isShowing()) year.dismiss();
        DatePickerDialog picker = datePicker;
        datePicker = null;
        if (picker != null && picker.isShowing()) picker.dismiss();
        super.dismiss();
    }

    private static long pickerBoundary(int year, int month, int day) {
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.clear();
        calendar.set(year, month, day, 0, 0, 0);
        return calendar.getTimeInMillis();
    }

    private void clearErrorWhenEditing(EditText input) {
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                input.setError(null);
                if (input == date) updateTypePreview();
            }
            @Override public void afterTextChanged(Editable value) { }
        });
    }

    private void updateTypePreview() {
        AnniversaryType type = typePicker.selectedType();
        String value = date.getText().toString();
        int day = AnniversaryDates.isValidDate(value) ? AnniversaryDates.dayOfMonth(value) : 0;
        typePreview.setState(day, Collections.singleton(type.marker()), false, false, false);
        String hint = switch (type.marker()) {
            case HEART -> "爱心圈住日期，记住心动的开始";
            case CAKE -> "日期下的小蛋糕，留给特别的你";
            case FLAG -> "用小旗帜，标记生活里的特别一天";
        };
        // Avoid repeatedly announcing a live region while the user is typing a date.
        if (!hint.contentEquals(typeHint.getText())) typeHint.setText(hint);
    }

    private EditText field(String hint, int id) {
        EditText input = Ui.input(getContext(), hint);
        input.setId(id);
        return input;
    }

    private void addField(LinearLayout form, String label, EditText input) {
        TextView heading = copy(label, 14, Ui.TEXT_PRIMARY);
        heading.setLabelFor(input.getId());
        form.addView(heading, margin(5));
        form.addView(input, margin(4));
    }

    private TextView copy(String value, int size, int color) {
        return Ui.text(getContext(), value, size, color, Typeface.NORMAL);
    }

    private LinearLayout.LayoutParams margin(int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Ui.dp(getContext(), value);
    }
}
