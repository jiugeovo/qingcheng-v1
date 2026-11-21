package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

final class FriendChatView {
    interface Actions {
        void bindClick(View view, Runnable action);

        void back();

        void clear();

        void refresh();

        void emoji();

        void image();

        void loadImage(String url, ImageView target);

        void previewImage(String url);

        void reply(JSONObject message);

        void send(TextView sendButton);
    }

    record State(String displayName) {
        State {
            displayName = displayName == null || displayName.isBlank() ? "好友" : displayName;
        }
    }

    record Result(
            View root,
            TextView title,
            LinearLayout messageList,
            ScrollView messageScroll,
            EditText input,
            LinearLayout replyBar,
            TextView replyText,
            TextView cancelReplyButton,
            TextView sendButton
    ) {
    }

    private final Context context;
    private final Actions actions;
    private final State state;

    FriendChatView(Context context, Actions actions, State state) {
        this.context = context;
        this.actions = actions;
        this.state = state;
    }

    Result build() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout panel = card();
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        HeaderResult header = buildHeader();
        panel.addView(header.root());
        addVerticalGap(panel, 8);

        ScrollView messageScroll = new ScrollView(context);
        messageScroll.setFillViewport(false);
        messageScroll.setNestedScrollingEnabled(true);
        LinearLayout messageList = new LinearLayout(context);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setPadding(0, 0, 0, dp(8));
        messageScroll.addView(messageList, new ScrollView.LayoutParams(-1, -2));
        panel.addView(messageScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        int panelHeight = Math.max(dp(420), screenHeight() - dp(260));
        root.addView(panel, new LinearLayout.LayoutParams(-1, panelHeight));

        ReplyResult reply = buildReplyBar();
        root.addView(reply.root());
        ComposerResult composer = buildComposer();
        root.addView(composer.root());

        actions.bindClick(header.back(), actions::back);
        actions.bindClick(header.clear(), actions::clear);
        actions.bindClick(header.refresh(), actions::refresh);
        actions.bindClick(composer.emoji(), actions::emoji);
        actions.bindClick(composer.image(), actions::image);
        actions.bindClick(composer.send(), () -> actions.send(composer.send()));

        return new Result(
                root,
                header.title(),
                messageList,
                messageScroll,
                composer.input(),
                reply.root(),
                reply.text(),
                reply.cancel(),
                composer.send()
        );
    }

    void renderMessages(LinearLayout messageList, JSONArray messages) {
        if (messageList == null) {
            return;
        }
        JSONArray safeMessages = messages == null ? new JSONArray() : messages;
        Map<String, View> reusable = reusableBubbles(messageList);
        int targetIndex = 0;
        if (android.os.Build.VERSION.SDK_INT >= 29) messageList.suppressLayout(true);
        try {
            if (safeMessages.length() == 0) {
                View empty = reusable.remove(EmptyTag.KEY);
                if (empty == null) {
                    empty = text("还没有消息，发一句吧。", 14, Ui.MUTED, Typeface.NORMAL);
                    empty.setTag(EmptyTag.INSTANCE);
                }
                placeAt(messageList, empty, targetIndex++);
            } else {
                for (int index = 0; index < safeMessages.length(); index++) {
                    JSONObject message = safeMessages.optJSONObject(index);
                    if (message == null) {
                        continue;
                    }
                    MessageVisual visual = MessageVisual.from(message);
                    String key = stableKey(message, index, visual);
                    View bubble = reusable.remove(key);
                    if (!(bubble != null
                            && bubble.getTag() instanceof MessageTag tag
                            && visual.equals(tag.visual()))) {
                        bubble = buildMessageBubble(message, visual);
                        bubble.setTag(new MessageTag(key, visual));
                    }
                    placeAt(messageList, bubble, targetIndex++);
                }
            }
            while (messageList.getChildCount() > targetIndex) {
                messageList.removeViewAt(targetIndex);
            }
        } finally {
            if (android.os.Build.VERSION.SDK_INT >= 29) messageList.suppressLayout(false);
        }
    }

    private Map<String, View> reusableBubbles(LinearLayout messageList) {
        Map<String, View> reusable = new LinkedHashMap<>();
        for (int index = 0; index < messageList.getChildCount(); index++) {
            View child = messageList.getChildAt(index);
            if (child.getTag() instanceof MessageTag tag) {
                reusable.putIfAbsent(tag.key(), child);
            } else if (child.getTag() == EmptyTag.INSTANCE) {
                reusable.putIfAbsent(EmptyTag.KEY, child);
            }
        }
        return reusable;
    }

    private String stableKey(JSONObject message, int index, MessageVisual visual) {
        long id = message.optLong("id", 0L);
        return id > 0L ? "message:" + id : "fallback:" + index + ":" + visual.hashCode();
    }

