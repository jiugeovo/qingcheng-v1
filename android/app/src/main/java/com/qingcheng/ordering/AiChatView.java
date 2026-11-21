package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;

final class AiChatView {
    private static final Pattern MARKDOWN_IMAGE =
            Pattern.compile("!\\[[^\\]]*\\]\\(([^)]+)\\)");

    interface Actions {
        void bindClick(View view, Runnable action);

        void back();

        void history();

        void pickImage();

        void send(TextView button);

        void removeAttachment(int index);

        void loadImage(String url, ImageView target);

        void downloadImage(String url);

        default boolean setOrderingMode(boolean enabled) { return false; }
    }

    record State(
            boolean showComposer,
            String draftText,
            List<AiAttachment> attachments,
            boolean orderingMode
    ) {
        State(boolean showComposer, String draftText, List<AiAttachment> attachments) {
            this(showComposer, draftText, attachments, false);
        }
        State {
            draftText = draftText == null ? "" : draftText;
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    record Result(
            View root,
            LinearLayout messages,
            ScrollView messageScroll,
            EditText input,
            LinearLayout attachmentList,
            TextView sendButton
    ) {
    }

    record ComposerResult(
            View root,
            EditText input,
            LinearLayout attachmentList,
            TextView sendButton
    ) {
    }

    record LiveReply(LinearLayout root, TextView status, TextView answer,
                     TextView summaryToggle, TextView summaryBody) { }

    LiveReply addLiveReply(LinearLayout messages) {
        LiveReply reply = liveReply();
        reply.root().setVisibility(View.GONE);
        messages.addView(reply.root());
        return reply;
    }

    private LiveReply liveReply() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(9), dp(12), dp(9));
        root.setBackground(Ui.stroke(Color.WHITE, dp(14), Ui.LINE, dp(1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(0, 0, dp(44), dp(10));
        root.setLayoutParams(params);
        TextView toggle = Ui.text(context, "思考摘要 ▾", 13, Ui.MUTED, Typeface.BOLD);
        toggle.setTag("ai-summary-toggle");
        toggle.setVisibility(View.GONE);
        TextView summary = Ui.text(context, "", 13, Ui.MUTED, Typeface.NORMAL);
        summary.setTag("ai-summary-text");
        summary.setTextIsSelectable(true);
        summary.setPadding(0, dp(5), 0, dp(10));
        summary.setVisibility(View.GONE);
        TextView answer = Ui.text(context, "", 14, Ui.TEXT, Typeface.NORMAL);
        answer.setTag("ai-answer-text");
        answer.setTextIsSelectable(true);
        answer.setLineSpacing(dp(2), 1.0f);
        TextView status = Ui.text(context, "", 12, Ui.MUTED, Typeface.NORMAL);
        status.setTag("ai-stream-status");
        status.setPadding(0, dp(6), 0, 0);
        root.addView(toggle); root.addView(summary); root.addView(answer); root.addView(status);
        actions.bindClick(toggle, () -> {
            boolean expand = summary.getVisibility() != View.VISIBLE;
            summary.setVisibility(expand ? View.VISIBLE : View.GONE);
            toggle.setText(expand ? "思考摘要 ▾" : "思考摘要 ▸");
        });
        return new LiveReply(root, status, answer, toggle, summary);
    }

    void renderLiveReply(LiveReply reply, AiStreamState.Update update) {
        reply.root().setVisibility(View.VISIBLE);
        reply.answer().setText(MarkdownRenderer.render(update.answer()));
        reply.answer().setVisibility(update.answer().isEmpty() ? View.GONE : View.VISIBLE);
        if (!update.summary().isEmpty()) {
            if (reply.summaryToggle().getVisibility() == View.GONE) {
                reply.summaryToggle().setVisibility(View.VISIBLE);
                reply.summaryBody().setVisibility(View.VISIBLE);
            }
            reply.summaryBody().setText(MarkdownRenderer.render(update.summary()));
        }
        reply.status().setText(update.status());
        reply.status().setVisibility(update.status().isEmpty() ? View.GONE : View.VISIBLE);
    }

    private final Context context;
    private final Actions actions;
    private final State state;

    AiChatView(Context context, Actions actions, State state) {
        this.context = context;
        this.actions = actions;
        this.state = state;
    }

    Result build() {
        LinearLayout panel = card();
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = smallPill("中心", Ui.MUTED, "center");
        TextView history = iconChip("◷", Ui.GOLD, Ui.TEXT, "history");
        header.addView(back, new LinearLayout.LayoutParams(dp(64), dp(38)));
        header.addView(new Space(context), new LinearLayout.LayoutParams(0, 1, 1));
        header.addView(history, new LinearLayout.LayoutParams(dp(42), dp(38)));
        panel.addView(header);
        addVerticalGap(panel, 8);

        ScrollView messageScroll = new ScrollView(context);
        messageScroll.setFillViewport(false);
        messageScroll.setNestedScrollingEnabled(true);
        messageScroll.setClipToPadding(false);
        LinearLayout messages = new LinearLayout(context);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(0, 0, 0, dp(8));
        messageScroll.addView(messages, new ScrollView.LayoutParams(-1, -2));
        panel.addView(messageScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(-1, 0, 1);
        panelParams.setMargins(0, 0, 0, dp(8));
        panel.setLayoutParams(panelParams);
        actions.bindClick(back, actions::back);
        actions.bindClick(history, actions::history);

        if (!state.showComposer()) {
            return new Result(panel, messages, messageScroll, null, null, null);
        }
        ComposerResult composer = buildComposer();
        return new Result(
                panel,
                messages,
                messageScroll,
                composer.input(),
                composer.attachmentList(),
                composer.sendButton()
        );
    }

    ComposerResult buildComposer() {
        LinearLayout composer = new LinearLayout(context);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setPadding(dp(10), dp(8), dp(10), dp(8));
        composer.setBackground(Ui.stroke(Color.WHITE, dp(16), Ui.LINE, dp(1)));

        EditText input = Ui.input(context, state.orderingMode() ? "说说想点什么菜、几份" : "输入消息");
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(3);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setText(state.draftText());
        input.setSelection(input.getText().length());
        composer.addView(input, new LinearLayout.LayoutParams(-1, dp(72)));

        LinearLayout attachmentList = new LinearLayout(context);
        attachmentList.setOrientation(LinearLayout.VERTICAL);
        composer.addView(attachmentList);
        renderAttachments(attachmentList, state.attachments());

        LinearLayout actionRow = new LinearLayout(context);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(0, dp(6), 0, 0);
        TextView image = flatTool("+", 24, Ui.GREEN, "+");
        TextView send = sendButton();
        actionRow.addView(image, new LinearLayout.LayoutParams(dp(34), dp(36)));
        TextView ordering = pill(state.orderingMode() ? "点菜中" : "点菜", Ui.GREEN, "ordering-mode");
        ordering.setMinHeight(dp(48));
        ordering.setContentDescription(state.orderingMode() ? "退出点菜模式" : "切换到点菜模式");
        boolean[] mode = {state.orderingMode()};
        actions.bindClick(ordering, () -> {
            boolean next = !mode[0];
            if (!actions.setOrderingMode(next)) return;
            mode[0] = next;
            ordering.setText(next ? "点菜中" : "点菜");
            ordering.setContentDescription(next ? "退出点菜模式" : "切换到点菜模式");
            input.setHint(next ? "说说想点什么菜、几份" : "输入消息");
        });
        actionRow.addView(ordering);
        actionRow.addView(new Space(context), new LinearLayout.LayoutParams(0, 1, 1));
        actionRow.addView(send, new LinearLayout.LayoutParams(dp(44), dp(44)));
        composer.addView(actionRow);

        actions.bindClick(image, actions::pickImage);
        actions.bindClick(send, () -> actions.send(send));
        return new ComposerResult(composer, input, attachmentList, send);
    }

    void renderAttachments(LinearLayout target, List<AiAttachment> attachments) {
        if (target == null) {
            return;
        }
        target.removeAllViews();
        List<AiAttachment> snapshot = attachments == null ? List.of() : List.copyOf(attachments);
        for (int index = 0; index < snapshot.size(); index++) {
            AiAttachment attachment = snapshot.get(index);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            row.setBackground(Ui.stroke(Color.WHITE, dp(12), Ui.LINE, dp(1)));
            TextView label = Ui.text(context, attachment.name, 13, Ui.TEXT, Typeface.BOLD);
            TextView remove = pill("移除", Ui.RED, "remove");
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(remove);
            int attachmentIndex = index;
            actions.bindClick(remove, () -> actions.removeAttachment(attachmentIndex));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, 0, 0, dp(8));
            target.addView(row, params);
        }
    }

    View addThinkingBubble(LinearLayout targetMessages) {
        LinearLayout bubble = new LinearLayout(context);
        bubble.setOrientation(LinearLayout.HORIZONTAL);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setPadding(dp(8), dp(6), dp(12), dp(6));
        bubble.setBackground(Ui.stroke(Color.WHITE, dp(14), Ui.LINE, dp(1)));
        GifMovieView animation = new GifMovieView(context);
        animation.setMovieResource(context.getResources().getIdentifier(
                "ai_thinking", "drawable", context.getPackageName()));
        bubble.addView(animation, new LinearLayout.LayoutParams(dp(46), dp(46)));
        addHorizontalGap(bubble, 7);
        bubble.addView(Ui.text(context, "思考中…", 13, Ui.MUTED, Typeface.NORMAL));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.gravity = Gravity.LEFT;
        params.setMargins(0, 0, dp(44), dp(10));
        bubble.setLayoutParams(params);
        targetMessages.addView(bubble);
        return bubble;
    }

    void removeThinkingBubble(LinearLayout targetMessages, View thinkingBubble) {
        if (targetMessages != null
                && thinkingBubble != null
                && thinkingBubble.getParent() == targetMessages) {
            targetMessages.removeView(thinkingBubble);
        }
    }

    void renderMessages(LinearLayout targetMessages, JSONArray messages) {
        if (targetMessages == null) return;
        new AiMessageHistory(targetMessages, messages, this::createBubble, actions::bindClick)
                .render();
    }

    void addBubble(LinearLayout targetMessages, String role, String value) {
        if (targetMessages == null) {
            return;
        }
        targetMessages.addView(createBubble(role, value));
    }

    private View createBubble(String role, String value) {
        boolean user = "user".equals(role);
        AiStoredAnswer stored = AiStoredAnswer.parse(value);
        if (!user && !stored.summary().isEmpty()) {
            LiveReply reply = liveReply();
            renderLiveReply(reply, new AiStreamState.Update(stored.answer(), stored.summary(), "", true));
            return reply.root();
        }
        LinearLayout bubble = new LinearLayout(context);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(12), dp(9), dp(12), dp(9));
        bubble.setBackground(Ui.stroke(
                user ? Ui.GREEN : Color.WHITE, dp(14), Ui.LINE, dp(1)));

        boolean hasContent = false;
        String source = value == null ? "" : value;
        Matcher matcher = MARKDOWN_IMAGE.matcher(source);
        int imageCount = 0;
        while (matcher.find()) {
            imageCount++;
        }
        String cleanText = MARKDOWN_IMAGE.matcher(source).replaceAll("").trim();
        if (!cleanText.isEmpty()) {
            TextView body = Ui.text(
                    context,
                    cleanText,
                    14,
                    user ? Color.WHITE : Ui.TEXT,
                    Typeface.NORMAL
            );
            body.setTextIsSelectable(true);
            if (!user) {
                body.setText(MarkdownRenderer.render(cleanText));
            }
            body.setLineSpacing(dp(2), 1.0f);
            bubble.addView(body);
            hasContent = true;
        }

        matcher = MARKDOWN_IMAGE.matcher(source);
        while (matcher.find()) {
            String url = matcher.group(1);
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(Ui.stroke(Color.WHITE, dp(12), Ui.LINE, dp(2)));
            if (hasContent) {
                addVerticalGap(bubble, 8);
            }
            bubble.addView(image, new LinearLayout.LayoutParams(-1, dp(190)));
            actions.loadImage(url, image);
            actions.bindClick(image, () -> actions.downloadImage(url));
            hasContent = true;
        }
        if (!hasContent) {
            bubble.addView(Ui.text(
                    context, "", 14, user ? Color.WHITE : Ui.TEXT, Typeface.NORMAL));
        }

        int bubbleWidth = imageCount > 0
                ? Math.min(screenWidth() - dp(88), dp(360))
                : -2;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(bubbleWidth, -2);
        params.gravity = user ? Gravity.RIGHT : Gravity.LEFT;
        params.setMargins(user ? dp(44) : 0, 0, user ? 0 : dp(44), dp(10));
        bubble.setLayoutParams(params);
        return bubble;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.block(Ui.CARD, dp(14), dp(1)));
        return card;
    }

    private TextView smallPill(String label, int color, String tag) {
        TextView view = Ui.text(context, label, 13, Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setBackground(Ui.block(color, dp(11), dp(1)));
        view.setSingleLine(true);
        view.setTag(tag);
        return view;
    }

    private TextView iconChip(String label, int background, int color, String tag) {
        TextView view = Ui.text(context, label, 22, color, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, 0, 0, dp(2));
        view.setBackground(Ui.stroke(Color.WHITE, dp(12), background, dp(1)));
        view.setTag(tag);
        return view;
    }

    private TextView flatTool(String label, int size, int color, String tag) {
        TextView view = Ui.text(context, label, size, color, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setPadding(dp(3), 0, dp(3), 0);
        view.setTag(tag);
        return view;
    }

    private TextView sendButton() {
        TextView view = Ui.text(context, "↑", 26, Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, 0, 0, dp(4));
        view.setBackground(Ui.block(Ui.GREEN, dp(13), dp(1)));
        view.setTag("send");
        view.setContentDescription("发送消息");
        return view;
    }

    private TextView pill(String label, int color, String tag) {
        TextView view = Ui.text(context, label, 14, Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(dp(82));
        view.setPadding(dp(13), dp(8), dp(13), dp(8));
        view.setBackground(Ui.block(color, dp(12), dp(1)));
        view.setTag(tag);
        return view;
    }

    private void addVerticalGap(LinearLayout parent, int heightDp) {
        parent.addView(new Space(context), new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private void addHorizontalGap(LinearLayout parent, int widthDp) {
        parent.addView(new Space(context), new LinearLayout.LayoutParams(dp(widthDp), 1));
    }

    private int dp(int value) {
        return Ui.dp(context, value);
    }

    private int screenWidth() {
        return context.getResources().getDisplayMetrics().widthPixels;
    }
}
