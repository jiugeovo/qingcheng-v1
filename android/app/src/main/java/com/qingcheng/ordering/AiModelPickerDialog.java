package com.qingcheng.ordering;

import android.app.Activity;
import android.app.AlertDialog;
import java.util.function.Consumer;

final class AiModelPickerDialog {
    static AlertDialog show(Activity activity, AiModels models, String selected,
            Consumer<String> choose, Runnable refresh) {
        return new AlertDialog.Builder(activity).setTitle("选择 AI 模型")
                .setSingleChoiceItems(models.labels(), models.ids().indexOf(selected), (dialog, index) -> {
                    choose.accept(models.ids().get(index)); dialog.dismiss();
                })
                .setNeutralButton("刷新列表", (dialog, which) -> refresh.run())
                .setNegativeButton("取消", null).show();
    }
}