    private void placeAt(LinearLayout messageList, View view, int targetIndex) {
        if (targetIndex < messageList.getChildCount()
                && messageList.getChildAt(targetIndex) == view) {
            return;
        }
        if (view.getParent() == messageList) {
            messageList.removeView(view);
        }
        messageList.addView(view, Math.min(targetIndex, messageList.getChildCount()));
    }

    private HeaderResult buildHeader() {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = iconButton(R.drawable.ic_action_back, Ui.MUTED, "返回好友中心");
        TextView clear = smallPill("删除", Ui.RED);
        TextView refresh = iconButton(R.drawable.ic_action_refresh, Ui.GREEN, "刷新");
        TextView title = text(state.displayName(), 16, Ui.TEXT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setContentDescription(state.displayName());
        row.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        addHorizontalGap(row, 8);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        addHorizontalGap(row, 8);
        row.addView(clear, new LinearLayout.LayoutParams(dp(48), dp(48)));
        addHorizontalGap(row, 8);
        row.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return new HeaderResult(row, back, clear, refresh, title);
    }

    private ComposerResult buildComposer() {
        LinearLayout composer = new LinearLayout(context);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(10), dp(8), dp(10), dp(8));
        composer.setBackground(Ui.stroke(Color.WHITE, dp(16), Ui.LINE, dp(1)));

        EditText input = Ui.input(context, "输入消息");
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(4);
        TextView emoji = iconChip("☺", Ui.GOLD, Ui.TEXT);
        emoji.setContentDescription("选择表情");
        TextView image = iconChip("", Ui.GREEN, Ui.GREEN);
        Ui.setStartIcon(image, android.R.drawable.ic_menu_gallery, 22, 0);
        image.setContentDescription("发送图片");
        TextView send = sendButton();
        send.setContentDescription("发送消息");
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        addHorizontalGap(composer, 8);
        composer.addView(emoji, new LinearLayout.LayoutParams(dp(44), dp(44)));
        addHorizontalGap(composer, 8);
        composer.addView(image, new LinearLayout.LayoutParams(dp(52), dp(44)));
        addHorizontalGap(composer, 8);
        composer.addView(send, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return new ComposerResult(composer, input, emoji, image, send);
    }

    private ReplyResult buildReplyBar() {
        LinearLayout replyBar = new LinearLayout(context);
        replyBar.setOrientation(LinearLayout.HORIZONTAL);
        replyBar.setGravity(Gravity.CENTER_VERTICAL);
        replyBar.setPadding(dp(12), dp(7), dp(8), dp(7));
        replyBar.setBackground(Ui.stroke(Color.rgb(247, 251, 249), dp(12), Ui.GREEN, dp(1)));
        replyBar.setVisibility(View.GONE);
        TextView replyText = text("", 13, Ui.MUTED, Typeface.NORMAL);
        replyText.setMaxLines(2);
        TextView cancel = iconChip("×", Ui.LINE, Ui.MUTED);
        cancel.setTextSize(20);
        cancel.setContentDescription("取消引用");
        replyBar.addView(replyText, new LinearLayout.LayoutParams(0, -2, 1));
        addHorizontalGap(replyBar, 8);
        replyBar.addView(cancel, new LinearLayout.LayoutParams(dp(40), dp(40)));
        return new ReplyResult(replyBar, replyText, cancel);
    }

    private View buildMessageBubble(JSONObject message, MessageVisual visual) {
        boolean mine = visual.mine();
        String value = visual.content();
        String type = visual.type();
        String mediaUrl = visual.mediaUrl();
        LinearLayout bubble = new LinearLayout(context);
        bubble.setOrientation(LinearLayout.VERTICAL);
        boolean media = ("IMAGE".equals(type) || "STICKER".equals(type))
                && mediaUrl != null && !mediaUrl.isBlank();
        bubble.setPadding(media ? dp(4) : dp(12), media ? dp(4) : dp(9),
                media ? dp(4) : dp(12), media ? dp(4) : dp(9));
        bubble.setBackground(Ui.stroke(
                media ? Color.WHITE : (mine ? Ui.GREEN : Color.WHITE), dp(14), Ui.LINE, dp(1)));
        if (visual.replyToMessageId() > 0L && !visual.replyToPreview().isBlank()) {
            TextView quoted = text(
                    "↪ " + visual.replyToPreview(),
                    12,
                    mine ? Color.rgb(232, 255, 247) : Ui.MUTED,
                    Typeface.NORMAL
            );
            quoted.setMaxLines(3);
            quoted.setEllipsize(android.text.TextUtils.TruncateAt.END);
            quoted.setHorizontallyScrolling(false);
            quoted.setMinWidth(dp(120));
            quoted.setPadding(dp(8), dp(5), dp(8), dp(5));
            quoted.setBackground(Ui.round(
                    mine ? Color.rgb(42, 154, 119) : Color.rgb(244, 246, 246), dp(9)));
            bubble.addView(quoted, new LinearLayout.LayoutParams(-2, -2));
            addVerticalGap(bubble, 6);
        }
        if (media) {
            int size = dp("STICKER".equals(type) ? 132 : 210);
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setAdjustViewBounds(false);
            image.setContentDescription("STICKER".equals(type) ? "表情图片" : "聊天图片，点按查看大图");
            actions.loadImage(mediaUrl, image);
            actions.bindClick(image, () -> actions.previewImage(mediaUrl));
            bubble.addView(image, new LinearLayout.LayoutParams(size, size));
        } else {
            TextView body = text(
                    value == null ? "" : value,
                    14,
                    mine ? Color.WHITE : Ui.TEXT,
                    Typeface.NORMAL
            );
            body.setTextIsSelectable(true);
            body.setLineSpacing(dp(2), 1.0f);
            // Explicit WRAP_CONTENT prevents LinearLayout's MATCH_PARENT uniform-width pass
            // from narrowing multiline text while retaining its earlier, shorter measured height.
            bubble.addView(body, new LinearLayout.LayoutParams(-2, -2));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.gravity = mine ? Gravity.RIGHT : Gravity.LEFT;
        params.setMargins(mine ? dp(44) : 0, 0, mine ? 0 : dp(44), dp(10));
        bubble.setLayoutParams(params);
        bubble.setContentDescription("长按引用消息");
        bindReplyLongPress(bubble, message);
        return bubble;
    }

    private void bindReplyLongPress(View target, JSONObject message) {
        target.setOnLongClickListener(view -> {
            actions.reply(message);
            return true;
        });
        if (target instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                bindReplyLongPress(group.getChildAt(index), message);
            }
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.block(Ui.CARD, dp(14), dp(1)));
        return card;
    }

    private TextView smallPill(String label, int color) {
        TextView view = text(label, 13, Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setBackground(Ui.block(color, dp(11), dp(1)));
        view.setSingleLine(true);
        Ui.ensureTouchTarget(view);
        return view;
    }

    private TextView iconButton(int iconRes, int color, String description) {
        TextView view = smallPill("", color);
        Ui.setStartIcon(view, iconRes, 22, 0);
        view.setContentDescription(description);
        return view;
    }

    private TextView iconChip(String label, int background, int color) {
        TextView view = text(label, 22, color, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, 0, 0, dp(2));
        view.setBackground(Ui.stroke(Color.WHITE, dp(12), background, dp(1)));
        Ui.ensureTouchTarget(view);
        return view;
    }

    private TextView sendButton() {
        TextView view = text("↑", 26, Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, 0, 0, dp(4));
        view.setBackground(Ui.block(Ui.GREEN, dp(13), dp(1)));
        Ui.ensureTouchTarget(view);
        return view;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = Ui.text(context, value, sp, color, style);
        view.setSingleLine(false);
        return view;
    }

    private void addVerticalGap(LinearLayout parent, int heightDp) {
        parent.addView(new Space(context), new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private void addHorizontalGap(LinearLayout parent, int widthDp) {
        parent.addView(new Space(context), new LinearLayout.LayoutParams(dp(widthDp), 1));
    }

    private int screenHeight() {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    static void scrollToBottomPreservingFocus(
            ScrollView scroll,
            LinearLayout messages,
            EditText input
    ) {
        if (scroll == null || messages == null) {
            return;
        }
        boolean keepInputFocus = input != null && input.hasFocus();
        int selectionStart = input == null ? 0 : Math.max(0, input.getSelectionStart());
        int selectionEnd = input == null ? 0 : Math.max(0, input.getSelectionEnd());
        int bottom = Math.max(0, messages.getHeight() - scroll.getHeight());
        scroll.scrollTo(0, bottom);
        if (keepInputFocus) {
            input.requestFocus();
            int length = input.length();
            input.setSelection(Math.min(selectionStart, length), Math.min(selectionEnd, length));
        }
    }

    private int dp(int value) {
        return Ui.dp(context, value);
    }

    private record HeaderResult(
            View root,
            TextView back,
            TextView clear,
            TextView refresh,
            TextView title
    ) {
    }

    private record ComposerResult(
            View root,
            EditText input,
            TextView emoji,
            TextView image,
            TextView send
    ) {
    }

    private record MessageVisual(
            boolean mine,
            String content,
            String type,
            String mediaUrl,
            long replyToMessageId,
            long replyToSenderId,
            String replyToType,
            String replyToPreview
    ) {
        private static MessageVisual from(JSONObject message) {
            return new MessageVisual(
                    message.optBoolean("mine", false),
                    message.optString("content", ""),
                    message.optString("type", "TEXT"),
                    message.optString("mediaUrl", ""),
                    message.optLong("replyToMessageId", 0L),
                    message.optLong("replyToSenderId", 0L),
                    message.optString("replyToType", ""),
                    message.optString("replyToPreview", "")
            );
        }
    }

    private record ReplyResult(
            LinearLayout root,
            TextView text,
            TextView cancel
    ) {
    }

    private record MessageTag(String key, MessageVisual visual) {
    }

    private enum EmptyTag {
        INSTANCE;

        private static final String KEY = "empty";
    }
}
