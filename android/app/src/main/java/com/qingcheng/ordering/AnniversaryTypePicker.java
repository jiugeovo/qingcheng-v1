package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Color;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;

import java.util.ArrayList;
import java.util.List;

/** Five explicit choices. Presentation metadata comes from the same catalog as the calendar. */
final class AnniversaryTypePicker extends LinearLayout {
    interface Listener { void onSelected(AnniversaryType type); }

    private static final int MINT = Color.rgb(33, 115, 94);
    private final List<RadioButton> choices = new ArrayList<>();
    private final Listener listener;
    private AnniversaryType selected;

    AnniversaryTypePicker(Context context, AnniversaryType initial, Listener listener) {
        super(context);
        this.listener = listener;
        setOrientation(VERTICAL);
        setId(R.id.anniversary_editor_type);
        setSaveEnabled(false); // The account-scoped draft owns restoration, not per-view checked state.
        AnniversaryType[] types = AnniversaryType.values();
        for (int start = 0; start < types.length; start += 3) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(HORIZONTAL);
            for (int column = 0; column < 3; column++) {
                int index = start + column;
                LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
                params.setMargins(column == 0 ? 0 : dp(4), dp(4), column == 2 ? 0 : dp(4), dp(4));
                if (index >= types.length) {
                    row.addView(new View(context), params);
                    continue;
                }
                AnniversaryType type = types[index];
                RadioButton button = new RadioButton(context);
                button.setId(View.generateViewId());
                button.setTag("anniversary-type:" + type.id());
                button.setSaveEnabled(false);
                button.setButtonDrawable(null);
                button.setText(type.label());
                button.setTextSize(12);
                button.setTextColor(Ui.TEXT_PRIMARY);
                button.setGravity(android.view.Gravity.CENTER);
                button.setMinimumWidth(0);
                button.setMinHeight(dp(68));
                button.setPadding(dp(3), dp(8), dp(3), dp(8));
                AnniversaryMarkerDrawable icon = new AnniversaryMarkerDrawable(type.marker());
                icon.setBounds(0, 0, dp(22), dp(22));
                button.setCompoundDrawables(null, icon, null, null);
                button.setCompoundDrawablePadding(dp(5));
                button.setOnClickListener(ignored -> choose(type, false));
                button.setOnFocusChangeListener((view, focused) -> renderSelection());
                button.setOnKeyListener((view, keyCode, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN || !isEnabled()) return false;
                    int offset = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1
                            : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1
                            : keyCode == KeyEvent.KEYCODE_DPAD_UP ? -3
                            : keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 3 : 0;
                    if (offset == 0) return false;
                    int target = Math.max(0, Math.min(types.length - 1, index + offset));
                    choose(types[target], true);
                    return true;
                });
                choices.add(button);
                row.addView(button, params);
            }
            addView(row, new LayoutParams(-1, -2));
        }
        selected = initial == null ? AnniversaryType.OTHER : initial;
        renderSelection();
    }

    AnniversaryType selectedType() { return selected; }

    private void choose(AnniversaryType type, boolean focus) {
        if (!isEnabled()) return;
        boolean changed = selected != type;
        selected = type;
        renderSelection();
        if (focus) choices.get(type.ordinal()).requestFocus();
        if (changed && listener != null) listener.onSelected(type);
    }

    private void renderSelection() {
        for (int index = 0; index < choices.size(); index++) {
            RadioButton button = choices.get(index);
            boolean checked = AnniversaryType.values()[index] == selected;
            button.setChecked(checked);
            boolean highlighted = checked || button.isFocused();
            button.setBackground(Ui.stroke(checked ? Color.rgb(232, 242, 233) : Ui.SURFACE_RAISED,
                    dp(12), highlighted ? MINT : Color.rgb(223, 230, 220), dp(highlighted ? 2 : 1)));
        }
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (RadioButton button : choices) button.setEnabled(enabled);
        setAlpha(enabled ? 1f : .5f);
    }

    private int dp(int value) { return Ui.dp(getContext(), value); }
}
