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

final class FriendCenterView {
    interface Actions {
        void bindClick(View view, Runnable action);

        void openSmartCenter();

        void refresh();

        void search();

        void acceptFriendRequest(RequestAction action);

        void rejectFriendRequest(RequestAction action);

        void requestFriend(UserAction action);

        void openChat(UserAction action);
    }

    record Result(View root, LinearLayout requestList, LinearLayout friendList) {
    }

    record RequestAction(long friendshipId) {
    }

    record UserAction(long userId, String displayName) {
        UserAction {
            displayName = displayName == null || displayName.trim().isEmpty()
                    ? "好友" : displayName.trim();
        }
    }

    private final Context context;
    private final Actions actions;

    FriendCenterView(Context context, Actions actions) {
        this.context = context;
        this.actions = actions;
    }

    Result build() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        addTools(root);

        LinearLayout requests = card();
        requests.addView(text("好友申请", 18, Ui.TEXT, Typeface.BOLD));
        LinearLayout requestList = new LinearLayout(context);
        requestList.setOrientation(LinearLayout.VERTICAL);
        addVerticalGap(requests, 8);
        requests.addView(requestList);
        root.addView(requests);

        LinearLayout friends = card();
        friends.addView(text("好友", 18, Ui.TEXT, Typeface.BOLD));
        LinearLayout friendList = new LinearLayout(context);
        friendList.setOrientation(LinearLayout.VERTICAL);
        addVerticalGap(friends, 8);
        friends.addView(friendList);
        root.addView(friends);

