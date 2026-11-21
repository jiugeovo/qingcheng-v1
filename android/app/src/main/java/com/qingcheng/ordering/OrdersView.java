package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Order presentation owns row identity and pending UI, never network or authentication. */
final class OrdersView {
    interface Actions {
        void bindClick(View view, Runnable action);
        void requestStatus(StatusAction action);
        void requestDelete(DeleteAction action);
    }

    record OrderKey(boolean menu, long orderId) { }
    record Anchor(OrderKey key, int position, int offset) { }

    record Prepared(List<Row> rows, String json, boolean admin, boolean menu) {
        Prepared {
            rows = List.copyOf(rows);
            if (json == null) throw new IllegalArgumentException("order snapshot is incomplete");
        }
    }

    record StatusAction(OrderKey key, String status, OrderList list) {
        long orderId() { return key.orderId(); }
        boolean menu() { return key.menu(); }
    }

    record DeleteAction(OrderKey key, OrderList list) {
        long orderId() { return key.orderId(); }
        boolean menu() { return key.menu(); }
    }

    record Row(OrderKey key, String title, String summary, String lines,
               String remark, boolean admin, boolean actionable, List<String> serverActions, String pending) {
        Row withPending(String action) {
            return new Row(key, title, summary, lines, remark, admin, actionable, serverActions, action);
        }

        boolean allows(String action) {
            return serverActions == null ? actionable && ("COMPLETED".equals(action) || "CANCELLED".equals(action))
                    : serverActions.contains(action);
        }
    }

    private final Context context;
    private final Actions actions;

    OrdersView(Context context, Actions actions) {
        this.context = context;
        this.actions = actions;
    }

    OrderList renderReadonly(LinearLayout parent, JSONArray items) throws Exception {
        Prepared prepared = prepare(items, false, true);
        return renderReadonly(parent, prepared);
    }

    OrderList renderReadonly(LinearLayout parent, Prepared prepared) {
        OrderList list = obtain(parent, "orders-personal", "你还没有提交订单");
        list.replace(prepared.rows());
        return list;
    }

    OrderList renderAdmin(LinearLayout parent, JSONArray items, boolean menu) throws Exception {
        return renderAdmin(parent, prepare(items, true, menu));
    }

    OrderList renderAdmin(LinearLayout parent, Prepared prepared) {
        boolean menu = prepared.menu();
        OrderList list = obtain(parent, menu ? "orders-menu-queue" : "orders-shop-queue", "暂无订单");
        list.replace(prepared.rows());
        return list;
    }

    static Prepared prepare(JSONArray items, boolean admin, boolean menu) throws Exception {
        String json = items == null ? "[]" : items.toString();
        return new Prepared(parse(new JSONArray(json), admin, menu), json, admin, menu);
    }

    private OrderList obtain(LinearLayout parent, String scope, String emptyMessage) {
        OrderList existing = findList(parent);
        if (existing != null && existing.scope().equals(scope)) {
            return existing;
        }
        if (existing != null) {
            parent.removeView(existing);
        }
        OrderList list = new OrderList(context, actions, scope, emptyMessage);
        parent.addView(list, new LinearLayout.LayoutParams(-1, -2));
        return list;
    }

