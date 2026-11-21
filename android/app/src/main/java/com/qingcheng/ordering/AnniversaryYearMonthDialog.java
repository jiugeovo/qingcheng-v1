package com.qingcheng.ordering;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.EditText;
import android.widget.TextView;
import java.util.Locale;
import java.util.function.Consumer;

/** Direct year input plus optional month selection, without paging through years. */
final class AnniversaryYearMonthDialog extends AlertDialog {
    private final NumberPicker year;
    private final NumberPicker month;
    private final boolean chooseMonth;
    private final Consumer<String> selected;
    AnniversaryYearMonthDialog(Context context, String initialMonth, boolean chooseMonth,
                               Consumer<String> selected) {
        super(context);
        this.chooseMonth = chooseMonth;
        this.selected = selected;
        if (!AnniversaryDates.isValidDate(initialMonth + "-01"))
            throw new IllegalArgumentException("无效的年月");
        setTitle(chooseMonth ? "跳转到年月" : "选择年份");
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), Ui.dp(context, 8));
        TextView hint = new TextView(context);
        hint.setText("点击数字可直接输入年份，也可以上下滑动选择。");
        hint.setTextSize(14);
        hint.setTextColor(Ui.TEXT_SECONDARY);
        form.addView(hint);
        LinearLayout selectors = new LinearLayout(context);
        selectors.setGravity(Gravity.CENTER);
        year = picker(context, 1000, 9999, Integer.parseInt(initialMonth.substring(0, 4)), "anniversary-year", "年份");
        month = picker(context, 1, 12, Integer.parseInt(initialMonth.substring(5, 7)), "anniversary-month", "月份");
        selectors.addView(column(context, "年份", year), new LinearLayout.LayoutParams(0, -2, 1));
        if (chooseMonth) selectors.addView(column(context, "月份", month), new LinearLayout.LayoutParams(0, -2, 1));
        form.addView(selectors);
        setView(form);
        setButton(BUTTON_POSITIVE, "确定", (OnClickListener) null);
        setButton(BUTTON_NEGATIVE, "取消", (dialog, which) -> { });
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getButton(BUTTON_POSITIVE).setOnClickListener(ignored -> {
            Integer chosenYear = commit(year);
            if (chosenYear == null) return;
            Integer chosenMonth = chooseMonth ? commit(month) : month.getValue();
            if (chosenMonth == null) return;
            selected.accept(String.format(Locale.ROOT, "%04d-%02d", chosenYear, chosenMonth));
            dismiss();
        });
    }

    // NumberPicker's current value may lag behind the focused editable text.
    private static Integer commit(NumberPicker picker) {
        EditText input = null;
        for (int i = 0; i < picker.getChildCount(); i++) {
            if (picker.getChildAt(i) instanceof EditText text) { input = text; break; }
        }
        try {
            int value = input == null ? picker.getValue() : Integer.parseInt(input.getText().toString().trim());
            if (value < picker.getMinValue() || value > picker.getMaxValue()) throw new NumberFormatException();
            picker.setValue(value);
            picker.clearFocus();
            return value;
        } catch (NumberFormatException invalid) {
            if (input != null) {
                input.setError("请输入 " + picker.getMinValue() + "–" + picker.getMaxValue());
                input.requestFocus();
            }
            return null;
        }
    }

    private static NumberPicker picker(Context context, int min, int max, int value, String tag, String description) {
        NumberPicker picker = new NumberPicker(context);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(value);
        picker.setWrapSelectorWheel(false);
        picker.setTag(tag);
        picker.setContentDescription(description);
        picker.setDescendantFocusability(LinearLayout.FOCUS_AFTER_DESCENDANTS);
        picker.setId(View.generateViewId());
        return picker;
    }

    private static LinearLayout column(Context context, String text, NumberPicker picker) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextColor(Ui.TEXT_PRIMARY);
        label.setLabelFor(picker.getId());
        column.addView(label);
        column.addView(picker, new LinearLayout.LayoutParams(Ui.dp(context, 120), Ui.dp(context, 180)));
        return column;
    }
}
