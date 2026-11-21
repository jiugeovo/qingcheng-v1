package com.qingcheng.ordering;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;

final class ItemEditorView {
    interface Actions {
        void bindClick(View view, Runnable action);

        void loadImage(String url, ImageView target);

        void pickImage(Result result);

        void clearImage(Result result);

        void save(Result result, Draft draft);

        void dismiss(Result result);

        void invalid(String message);
    }

    record State(
            Long itemId,
            boolean menu,
            String name,
            String category,
            String description,
            String price,
            String imageUrl,
            boolean available,
            List<String> presetCategories
    ) {
        State {
            name = safe(name);
            category = safe(category);
            description = safe(description);
            price = safe(price);
            imageUrl = safe(imageUrl);
            presetCategories = presetCategories == null
                    ? List.of() : List.copyOf(presetCategories);
        }

        static State from(JSONObject item, boolean menu, List<String> presetCategories) {
            Long id = item == null ? null : item.optLong("id");
            String category = menu ? menuCategory(item) : "宝宝商店";
            return new State(
                    id,
                    menu,
                    item == null ? "" : item.optString("name"),
                    category,
                    item == null ? "" : item.optString("description"),
                    item == null ? "" : item.optString("price", "1.00"),
                    item == null ? "" : item.optString("imageUrl"),
                    item == null || item.optBoolean("available", true),
                    presetCategories
            );
        }

        private static String menuCategory(JSONObject item) {
            String value = item == null ? "" : item.optString("category", "炒菜");
            value = safe(value);
            return value.isEmpty() || "null".equals(value) ? "炒菜" : value;
        }
    }

    record Draft(
            Long itemId,
            boolean menu,
            String name,
            String category,
            String description,
            String imageUrl,
            String price,
            boolean available
    ) {
    }

    static final class Result {
        private final LinearLayout root;
        private final AlertDialog dialog;
        private final EditText name;
        private final EditText category;
        private final EditText description;
        private final EditText price;
        private final EditText imageUrl;
        private final EditText available;
        private final ImageView preview;
        private final TextView uploadStatus;
        private final State state;

        Result(
                LinearLayout root,
                AlertDialog dialog,
                EditText name,
                EditText category,
                EditText description,
                EditText price,
                EditText imageUrl,
                EditText available,
                ImageView preview,
                TextView uploadStatus,
                State state
        ) {
            this.root = root;
            this.dialog = dialog;
            this.name = name;
            this.category = category;
            this.description = description;
            this.price = price;
            this.imageUrl = imageUrl;
            this.available = available;
            this.preview = preview;
            this.uploadStatus = uploadStatus;
            this.state = state;
        }

        LinearLayout root() {
            return root;
        }

        AlertDialog dialog() {
            return dialog;
        }

        EditText imageUrl() {
            return imageUrl;
        }

        ImageView preview() {
            return preview;
        }

        TextView uploadStatus() {
            return uploadStatus;
        }

        State state() {
            return state;
        }

        Draft snapshot() {
            return new Draft(
                    state.itemId(),
                    state.menu(),
                    text(name),
                    state.menu() ? text(category) : "宝宝商店",
                    text(description),
                    text(imageUrl),
                    text(price),
                    state.menu() ? Boolean.parseBoolean(text(available)) : true
            );
        }

        void setSaving(boolean saving) {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(!saving);
        }

        private static String text(EditText input) {
            return input.getText().toString().trim();
        }
    }

    private final Activity activity;
    private final Actions actions;
    private final State state;

    ItemEditorView(Activity activity, Actions actions, State state) {
        this.activity = activity;
        this.actions = actions;
        this.state = state;
    }