        return new Result(root, requestList, friendList);
    }

    void renderCenter(LinearLayout requestList, LinearLayout friendList, JSONObject data) {
        if (requestList == null || friendList == null) {
            return;
        }
        requestList.removeAllViews();
        friendList.removeAllViews();
        JSONObject center = data == null ? new JSONObject() : data;

        boolean hasRequests = addRequests(requestList, center.optJSONArray("incomingRequests"), true);
        hasRequests = addRequests(requestList, center.optJSONArray("outgoingRequests"), false)
                || hasRequests;
        if (!hasRequests) {
            requestList.addView(text("暂无好友申请", 14, Ui.MUTED, Typeface.NORMAL));
        }

        JSONArray friends = center.optJSONArray("friends");
        if (friends == null || friends.length() == 0) {
            friendList.addView(text("还没有好友，点“加好友”搜索用户。",
                    14, Ui.MUTED, Typeface.NORMAL));
            return;
        }
        for (int index = 0; index < friends.length(); index++) {
            JSONObject friend = friends.optJSONObject(index);
            if (friend != null) {
                addFriendRow(friendList, friend);
            }
        }
    }

    View buildSearchResults(JSONArray users) {
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(4), dp(8), dp(4));
        if (users == null || users.length() == 0) {
            list.addView(text("没有找到用户", 14, Ui.MUTED, Typeface.NORMAL));
            return list;
        }
        for (int index = 0; index < users.length(); index++) {
            JSONObject user = users.optJSONObject(index);
            if (user == null) {
                continue;
            }
            LinearLayout row = friendRowBase(friendDisplayName(user), friendMeta(user));
            TextView add = smallPill("申请", Ui.GREEN);
            LinearLayout actionRow = new LinearLayout(context);
            actionRow.setGravity(Gravity.RIGHT);
            actionRow.addView(add, new LinearLayout.LayoutParams(dp(64), dp(36)));
            row.addView(actionRow);
            long userId = user.optLong("id");
            String displayName = friendDisplayName(user);
            actions.bindClick(add, () -> actions.requestFriend(
                    new UserAction(userId, displayName)));
            list.addView(row, rowParams());
        }
        return list;
    }

    private void addTools(LinearLayout root) {
        LinearLayout tools = card();
        tools.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = iconButton(R.drawable.ic_action_back, Ui.MUTED, "返回智能中心");
        TextView refresh = iconButton(R.drawable.ic_action_refresh, Ui.GREEN, "刷新");
        TextView search = smallPill("加好友", Ui.GOLD);
        row.addView(back, new LinearLayout.LayoutParams(dp(64), dp(38)));
        row.addView(refresh, new LinearLayout.LayoutParams(dp(64), dp(38)));
        addHorizontalGap(row, 8);
        row.addView(search, new LinearLayout.LayoutParams(dp(82), dp(38)));
        tools.addView(row);
        root.addView(tools);
        actions.bindClick(back, actions::openSmartCenter);
        actions.bindClick(refresh, actions::refresh);
        actions.bindClick(search, actions::search);
    }

    private boolean addRequests(LinearLayout parent, JSONArray requests, boolean incoming) {
        if (requests == null) {
            return false;
        }
        boolean added = false;
        for (int index = 0; index < requests.length(); index++) {
            JSONObject request = requests.optJSONObject(index);
            if (request != null) {
                addFriendRequestRow(parent, request, incoming);
                added = true;
            }
        }
        return added;
    }

    private void addFriendRequestRow(LinearLayout parent, JSONObject request, boolean incoming) {
        JSONObject user = request.optJSONObject("user");
        LinearLayout row = friendRowBase(friendDisplayName(user),
                incoming ? friendMeta(user) : "等待对方同意 · " + friendMeta(user));
        if (incoming) {
            LinearLayout actionRow = new LinearLayout(context);
            actionRow.setGravity(Gravity.RIGHT);
            TextView accept = smallPill("同意", Ui.GREEN);
            TextView reject = smallPill("拒绝", Ui.RED);
            actionRow.addView(reject, new LinearLayout.LayoutParams(dp(58), dp(36)));
            addHorizontalGap(actionRow, 8);
            actionRow.addView(accept, new LinearLayout.LayoutParams(dp(58), dp(36)));
            row.addView(actionRow);
            long friendshipId = request.optLong("friendshipId");
            actions.bindClick(accept, () -> actions.acceptFriendRequest(
                    new RequestAction(friendshipId)));
            actions.bindClick(reject, () -> actions.rejectFriendRequest(
                    new RequestAction(friendshipId)));
        }
        parent.addView(row, rowParams());
    }

    private void addFriendRow(LinearLayout parent, JSONObject friend) {
        JSONObject user = friend.optJSONObject("user");
        String last = friend.optString("lastMessage", "");
        long unread = friend.optLong("unreadCount", 0);
        String subtitle = last == null || last.trim().isEmpty() ? friendMeta(user) : last;
        if (unread > 0) {
            subtitle = unread + " 条未读 · " + subtitle;
        }
        LinearLayout row = friendRowBase(friendDisplayName(user), subtitle, unread);
        TextView open = smallPill("聊天", unread > 0 ? Ui.RED : Ui.GREEN);
        LinearLayout actionRow = new LinearLayout(context);
        actionRow.setGravity(Gravity.RIGHT);
        actionRow.addView(open, new LinearLayout.LayoutParams(dp(64), dp(36)));
        row.addView(actionRow);
        long userId = user == null ? 0 : user.optLong("id");
        String displayName = friendDisplayName(user);
        actions.bindClick(row, () -> actions.openChat(new UserAction(userId, displayName)));
        actions.bindClick(open, () -> actions.openChat(new UserAction(userId, displayName)));
        parent.addView(row, rowParams());
    }

    private LinearLayout friendRowBase(String titleText, String subtitleText) {
        return friendRowBase(titleText, subtitleText, 0);
    }

    private LinearLayout friendRowBase(String titleText, String subtitleText, long unread) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(9), dp(10), dp(9));
        row.setBackground(Ui.stroke(Color.WHITE, dp(12), Ui.LINE, dp(1)));
        LinearLayout titleLine = new LinearLayout(context);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        titleLine.addView(text(titleText, 15, unread > 0 ? Ui.RED : Ui.TEXT, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, -2, 1));
        if (unread > 0) {
            TextView badge = text(unread > 99 ? "99+" : String.valueOf(unread),
                    11, Color.WHITE, Typeface.BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(Ui.round(Ui.RED, dp(999)));
            titleLine.addView(badge,
                    new LinearLayout.LayoutParams(dp(unread > 99 ? 34 : 26), dp(24)));
        }
        row.addView(titleLine);
        if (subtitleText != null && !subtitleText.trim().isEmpty()) {
            row.addView(text(subtitleText, 12, Ui.MUTED, Typeface.NORMAL));
        }
        return row;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(8));
        return params;
    }

    private String friendDisplayName(JSONObject user) {
        if (user == null) {
            return "好友";
        }
        String nickname = user.optString("nickname", "");
        if (nickname != null && !nickname.trim().isEmpty() && !"null".equals(nickname)) {
            return nickname.trim();
        }
        String username = user.optString("username", "");
        return username == null || username.trim().isEmpty() ? "好友" : username.trim();
    }

    private String friendMeta(JSONObject user) {
        if (user == null) {
            return "";
        }
        String username = user.optString("username", "");
        String phone = user.optString("phone", "");
        if (phone != null && !phone.trim().isEmpty() && !"null".equals(phone)) {
            return username + " · " + phone;
        }
        return username;
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

    private int dp(int value) {
        return Ui.dp(context, value);
    }
}
