package com.qingcheng.ordering;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import org.json.JSONObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WeatherView {
    interface Actions {
        void bindClick(View view, Runnable action);
        void back();
        void search(EditText input);
    }
    record State(JSONObject weather, String queryText, String defaultLocation) {
        State {
            queryText = queryText == null ? "" : queryText;
            defaultLocation = defaultLocation == null || defaultLocation.isBlank() ? "合肥" : defaultLocation;
        }
    }
    record Result(View root, EditText query) { }

    private final Context context;
    private final Actions actions;
    private final State state;
    WeatherView(Context context, Actions actions, State state) {
        this.context = context;
        this.actions = actions;
        this.state = state;
    }

    Result build() {
        JSONObject data = state.weather() == null ? placeholder(state.queryText()) : state.weather();
        LinearLayout page = column();
        page.setPadding(dp(20), dp(14), dp(20), dp(24));
        page.setBackgroundColor(RefreshUi.PAPER);
        LinearLayout heading = row();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("", 16, RefreshUi.GREEN, true);
        back.setTag("back");
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("返回生活小帮手");
        Ui.setStartIcon(back, R.drawable.ic_action_back, 22, 0);
        back.setBackground(Ui.round(RefreshUi.SAGE, dp(16)));
        heading.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("青程天气", 22, RefreshUi.TEXT, true);
        title.setPadding(dp(12), 0, 0, 0);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(heading, margin(16));

        LinearLayout searchRow = row();
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText query = Ui.input(context, "输入城市 / 留空默认" + state.defaultLocation());
        query.setText(normalizeQuery(state.queryText()));
        query.setTextColor(RefreshUi.TEXT);
        query.setHintTextColor(RefreshUi.MUTED);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setBackground(RefreshUi.card(context, RefreshUi.WHITE, 16));
        searchRow.addView(query, new LinearLayout.LayoutParams(0, dp(48), 1));
        gap(searchRow, 10, false);
        TextView search = text("查询", 13, RefreshUi.WHITE, true);
        search.setTag("search");
        search.setGravity(Gravity.CENTER);
        search.setBackground(Ui.round(RefreshUi.GREEN, dp(16)));
        search.setContentDescription("查询城市天气");
        searchRow.addView(search, new LinearLayout.LayoutParams(dp(64), dp(48)));
        page.addView(searchRow, margin(16));

        LinearLayout hero = column();
        hero.setBackground(Ui.round(RefreshUi.WHITE, dp(24)));
        hero.setClipToOutline(true);
        ImageView scene = new ImageView(context) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int width = MeasureSpec.getSize(widthSpec);
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(Math.round(width * 2f / 3f), MeasureSpec.EXACTLY));
            }
        };
        Ui.setImageResource(scene, R.drawable.weather_scene_outing);
        scene.setTag("weather-outing-scene");
        scene.setScaleType(ImageView.ScaleType.FIT_CENTER);
        scene.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        hero.addView(scene, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout current = column();
        current.setPadding(dp(22), dp(4), dp(22), dp(22));
        TextView place = text("● " + displayPlace(data), 13, RefreshUi.TEXT, true);
        current.addView(place);
        gap(current, 8, true);
        LinearLayout temperatureRow = row();
        temperatureRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView temperature = text(data.optString("temperature", "--"), 46, RefreshUi.TEXT, true);
        temperatureRow.addView(temperature);
        TextView unit = text("°C", 18, RefreshUi.MUTED, false);
        unit.setPadding(dp(3), dp(14), 0, 0);
        temperatureRow.addView(unit);
        temperatureRow.addView(new Space(context), new LinearLayout.LayoutParams(0, 1, 1));
        temperatureRow.addView(image(iconResource(data.optString("weather", ""))),
                new LinearLayout.LayoutParams(dp(60), dp(60)));
        current.addView(temperatureRow);
        LinearLayout conditions = row();
        conditions.setGravity(Gravity.CENTER_VERTICAL);
        TextView condition = text(data.optString("weather", "--"), 14, RefreshUi.GREEN, true);
        conditions.addView(condition, new LinearLayout.LayoutParams(0, -2, 1));
        TextView feels = text("体感 " + value(data, "feelsLike", "°"), 12, RefreshUi.MUTED, false);
        conditions.addView(feels);
        current.addView(conditions);
        hero.addView(current);
        page.addView(hero, margin(16));

        LinearLayout first = row();
        first.addView(metric("湿度", value(data, "humidity", "%"), R.drawable.weather_icon_rain), weighted());
        gap(first, 12, false);
        first.addView(metric(data.optString("windDirection", "风力"), value(data, "windScale", "级"),
                R.drawable.weather_icon_wind), weighted());
        page.addView(first, margin(12));
        LinearLayout second = row();
        second.addView(metric("降水", value(data, "precipitation", "mm"), R.drawable.weather_icon_rain), weighted());
        gap(second, 12, false);
        second.addView(metric("能见度", value(data, "visibility", "km"), R.drawable.weather_icon_fog), weighted());
        page.addView(second, margin(18));
        TextView updated = text("↻ " + updateShort(data), 11, RefreshUi.MUTED, false);
        updated.setGravity(Gravity.CENTER);
        page.addView(updated);

        actions.bindClick(back, actions::back);
        actions.bindClick(search, () -> actions.search(query));
        query.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            actions.search(query);
            return true;
        });
        return new Result(page, query);
    }

    private View metric(String label, String value, int resource) {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setMinimumHeight(dp(112));
        card.setBackground(RefreshUi.card(context, RefreshUi.WHITE, 20));
        LinearLayout heading = row();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text(label, 12, RefreshUi.MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(image(resource), new LinearLayout.LayoutParams(dp(30), dp(30)));
        card.addView(heading);
        gap(card, 8, true);
        card.addView(text(value, 22, RefreshUi.TEXT, true));
        return card;
    }

    static String queryText(JSONObject weather) {
        return WeatherDisplayFormatter.queryText(weather.optString("locationName", ""), weather.optString("query", ""));
    }
    static String displayPlace(JSONObject weather) {
        return WeatherDisplayFormatter.displayPlace(weather.optString("locationName", ""), weather.optString("query", ""));
    }
    static String iconKey(String weather) {
        String value = weather == null ? "" : weather.trim();
        if (value.isEmpty() || value.contains("加载")) return "unknown";
        if (value.contains("雷")) return "thunder";
        if (value.contains("暴雨") || value.contains("强阵雨") || value.contains("大雨")) return "heavy_rain";
        if (value.contains("雪") || value.contains("雨夹雪")) return "snow";
        if (value.contains("雨") || value.contains("毛毛雨") || value.contains("细雨")) return "rain";
        if (value.contains("雾")) return "fog";
        if (value.contains("霾") || value.contains("沙") || value.contains("尘")) return "haze";
        if (value.contains("风") || value.contains("平静")) return "wind";
        if (value.contains("阴")) return "overcast";
        if (value.contains("云")) return "cloudy";
        if (value.contains("晴")) return "sunny";
        return "unknown";
    }
    static int iconResource(String weather) {
        return switch (iconKey(weather)) {
            case "sunny" -> R.drawable.weather_icon_sunny;
            case "cloudy" -> R.drawable.weather_icon_cloudy;
            case "overcast" -> R.drawable.weather_icon_overcast;
            case "rain" -> R.drawable.weather_icon_rain;
            case "heavy_rain" -> R.drawable.weather_icon_heavy_rain;
            case "thunder" -> R.drawable.weather_icon_thunder;
            case "snow" -> R.drawable.weather_icon_snow;
            case "fog" -> R.drawable.weather_icon_fog;
            case "haze" -> R.drawable.weather_icon_haze;
            case "wind" -> R.drawable.weather_icon_wind;
            default -> R.drawable.weather_icon_unknown;
        };
    }
    private JSONObject placeholder(String query) {
        JSONObject data = new JSONObject();
        try {
            data.put("query", normalizeQuery(query));
            data.put("locationName", normalizeQuery(query));
            data.put("weather", "加载中");
        } catch (Exception ignored) { }
        return data;
    }
    private String normalizeQuery(String query) {
        return query == null || query.trim().isEmpty() ? state.defaultLocation() : query.trim();
    }
    private String updateShort(JSONObject weather) {
        String observation = weather.optString("observationTime", "");
        if (observation.isEmpty()) return "实时天气";
        Matcher matcher = Pattern.compile("(\\d{2}:\\d{2})").matcher(observation);
        return matcher.find() ? matcher.group(1) + " 更新" : "已更新";
    }
    private String value(JSONObject weather, String key, String unit) {
        String value = weather.optString(key, "").trim();
        if ("precipitation".equals(key) && value.matches("\\d+\\.0+")) value = value.substring(0, value.indexOf('.'));
        return value.isEmpty() ? "--" : value + unit;
    }
    private TextView text(String value, int size, int color, boolean bold) { return RefreshUi.text(context, value, size, color, bold); }
    private ImageView image(int resource) {
        ImageView view = new ImageView(context);
        Ui.setImageResource(view, resource);
        view.setTag(resource);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    private LinearLayout row() { return new LinearLayout(context); }
    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, -2, 1); }
    private LinearLayout.LayoutParams margin(int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(bottom);
        return params;
    }
    private void gap(LinearLayout parent, int size, boolean vertical) {
        parent.addView(new Space(context), new LinearLayout.LayoutParams(vertical ? 1 : dp(size), vertical ? dp(size) : 1));
    }
    private int dp(int value) { return Ui.dp(context, value); }
}