    Result show() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), 0, dp(12), 0);

        EditText name = input("名称");
        EditText category = input("分类：点击选择");
        if (state.menu()) {
            category.setFocusable(false);
            category.setInputType(InputType.TYPE_NULL);
            actions.bindClick(category, () -> showCategoryPicker(category));
        }
        EditText description = input("描述");
        EditText price = input("价格/青程币");
        price.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText imageUrl = input("图片地址");
        EditText available = input("是否上架 true/false");
        ImageView preview = new ImageView(activity);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(Ui.stroke(Ui.PATCH, dp(16), Ui.LINE, dp(2)));
        TextView uploadStatus = Ui.text(activity,
                "可以直接从手机选择图片上传", 13, Ui.MUTED, Typeface.BOLD);

        TextView pickImage = actionButton("选择图片", Ui.GREEN);
        TextView clearImage = actionButton("删除图片", Ui.RED);
        LinearLayout imageActions = new LinearLayout(activity);
        imageActions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        actionParams.setMargins(dp(4), 0, dp(4), 0);
        imageActions.addView(pickImage, actionParams);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        clearParams.setMargins(dp(4), 0, dp(4), 0);
        imageActions.addView(clearImage, clearParams);

        name.setText(state.name());
        category.setText(state.category());
        description.setText(state.description());
        price.setText(state.price());
        imageUrl.setText(state.imageUrl());
        available.setText(String.valueOf(state.available()));

        box.addView(name);
        if (state.menu()) {
            gap(box, 8);
            box.addView(category);
        }
        gap(box, 8);
        box.addView(description);
        gap(box, 8);
        box.addView(price);
        gap(box, 8);
        box.addView(preview, new LinearLayout.LayoutParams(-1, dp(132)));
        gap(box, 8);
        box.addView(uploadStatus);
        gap(box, 8);
        box.addView(imageActions);
        gap(box, 8);
        box.addView(imageUrl);
        if (state.menu()) {
            gap(box, 8);
            box.addView(available);
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle((state.itemId() == null ? "新建" : "编辑")
                        + (state.menu() ? "菜品" : "产品"))
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        Result result = new Result(
                box,
                dialog,
                name,
                category,
                description,
                price,
                imageUrl,
                available,
                preview,
                uploadStatus,
                state
        );
        actions.bindClick(pickImage, () -> actions.pickImage(result));
        actions.bindClick(clearImage, () -> actions.clearImage(result));
        dialog.setOnDismissListener(ignored -> actions.dismiss(result));
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(clicked -> actions.save(result, result.snapshot())));
        actions.loadImage(state.imageUrl(), preview);
        dialog.show();
        return result;
    }

    private void showCategoryPicker(EditText target) {
        String[] options = new String[state.presetCategories().size() + 1];
        for (int index = 0; index < state.presetCategories().size(); index++) {
            options[index] = state.presetCategories().get(index);
        }
        options[options.length - 1] = "自定义分类";
        new AlertDialog.Builder(activity)
                .setTitle("选择分类")
                .setItems(options, (dialog, which) -> {
                    if (which < state.presetCategories().size()) {
                        target.setText(options[which]);
                    } else {
                        showCustomCategoryDialog(target);
                    }
                })
                .show();
    }

    private void showCustomCategoryDialog(EditText target) {
        EditText custom = input("自定义分类（最多20字）");
        String current = target.getText().toString().trim();
        if (!state.presetCategories().contains(current)) {
            custom.setText(current);
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("自定义分类")
                .setView(custom)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(clicked -> {
            String value = custom.getText().toString().trim();
            if (value.isEmpty()) {
                actions.invalid("分类不能为空");
            } else if (value.length() > 20) {
                actions.invalid("分类最多20个字");
            } else {
                target.setText(value);
                dialog.dismiss();
            }
        });
    }

    private EditText input(String hint) {
        EditText input = Ui.input(activity, hint);
        input.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(48)));
        return input;
    }

    private TextView actionButton(String label, int color) {
        TextView view = Ui.text(activity, label, 14, Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(13), dp(8), dp(13), dp(8));
        view.setBackground(Ui.block(color, dp(12), dp(1)));
        Ui.ensureTouchTarget(view);
        return view;
    }

    private void gap(LinearLayout parent, int heightDp) {
        parent.addView(new Space(activity), new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private int dp(int value) {
        return Ui.dp(activity, value);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
