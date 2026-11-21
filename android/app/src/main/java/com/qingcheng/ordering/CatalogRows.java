package com.qingcheng.ordering;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable snapshots: mutable response JSON must not change an in-flight diff. */
final class CatalogRows {
    record Product(String json, String name, String description, String price, String imageUrl) {
        JSONObject actionItem() {
            try { return new JSONObject(json); }
            catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        }
    }

    record Row(String key, String category, Product product) {
        boolean heading() { return product == null; }
    }

    record Snapshot(List<Row> rows, List<String> categories) {
        Snapshot { rows = List.copyOf(rows); categories = List.copyOf(categories); }
    }

    /** Worker-produced immutable catalog; the main thread only submits the already mapped rows. */
    record Prepared(Snapshot snapshot, String json, boolean menu) {
        Prepared {
            if (snapshot == null || json == null) throw new IllegalArgumentException("catalog snapshot is incomplete");
        }
    }

    static Prepared prepare(JSONArray input, boolean menu, List<String> presets) throws JSONException {
        String json = input == null ? "[]" : input.toString();
        return new Prepared(parse(new JSONArray(json), menu, presets), json, menu);
    }

    static Snapshot parse(JSONArray input, boolean menu, List<String> presets) throws JSONException {
        List<Row> products = new ArrayList<>();
        Map<String, List<Row>> grouped = new LinkedHashMap<>();
        Map<String, Integer> occurrences = new HashMap<>();
        for (int index = 0; input != null && index < input.length(); index++) {
            JSONObject item = input.getJSONObject(index);
            String json = item.toString();
            String category = item.optString("category", "炒菜").trim();
            if (category.isEmpty()) category = "炒菜";
            String identity = item.has("id") && !item.isNull("id")
                    ? "id:" + item.optString("id") : "value:" + json;
            int occurrence = occurrences.getOrDefault(identity, 0);
            occurrences.put(identity, occurrence + 1);
            String key = (menu ? "menu:" : "shop:") + "item:" + identity + ":" + occurrence;
            String description = item.optString("description", "");
            if ("null".equals(description)) description = "";
            Row row = new Row(key, category, new Product(json, item.optString("name", "未命名"),
                    description, item.optString("price", "0"), item.optString("imageUrl", "")));
            products.add(row);
            grouped.computeIfAbsent(category, ignored -> new ArrayList<>()).add(row);
        }
        if (!menu) return new Snapshot(products, List.of());
        LinkedHashSet<String> categoryOrder = new LinkedHashSet<>();
        for (String preset : presets)
            if (grouped.containsKey(preset)) categoryOrder.add(preset);
        categoryOrder.addAll(grouped.keySet());
        List<String> categories = List.copyOf(categoryOrder);
        List<Row> rows = new ArrayList<>();
        for (String category : categories) {
            rows.add(new Row("menu:heading:" + category, category, null));
            rows.addAll(grouped.get(category));
        }
        return new Snapshot(rows, categories);
    }

    /** Keep original row identities and actions while narrowing the mounted catalog. */
    static Snapshot filter(Snapshot snapshot, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return snapshot;
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        LinkedHashSet<String> matchedCategories = new LinkedHashSet<>();
        for (Row row : snapshot.rows()) {
            if (row.heading()) continue;
            Product product = row.product();
            if (product.name().toLowerCase(Locale.ROOT).contains(needle)
                    || product.description().toLowerCase(Locale.ROOT).contains(needle)
                    || row.category().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(row.key());
                matchedCategories.add(row.category());
            }
        }
        List<Row> rows = new ArrayList<>();
        for (Row row : snapshot.rows()) {
            if (row.heading() ? matchedCategories.contains(row.category()) : matches.contains(row.key()))
                rows.add(row);
        }
        List<String> categories = new ArrayList<>();
        for (String category : snapshot.categories())
            if (matchedCategories.contains(category)) categories.add(category);
        return new Snapshot(rows, categories);
    }

    private CatalogRows() { }
}