    static OrderList findList(View parent) {
        if (parent instanceof OrderList list) {
            return list;
        }
        if (parent instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                OrderList list = findList(group.getChildAt(index));
                if (list != null) {
                    return list;
                }
            }
        }
        return null;
    }

    /** A finite viewport preserves RecyclerView recycling inside the existing scroll shell. */
    static final class OrderList extends FrameLayout {
        private final String scope;
        private final RecyclerView recycler;
        private final LinearLayoutManager layoutManager;
        private final OrderAdapter adapter;
        private final TextView empty;
        // Authoritative snapshot: AsyncListDiffer may still be applying an older list.
        private List<Row> rows = List.of();
        private Map<OrderKey, Row> rowsByKey = Map.of();
        private Anchor pendingAnchor;
        private long revision;

        private OrderList(Context context, Actions actions, String scope, String emptyMessage) {
            super(context);
            this.scope = scope;
            recycler = new RecyclerView(context);
            recycler.setClipToPadding(false);
            recycler.setPadding(0, 0, 0, Ui.dp(context, 4));
            layoutManager = new LinearLayoutManager(context);
            recycler.setLayoutManager(layoutManager);
            recycler.setNestedScrollingEnabled(true);
            // Pending updates must not cross-fade or leave duplicate actionable holders visible.
            recycler.setItemAnimator(null);
            adapter = new OrderAdapter(context, actions, this);
            recycler.setAdapter(adapter);
            recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override public void onScrolled(RecyclerView view, int dx, int dy) {
                    if (pendingAnchor != null && view.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
                        Anchor latest = captureAnchor();
                        if (latest != null) {
                            pendingAnchor = latest;
                        }
                    }
                }
            });
            addView(recycler, new FrameLayout.LayoutParams(-1, -1));
            empty = text(context, emptyMessage, 15, Ui.MUTED, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Ui.dp(context, 14), Ui.dp(context, 20),
                    Ui.dp(context, 14), Ui.dp(context, 20));
            empty.setBackground(RefreshUi.card(context, RefreshUi.WHITE, 22));
            addView(empty, new FrameLayout.LayoutParams(-1, -2));
            recycler.setVisibility(GONE);
        }

        String scope() { return scope; }

        boolean isAttachedTo(LinearLayout parent) {
            return parent != null && getParent() == parent;
        }

        boolean contains(OrderKey key) { return indexOf(key) >= 0; }

        boolean beginPending(OrderKey key, String action) {
            int index = indexOf(key);
            if (index < 0 || action == null || action.isEmpty() || !rows.get(index).pending().isEmpty()) {
                return false;
            }
            List<Row> next = new ArrayList<>(rows);
            next.set(index, rows.get(index).withPending(action));
            submit(next, false);
            // A second click can happen before the differ commits. Update this holder immediately.
            adapter.refreshVisible(recycler, key);
            return true;
        }

        void clearPending(OrderKey key) {
            int index = indexOf(key);
            if (index < 0 || rows.get(index).pending().isEmpty()) {
                return;
            }
            List<Row> next = new ArrayList<>(rows);
            next.set(index, rows.get(index).withPending(""));
            submit(next, false);
            adapter.refreshVisible(recycler, key);
        }

        void remove(OrderKey key) {
            if (!contains(key)) {
                return;
            }
            List<Row> next = new ArrayList<>(rows);
            next.removeIf(row -> row.key().equals(key));
            if (next.isEmpty()) {
                empty.setText("暂无待处理订单");
            }
            submit(next, true);
        }

        void submitReadonly(JSONArray items) throws Exception {
            replace(parse(items, false, true));
        }

        void submitAdmin(JSONArray items, boolean menu) throws Exception {
            replace(parse(items, true, menu));
        }

        private void replace(List<Row> incoming) {
            List<Row> next = new ArrayList<>(incoming.size());
            for (Row row : incoming) {
                Row current = row(row.key());
                next.add(current == null ? row : row.withPending(current.pending()));
            }
            submit(next, true);
        }

        private void submit(List<Row> next, boolean preserveAnchor) {
            if (preserveAnchor && pendingAnchor == null) {
                pendingAnchor = captureAnchor();
            }
            rows = List.copyOf(next);
            Map<OrderKey, Row> indexed = new HashMap<>();
            for (Row row : rows) {
                indexed.put(row.key(), row);
            }
            rowsByKey = Map.copyOf(indexed);
            long submission = ++revision;
            recycler.setVisibility(rows.isEmpty() ? GONE : VISIBLE);
            empty.setVisibility(rows.isEmpty() ? VISIBLE : GONE);
            requestLayout();
            adapter.submitList(rows, () -> {
                if (submission == revision) {
                    applyPendingAnchor();
                }
            });
        }

        Anchor captureAnchor() {
            int position = layoutManager.findFirstVisibleItemPosition();
            if (position == RecyclerView.NO_POSITION || position >= adapter.getCurrentList().size()) {
                return null;
            }
            View child = layoutManager.findViewByPosition(position);
            if (child == null || !(recycler.getChildViewHolder(child) instanceof OrderHolder holder)
                    || holder.key == null) {
                return null;
            }
            return new Anchor(holder.key, position,
                    layoutManager.getDecoratedTop(child) - recycler.getPaddingTop());
        }

        void restoreAnchor(Anchor anchor) {
            if (anchor == null) {
                return;
            }
            pendingAnchor = anchor;
            if (adapter.getCurrentList().equals(rows)) {
                applyPendingAnchor();
            }
        }

        private void applyPendingAnchor() {
            if (pendingAnchor == null || rows.isEmpty()) {
                return;
            }
            Anchor anchor = pendingAnchor;
            pendingAnchor = null;
            int position = indexOf(anchor.key());
            if (position < 0) {
                position = Math.max(0, Math.min(anchor.position(), rows.size() - 1));
            }
            layoutManager.scrollToPositionWithOffset(position, anchor.offset());
        }

        private int indexOf(OrderKey key) {
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).key().equals(key)) {
                    return index;
                }
            }
            return -1;
        }

        private Row row(OrderKey key) {
            return rowsByKey.get(key);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int viewport = Math.max(Ui.dp(getContext(), 160),
                    getResources().getDisplayMetrics().heightPixels - Ui.dp(getContext(), 220));
            ViewParent ancestor = getParent();
            while (ancestor instanceof View view) {
                if (view instanceof ScrollView && view.getMeasuredHeight() > 0) {
                    int occupied = view.getPaddingTop() + view.getPaddingBottom();
                    if (getParent() instanceof LinearLayout parent) {
                        occupied += parent.getPaddingTop() + parent.getPaddingBottom();
                        for (int index = 0; index < parent.getChildCount(); index++) {
                            View sibling = parent.getChildAt(index);
                            if (sibling == this) {
                                break;
                            }
                            if (sibling.getVisibility() != GONE) {
                                occupied += sibling.getMeasuredHeight();
                                if (sibling.getLayoutParams() instanceof MarginLayoutParams margins) {
                                    occupied += margins.topMargin + margins.bottomMargin;
                                }
                            }
                        }
                    }
                    viewport = Math.max(Ui.dp(getContext(), 160), view.getMeasuredHeight() - occupied);
                    break;
                }
                ancestor = view.getParent();
            }
            if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                viewport = Math.min(viewport, MeasureSpec.getSize(heightMeasureSpec));
            }
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(viewport,
                    rows.isEmpty() ? MeasureSpec.AT_MOST : MeasureSpec.EXACTLY));
        }
    }

    private static List<Row> parse(JSONArray items, boolean admin, boolean menu) throws Exception {
        List<Row> rows = new ArrayList<>();
        Set<OrderKey> keys = new HashSet<>();
        if (items == null) {
            return rows;
        }
        for (int index = 0; index < items.length(); index++) {
            JSONObject order = items.getJSONObject(index);
            boolean orderMenu = admin ? menu : order.optBoolean("_orderMenu", true);
            OrderKey key = new OrderKey(orderMenu, order.optLong("id"));
            if (!keys.add(key)) {
                continue;
            }
            String who = order.optString("nickname", order.optString("username", "用户"));
            String type = DisplayFormatters.orderType(order, orderMenu);
            JSONArray actions = order.optJSONArray("allowedActions");
            List<String> serverActions = null;
            if (actions != null) {
                List<String> parsed = new ArrayList<>(actions.length());
                for (int action = 0; action < actions.length(); action++) parsed.add(actions.optString(action));
                serverActions = List.copyOf(parsed);
            }
            String remark = order.optString("remark", "");
            String label = admin ? (serverActions == null ? who : who + " · " + type) : type;
            String storeLabel = DisplayFormatters.storeOrderLabel(order, orderMenu);
            String title = storeLabel.isEmpty() ? "#" + key.orderId() + " " + label
                    : admin ? storeLabel + " · " + who : storeLabel;
            String time = storeLabel.isEmpty() || order.isNull("createdAt") ? ""
                    : DisplayFormatters.shortTime(order.optString("createdAt", ""));
            String summary = (time.isEmpty() ? "" : time + "\n")
                    + "状态：" + DisplayFormatters.orderStatus(order.optString("status"))
                    + "    合计：" + order.optString("totalAmount", "0") + " 青程币";
            rows.add(new Row(key, title, summary,
                    DisplayFormatters.orderLines(order.optJSONArray("items")),
                    remark.isEmpty() || "null".equals(remark) ? "" : "备注：" + remark,
                    admin, serverActions == null ? admin && "PENDING".equals(order.optString("status"))
                            : !serverActions.isEmpty(), serverActions, ""));
        }
        return List.copyOf(rows);
    }

    private static final class OrderAdapter extends ListAdapter<Row, OrderHolder> {
        private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<>() {
            @Override public boolean areItemsTheSame(Row before, Row after) {
                return before.key().equals(after.key());
            }
            @Override public boolean areContentsTheSame(Row before, Row after) {
                return before.equals(after);
            }
        };
        private final Context context;
        private final Actions actions;
        private final OrderList list;

        OrderAdapter(Context context, Actions actions, OrderList list) {
            super(DIFF);
            this.context = context;
            this.actions = actions;
            this.list = list;
            setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        }

        @Override public OrderHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new OrderHolder(context);
        }

        @Override public void onBindViewHolder(OrderHolder holder, int position) {
            Row row = getItem(position);
            Row latest = list.row(row.key());
            holder.bind(latest == null ? row : latest, actions, list);
        }

        @Override public void onViewRecycled(OrderHolder holder) {
            holder.unbind();
            super.onViewRecycled(holder);
        }

        void refreshVisible(RecyclerView recycler, OrderKey key) {
            Row row = list.row(key);
            if (row == null) {
                return;
            }
            for (int index = 0; index < recycler.getChildCount(); index++) {
                RecyclerView.ViewHolder candidate = recycler.getChildViewHolder(recycler.getChildAt(index));
                if (candidate instanceof OrderHolder holder && key.equals(holder.key)) {
                    holder.bind(row, actions, list);
                }
            }
        }
    }

    private static final class OrderHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView summary;
        private final TextView lines;
        private final TextView remark;
        private final LinearLayout statusRow;
        private final LinearLayout deleteRow;
        private final TextView complete;
        private final TextView cancel;
        private final TextView accept;
        private final TextView delete;
        private OrderKey key;

        OrderHolder(Context context) {
            super(card(context));
            LinearLayout card = (LinearLayout) itemView;
            title = text(context, "", 17, RefreshUi.TEXT, Typeface.BOLD);
            summary = text(context, "", 13, RefreshUi.GREEN, Typeface.BOLD);
            summary.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 8));
            lines = text(context, "", 14, RefreshUi.TEXT, Typeface.NORMAL);
            lines.setLineSpacing(Ui.dp(context, 4), 1f);
            remark = text(context, "", 12, RefreshUi.MUTED, Typeface.NORMAL);
            card.addView(title);
            card.addView(summary);
            card.addView(lines);
            card.addView(remark);
            statusRow = actionRow(context, 10);
            complete = pill(context, "完成", RefreshUi.GREEN);
            accept = pill(context, "接单", RefreshUi.GREEN);
            cancel = pill(context, "取消", Ui.RED);
            statusRow.addView(cancel);
            statusRow.addView(new Space(context), new LinearLayout.LayoutParams(Ui.dp(context, 8), 1));
            statusRow.addView(complete);
            statusRow.addView(accept);
            card.addView(statusRow);
            deleteRow = actionRow(context, 8);
            delete = pill(context, "删除订单", Ui.RED);
            deleteRow.addView(delete);
            card.addView(deleteRow);
        }

        void bind(Row row, Actions actions, OrderList list) {
            key = row.key();
            title.setText(row.title());
            summary.setText(row.summary());
            lines.setText(row.lines());
            lines.setVisibility(row.lines().isEmpty() ? View.GONE : View.VISIBLE);
            remark.setText(row.remark());
            remark.setVisibility(row.remark().isEmpty() ? View.GONE : View.VISIBLE);
            statusRow.setVisibility(row.actionable() ? View.VISIBLE : View.GONE);
            boolean legacy = row.serverActions() == null;
            boolean deletable = legacy && row.admin();
            deleteRow.setVisibility(deletable ? View.VISIBLE : View.GONE);
            bindStatus(complete, row, actions, list, legacy ? "COMPLETED" : "COMPLETE", "完成");
            bindStatus(cancel, row, actions, list, legacy ? "CANCELLED" : "CANCEL", row.admin() ? "取消" : "撤销");
            bindStatus(accept, row, actions, list, "ACCEPT", "接单");
            button(delete, "DELETE".equals(row.pending()) ? "删除中" : "删除订单", row.pending());
            if (deletable) {
                actions.bindClick(delete, () -> {
                    Row current = list.row(row.key());
                    if (row.key().equals(key) && current != null && current.pending().isEmpty()) {
                        actions.requestDelete(new DeleteAction(row.key(), list));
                    }
                });
            } else {
                delete.setOnClickListener(null);
            }
        }

        private void bindStatus(TextView view, Row row, Actions actions, OrderList list, String action, String label) {
            boolean allowed = row.allows(action);
            view.setVisibility(allowed ? View.VISIBLE : View.GONE);
            button(view, action.equals(row.pending()) ? "处理中" : label, row.pending());
            if (allowed) actions.bindClick(view, () -> requestStatus(row.key(), action, actions, list));
            else view.setOnClickListener(null);
        }

        private void requestStatus(OrderKey target, String status, Actions actions, OrderList list) {
            Row current = list.row(target);
            if (target.equals(key) && current != null && current.allows(status) && current.pending().isEmpty()) {
                actions.requestStatus(new StatusAction(target, status, list));
            }
        }

        void unbind() {
            key = null;
            complete.setOnClickListener(null);
            cancel.setOnClickListener(null);
            accept.setOnClickListener(null);
            delete.setOnClickListener(null);
        }

        private static void button(TextView view, String label, String pending) {
            view.animate().cancel();
            view.setTranslationY(0);
            view.setScaleX(1);
            view.setScaleY(1);
            view.setText(label);
            view.setEnabled(pending.isEmpty());
            view.setAlpha(pending.isEmpty() ? 1f : .55f);
        }
    }

    private static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(context, 18), Ui.dp(context, 18), Ui.dp(context, 18), Ui.dp(context, 18));
        card.setBackground(RefreshUi.card(context, RefreshUi.WHITE, 22));
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, Ui.dp(context, 12));
        card.setLayoutParams(params);
        return card;
    }

    private static LinearLayout actionRow(Context context, int padding) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.END);
        row.setPadding(0, Ui.dp(context, padding), 0, 0);
        return row;
    }

    private static TextView pill(Context context, String label, int color) {
        TextView view = text(context, label, 14, Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(Ui.dp(context, 82));
        view.setPadding(Ui.dp(context, 13), Ui.dp(context, 8), Ui.dp(context, 13), Ui.dp(context, 8));
        view.setBackground(Ui.round(color, Ui.dp(context, 14)));
        return view;
    }

    private static TextView text(Context context, String value, int sp, int color, int style) {
        TextView view = Ui.text(context, value, sp, color, style);
        view.setSingleLine(false);
        return view;
    }
}
