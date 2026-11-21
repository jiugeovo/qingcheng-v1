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
import android.widget.Space;
import android.widget.TextView;
import android.widget.ScrollView;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AiHistoryView {
    private static final String HISTORY_LIST = "ai-history-list";
    private record Row(long id, String title, String detail) { }
    interface Actions {
        void bindClick(View view, Runnable action);
        void newConversation();
        void open(long conversationId);
        void delete(long conversationId);
        default void loadMore() { }
    }

    private final Context context;
    private final Actions actions;

    AiHistoryView(Context context, Actions actions) {
        this.context = context;
        this.actions = actions;
    }

    void render(LinearLayout target, JSONArray items) {
        render(target, AiHistoryState.conversations(items), false, false);
    }

    void render(LinearLayout target, List<AiHistoryState.ConversationSummary> items,
                boolean hasMore, boolean loading) {
        Map<Long, Row> rows = rows(items);
        View previous = target.findViewWithTag(HISTORY_LIST);
        HistoryList list;
        if (previous instanceof HistoryList existing && existing.getParent() == target) {
            list = existing;
            View fresh = target.findViewWithTag("new");
            if (fresh != null) actions.bindClick(fresh, actions::newConversation);
        } else {
            target.removeAllViews();
            addControls(target);
            list = new HistoryList();
            target.addView(list, new LinearLayout.LayoutParams(-1, -2));
        }
        list.submit(rows, actions, hasMore, loading);
    }

    private Map<Long, Row> rows(List<AiHistoryState.ConversationSummary> items) {
        Map<Long, Row> result = new LinkedHashMap<>();
        for (AiHistoryState.ConversationSummary item : items)
            result.putIfAbsent(item.id(), new Row(item.id(), item.title(), item.detail()));
        return result;
    }

    void addControls(LinearLayout target) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(context, "历史记录", 15, Ui.TEXT, Typeface.BOLD);
        TextView fresh = smallPill("新建对话", Ui.GREEN, "new");
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(fresh, new LinearLayout.LayoutParams(dp(100), dp(48)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        target.addView(row, params);
        actions.bindClick(fresh, actions::newConversation);
    }

    /** Finite viewport keeps the surrounding message ScrollView from expanding every row. */
    private final class HistoryList extends FrameLayout {
        private final RecyclerView recycler;
        private final TextView empty;
        private final HistoryAdapter adapter;
        private final TextView loadMore;
        private Map<Long, Row> rows = Map.of();
        private Actions currentActions;

        HistoryList() {
            super(context);
            setTag(HISTORY_LIST);
            recycler = new RecyclerView(context);
            recycler.setTag("ai-history-rows");
            recycler.setLayoutManager(new LinearLayoutManager(context));
            recycler.setNestedScrollingEnabled(true);
            recycler.setItemAnimator(null);
            recycler.setClipToPadding(false);
            adapter = new HistoryAdapter(this);
            recycler.setAdapter(adapter);
            FrameLayout.LayoutParams listParams = new FrameLayout.LayoutParams(-1, -1);
            listParams.bottomMargin = dp(54);
            addView(recycler, listParams);
            loadMore = smallPill("加载更多", Ui.GREEN, "load-more");
            FrameLayout.LayoutParams moreParams = new FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM);
            moreParams.topMargin = dp(6);
            addView(loadMore, moreParams);
            empty = Ui.text(context, "还没有历史对话。点新建对话后可以继续聊天。",
                    14, Ui.TEXT, Typeface.NORMAL);
            empty.setPadding(dp(12), dp(12), dp(12), dp(12));
            empty.setBackground(Ui.stroke(Color.WHITE, dp(14), Ui.LINE, dp(1)));
            addView(empty, new FrameLayout.LayoutParams(-1, -2));
        }

        void submit(Map<Long, Row> values, Actions nextActions, boolean hasMore, boolean loading) {
            // Latest data, rather than the asynchronously committed list, authorizes row actions.
            rows = Map.copyOf(values);
            currentActions = nextActions;
            loadMore.setVisibility(hasMore ? VISIBLE : GONE);
            loadMore.setText(loading ? "正在加载…" : "加载更多");
            loadMore.setEnabled(!loading);
            currentActions.bindClick(loadMore, currentActions::loadMore);
            recycler.setVisibility(rows.isEmpty() ? GONE : VISIBLE);
            empty.setVisibility(rows.isEmpty() ? VISIBLE : GONE);
            adapter.submitList(List.copyOf(values.values()));
            requestLayout();
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int height = Math.max(dp(160), getResources().getDisplayMetrics().heightPixels - dp(260));
            ViewParent ancestor = getParent();
            View child = this;
            int occupied = 0;
            while (ancestor instanceof View view) {
                if (view instanceof ScrollView && view.getMeasuredHeight() > 0) {
                    height = Math.max(1, view.getMeasuredHeight()
                            - view.getPaddingTop() - view.getPaddingBottom() - occupied);
                    break;
                }
                occupied += view.getPaddingTop() + view.getPaddingBottom();
                if (view instanceof LinearLayout layout && layout.getOrientation() == LinearLayout.VERTICAL) {
                    for (int index = 0; index < layout.getChildCount(); index++) {
                        View sibling = layout.getChildAt(index);
                        if (sibling == child || sibling.getVisibility() == GONE) continue;
                        occupied += sibling.getMeasuredHeight();
                        if (sibling.getLayoutParams() instanceof ViewGroup.MarginLayoutParams margins) {
                            occupied += margins.topMargin + margins.bottomMargin;
                        }
                    }
                }
                child = view;
                ancestor = view.getParent();
            }
            if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
            }
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height,
                    rows.isEmpty() ? MeasureSpec.AT_MOST : MeasureSpec.EXACTLY));
        }
    }

    private final class HistoryAdapter extends ListAdapter<Row, HistoryHolder> {
        private final HistoryList list;

        HistoryAdapter(HistoryList list) {
            super(new DiffUtil.ItemCallback<>() {
                @Override public boolean areItemsTheSame(Row before, Row after) { return before.id() == after.id(); }
                @Override public boolean areContentsTheSame(Row before, Row after) { return before.equals(after); }
            });
            this.list = list;
            setHasStableIds(true);
            setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        }

        @Override public long getItemId(int position) { return getItem(position).id(); }
        @Override public HistoryHolder onCreateViewHolder(ViewGroup parent, int type) { return new HistoryHolder(list); }
        @Override public void onBindViewHolder(HistoryHolder holder, int position) { holder.bind(getItem(position)); }
        @Override public void onViewRecycled(HistoryHolder holder) {
            holder.unbind();
            super.onViewRecycled(holder);
        }
    }

    private final class HistoryHolder extends RecyclerView.ViewHolder {
        private final HistoryList list;
        private final TextView title;
        private final TextView detail;
        private final TextView open;
        private final TextView delete;
        private long boundId;

        HistoryHolder(HistoryList list) {
            super(new LinearLayout(context));
            this.list = list;
            LinearLayout row = (LinearLayout) itemView;
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(8), dp(6), dp(8));
            row.setBackground(Ui.stroke(Color.WHITE, dp(12), Ui.LINE, dp(1)));
            title = Ui.text(context, "", 13, Ui.TEXT, Typeface.BOLD);
            title.setSingleLine(false);
            detail = Ui.text(context, "", 12, Ui.MUTED, Typeface.NORMAL);
            detail.setPadding(0, dp(3), 0, 0);
            LinearLayout description = new LinearLayout(context);
            description.setOrientation(LinearLayout.VERTICAL);
            description.addView(title);
            description.addView(detail);
            open = smallPill("打开", Ui.GREEN, null);
            delete = iconButton("⌫", Ui.RED, null);
            row.addView(description, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(open, new LinearLayout.LayoutParams(dp(58), dp(48)));
            row.addView(new Space(context), new LinearLayout.LayoutParams(dp(6), 1));
            row.addView(delete, new LinearLayout.LayoutParams(dp(48), dp(48)));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
            params.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(params);
        }

        void bind(Row row) {
            boundId = row.id();
            title.setText(row.title());
            detail.setText(row.detail());
            detail.setVisibility(row.detail().isEmpty() ? View.GONE : View.VISIBLE);
            open.setTag("open:" + row.id());
            delete.setTag("delete:" + row.id());
            open.setContentDescription("打开对话：" + row.title());
            delete.setContentDescription("删除对话：" + row.title());
            list.currentActions.bindClick(itemView, () -> dispatch(row.id(), false));
            list.currentActions.bindClick(open, () -> dispatch(row.id(), false));
            list.currentActions.bindClick(delete, () -> dispatch(row.id(), true));
        }

        private void dispatch(long id, boolean deleting) {
            if (id != boundId || getBindingAdapterPosition() == RecyclerView.NO_POSITION
                    || !list.rows.containsKey(id)) return;
            if (deleting) list.currentActions.delete(id);
            else list.currentActions.open(id);
        }

        void unbind() {
            boundId = 0;
            itemView.setOnClickListener(null);
            open.setOnClickListener(null);
            delete.setOnClickListener(null);
        }
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

    private TextView iconButton(String label, int color, String tag) {
        TextView view = Ui.text(context, label, 22, Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, 0, 0, dp(2));
        view.setBackground(Ui.block(color, dp(12), dp(1)));
        view.setTag(tag);
        return view;
    }

    private int dp(int value) {
        return Ui.dp(context, value);
    }
}
