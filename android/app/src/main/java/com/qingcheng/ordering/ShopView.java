package com.qingcheng.ordering;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

final class ShopView {
    interface Actions {
        void bindClick(View view, Runnable action);
        void switchSection(boolean menu);
        void createItem(boolean menu);
        void editItem(ItemAction action);
        void requestDelete(DeleteAction action);
        void orderItem(ItemAction action);
        default boolean canOrder(boolean menu) { return true; }
        void loadImage(String url, ImageView target);
        default void cancelImage(ImageView target) { }
    }

    private static final String PRODUCT_SCROLL = "catalog-products";
    private static final String CATEGORY_SCROLL = "catalog-categories";
    private static final String CATALOG_HOST = "catalog-host";

    record Viewport(int outerY, int productY, int categoryY, String anchorId, int anchorOffset) {
        static final Viewport TOP = new Viewport(0, 0, 0);
        Viewport(int outerY, int productY, int categoryY) {
            this(outerY, productY, categoryY, null, 0);
        }
    }

    static Viewport captureViewport(ScrollView outer, View content) {
        RecyclerView products = content.findViewWithTag(PRODUCT_SCROLL);
        HorizontalScrollView categories = content.findViewWithTag(CATEGORY_SCROLL);
        String anchor = null;
        int offset = 0;
        if (products != null && products.getLayoutManager() instanceof LinearLayoutManager layout
                && products.getAdapter() instanceof ShopView.CatalogAdapter adapter) {
            int position = layout.findFirstVisibleItemPosition();
            View child = layout.findViewByPosition(position);
            if (position >= 0 && position < adapter.getItemCount() && child != null) {
                anchor = adapter.row(position).key();
                offset = layout.getDecoratedTop(child) - products.getPaddingTop();
            }
        }
        return new Viewport(outer == null ? 0 : outer.getScrollY(),
                products == null ? 0 : products.computeVerticalScrollOffset(),
                categories == null ? 0 : categories.getScrollX(), anchor, offset);
    }

    static void restoreViewport(ScrollView outer, View content, Viewport viewport) {
        new ViewportRestoration(outer, content, viewport).awaitLayout();
    }

    /** Parse fully before submission; bad responses never destroy the last good catalog. */
    static boolean updateCatalog(View content, JSONArray items, boolean menu, Runnable onCommitted)
            throws JSONException {
        View found = content.findViewWithTag(CATALOG_HOST);
        if (!(found instanceof ShopView.CatalogHost host) || host.menu != menu) return false;
        return updateCatalog(content, CatalogRows.prepare(items, menu, host.presets), onCommitted);
    }

    static boolean updateCatalog(View content, CatalogRows.Prepared prepared, Runnable onCommitted) {
        View found = content.findViewWithTag(CATALOG_HOST);
        if (!(found instanceof ShopView.CatalogHost host) || host.menu != prepared.menu()) return false;
        host.submit(prepared.snapshot(), onCommitted);
        return true;
    }

    static boolean updateCatalog(View content, JSONArray items, boolean menu) throws JSONException {
        return updateCatalog(content, items, menu, () -> { });
    }

    /** Wait for a real viewport; restoring before first layout would clamp offsets to zero. */
    private static final class ViewportRestoration
            implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        private final ScrollView outer;
        private final View content;
        private final Viewport viewport;

        private ViewportRestoration(ScrollView outer, View content, Viewport viewport) {
            this.outer = outer;
            this.content = content;
            this.viewport = viewport;
        }

        private void awaitLayout() {
            content.addOnAttachStateChangeListener(this);
            content.getViewTreeObserver().addOnPreDrawListener(this);
            content.invalidate();
        }

