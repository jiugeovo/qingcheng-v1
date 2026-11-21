package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

final class LoveRulesView {
    private static final String RULE_FONT_ASSET =
            "fonts/ZCOOLKuaiLe-Regular.ttf";
    private static volatile Typeface ruleTypeface;

    interface Actions {
        void bindClick(View view, Runnable action);
        void back();
        void refresh();
        void add();
        void edit(JSONObject rule);
        void delete(JSONObject rule);
    }

    record State(boolean canManage) {
    }

    record Result(View root, LinearLayout list) {
    }

    private final Context context;
    private final Actions actions;
    private final State state;

    LoveRulesView(Context context, Actions actions, State state) {
        this.context = context;
        this.actions = actions;
        this.state = state;
    }

    Result build() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(tools());

        LinearLayout list = card();
        list.addView(ruleText("爱老婆守则", 20, Ui.TEXT));
        root.addView(list);
        return new Result(root, list);
    }

    void render(LinearLayout list, JSONArray rules) {
        list.removeAllViews();
        list.addView(ruleText("爱老婆守则", 20, Ui.TEXT));
        gap(list, 8);
        if (rules == null || rules.length() == 0) {
            list.addView(ruleText("还没有守则。", 15, Ui.MUTED));
            return;
        }
        for (int index = 0; index < rules.length(); index++) {
            JSONObject rule = rules.optJSONObject(index);
            if (rule != null) {
                addRule(list, rule, index + 1);
            }
        }
    }

    private LinearLayout tools() {
        LinearLayout tools = card();
        tools.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = iconButton(
                R.drawable.ic_action_back, Ui.MUTED, "back", "返回智能中心");
        TextView refresh = iconButton(
                R.drawable.ic_action_refresh, Ui.GREEN, "refresh", "刷新");
        row.addView(back, new LinearLayout.LayoutParams(dp(64), dp(38)));
        row.addView(space(8));
        row.addView(refresh, new LinearLayout.LayoutParams(dp(64), dp(38)));
        actions.bindClick(back, actions::back);
        actions.bindClick(refresh, actions::refresh);
        if (state.canManage()) {
            TextView add = pill("新增", Ui.GOLD, "add");
            row.addView(space(8));
            row.addView(add, new LinearLayout.LayoutParams(dp(64), dp(38)));
            actions.bindClick(add, actions::add);
        }
        tools.addView(row);
        return tools;
    }

    private void addRule(LinearLayout parent, JSONObject rule, int index) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        row.setBackground(Ui.stroke(Color.WHITE, dp(12), Ui.LINE, dp(1)));
        row.addView(ruleText(
                index + ". " + rule.optString("content", ""), 15, Ui.TEXT));
        if (state.canManage()) {
            gap(row, 8);
            LinearLayout controls = new LinearLayout(context);
            controls.setGravity(Gravity.RIGHT);
            long id = rule.optLong("id");
            TextView edit = pill("编辑", Ui.GREEN, "edit:" + id);
            TextView delete = pill("删除", Ui.RED, "delete:" + id);
            controls.addView(edit, new LinearLayout.LayoutParams(dp(64), dp(34)));
            controls.addView(space(8));
            controls.addView(delete, new LinearLayout.LayoutParams(dp(64), dp(34)));
            row.addView(controls);
            actions.bindClick(edit, () -> actions.edit(rule));
            actions.bindClick(delete, () -> actions.delete(rule));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(8));
        parent.addView(row, params);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(Ui.block(Ui.CARD, dp(14), dp(1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    private TextView pill(String label, int color, String tag) {
        TextView view = Ui.text(context, label, 13, Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setBackground(Ui.block(color, dp(11), dp(1)));
        view.setSingleLine(true);
        view.setTag(tag);
        return view;
    }

    private TextView iconButton(
            int iconRes,
            int color,
            String tag,
            String description
    ) {
        TextView view = pill("", color, tag);
        Ui.setStartIcon(view, iconRes, 22, 0);
        view.setContentDescription(description);
        return view;
    }

    private TextView ruleText(String text, int size, int color) {
        TextView view = Ui.text(context, text, size, color, Typeface.NORMAL);
        view.setTypeface(loadRuleTypeface());
        view.setLineSpacing(dp(3), 1.08f);
        return view;
    }

    private Typeface loadRuleTypeface() {
        Typeface cached = ruleTypeface;
        if (cached != null) {
            return cached;
        }
        synchronized (LoveRulesView.class) {
            if (ruleTypeface == null) {
                try {
                    ruleTypeface = Typeface.createFromAsset(context.getAssets(), RULE_FONT_ASSET);
                } catch (RuntimeException ignored) {
                    ruleTypeface = Typeface.DEFAULT;
                }
            }
            return ruleTypeface;
        }
    }

    private Space space(int width) {
        Space space = new Space(context);
        space.setLayoutParams(new LinearLayout.LayoutParams(dp(width), 1));
        return space;
    }

    private void gap(LinearLayout parent, int height) {
        parent.addView(new Space(context), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(int value) {
        return Ui.dp(context, value);
    }
}