        @Override public boolean onPreDraw() {
            if (!content.isAttachedToWindow() || !content.isLaidOut() || content.isLayoutRequested()
                    || (outer != null && outer.isLayoutRequested())) return true;
            dispose();
            RecyclerView products = content.findViewWithTag(PRODUCT_SCROLL);
            HorizontalScrollView categories = content.findViewWithTag(CATEGORY_SCROLL);
            if (outer != null) outer.scrollTo(0, viewport.outerY());
            if (products != null && products.getLayoutManager() instanceof LinearLayoutManager layout
                    && products.getAdapter() instanceof ShopView.CatalogAdapter adapter) {
                int position = adapter.positionOf(viewport.anchorId());
                if (position >= 0) layout.scrollToPositionWithOffset(position, viewport.anchorOffset());
                else if (viewport.anchorId() == null && viewport.productY() == 0)
                    layout.scrollToPositionWithOffset(0, 0);
                else if (viewport.anchorId() == null)
                    products.scrollBy(0, viewport.productY() - products.computeVerticalScrollOffset());
                // Removed anchors use RecyclerView's surviving neighbouring row.
            }
            // categoryY is the legacy saved field; the refreshed category rail scrolls horizontally.
            if (categories != null) categories.scrollTo(viewport.categoryY(), 0);
            // scrollToPositionWithOffset schedules a layout; do not paint the interim top frame.
            return products == null || !products.isLayoutRequested();
        }

        private void dispose() {
            content.removeOnAttachStateChangeListener(this);
            content.getViewTreeObserver().removeOnPreDrawListener(this);
        }
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) { dispose(); }
    }

    record State(boolean showingMenu, boolean manager, int screenHeightPx, List<String> presetCategories) {
        State { presetCategories = presetCategories == null ? List.of() : List.copyOf(presetCategories); }
    }
    record ItemAction(JSONObject item, boolean menu) { }
    record DeleteAction(JSONObject item, boolean menu) { }

    private final Context context;
    private final Actions actions;
    private final State state;

    ShopView(Context context, Actions actions, State state) {
        this.context = context;
        this.actions = actions;
        this.state = state;
    }

    void buildTabs(LinearLayout parent) {
        LinearLayout tabs = new LinearLayout(context);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(6), dp(6), dp(6), dp(6));
        tabs.setBackground(RefreshUi.card(context, RefreshUi.WHITE, 18));
        TextView menuTab = shopTab("菜单", state.showingMenu());
        TextView shopTab = shopTab("宝宝商店", !state.showingMenu());
        tabs.addView(menuTab, new LinearLayout.LayoutParams(0, dp(48), 1));
        addHorizontalGap(tabs, 8);
        tabs.addView(shopTab, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        parent.addView(tabs, params);
        actions.bindClick(menuTab, () -> actions.switchSection(true));
        actions.bindClick(shopTab, () -> actions.switchSection(false));
    }

    void renderMenu(LinearLayout parent, JSONArray items) throws Exception { renderCatalog(parent, items, true); }
    void renderBabyShop(LinearLayout parent, JSONArray items) throws Exception { renderCatalog(parent, items, false); }

    void renderMenu(LinearLayout parent, CatalogRows.Prepared prepared) { renderCatalog(parent, prepared); }
    void renderBabyShop(LinearLayout parent, CatalogRows.Prepared prepared) { renderCatalog(parent, prepared); }

    private void renderCatalog(LinearLayout parent, JSONArray items, boolean menu) throws JSONException {
        renderCatalog(parent, CatalogRows.prepare(items, menu, state.presetCategories()));
    }

    private void renderCatalog(LinearLayout parent, CatalogRows.Prepared prepared) {
        boolean menu = prepared.menu();
        CatalogRows.Snapshot snapshot = prepared.snapshot();
        if (state.manager()) addTopAction(parent, menu ? "新建菜品" : "新建产品",
                Ui.GREEN, () -> actions.createItem(menu));
        addSectionTitle(parent, menu ? "今日菜单" : "宝宝商店");
        CatalogHost host = new CatalogHost(menu);
        addSearch(parent, host, menu);
        // Bounded viewport: wrap_content inside the shell ScrollView defeats recycling.
        int height = Math.max(dp(280), state.screenHeightPx() - dp(238));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, height);
        params.setMargins(0, 0, 0, dp(16));
        parent.addView(host, params);
        host.submit(snapshot, () -> { });
    }

    private void addSearch(LinearLayout parent, CatalogHost host, boolean menu) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), 0, dp(8), 0);
        row.setBackground(RefreshUi.card(context, RefreshUi.WHITE, 16));
        EditText input = Ui.input(context, menu ? "搜索菜品" : "搜索商品");
        input.setTag("catalog-search");
        input.setContentDescription(menu ? "搜索菜品" : "搜索商品");
        input.setTextSize(14);
        input.setTextColor(RefreshUi.TEXT);
        input.setHintTextColor(RefreshUi.MUTED);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setBackgroundColor(Color.TRANSPARENT);
        row.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        TextView clear = text("清除", 13, RefreshUi.MUTED, Typeface.NORMAL);
        clear.setGravity(Gravity.CENTER);
        clear.setVisibility(View.GONE);
        row.addView(clear, new LinearLayout.LayoutParams(dp(48), dp(48)));
        actions.bindClick(clear, () -> input.setText(""));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clear.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                host.search(s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            InputMethodManager keyboard = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
            return true;
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        parent.addView(row, params);
    }

    private final class CatalogHost extends LinearLayout {
        private final boolean menu;
        private final List<String> presets = state.presetCategories();
        private final RecyclerView products;
        private final LinearLayoutManager layout;
        private final CatalogAdapter adapter;
        private final HorizontalScrollView categoryScroll;
        private final LinearLayout rail;
        private final TextView empty;
        private final List<TextView> tabs = new ArrayList<>();
        private List<String> categories = List.of();
        private CatalogRows.Snapshot unfiltered = new CatalogRows.Snapshot(List.of(), List.of());
        private String query = "";
        private final List<Runnable> pendingCommits = new ArrayList<>();
        private long submissionVersion;

        CatalogHost(boolean menu) {
            super(context);
            this.menu = menu;
            setTag(CATALOG_HOST);
            setOrientation(VERTICAL);
            setBaselineAligned(false);
            categoryScroll = new HorizontalScrollView(context);
            categoryScroll.setTag(CATEGORY_SCROLL);
            categoryScroll.setHorizontalScrollBarEnabled(false);
            categoryScroll.setNestedScrollingEnabled(true);
            rail = new LinearLayout(context);
            rail.setOrientation(HORIZONTAL);
            rail.setPadding(0, 0, 0, dp(12));
            categoryScroll.addView(rail, new HorizontalScrollView.LayoutParams(-2, -2));
            addView(categoryScroll, new LinearLayout.LayoutParams(-1, -2));
            products = new RecyclerView(context);
            products.setTag(PRODUCT_SCROLL);
            products.setPadding(0, 0, 0, dp(12));
            products.setClipToPadding(false);
            products.setNestedScrollingEnabled(true);
            products.setHasFixedSize(true);
            products.setItemAnimator(null);
            int columns = state.manager() ? 1 : 2;
            GridLayoutManager grid = new GridLayoutManager(context, columns);
            grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override public int getSpanSize(int position) {
                    return adapter.row(position).heading() ? columns : 1;
                }
            });
            layout = grid;
            products.setLayoutManager(layout);
            adapter = new CatalogAdapter(menu);
            products.setAdapter(adapter);
            addView(products, new LinearLayout.LayoutParams(-1, 0, 1));
            empty = text(menu ? "还没有可点的菜品" : "商店暂时空空的", 15, Ui.MUTED, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
            products.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override public void onScrolled(RecyclerView view, int dx, int dy) { syncCategory(); }
            });
            products.addOnLayoutChangeListener((v, left, top, right, bottom,
                    oldLeft, oldTop, oldRight, oldBottom) -> syncCategory());
        }

        void submit(CatalogRows.Snapshot snapshot, Runnable committed) {
            unfiltered = snapshot;
            pendingCommits.add(committed);
            display(CatalogRows.filter(snapshot, query), false);
        }

        void search(String nextQuery) {
            query = nextQuery;
            display(CatalogRows.filter(unfiltered, query), true);
        }

        private void display(CatalogRows.Snapshot snapshot, boolean resetScroll) {
            long version = ++submissionVersion;
            int beforePosition = layout.findFirstVisibleItemPosition();
            View beforeView = layout.findViewByPosition(beforePosition);
            String anchorKey = beforePosition >= 0 && beforePosition < adapter.getItemCount() && beforeView != null
                    ? adapter.row(beforePosition).key() : null;
            int anchorOffset = beforeView == null ? 0 : layout.getDecoratedTop(beforeView) - products.getPaddingTop();
            adapter.submitList(snapshot.rows(), () -> {
                if (version != submissionVersion) return;
                boolean isEmpty = snapshot.rows().isEmpty();
                empty.setText(query.trim().isEmpty()
                        ? (menu ? "还没有可点的菜品" : "商店暂时空空的")
                        : (menu ? "没有找到匹配的菜品" : "没有找到匹配的商品"));
                empty.setVisibility(isEmpty ? VISIBLE : GONE);
                products.setVisibility(isEmpty ? GONE : VISIBLE);
                categoryScroll.setVisibility(menu && !isEmpty ? VISIBLE : GONE);
                if (!categories.equals(snapshot.categories())) {
                    categories = snapshot.categories();
                    rebuildCategories();
                }
                int anchorPosition = adapter.positionOf(anchorKey);
                if (resetScroll) {
                    products.stopScroll();
                    layout.scrollToPositionWithOffset(0, 0);
                    categoryScroll.scrollTo(0, 0);
                } else if (anchorPosition >= 0 && anchorPosition != beforePosition) {
                    // Grid rows change parity when a product is inserted. Keep the prior visible product in place.
                    layout.scrollToPositionWithOffset(anchorPosition, anchorOffset);
                }
                syncCategory();
                List<Runnable> callbacks = new ArrayList<>(pendingCommits);
                pendingCommits.clear();
                for (Runnable callback : callbacks) callback.run();
            });
        }

        private void rebuildCategories() {
            rail.removeAllViews();
            tabs.clear();
            for (String category : categories) {
                TextView tab = text(category, 12, RefreshUi.TEXT, Typeface.BOLD);
                tab.setGravity(Gravity.CENTER);
                tab.setMinHeight(dp(48));
                tab.setPadding(dp(16), dp(8), dp(16), dp(8));
                setMenuCategoryButtonSelected(tab, false);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
                params.setMargins(0, 0, dp(8), 0);
                rail.addView(tab, params);
                tabs.add(tab);
                actions.bindClick(tab, () -> {
                    int position = adapter.positionOf("menu:heading:" + category);
                    if (position < 0) return;
                    products.stopScroll();
                    if (MotionPolicy.systemAnimatorsEnabled(context)) {
                        LinearSmoothScroller scroller = new LinearSmoothScroller(context) {
                            @Override protected int getVerticalSnapPreference() { return SNAP_TO_START; }
                        };
                        scroller.setTargetPosition(position);
                        layout.startSmoothScroll(scroller);
                    } else layout.scrollToPositionWithOffset(position, 0);
                });
            }
        }

        private void syncCategory() {
            if (!menu || categories.isEmpty() || adapter.getItemCount() == 0) return;
            int first = layout.findFirstVisibleItemPosition();
            if (first < 0) first = 0;
            String selected = adapter.row(Math.min(first, adapter.getItemCount() - 1)).category();
            if (products.canScrollVertically(-1) && !products.canScrollVertically(1))
                selected = categories.get(categories.size() - 1);
            for (int index = 0; index < tabs.size(); index++) {
                TextView tab = tabs.get(index);
                boolean active = categories.get(index).equals(selected);
                if (tab.isSelected() != active) {
                    setMenuCategoryButtonSelected(tab, active);
                    if (active && categoryScroll.getWidth() > 0 && tab.getWidth() > 0) {
                        int left = categoryScroll.getScrollX();
                        int right = left + categoryScroll.getWidth();
                        if (tab.getLeft() < left) categoryScroll.scrollTo(tab.getLeft(), 0);
                        else if (tab.getRight() > right)
                            categoryScroll.scrollTo(tab.getRight() - categoryScroll.getWidth(), 0);
                    }
                }
            }
        }
    }

    private static final DiffUtil.ItemCallback<CatalogRows.Row> ROW_DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(CatalogRows.Row oldRow, CatalogRows.Row newRow) {
            return oldRow.key().equals(newRow.key());
        }
        @Override public boolean areContentsTheSame(CatalogRows.Row oldRow, CatalogRows.Row newRow) {
            return oldRow.equals(newRow);
        }
    };

    private final class CatalogAdapter extends ListAdapter<CatalogRows.Row, RecyclerView.ViewHolder> {
        private final boolean menu;
        // Allocated identities, not string hashes: different keys never collide.
        private final Map<String, Long> stableIds = new HashMap<>();
        private long nextStableId;

        CatalogAdapter(boolean menu) {
            super(ROW_DIFF);
            this.menu = menu;
            setHasStableIds(true);
            setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        }
        CatalogRows.Row row(int position) { return getItem(position); }
        int positionOf(String key) {
            if (key == null) return -1;
            for (int index = 0; index < getItemCount(); index++)
                if (key.equals(getItem(index).key())) return index;
            return -1;
        }
        @Override public long getItemId(int position) {
            return stableIds.computeIfAbsent(getItem(position).key(), ignored -> nextStableId++);
        }
        @Override public void onCurrentListChanged(List<CatalogRows.Row> previous,
                List<CatalogRows.Row> current) {
            HashSet<String> retained = new HashSet<>();
            for (CatalogRows.Row row : current) retained.add(row.key());
            stableIds.keySet().retainAll(retained);
        }
        @Override public int getItemViewType(int position) { return getItem(position).heading() ? 0 : 1; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            if (type == 0) {
                TextView title = text("", 18, RefreshUi.TEXT, Typeface.BOLD);
                title.setPadding(dp(2), dp(2), 0, dp(8));
                title.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new RecyclerView.ViewHolder(title) { };
            }
            return new ProductHolder(menu);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            CatalogRows.Row row = getItem(position);
            if (holder instanceof ProductHolder product) product.bind(row.product());
            else ((TextView) holder.itemView).setText(row.category());
        }
        @Override public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder instanceof ProductHolder product) {
                product.product = null;
                actions.cancelImage(product.image);
            }
            super.onViewRecycled(holder);
        }
    }

    private final class ProductHolder extends RecyclerView.ViewHolder {
        private final ImageView image;
        private final TextView name;
        private final TextView description;
        private final TextView price;
        private CatalogRows.Product product;

        ProductHolder(boolean menu) {
            super(new LinearLayout(context));
            boolean compact = menu;
            boolean grid = !state.manager();
            LinearLayout card = (LinearLayout) itemView;
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(compact ? 10 : 12), dp(10), dp(compact ? 10 : 12));
            card.setBackground(RefreshUi.card(context, RefreshUi.WHITE, 20));
            RecyclerView.LayoutParams cardParams = new RecyclerView.LayoutParams(-1, -2);
            cardParams.bottomMargin = dp(compact ? 10 : 12);
            if (grid) { cardParams.leftMargin = dp(3); cardParams.rightMargin = dp(3); }
            card.setLayoutParams(cardParams);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(grid ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            image.setBackground(Ui.round(menu ? RefreshUi.SAGE : RefreshUi.APRICOT, dp(12)));
            image.setClipToOutline(true);
            int size = dp(compact ? 74 : 92);
            row.addView(image, new LinearLayout.LayoutParams(grid ? -1 : size, grid ? dp(124) : size));
            LinearLayout info = new LinearLayout(context);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(grid ? 0 : dp(12), grid ? dp(10) : 0, 0, 0);
            name = text("", grid ? 14 : 17, RefreshUi.TEXT, Typeface.BOLD);
            name.setMaxLines(2);
            info.addView(name);
            description = text("", grid ? 11 : 13, RefreshUi.MUTED, Typeface.NORMAL);
            description.setMaxLines(2);
            info.addView(description);
            info.addView(new Space(context), new LinearLayout.LayoutParams(1, dp(compact ? 5 : 7)));
            price = text("", grid ? 13 : 16, RefreshUi.GREEN, Typeface.BOLD);
            info.addView(price);
            row.addView(info, grid ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(row);
            LinearLayout buttons = new LinearLayout(context);
            buttons.setGravity(Gravity.RIGHT);
            buttons.setPadding(0, dp(12), 0, 0);
            TextView order = pill(menu ? "点菜" : "购买", RefreshUi.GREEN, true);
            if (actions.canOrder(menu)) {
                buttons.addView(order, grid ? new LinearLayout.LayoutParams(-1, dp(48)) : new LinearLayout.LayoutParams(0, dp(48), 1));
                actions.bindClick(order, () -> {
                    if (hasCurrentProduct()) actions.orderItem(new ItemAction(product.actionItem(), menu));
                });
            }
            if (state.manager()) {
                if (buttons.getChildCount() > 0) addHorizontalGap(buttons, compact ? 6 : 8);
                TextView edit = pill("编辑", RefreshUi.GREEN, true);
                TextView delete = pill("删除", Ui.RED, true);
                buttons.addView(edit, new LinearLayout.LayoutParams(0, dp(48), 1));
                addHorizontalGap(buttons, compact ? 6 : 8);
                buttons.addView(delete, new LinearLayout.LayoutParams(0, dp(48), 1));
                actions.bindClick(edit, () -> {
                    if (hasCurrentProduct()) actions.editItem(new ItemAction(product.actionItem(), menu));
                });
                actions.bindClick(delete, () -> {
                    if (hasCurrentProduct()) actions.requestDelete(new DeleteAction(product.actionItem(), menu));
                });
            }
            if (buttons.getChildCount() > 0) card.addView(buttons);
        }

        private boolean hasCurrentProduct() {
            return product != null && getBindingAdapterPosition() != RecyclerView.NO_POSITION;
        }

        void bind(CatalogRows.Product value) {
            product = value;
            name.setText(value.name());
            description.setText(value.description());
            description.setVisibility(value.description().isEmpty() ? View.GONE : View.VISIBLE);
            price.setText(value.price() + " 青程币");
            actions.loadImage(value.imageUrl(), image);
        }
    }

    private TextView shopTab(String label, boolean selected) {
        TextView view = text(label, 15, selected ? RefreshUi.WHITE : RefreshUi.TEXT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setBackground(Ui.round(selected ? RefreshUi.GREEN : Color.TRANSPARENT, dp(14)));
        return view;
    }
    private void setMenuCategoryButtonSelected(TextView view, boolean selected) {
        view.setSelected(selected);
        view.setTextColor(selected ? RefreshUi.WHITE : RefreshUi.TEXT);
        view.setBackground(Ui.round(selected ? RefreshUi.GREEN : RefreshUi.SAGE, dp(99)));
    }
    private void addTopAction(LinearLayout parent, String label, int color, Runnable action) {
        TextView view = pill(label, color, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.setMargins(0, 0, 0, dp(12));
        parent.addView(view, params);
        actions.bindClick(view, action);
    }
    private void addSectionTitle(LinearLayout parent, String value) {
        TextView title = text(value, 18, RefreshUi.TEXT, Typeface.BOLD);
        title.setPadding(dp(2), dp(4), 0, dp(10));
        parent.addView(title);
    }
    private TextView pill(String label, int color, boolean compact) {
        TextView view = text(label, 14, Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(dp(compact ? 58 : 82));
        view.setPadding(dp(compact ? 10 : 13), dp(8), dp(compact ? 10 : 13), dp(8));
        view.setBackground(Ui.round(color, dp(12)));
        Ui.ensureTouchTarget(view);
        return view;
    }
    private TextView text(String value, int sp, int color, int style) {
        TextView view = Ui.text(context, value, sp, color, style);
        view.setSingleLine(false);
        return view;
    }
    private void addHorizontalGap(LinearLayout parent, int widthDp) {
        parent.addView(new Space(context), new LinearLayout.LayoutParams(dp(widthDp), 1));
    }
    private int dp(int value) { return Ui.dp(context, value); }
}
