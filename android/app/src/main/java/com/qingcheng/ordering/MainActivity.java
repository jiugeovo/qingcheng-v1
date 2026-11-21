package com.qingcheng.ordering;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import androidx.core.content.ContextCompat;

public class MainActivity extends android.app.Activity {
    private static final int APP_VERSION_CODE = BuildConfig.VERSION_CODE;
    private static final String APP_VERSION_NAME = BuildConfig.VERSION_NAME;
    private static final int REQUEST_PICK_ITEM_IMAGE = 201;
    private static final int REQUEST_PICK_AI_IMAGE = 202;
    private static final int REQUEST_PICK_FRIEND_IMAGE = 203;
    private static final int REQUEST_PICK_FRIEND_STICKER = 204;
    private static final int REQUEST_ANNIVERSARIES = 205;
    private static final String[] MENU_CATEGORIES = {"炖汤", "炒菜", "炖菜", "主食", "甜品", "炸货", "猪猪商店"};
    private final ExecutorService executor = WorkerPools.fixed("api", 4, 128);
    private final TaskRunner taskRunner = new TaskRunner(executor);
    private OrderIdempotencyStore orderIdempotency;
    private final AiHistoryCoordinator aiHistoryCoordinator = new AiHistoryCoordinator();
    private final WalletCoordinator walletCoordinator = new WalletCoordinator();
    private WalletService walletService;
    private FriendService friendService;
    private WeatherService weatherService;
    private final SessionAwareHandler main = new SessionAwareHandler(Looper.getMainLooper(),
            this::currentAccountScope, this::activityIsAlive);
    private final ClickBurstEffect clickBurstEffect = new ClickBurstEffect();
    private final ScreenGeneration screenGeneration = new ScreenGeneration();
    private final LoginCoordinator loginCoordinator = new LoginCoordinator(screenGeneration);
    private final AiChatCoordinator aiChatCoordinator = new AiChatCoordinator(screenGeneration);
    private AiStreamSession activeAiStream;
    private boolean aiOrderingMode;
    private final CheckInCoordinator checkInCoordinator = new CheckInCoordinator();
    private final ItemImageCoordinator itemImageCoordinator =
            new ItemImageCoordinator(screenGeneration);
    private final UserAdminMutationCoordinator userAdminMutations =
            new UserAdminMutationCoordinator();
    private final OrderMutationCoordinator orderMutations =
            new OrderMutationCoordinator();
    private final ProductMutationCoordinator productMutations =
            new ProductMutationCoordinator();
    private final FriendUnreadCoordinator friendUnreadCoordinator = new FriendUnreadCoordinator();
    private final FriendSessionGuard friendSessionGuard = new FriendSessionGuard();
    private final FriendChatMutationCoordinator friendChatMutations =
            new FriendChatMutationCoordinator();
    private final AsyncPresentationGate presentationGate = new AsyncPresentationGate(screenGeneration);
    private final ProgressLeases progressLeases = new ProgressLeases(this::setProgressVisible);
    private final Deque<ProgressLeases.Lease> compatibilityProgressLeases = new ArrayDeque<>();
    private ScreenGeneration.ScreenToken currentScreen;

    private SessionStore sessionStore;
    private ApiClient apiClient;
    private ImageLoader imageLoader;
    private LinearLayout root;
    private View shellHeader;
    private LinearLayout content;
    private LinearLayout aiComposerDock;
    private MainShellView.Navigation shellNavigation;
    private TextView title;
    private TextView subtitle;
    private TextView walletText;
    private String walletCoins = "--";
    private long homePendingOrderCount;
    private TextView homePendingOrderValue;
    private HomeView.Result homeView;
    private String homeViewAuthToken;
    private boolean checkedInToday;
    private ProgressBar progress;
    private FrameLayout shellFrame;
    private ScrollView mainScroll;
    private ContentRefreshLayout catalogRefresh;
    private final CatalogState catalogState = new CatalogState();
    private final OrderScreenState orderScreenState = new OrderScreenState();
    private final PrimaryViewportState primaryViewportState = new PrimaryViewportState();
    private final WalletScreenState walletScreenState = new WalletScreenState();
    private PrimaryViewportRestorer primaryViewportRestorer;
    private ScreenGeneration.ScreenToken primaryViewportScreen;
    private WalletScreenView.Result walletView;
    private ScreenGeneration.RequestToken walletRequest;
    private long walletRefreshSequence;
    private boolean orderQueueShowingMenu = true;
    private LinearLayout catalogContent;
    private ScreenGeneration.RequestToken catalogRequest;
    private OrderQuantityDialog.Result activeOrderQuantityDialog;
    private String currentTab = "home";
    private boolean shopShowingMenu = true;
    private Long aiConversationId;
    private ScrollView aiMessagesScroll;
    private LinearLayout aiMessages;
    private EditText aiInput;
    private LinearLayout aiAttachmentList;
    private long aiDraftId;
    private AiPickerContext pendingAiPickerContext;
    private final List<AiAttachment> aiAttachments = new ArrayList<>();
    private LinearLayout friendListView;
    private LinearLayout friendRequestView;
    private LinearLayout friendMessageList;
    private ScrollView friendMessageScroll;
    private EditText friendMessageInput;
    private LinearLayout friendReplyBar;
    private TextView friendReplyText;
    private TextView friendCancelReplyButton;
    private TextView friendChatTitle;
    private TextView friendSendButton;
    private Long activeFriendId;
    private String activeFriendName = "";
    private JSONObject friendCenterData;
    private JSONArray friendMessagesData = new JSONArray();
    private JSONObject friendReplyTarget;
    private JSONArray friendStickersData = new JSONArray();
    private long friendUnreadCount;
    private TextView friendUnreadSummary;
    private TextView friendSmartBadge;
    private String pendingPushTarget = "";
    private String testDeveloperToken = "";
    private JSONObject testDeveloperProfile;
    private boolean testWorkspaceBusy;
    private boolean weatherScreenOpen;
    private boolean pushReceiverRegistered;
    private final BroadcastReceiver pushArrivedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            handlePushArrived(intent.getStringExtra(PushManager.EXTRA_PUSH_TARGET));
        }
    };
    private ItemImagePickerContext pendingItemImagePickerContext;
    private FriendMediaPickerContext pendingFriendMediaPickerContext;
    private UserAdminView.Mode activeUserManageMode;
    private boolean handlingUnauthorized;
    private boolean automaticUpdateCheckStarted;
    private StartupSplashController startupSplash;
    private volatile boolean activityDestroyed;
    private boolean activityResumed;
    private final Runnable piggySyncRetry = this::refreshPiggyProgress;
    private long profileRefreshSequence;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enforceLegacySystemBarLayout();
        sessionStore = SessionStore.shared(this);
        PiggyCloud.shared(this).rememberLegacyOwner();
        orderIdempotency = new OrderIdempotencyStore(this, sessionStore);
        apiClient = new ApiClient(sessionStore);
        walletService = new WalletService(apiClient);
        friendService = new FriendService(apiClient);
        weatherService = new WeatherService(apiClient);
        imageLoader = new ImageLoader();
        PushManager.init(this);
        capturePushTarget(getIntent());
        askNotificationPermission();
        startupSplash = new StartupSplashController(this, main.handler(), this::enterInitialScreen);
        startupSplash.show();
    }

    private void enforceLegacySystemBarLayout() {
        if (Build.VERSION.SDK_INT >= 35) {
            getWindow().setStatusBarColor(Color.rgb(255, 243, 223));
            getWindow().setNavigationBarColor(Color.rgb(255, 243, 223));
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (shellFrame != null && isViewAvailable(shellFrame)) refreshPiggyProgress();
        if (content != null && currentScreen != null
                && currentScreen.route() == ScreenRoute.HOME && "home".equals(currentTab)) {
            refreshHomeStatus();
            refreshHomePendingOrders();
        }
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        main.handler().removeCallbacks(piggySyncRetry);
        super.onPause();
    }

    private void enterInitialScreen() {
        if (sessionStore.isLoggedIn() && isTestDeveloper()) {
            rememberTestDeveloper();
            showLogin();
            showTestWorkspace();
        } else if (sessionStore.isLoggedIn()) {
            PushManager.syncRegistration(this, sessionStore, apiClient);
            showShell();
            refreshProfileAndWallet();
        } else {
            showLogin();
        }
        if (!BuildConfig.COUPLE_LOCAL) checkForUpdateSilently();
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        cancelAiStream();
        clearPrimaryScreenState();
        profileRefreshSequence++;
        dismissOrderQuantityDialog();
        clickBurstEffect.cancel();
        ScreenTransitions.reset(content);
        catalogContent = null;
        catalogRequest = null;
        catalogState.clear();
        productMutations.reset();
        orderScreenState.clear();
        presentationGate.invalidate();
        checkInCoordinator.invalidate();
        userAdminMutations.reset();
        orderMutations.reset();
        friendUnreadCoordinator.invalidate();
        friendSessionGuard.invalidate();
        friendChatMutations.invalidate();
        progressLeases.reset();
        compatibilityProgressLeases.clear();
        unregisterPushArrivedReceiver();
        if (startupSplash != null) {
            startupSplash.destroy();
            startupSplash = null;
        }
        if (imageLoader != null) {
            imageLoader.shutdown();
        }
        executor.shutdownNow();
        taskRunner.close();
        walletCoordinator.invalidate();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        capturePushTarget(intent);
        if (content != null && sessionStore != null && sessionStore.isLoggedIn()) {
            selectTab(currentTab);
            openPendingPushTarget();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ANNIVERSARIES) {
            if (resultCode == AnniversaryActivity.RESULT_SESSION_EXPIRED && data != null
                    && AnniversarySessionResult.matches(data.getStringExtra(
                            AnniversarySessionResult.EXTRA_FINGERPRINT), sessionStore.getToken())) {
                forceLogoutToLogin("登录状态已失效，请重新登录");
            }
            return;
        }
        if (requestCode == REQUEST_PICK_AI_IMAGE) {
            AiPickerContext pickerContext = pendingAiPickerContext;
            pendingAiPickerContext = null;
            if (resultCode == RESULT_OK
                    && data != null
                    && data.getData() != null
                    && pickerContext != null) {
                Uri uri = data.getData();
                attachAiImage(uri, pickerContext);
            }
            return;
        }
        if (requestCode == REQUEST_PICK_ITEM_IMAGE) {
            ItemImagePickerContext pickerContext = pendingItemImagePickerContext;
            pendingItemImagePickerContext = null;
            if (resultCode == RESULT_OK
                    && data != null
                    && data.getData() != null
                    && pickerContext != null) {
                Uri uri = data.getData();
                uploadItemImage(uri, pickerContext);
            } else if (pickerContext != null) {
                itemImageCoordinator.cancel(pickerContext.upload());
            }
            return;
        }
        if (requestCode == REQUEST_PICK_FRIEND_IMAGE
                || requestCode == REQUEST_PICK_FRIEND_STICKER) {
            FriendMediaPickerContext pickerContext = pendingFriendMediaPickerContext;
            pendingFriendMediaPickerContext = null;
            if (resultCode == RESULT_OK
                    && data != null
                    && data.getData() != null
                    && pickerContext != null
                    && pickerContext.requestCode() == requestCode) {
                uploadFriendMedia(data.getData(), pickerContext);
            } else if (pickerContext != null && pickerContext.mutation() != null) {
                friendChatMutations.finish(pickerContext.mutation());
            }
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
    }

    private void showLogin() {
        beginScreen(ScreenRoute.LOGIN);
        clearPrimaryScreenState();
        profileRefreshSequence++;
        walletCoins = "--";
        checkedInToday = false;
        homePendingOrderCount = 0;
        homePendingOrderValue = null;
        catalogState.clear();
        orderScreenState.clear();
        ScreenGeneration.ScreenToken screen = currentScreen;
        checkInCoordinator.invalidate();
        userAdminMutations.reset();
        friendUnreadCoordinator.invalidate();
        friendSessionGuard.invalidate();
        friendChatMutations.invalidate();
        friendUnreadCount = 0;
        pendingPushTarget = "";
        resetProgress();
        LoginView.Result view = new LoginView(this, loginViewActions(screen)).build();
        setContentView(view.root());
    }

    private LoginView.Actions loginViewActions(ScreenGeneration.ScreenToken screen) {
        return new LoginView.Actions() {
            @Override
            public void bindClick(View view, Runnable action) {
                MainActivity.this.bindClick(view, clicked -> action.run());
            }

            @Override
            public void login(LoginView.Result view, LoginView.Draft draft) {
                submitLoginRequest(
                        new LoginViewTarget(screen, view), LoginCoordinator.Kind.LOGIN, draft);
            }

            @Override
            public void register(LoginView.Result view, LoginView.Draft draft) {
                submitLoginRequest(
                        new LoginViewTarget(screen, view), LoginCoordinator.Kind.REGISTER, draft);
            }
        };
    }

    private void submitLoginRequest(
            LoginViewTarget viewTarget,
            LoginCoordinator.Kind kind,
            LoginView.Draft draft
    ) {
        if (!isCurrentLoginViewTarget(viewTarget)) {
            return;
        }
        ScreenGeneration.RequestToken requestToken = screenGeneration.request(
                viewTarget.screen(), LoginCoordinator.REQUEST_SLOT);
        LoginCoordinator.Request request = loginCoordinator.begin(requestToken, kind);
        if (request == null) {
            return;
        }
        LoginRequestTarget target = new LoginRequestTarget(
                viewTarget.screen(), viewTarget.view(), request);
        String username = draft.username();
        String password = draft.password();
        String nickname = draft.nickname();
        String phone = draft.phone();
        ProgressLeases.Lease lease = progressLeases.open();
        executeTask(() -> {
            if (kind == LoginCoordinator.Kind.LOGIN) {
                JSONObject data = apiClient.login(username, password);
                main.post(() -> {
                    if (!isCurrentLoginTarget(target)) {
                        return;
                    }
                    boolean persisted = sessionStore.save(
                            data.optString("token"), data.optJSONObject("user"));
                    loginCoordinator.finish(request);
                    if (isTestDeveloper()) {
                        rememberTestDeveloper();
                        showTestWorkspace();
                        return;
                    }
                    PushManager.init(this);
                    PushManager.syncRegistration(this, sessionStore, apiClient);
                    showShell();
                    refreshProfileAndWallet();
                    Ui.toast(this, "登录成功");
                    if (!persisted) {
                        Ui.toast(this, "设备安全存储暂不可用，本次重启后需要重新登录");
                    }
                });
                return;
            }
            JSONObject user = apiClient.register(username, password, nickname, phone);
            String message = "注册成功，可以登录了："
                    + user.optString("nickname", username);
            main.post(() -> {
                if (!isCurrentLoginTarget(target)) {
                    return;
                }
                loginCoordinator.finish(request);
                Ui.toast(this, message);
            });
        }, error -> {
            main.post(() -> {
                if (!isCurrentLoginTarget(target)) {
                    return;
                }
                loginCoordinator.finish(request);
                if (!handleUnauthorized(error)) {
                    Ui.toast(this, error.getMessage() == null
                            ? "操作失败" : error.getMessage());
                }
            });
        }, () -> {
            main.post(lease::close);
        });
    }

    private boolean isCurrentLoginViewTarget(LoginViewTarget target) {
        return target != null
                && target.screen().route() == ScreenRoute.LOGIN
                && screenGeneration.isCurrent(target.screen())
                && isViewAvailable(target.view().root());
    }

    private boolean isCurrentLoginTarget(LoginRequestTarget target) {
        return target != null
                && target.screen().route() == ScreenRoute.LOGIN
                && screenGeneration.isCurrent(target.screen())
                && loginCoordinator.isCurrent(target.request())
                && isViewAvailable(target.view().root());
    }

    private boolean isTestDeveloper() {
        JSONObject profile = sessionStore.coupleProfile();
        return BuildConfig.COUPLE_TEST && profile != null && "DEVELOPER".equals(profile.optString("identity"));
    }

    private boolean isCoupleMember() {
        JSONObject profile = sessionStore == null ? null : sessionStore.coupleProfile();
        if (profile == null) return false;
        String identity = profile.optString("identity");
        return "JIUGE".equals(identity) || "BAOBAO".equals(identity);
    }

    private boolean canBuyCoupleOrder(boolean menu) {
        JSONObject profile = sessionStore.coupleProfile();
        return profile == null || profile.optBoolean(menu ? "canBuyMenu" : "canBuyShop");
    }

    private void rememberTestDeveloper() {
        testDeveloperToken = sessionStore.getToken();
        testDeveloperProfile = sessionStore.getUser();
    }

    private void showTestWorkspace() {
        if (testWorkspaceBusy) return;
        if (testDeveloperToken.isEmpty()) {
            Ui.toast(this, "请重新登录开发者账号");
            resetToLogin();
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("开发者测试区")
                .setItems(new String[]{"模拟九歌", "模拟宝宝", "重置测试订单和钱包", "退出测试区"}, (dialog, selected) -> {
                    if (selected == 2) {
                        confirm("重置测试数据", "清空样例订单和流水，两个模拟账号各恢复 500 青程币。",
                                () -> applyTestWorkspaceAction(selected));
                    } else {
                        applyTestWorkspaceAction(selected);
                    }
                })
                .setNegativeButton(isTestDeveloper() ? "返回登录" : "继续模拟",
                        (dialog, which) -> { if (isTestDeveloper()) resetToLogin(); })
                .setCancelable(!isTestDeveloper())
                .show();
    }

    private void applyTestWorkspaceAction(int selected) {
        if (testWorkspaceBusy || testDeveloperToken.isEmpty()) return;
        String developerToken = testDeveloperToken;
        String previousToken = sessionStore.getToken();
        testWorkspaceBusy = true;
        executeTask(() -> {
            JSONObject actor = null;
            if (selected == 3) {
                apiClient.logout(developerToken);
                if (!previousToken.equals(developerToken)) apiClient.logout(previousToken);
            } else if (selected == 2) {
                apiClient.resetTestOrders(developerToken);
            } else {
                actor = apiClient.selectTestMember(selected == 0 ? "JIUGE" : "BAOBAO", developerToken);
                if (!previousToken.equals(developerToken)) {
                    try {
                        apiClient.logout(previousToken);
                    } catch (Exception ignored) {
                        // A previous simulation may already have been invalidated.
                    }
                }
            }
            JSONObject next = actor;
            main.post(() -> {
                if (!developerToken.equals(testDeveloperToken) || isFinishing() || isDestroyed()) return;
                testWorkspaceBusy = false;
                if (selected == 3) {
                    resetToLogin();
                    return;
                }
                taskRunner.invalidate();
                walletCoordinator.invalidate();
                imageLoader.clear();
                cancelAiStream();
                currentTab = "home";
                showLogin();
                if (next == null) {
                    sessionStore.save(developerToken, testDeveloperProfile);
                    Ui.toast(this, "测试订单和钱包已重置");
                    showTestWorkspace();
                } else {
                    sessionStore.save(next.optString("token"), next.optJSONObject("user"));
                    showShell();
                    refreshProfileAndWallet();
                }
            });
        }, error -> main.post(() -> {
            if (!developerToken.equals(testDeveloperToken) || isFinishing() || isDestroyed()) return;
            testWorkspaceBusy = false;
            if (handleUnauthorized(error)) return;
            Ui.toast(this, error.getMessage() == null ? "测试操作失败，请重试" : error.getMessage());
            showTestWorkspace();
        }), () -> main.post(() -> {
            if (developerToken.equals(testDeveloperToken)) testWorkspaceBusy = false;
        }));
    }

    private void showShell() {
        resetProgress();
        handlingUnauthorized = false;
        registerPushArrivedReceiver();
        MainShellView.State state = new MainShellView.State(currentTab, friendUnreadCount);
        MainShellView.Actions actions = new MainShellView.Actions() {
            @Override
            public void bindClick(View view, Runnable action) {
                MainActivity.this.bindClick(view, ignored -> action.run());
            }

            @Override
            public void selectTab(String tab) {
                if (!isSelectedPrimaryTab(tab)) {
                    MainActivity.this.selectTab(tab);
                }
            }

            @Override
            public void refreshCatalog() {
                refreshCurrentCatalog();
            }
        };
        MainShellView.Result shell = new MainShellView(this, actions, state).build();
        root = shell.backgroundRoot();
        shellHeader = shell.header();
        content = shell.content();
        aiComposerDock = shell.aiComposerDock();
        title = shell.title();
        subtitle = shell.subtitle();
        walletText = shell.walletText();
        progress = shell.progress();
        shellFrame = shell.frame();
        mainScroll = shell.mainScroll();
        catalogRefresh = shell.refreshLayout();
        shellNavigation = shell.navigation();
        setContentView(shell.frame());
        selectTab(currentTab);
        openPendingPushTarget();
        refreshFriendBadge();
        refreshPiggyProgress();
    }

    private void refreshNavSelection() {
        if (shellNavigation == null) {
            return;
        }
        shellNavigation.update(currentTab, friendUnreadCount);
    }

    private void showHome() {
        beginScreen(ScreenRoute.HOME);
        setHeader(homeGreeting(), "青程 · 日常，也有小欢喜");
        HomeView.State state = homeState();
        HomeView.Actions actions = new HomeView.Actions() {
            @Override
            public void bindClick(View view, Runnable action) {
                MainActivity.this.bindClick(view, ignored -> action.run());
            }

            @Override
            public void openWeather() {
                selectTab("ai");
                showWeatherCard();
            }

            @Override
            public void openFriends() {
                selectTab("ai");
                showFriendCenter();
            }

            @Override
            public void openCheckIn() {
                showCheckInDialog();
            }

            @Override
            public void openOrders() {
                selectTab("orders");
            }

            @Override
            public void openMenu() {
                showMenu();
            }

            @Override
            public void openPiggy() {
                startActivity(new Intent(MainActivity.this, PiggyActivity.class));
            }

            @Override
            public void openAnniversaries() {
                startActivityForResult(new Intent(MainActivity.this, AnniversaryActivity.class), REQUEST_ANNIVERSARIES);
            }

            @Override
            public void openAiChat() {
                selectTab("ai");
                showAiChat();
            }

            @Override
            public void openTab(String tab) {
                selectTab(tab);
            }
        };
        HomeView.Result home = new HomeView(this, actions, state).build();
        homeView = home;
        homeViewAuthToken = sessionStore.getToken();
        content.addView(home.root());
        View homeWeatherAction = home.weatherAction();
        TextView homeWeatherLocation = home.weatherLocation();
        TextView homeWeatherChip = home.weatherChip();
        ImageView homeWeatherIcon = home.weatherIcon();
        homePendingOrderValue = home.pendingOrderValue();
        loadHomeWeatherSummary(
                homeWeatherAction, homeWeatherLocation, homeWeatherChip, homeWeatherIcon);
        refreshHomePendingOrders();
        restorePrimaryViewport();
    }

    private HomeView.State homeState() {
        return new HomeView.State(
                defaultWeatherLocation(),
                walletCoins,
                displayedPiggyCount(),
                friendUnreadCount,
                checkedInToday,
                canShowHomePendingOrders(),
                homePendingOrderCount
        );
    }

    private long displayedPiggyCount() {
        PiggyCloud cloud = PiggyCloud.shared(this);
        PiggyCloud.Member member = cloud.member();
        return member == null
                ? PiggyState.load(getSharedPreferences(PiggyState.PREFERENCES, Context.MODE_PRIVATE)).count
                : cloud.state(member).visible();
    }

    private void refreshPiggyProgress() {
        main.handler().removeCallbacks(piggySyncRetry);
        PiggyCloud cloud = PiggyCloud.shared(this);
        cloud.rememberLegacyOwner();
        PiggyCloud.Member member = cloud.member();
        if (member != null) cloud.refresh(member, success -> {
            if (activityDestroyed || isFinishing() || isDestroyed()) return;
            refreshHomeStatus();
            if (!success && activityResumed && cloud.state(member).pending()) {
                main.handler().removeCallbacks(piggySyncRetry);
                main.handler().postDelayed(piggySyncRetry, PiggyCloud.RETRY_DELAY_MS);
            }
        });
    }

    private void refreshHomeStatus() {
        HomeView.Result target = homeView;
        if (currentScreen == null || currentScreen.route() != ScreenRoute.HOME
                || target == null || !isCurrentSession(homeViewAuthToken)
                || !isViewAvailable(target.root()) || target.root().getParent() != content) {
            return;
        }
        target.update(homeState());
        if (title != null) title.setText(homeGreeting());
    }

    private boolean canShowHomePendingOrders() {
        if (isCoupleMember()) return true;
        String role = sessionStore == null ? "" : sessionStore.role();
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }

    private String homeGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour <= 10) {
            return "早上好";
        }
        if (hour <= 13) {
            return "中午好";
        }
        if (hour <= 17) {
            return "下午好";
        }
        return "晚上好";
    }

    private void loadHomeWeatherSummary(
            View action,
            TextView location,
            TextView chip,
            ImageView icon
    ) {
        ScreenGeneration.ScreenToken screen = currentScreen;
        View targetAction = action;
        TextView targetLocation = location;
        TextView targetChip = chip;
        ImageView targetIcon = icon;
        if (screen == null || targetAction == null || targetLocation == null
                || targetChip == null || targetIcon == null) {
            return;
        }
        ScreenGeneration.RequestToken request = screenGeneration.request(screen, "home-weather");
        String query = defaultWeatherLocation();
        String authToken = sessionStore.getToken();
        executeTask(() -> {
            if (!isCurrentSession(authToken)) return;
            JSONObject weather = apiClient.weather(query, authToken);
            String place = weatherDisplayPlace(weather);
            String condition = weather.optString("weather", "今日天气").trim();
            String temperature = weather.optString("temperature", "").trim();
            String summary = condition.isEmpty() ? "今日天气" : condition;
            if (!temperature.isEmpty()) {
                summary += " · " + temperature + "°";
            }
            String placeText = place;
            String summaryText = summary;
            int iconRes = weatherIconResource(condition);
            main.post(() -> presentationGate.present(request,
                    () -> isCurrentSession(authToken) && isViewAvailable(targetAction)
                            && isViewAvailable(targetLocation)
                            && isViewAvailable(targetChip)
                            && isViewAvailable(targetIcon), () -> {
                targetAction.setContentDescription(
                        "当前位置：" + placeText + "，天气：" + summaryText);
                targetLocation.setText(placeText);
                targetChip.setText(summaryText);
                if (iconRes != 0) {
                    targetIcon.setImageResource(iconRes);
                }
            }));
        }, ignored -> {
            // Optional home summary: keep its existing placeholder on failure or queue rejection.
        });
    }

    private void refreshHomePendingOrders() {
        ScreenGeneration.ScreenToken screen = currentScreen;
        TextView targetValue = homePendingOrderValue;
        if (screen == null || screen.route() != ScreenRoute.HOME) {
            return;
        }
        ScreenGeneration.RequestToken request = screenGeneration.request(screen, "home-pending-orders");
        String authToken = sessionStore.getToken();
        String role = sessionStore.role();
        boolean coupleMember = isCoupleMember();
        boolean handlesMenu = !canBuyCoupleOrder(true);
        executeTask(() -> {
            if (!isCurrentSession(authToken)) return;
            long count = 0L;
            if (coupleMember) {
                count = countPendingOrders(apiClient.receivedOrders(handlesMenu, authToken));
            } else if ("SUPER_ADMIN".equals(role)) {
                count += countPendingOrders(apiClient.adminMenuOrders(authToken));
            } else if ("ADMIN".equals(role)) {
                count += countPendingOrders(apiClient.adminShopOrders(authToken));
            }
            long nextCount = count;
            main.post(() -> presentationGate.present(request,
                    () -> isCurrentSession(authToken) && role.equals(sessionStore.role()),
                    () -> {
                        boolean changed = homePendingOrderCount != nextCount;
                        homePendingOrderCount = nextCount;
                        if (changed && isViewAvailable(targetValue)) {
                            targetValue.setText("待处理 " + homePendingOrderCount + " 单\n快捷管理");
                            if (homeView != null && targetValue == homeView.pendingOrderValue()) {
                                homeView.updatePendingOrders(homePendingOrderCount);
                            }
                        }
                    }));
        }, ignored -> {
            // Optional home badge: retain the last confirmed count; a later visit refreshes it.
        });
    }

    private long countPendingOrders(JSONArray orders) {
        if (orders == null) {
            return 0L;
        }
        long count = 0L;
        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (order != null && "PENDING".equals(order.optString("status"))) {
                count++;
            }
        }
        return count;
    }

    private void selectTab(String tab) {
        rememberPrimaryViewport();
        rememberCatalogViewport();
        rememberOrdersViewport();
        weatherScreenOpen = false;
        currentTab = tab;
        refreshNavSelection();
        if (!"ai".equals(tab)) {
            aiMessagesScroll = null;
            aiMessages = null;
            aiInput = null;
            aiAttachmentList = null;
            pendingAiPickerContext = null;
        }
        if (content == null) {
            return;
        }
        if (shellHeader != null) {
            shellHeader.setVisibility(View.VISIBLE);
        }
        if (aiComposerDock != null) {
            aiComposerDock.removeAllViews();
            aiComposerDock.setVisibility(View.GONE);
        }
        applyTabBackground(tab);
        if ("me".equals(tab)) {
            content.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 16));
        } else {
            content.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 24));
        }
        content.removeAllViews();
        mainScroll.scrollTo(0, 0);
        switch (tab) {
            case "shop" -> showShop();
            case "orders" -> showOrders();
            case "ai" -> showAiCenter();
            case "wallet" -> showWallet();
            case "me" -> showMe();
            default -> showHome();
        }
        ScreenTransitions.enter(content);
    }

    private boolean isSelectedPrimaryTab(String tab) {
        if (content == null || currentScreen == null || !tab.equals(currentTab)) {
            return false;
        }
        ScreenRoute route = switch (tab) {
            case "shop" -> ScreenRoute.SHOP;
            case "orders" -> ScreenRoute.ORDERS;
            case "ai" -> ScreenRoute.SMART;
            case "wallet" -> ScreenRoute.WALLET;
            case "me" -> ScreenRoute.PROFILE;
            default -> ScreenRoute.HOME;
        };
        return currentScreen.route() == route && !weatherScreenOpen;
    }

    private void applyTabBackground(String tab) {
        if (root == null) {
            return;
        }
        int resource = switch (tab) {
            case "me" -> R.drawable.checkin_garden_bg;
            default -> 0;
        };
        if (resource != 0) {
            Drawable image = getResources().getDrawable(resource);
            image.setAlpha(86);
            GradientDrawable wash = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.argb(222, 255, 255, 250), Color.argb(232, 248, 252, 247)});
            root.setBackground(new LayerDrawable(new Drawable[]{image, wash}));
            return;
        }
        root.setBackgroundColor(RefreshUi.PAPER);
    }

    private void showMenu() {
        openShopSection(true);
    }

    private void showMenuContent() {
        loadCatalog(true);
    }

    private void showShop() {
        beginScreen(ScreenRoute.SHOP);
        content.removeAllViews();
        boolean manager = canManageItems();
        setHeader("我们的小厨房", manager ? "菜单和宝宝商店管理" : "一日三餐，都是小期待。");
        shopView().buildTabs(content);
        catalogState.useScope(sessionStore.getToken(), manager);
        catalogContent = new LinearLayout(this);
        catalogContent.setOrientation(LinearLayout.VERTICAL);
        content.addView(catalogContent, new LinearLayout.LayoutParams(-1, -2));
        try {
            CatalogRows.Prepared cachedPrepared = catalogState.prepared(shopShowingMenu);
            if (cachedPrepared != null) {
                renderCatalog(cachedPrepared, false);
            } else {
                JSONArray cached = catalogState.items(shopShowingMenu);
                if (cached != null) renderCatalog(cached, shopShowingMenu, false);
            }
        } catch (Exception ignored) {
            catalogState.clear();
        }
        if (shopShowingMenu) {
            showMenuContent();
            return;
        }
        showBabyShopContent();
    }

    private void showBabyShopContent() {
        loadCatalog(false);
    }

    private ShopView shopView() {
        return new ShopView(this, new ShopView.Actions() {
            @Override
            public void bindClick(View view, Runnable action) {
                MainActivity.this.bindClick(view, ignored -> action.run());
            }

            @Override
            public void switchSection(boolean menu) {
                if (shopShowingMenu == menu) return;
                rememberCatalogViewport();
                shopShowingMenu = menu;
                showShop();
            }

            @Override
            public void createItem(boolean menu) {
                showItemEditor(null, menu);
            }

            @Override
            public void editItem(ShopView.ItemAction action) {
                showItemEditor(action.item(), action.menu());
            }

            @Override
            public void requestDelete(ShopView.DeleteAction action) {
                deleteProduct(action);
            }

            @Override
            public void orderItem(ShopView.ItemAction action) {
                askQuantityAndOrder(action.item(), action.menu());
            }

            @Override
            public boolean canOrder(boolean menu) {
                return canBuyCoupleOrder(menu);
            }

            @Override
            public void loadImage(String url, ImageView target) {
                imageLoader.load(url, target, isCoupleMember() ? sessionStore.getToken() : null);
            }

            @Override
            public void cancelImage(ImageView target) {
                imageLoader.cancel(target);
            }

        }, new ShopView.State(
                shopShowingMenu,
                canManageItems(),
                screenHeight(),
                List.of(MENU_CATEGORIES)));
    }

    private void deleteProduct(ShopView.DeleteAction action) {
        JSONObject item = action.item();
        boolean menu = action.menu();
        confirm("确认删除：", item.optString("name"), () -> {
            ScreenGeneration.ScreenToken origin = currentScreen;
            LinearLayout targetContent = content;
            String authToken = sessionStore.getToken();
            ProductMutationCoordinator.Mutation mutation =
                    productMutations.beginDelete(item.optLong("id"), menu, authToken);
            if (mutation == null) {
                return;
            }
            ProgressLeases.Lease lease = progressLeases.open();
            executeTask(() -> {
                if (menu) {
                    apiClient.deleteMenuItem(item.optLong("id"), authToken);
                } else {
                    apiClient.deleteShopItem(item.optLong("id"), authToken);
                }
                main.post(() -> {
                    if (!authToken.equals(sessionStore.getToken())
                            || !productMutations.isCurrent(mutation)) {
                        return;
                    }
                    openShopSectionIfCurrent(origin, targetContent, menu);
                    Ui.toast(this, "已删除");
                });
            }, e -> {
                main.post(() -> {
                    if (!authToken.equals(sessionStore.getToken())) {
                        return;
                    }
                    if (!handleUnauthorized(e)
                            && productMutations.isCurrent(mutation)) {
                        Ui.toast(this, e.getMessage() == null ? "删除失败" : e.getMessage());
                    }
                });
            }, () -> {
                main.post(() -> {
                    productMutations.finish(mutation);
                    lease.close();
                });
            });
        });
    }

    private void showItemEditor(JSONObject item, boolean menu) {
        ScreenGeneration.ScreenToken origin = currentScreen;
        LinearLayout targetContent = content;
        String authToken = sessionStore.getToken();
        ItemImageCoordinator.Editor editor = itemImageCoordinator.open(origin, authToken);
        if (editor == null) {
            return;
        }
        ItemEditorContext[] target = new ItemEditorContext[1];
        ItemEditorView.Result view = new ItemEditorView(
                this,
                itemEditorActions(() -> target[0]),
                ItemEditorView.State.from(item, menu, List.of(MENU_CATEGORIES))
        ).show();
        target[0] = new ItemEditorContext(
                origin, targetContent, editor, view, authToken);
    }

    private ItemEditorView.Actions itemEditorActions(
            java.util.function.Supplier<ItemEditorContext> target
    ) {
        return new ItemEditorView.Actions() {
            @Override
            public void bindClick(View view, Runnable action) {
                MainActivity.this.bindClick(view, clicked -> action.run());
            }

            @Override
            public void loadImage(String url, ImageView targetView) {
                imageLoader.load(url, targetView);
            }

            @Override
            public void pickImage(ItemEditorView.Result view) {
                ItemEditorContext context = target.get();
                if (!isCurrentItemEditorTarget(context)) {
                    return;
                }
                ItemImageCoordinator.Upload upload =
                        itemImageCoordinator.begin(context.editor());
                if (upload == null) {
                    return;
                }
                pendingItemImagePickerContext = new ItemImagePickerContext(
                        context.origin(), context.editor(), upload,
                        view.imageUrl(), view.preview(), view.uploadStatus());
                pickImageFromDevice(REQUEST_PICK_ITEM_IMAGE);
            }

            @Override
            public void clearImage(ItemEditorView.Result view) {
                ItemEditorContext context = target.get();
                if (!isCurrentItemEditorTarget(context)) {
                    return;
                }
                itemImageCoordinator.cancelActive(context.editor());
                view.imageUrl().setText("");
                imageLoader.load("", view.preview());
                view.uploadStatus().setText("图片已清除");
            }

            @Override
            public void save(ItemEditorView.Result view, ItemEditorView.Draft draft) {
                saveItemEditor(target.get(), draft);
            }

            @Override
            public void dismiss(ItemEditorView.Result view) {
                ItemEditorContext context = target.get();
                if (context == null) {
                    return;
                }
                if (pendingItemImagePickerContext != null
                        && pendingItemImagePickerContext.editor().equals(context.editor())) {
                    itemImageCoordinator.cancel(pendingItemImagePickerContext.upload());
                    pendingItemImagePickerContext = null;
                }
                itemImageCoordinator.close(context.editor());
            }

            @Override
            public void invalid(String message) {
                Ui.toast(MainActivity.this, message);
            }
        };
    }

    private void saveItemEditor(ItemEditorContext target, ItemEditorView.Draft draft) {
        if (!isCurrentItemEditorTarget(target)) {
            return;
        }
        if (itemImageCoordinator.hasActiveUpload(target.editor())) {
            Ui.toast(this, "请等待图片上传完成");
            return;
        }
        if (!target.view().dialog().getButton(
                android.content.DialogInterface.BUTTON_POSITIVE).isEnabled()) {
            return;
        }
        target.view().setSaving(true);
        ProgressLeases.Lease lease = progressLeases.open();
        executeTask(() -> {
            if (draft.menu()) {
                apiClient.saveMenuItem(
                        draft.itemId(), draft.name(), draft.category(), draft.description(),
                        draft.imageUrl(), draft.price(), draft.available(), target.authToken());
            } else {
                apiClient.saveShopItem(
                        draft.itemId(), draft.name(), "宝宝商店", draft.description(),
                        draft.imageUrl(), draft.price(), target.authToken());
            }
            main.post(() -> presentationGate.present(
                    target.origin(),
                    () -> isCurrentItemEditorTarget(target),
                    () -> {
                        target.view().dialog().dismiss();
                        openShopSection(draft.menu());
                        Ui.toast(this, "已保存");
                    }));
        }, error -> {
            main.post(() -> presentationGate.present(
                    target.origin(),
                    () -> isCurrentItemEditorTarget(target),
                    () -> {
                        target.view().setSaving(false);
                        if (!handleUnauthorized(error)) {
                            Ui.toast(this, error.getMessage() == null
                                    ? "保存失败" : error.getMessage());
                        }
                    }));
        }, () -> {
            main.post(lease::close);
        });
    }

    private boolean isCurrentItemEditorTarget(ItemEditorContext target) {
        return target != null
                && target.origin().route() == ScreenRoute.SHOP
                && itemImageCoordinator.isCurrent(
                        target.editor(), sessionStore.getToken())
                && target.authToken().equals(sessionStore.getToken())
                && target.targetContent() == content
                && isContentTargetAvailable(target.targetContent())
                && target.view().dialog().isShowing()
                && isViewAvailable(target.view().root());
    }

    private void openShopSectionIfCurrent(
            ScreenGeneration.ScreenToken origin,
            LinearLayout targetContent,
            boolean menu
    ) {
        presentationGate.present(origin,
                () -> origin.route() == ScreenRoute.SHOP
                        && targetContent == content
                        && isContentTargetAvailable(targetContent),
                () -> {
                    openShopSection(menu);
                });
    }

    private void openShopSection(boolean menu) {
        rememberCatalogViewport();
        // The outgoing viewport belongs to the old section, not the next one.
        catalogContent = null;
        shopShowingMenu = menu;
        selectTab("shop");
    }

    private void askQuantityAndOrder(JSONObject item, boolean menu) {
        if (!canBuyCoupleOrder(menu)) return;
        ScreenGeneration.ScreenToken origin = currentScreen;
        LinearLayout targetContent = content;
        String authToken = sessionStore.getToken();
        dismissOrderQuantityDialog();
        activeOrderQuantityDialog = new OrderQuantityDialog(
                this,
                item.optString("name"),
                menu,
                (view, draft) -> {
                    OrderQuantityContext target = new OrderQuantityContext(
                            origin, targetContent, authToken, view);
                    if (isCurrentOrderQuantityTarget(target)) {
                        submitOrder(item.optLong("id"), menu, draft, target);
                    } else {
                        view.dismiss();
                    }
                }
        ).show();
    }

    private boolean isCurrentOrderQuantityTarget(OrderQuantityContext target) {
        return target != null
                && target.origin().route() == ScreenRoute.SHOP
                && screenGeneration.isCurrent(target.origin())
                && target.authToken().equals(sessionStore.getToken())
                && target.targetContent() == content
                && isContentTargetAvailable(target.targetContent())
                && target.view().dialog().isShowing()
                && isViewAvailable(target.view().root());
    }

    private void submitOrder(
            long itemId,
            boolean menu,
            OrderQuantityDialog.Draft draft,
            OrderQuantityContext target
    ) {
        ScreenGeneration.ScreenToken origin = currentScreen;
        LinearLayout targetContent = content;
        String authToken = sessionStore.getToken();
        ProductMutationCoordinator.Mutation mutation =
                productMutations.beginOrder(itemId, menu, authToken);
        if (mutation == null) {
            target.view().showUncertain("订单正在提交，请关闭后在订单中查看结果");
            return;
        }
        int count = draft.quantity();
        String orderRemark = draft.remark();
        executeTask(() -> {
            String idempotencyKey = orderIdempotency.key(menu, itemId, count, orderRemark);
            JSONObject order = new OrderService(apiClient).create(
                    menu, itemId, count, orderRemark, authToken, idempotencyKey);
            orderIdempotency.complete(menu, itemId, count, orderRemark, idempotencyKey);
            refreshProfileAndWallet(authToken);
            main.post(() -> {
                if (isFinishing() || isDestroyed() || !authToken.equals(sessionStore.getToken())
                        || !productMutations.isCurrent(mutation)) {
                    return;
                }
                onOrderDataChanged(origin);
                if (isCurrentOrderQuantityTarget(target)) {
                    target.view().complete();
                    showOrderSuccessIfCurrent(origin, targetContent, order, menu);
                }
                Ui.toast(this, "订单已提交");
            });
        }, error -> {
            main.post(() -> {
                if (isFinishing() || isDestroyed() || !authToken.equals(sessionStore.getToken())) {
                    return;
                }
                if (!handleUnauthorized(error)
                        && productMutations.isCurrent(mutation)) {
                    String message = error.getMessage() == null ? "提交失败" : error.getMessage();
                    if (isCurrentOrderQuantityTarget(target)) {
                        if (error instanceof ApiClient.RequestRejectedException
                                || error instanceof TaskSubmission.NotStartedException) {
                            target.view().showError(message);
                        } else {
                            target.view().showUncertain("提交结果尚未确认，请关闭后先查看订单，勿重复下单。");
                        }
                    } else {
                        Ui.toast(this, "提交结果未确认，请查看订单");
                    }
                }
            });
        }, () -> {
            main.post(() -> {
                productMutations.finish(mutation);
            });
        });
    }

    private void showOrderSuccessIfCurrent(
            ScreenGeneration.ScreenToken origin,
            LinearLayout targetContent,
            JSONObject order,
            boolean menu
    ) {
        presentationGate.present(origin,
                () -> origin.route() == ScreenRoute.SHOP
                        && targetContent == content
                        && isContentTargetAvailable(targetContent),
                () -> {
                    showOrderSuccess(order, menu);
                });
    }

    private void showOrders() {
        beginScreen(ScreenRoute.ORDERS);
        content.removeAllViews();
        String role = sessionStore.role();
        setHeader("我的订单", "每一餐，都有着落。");
        if (isCoupleMember()) {
            boolean handlesMenu = !canBuyCoupleOrder(true);
            addTopAction(handlesMenu ? "处理点菜和猪猪商店订单" : "处理宝宝商店订单",
                    Ui.GREEN, () -> showAdminOrderQueue(handlesMenu));
        } else if ("SUPER_ADMIN".equals(role)) {
            addTopAction("处理点菜订单", Ui.GREEN, () -> showAdminOrderQueue(true));
        } else if ("ADMIN".equals(role)) {
            addTopAction("处理商店订单", Ui.GREEN, () -> showAdminOrderQueue(false));
        }
        loadMyOrders();
    }

    private void showAdminOrderQueue(boolean menu) {
        beginScreen(ScreenRoute.ORDER_QUEUE);
        orderQueueShowingMenu = menu;
        content.removeAllViews();
        if (isCoupleMember()) {
            setHeader(menu ? "点菜和猪猪商店来单" : "宝宝商店来单",
                    menu ? "完成后获得 80% 报酬" : "完成后获得 60% 报酬");
        } else {
            setHeader(menu ? "点菜订单" : "商店订单", menu ? "超管处理点菜订单，完成后获得 80% 报酬" : "管理员处理宝宝商店订单，完成后获得 60% 报酬");
        }
        addTopAction("查看我的订单", Ui.GREEN, this::showOrders);
        loadAdminOrders(menu);
    }

    private void loadMyOrders() {
        loadOrders(false, true);
    }

    private void loadAdminOrders(boolean menu) {
        loadOrders(true, menu);
    }

    private void renderReadonlyOrders(JSONArray items) throws Exception {
        ordersView().renderReadonly(content, items);
    }

    private void onOrderDataChanged(ScreenGeneration.ScreenToken origin) {
        orderScreenState.invalidateData();
        if (currentScreen == null || !isContentTargetAvailable(content)) return;
        if (currentScreen.route() == ScreenRoute.HOME) {
            // A read started when returning home may predate this completed mutation.
            // Advancing the request generation also prevents that old count from winning later.
            refreshHomePendingOrders();
            return;
        }
        if (screenGeneration.isCurrent(origin)) {
            // Another completion can have started a GET while this mutation was in flight.
            if (currentScreen.route() == ScreenRoute.ORDER_QUEUE) {
                screenGeneration.request(currentScreen,
                        orderQueueShowingMenu ? "orders-menu-queue" : "orders-shop-queue");
            }
            return;
        }
        if (currentScreen.route() == ScreenRoute.ORDERS) {
            loadMyOrders();
        } else if (currentScreen.route() == ScreenRoute.ORDER_QUEUE) {
            // The first GET may still be in flight, before any list exists on this screen.
            loadAdminOrders(orderQueueShowingMenu);
        }
    }

    private void rememberOrdersViewport() {
        if (content == null || currentScreen == null || (currentScreen.route() != ScreenRoute.ORDERS
                && currentScreen.route() != ScreenRoute.ORDER_QUEUE)) return;
        OrdersView.OrderList list = OrdersView.findList(content);
        if (list != null && list.isAttachedTo(content)) {
            orderScreenState.rememberPosition(list.scope(), list.captureAnchor(), mainScroll.getScrollY());
        }
    }

    private void loadOrders(boolean admin, boolean menu) {
        String scope = admin ? (menu ? "orders-menu-queue" : "orders-shop-queue") : "orders-personal";
        String authToken = sessionStore.getToken();
        String role = sessionStore.role();
        orderScreenState.useScope(authToken, role);
        LinearLayout target = content;
        ScreenGeneration.RequestToken request = screenGeneration.request(currentScreen, scope);
        try {
            OrdersView.Prepared cachedPrepared = orderScreenState.prepared(scope, admin, menu);
            if (cachedPrepared != null) {
                renderOrders(target, cachedPrepared, true);
            } else {
                JSONArray cached = orderScreenState.items(scope);
                if (cached != null) renderOrders(target, cached, admin, menu, true);
            }
        } catch (Exception ignored) {
            orderScreenState.invalidateData();
        }
        ProgressLeases.Lease lease = OrdersView.findList(target) == null ? progressLeases.open() : null;
        boolean coupleMember = isCoupleMember();
        executeTask(() -> {
            JSONArray items;
            if (admin && coupleMember) {
                items = apiClient.receivedOrders(menu, authToken);
            } else if (admin) {
                items = menu ? apiClient.adminMenuOrders(authToken) : apiClient.adminShopOrders(authToken);
            } else {
                items = mergeMyOrders(apiClient.myOrders(authToken), apiClient.myShopOrders(authToken));
            }
            OrdersView.Prepared prepared = OrdersView.prepare(items, admin, menu);
            main.post(() -> presentationGate.present(request,
                    () -> authToken.equals(sessionStore.getToken()) && role.equals(sessionStore.role())
                            && isContentTargetAvailable(target), () -> {
                        try {
                            renderOrders(target, prepared, OrdersView.findList(target) == null);
                            orderScreenState.remember(scope, prepared);
                        } catch (Exception error) {
                            showOrdersError(target, admin, menu, error);
                        }
                    }));
        }, error -> {
            main.post(() -> presentationGate.present(request,
                    () -> authToken.equals(sessionStore.getToken()) && role.equals(sessionStore.role())
                            && isContentTargetAvailable(target), () -> {
                        if (!handleUnauthorized(error)) showOrdersError(target, admin, menu, error);
                    }));
        }, () -> {
            if (lease != null) main.post(lease::close);
        });
    }

    private void renderOrders(LinearLayout target, JSONArray items, boolean admin, boolean menu,
                              boolean restore) throws Exception {
        renderOrders(target, OrdersView.prepare(items, admin, menu), restore);
    }

    private void renderOrders(LinearLayout target, OrdersView.Prepared prepared, boolean restore) {
        OrdersView.OrderList list = prepared.admin() ? ordersView().renderAdmin(target, prepared)
                : ordersView().renderReadonly(target, prepared);
        View retry = target.findViewWithTag("orders-retry");
        if (retry != null) target.removeView(retry);
        if (prepared.admin() || isCoupleMember()) {
            for (OrderMutationCoordinator.Mutation mutation : orderMutations.active(sessionStore.getToken())) {
                list.beginPending(new OrdersView.OrderKey(mutation.menu(), mutation.orderId()), mutation.action());
            }
        }
        if (restore) {
            list.restoreAnchor(orderScreenState.anchor(list.scope()));
            ShopView.restoreViewport(mainScroll, list,
                    new ShopView.Viewport(orderScreenState.outerY(list.scope()), 0, 0));
        }
    }

    private void showOrdersError(LinearLayout target, boolean admin, boolean menu, Exception error) {
        if (OrdersView.findList(target) == null && target.findViewWithTag("orders-retry") == null) {
            TextView retry = new TextView(this);
            retry.setTag("orders-retry");
            retry.setText("订单加载失败，点击重试");
            retry.setTextColor(Ui.GREEN);
            retry.setPadding(Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16));
            target.addView(retry);
            bindClick(retry, ignored -> {
                target.removeView(retry);
                loadOrders(admin, menu);
            });
        }
        Ui.toast(this, error.getMessage() == null ? "订单更新失败，稍后再试" : error.getMessage());
    }

    private OrdersView ordersView() {
        return new OrdersView(this, new OrdersView.Actions() {
            @Override
            public void bindClick(View view, Runnable action) {
                MainActivity.this.bindClick(view, ignored -> action.run());
            }

            @Override
            public void requestStatus(OrdersView.StatusAction action) {
                updateOrderStatus(action);
            }

            @Override
            public void requestDelete(OrdersView.DeleteAction action) {
                ScreenGeneration.ScreenToken origin = currentScreen;
                confirm("删除订单？", "删除后不可恢复",
                        () -> {
                            if (screenGeneration.isCurrent(origin)) deleteOrderAndRemoveCard(action);
                        });
            }
        });
    }

    private void deleteOrderAndRemoveCard(OrdersView.DeleteAction action) {
        ScreenGeneration.ScreenToken origin = currentScreen;
        LinearLayout targetContent = content;
        OrdersView.OrderList targetList = action.list();
        OrdersView.OrderKey key = action.key();
        long orderId = action.orderId();
        boolean menu = action.menu();
        String authToken = sessionStore.getToken();
        if (origin == null || origin.route() != ScreenRoute.ORDER_QUEUE
                || !isContentTargetAvailable(targetContent)
                || !targetList.isAttachedTo(targetContent) || !targetList.contains(key)) return;
        OrderMutationCoordinator.Mutation mutation =
                orderMutations.begin(orderId, menu, authToken, "DELETE");
        if (mutation == null) {
            Ui.toast(this, "该订单正在处理中");
            return;
        }
        if (!targetList.beginPending(key, "DELETE")) {
            orderMutations.finish(mutation);
            return;
        }
        screenGeneration.request(origin, targetList.scope());
        ScreenGeneration.RequestToken request = origin != null
                && origin.route() == ScreenRoute.ORDER_QUEUE
                ? screenGeneration.request(origin, "order-delete-" + orderId)
                : null;
        ProgressLeases.Lease lease = progressLeases.open();
        executeTask(() -> {
            if (menu) {
                apiClient.deleteMenuOrder(orderId, authToken);
            } else {
                apiClient.deleteShopOrder(orderId, authToken);
            }
            main.post(() -> {
                if (!authToken.equals(sessionStore.getToken()) || isFinishing() || isDestroyed()) return;
                onOrderDataChanged(origin);
                presentationGate.present(request,
                    () -> authToken.equals(sessionStore.getToken())
                            && orderMutations.isCurrent(mutation)
                            && isContentTargetAvailable(targetContent)
                            && targetList.isAttachedTo(targetContent)
                            && targetList.contains(key),
                    () -> {
                        targetList.remove(key);
                        Ui.toast(this, "订单已删除");
                    });
            });
        }, e -> {
            main.post(() -> {
                if (isFinishing() || isDestroyed() || !authToken.equals(sessionStore.getToken())) {
                    return;
                }
                if (handleUnauthorized(e)) {
                    return;
                }
                presentationGate.present(request,
                        () -> authToken.equals(sessionStore.getToken())
                                && orderMutations.isCurrent(mutation)
                                && isContentTargetAvailable(targetContent)
                                && targetList.isAttachedTo(targetContent)
                                && targetList.contains(key),
                        () -> Ui.toast(this, e.getMessage() == null
                                ? "删除失败" : e.getMessage()));
            });
        }, () -> {
            main.post(() -> {
                targetList.clearPending(key);
                finishOrderMutation(mutation, key);
                lease.close();
            });
        });
    }

    private JSONArray mergeMyOrders(JSONArray menuOrders, JSONArray shopOrders) throws Exception {
        List<JSONObject> merged = new ArrayList<>();
        addTaggedOrders(merged, menuOrders, true, "点菜订单");
        addTaggedOrders(merged, shopOrders, false, "宝宝商店订单");
        merged.sort((left, right) -> right.optString("createdAt", "").compareTo(left.optString("createdAt", "")));
        JSONArray result = new JSONArray();
        for (JSONObject order : merged) {
            result.put(order);
        }
        return result;
    }

    private void addTaggedOrders(List<JSONObject> target, JSONArray orders, boolean menu, String typeName) throws Exception {
        if (orders == null) {
            return;
        }
        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = new JSONObject(orders.getJSONObject(i).toString());
            order.put("_orderMenu", menu);
            order.put("_orderTypeName", typeName);
            target.add(order);
        }
    }

    private void showOrderSuccess(JSONObject order, boolean menu) {
        if (isFinishing()) {
            return;
        }
        new OrderSuccessDialog(
                this,
                OrderSuccessDialog.State.from(order, menu),
                () -> selectTab("orders")
        ).show();
    }

    private void updateOrderStatus(OrdersView.StatusAction action) {
        ScreenGeneration.ScreenToken origin = currentScreen;
        LinearLayout targetContent = content;
        OrdersView.OrderList targetList = action.list();
        OrdersView.OrderKey key = action.key();
        long orderId = action.orderId();
        boolean menu = action.menu();
        String status = action.status();
        String authToken = sessionStore.getToken();
        boolean coupleMember = isCoupleMember();
        if (origin == null || (origin.route() != ScreenRoute.ORDER_QUEUE
                && !(coupleMember && origin.route() == ScreenRoute.ORDERS))
                || !isContentTargetAvailable(targetContent)
                || !targetList.isAttachedTo(targetContent) || !targetList.contains(key)) return;
        OrderMutationCoordinator.Mutation mutation =
                orderMutations.begin(orderId, menu, authToken, status);
        if (mutation == null) {
            Ui.toast(this, "该订单正在处理中");
            return;
        }
        if (!targetList.beginPending(key, status)) {
            orderMutations.finish(mutation);
            return;
        }
        screenGeneration.request(origin, targetList.scope());
        ScreenGeneration.RequestToken request = origin != null
                && (origin.route() == ScreenRoute.ORDER_QUEUE || coupleMember)
                ? screenGeneration.request(origin, "order-status-" + orderId + "-" + status)
                : null;
        executeTask(() -> {
            if (coupleMember) {
                apiClient.actOnOrder(menu, orderId, status, authToken);
            } else if (menu) {
                apiClient.updateMenuOrderStatus(orderId, status, authToken);
            } else {
                apiClient.updateShopOrderStatus(orderId, status, authToken);
            }
            refreshProfileAndWallet(authToken);
            main.post(() -> {
                if (!authToken.equals(sessionStore.getToken()) || isFinishing() || isDestroyed()) return;
                onOrderDataChanged(origin);
                presentationGate.present(request,
                    () -> authToken.equals(sessionStore.getToken())
                            && orderMutations.isCurrent(mutation)
                            && isContentTargetAvailable(targetContent)
                            && targetList.isAttachedTo(targetContent)
                            && targetList.contains(key), () -> {
                if (coupleMember) {
                    loadOrders(origin.route() == ScreenRoute.ORDER_QUEUE, menu);
                } else {
                    targetList.remove(key);
                }
                Ui.toast(this, "订单已更新");
                });
            });
        }, e -> {
            main.post(() -> {
                if (isFinishing() || isDestroyed() || !authToken.equals(sessionStore.getToken())) {
                    return;
                }
                if (handleUnauthorized(e)) {
                    return;
                }
                presentationGate.present(request,
                        () -> authToken.equals(sessionStore.getToken())
                                && orderMutations.isCurrent(mutation)
                                && isContentTargetAvailable(targetContent)
                                && targetList.isAttachedTo(targetContent)
                                && targetList.contains(key), () -> {
                    Ui.toast(this, e.getMessage() == null ? "更新失败" : e.getMessage());
                    if (coupleMember) loadOrders(origin.route() == ScreenRoute.ORDER_QUEUE, menu);
                });
            });
        }, () -> {
            main.post(() -> {
                targetList.clearPending(key);
                finishOrderMutation(mutation, key);
            });
        });
    }

    private void finishOrderMutation(OrderMutationCoordinator.Mutation mutation, OrdersView.OrderKey key) {
        if (!orderMutations.finish(mutation) || !mutation.authToken().equals(sessionStore.getToken())
                || !isContentTargetAvailable(content)) return;
        OrdersView.OrderList currentList = OrdersView.findList(content);
        if (currentList != null) currentList.clearPending(key);
    }

    private void showWallet() {
        beginScreen(ScreenRoute.WALLET);
        content.removeAllViews();
        setHeader("青程钱包", "把日常的每一份小奖励，攒起来。");
        walletScreenState.useScope(sessionStore.getToken(), sessionStore.role());
        primaryViewportState.useScope(sessionStore.getToken(), sessionStore.role());
        if (primaryViewportRestorer == null && mainScroll != null) {
            primaryViewportRestorer = new PrimaryViewportRestorer(mainScroll);
        }
        ScreenGeneration.ScreenToken screen = currentScreen;
        String authToken = sessionStore.getToken();
        String role = sessionStore.role();
        LinearLayout target = content;
        walletView = new WalletScreenView.Result(this, source -> {
            if (screenGeneration.isCurrent(screen)
                    && isCurrentWalletTarget(authToken, role, target, source)) refreshWallet();
        });
        content.addView(walletView.root());
        walletView.render(walletScreenState.snapshot());
        // A loading placeholder is shorter than the ledger; do not clamp its saved anchor yet.
        if (walletScreenState.snapshot().data() != null) restorePrimaryViewport();
        refreshWallet();
    }

    private void refreshWallet() {
        requestWallet(authToken -> walletService().load(authToken));
    }

    private void requestWallet(JsonObjectCall call) {
        ScreenGeneration.ScreenToken screen = currentScreen;
        WalletScreenView.Result view = walletView;
        LinearLayout target = content;
        String authToken = sessionStore.getToken();
        String role = sessionStore.role();
        if (screen == null || screen.route() != ScreenRoute.WALLET || view == null
                || walletRequest != null || !isCurrentSession(authToken)) return;
        walletScreenState.useScope(authToken, role);
        long gesture = primaryViewportRestorer == null ? 0 : primaryViewportRestorer.gestureSequence();
        ScreenGeneration.RequestToken request = screenGeneration.request(screen, "wallet-ledger");
        WalletCoordinator.Request walletScope = walletCoordinator.begin(authToken, role);
        long refreshSequence = ++walletRefreshSequence;
        walletRequest = request;
        walletScreenState.loading();
        view.render(walletScreenState.snapshot());
        executeTask(() -> {
            if (!isCurrentSession(authToken) || !role.equals(sessionStore.role())) return;
            WalletView.State state = WalletView.State.from(call.call(authToken));
            main.post(() -> presentationGate.present(request,
                    () -> refreshSequence == walletRefreshSequence
                            && walletCoordinator.isCurrent(walletScope, sessionStore.getToken(), sessionStore.role())
                            && isCurrentWalletTarget(authToken, role, target, view), () -> {
                        int scrollY = primaryViewportRestorer == null ? 0 : primaryViewportRestorer.captureY();
                        if (walletScreenState.snapshot().data() == null && primaryViewportRestorer != null
                                && gesture == primaryViewportRestorer.gestureSequence()) {
                            scrollY = primaryViewportState.scrollY(ScreenRoute.WALLET);
                        }
                        walletScreenState.loaded(state);
                        view.render(walletScreenState.snapshot());
                        restorePrimaryViewport(scrollY);
                    }));
        }, error -> main.post(() -> presentationGate.present(request,
                () -> refreshSequence == walletRefreshSequence
                        && walletCoordinator.isCurrent(walletScope, sessionStore.getToken(), sessionStore.role())
                        && isCurrentWalletTarget(authToken, role, target, view), () -> {
                    if (handleUnauthorized(error)) return;
                    walletScreenState.failed();
                    view.render(walletScreenState.snapshot());
                })), () -> main.post(() -> {
                    if (request.equals(walletRequest)) walletRequest = null;
                }));
    }

    private boolean isCurrentWalletTarget(String authToken, String role,
                                          LinearLayout target, WalletScreenView.Result view) {
        return isCurrentSession(authToken) && role.equals(sessionStore.role())
                && walletScreenState.matchesScope(authToken, role)
                && target == content && view == walletView && view.root().getParent() == target
                && isContentTargetAvailable(target);
    }

    private void showAiCenter() {
        beginScreen(ScreenRoute.SMART);
        weatherScreenOpen = false;
        if (shellHeader != null) {
            shellHeader.setVisibility(View.VISIBLE);
        }
        content.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 24));
        content.removeAllViews();
        setHeader("生活小帮手", "一点陪伴，一点生活的小灵感。");
        friendSmartBadge = null;
        friendUnreadSummary = null;
        SmartCenterView.Actions smartActions = new SmartCenterView.Actions() {
            @Override
            public void bindClick(View view, Runnable action) {
                MainActivity.this.bindClick(view, clicked -> action.run());
            }

            @Override
            public void refreshFriendBadge() { MainActivity.this.refreshFriendBadge(); }

            @Override
            public void openAiChat() { showAiChat(); }

            @Override
            public void openFriends() { showFriendCenter(); }

            @Override
            public void openWeather() { showWeatherCard(); }

            @Override
            public void openLoveRules() { showLoveRules(); }

            @Override
            public void openPiggy() { startActivity(new Intent(MainActivity.this, PiggyActivity.class)); }

            @Override
            public void openAnniversaries() {
                startActivityForResult(new Intent(MainActivity.this, AnniversaryActivity.class),
                        REQUEST_ANNIVERSARIES);
            }
        };
        SmartCenterView.Result smartCenter = new SmartCenterView(
                this,
                smartActions,
                friendUnreadCount,
                canManageItems()
        ).build();
        content.addView(smartCenter.root());
        friendSmartBadge = smartCenter.friendBadge();
        refreshFriendBadge();
        restorePrimaryViewport();
    }

    private String unreadFriendBadgeText() {
        if (friendUnreadCount <= 0) {
            return "0";
        }
        return friendUnreadCount > 99 ? "99+" : String.valueOf(friendUnreadCount);
    }

    private void showWeatherCard() {
        beginScreen(ScreenRoute.WEATHER);
        weatherScreenOpen = true;
        content.removeAllViews();
        content.setPadding(0, 0, 0, 0);
        if (shellHeader != null) {
            shellHeader.setVisibility(View.GONE);
        }
        if (aiComposerDock != null) {
            aiComposerDock.removeAllViews();
            aiComposerDock.setVisibility(View.GONE);
        }

        WeatherView.Result weatherView = new WeatherView(
                this,
                weatherViewActions(),
                new WeatherView.State(
                        null,
                        defaultWeatherLocation(),
                        defaultWeatherLocation())
        ).build();
        content.addView(weatherView.root());
        scrollContentToTop();
        loadWeather(weatherView.query());
    }

    private void loadWeather(EditText input) {
        ScreenGeneration.ScreenToken screen = currentScreen;
        LinearLayout targetContent = content;
        EditText targetInput = input;
        if (screen == null || screen.route() != ScreenRoute.WEATHER
                || targetContent == null || targetInput == null) {
            return;
        }
        String query = text(input);
        if (query.isBlank()) {
            query = defaultWeatherLocation();
            input.setText(query);
        }
        String weatherQuery = query;
        String authToken = sessionStore.getToken();
        ScreenGeneration.RequestToken request = screenGeneration.request(screen, "weather-query");
        ProgressLeases.Lease lease = progressLeases.open();
        executeTask(() -> {
            if (!isCurrentSession(authToken)) return;
            JSONObject weather = weatherService().current(weatherQuery, authToken);
            main.post(() -> presentationGate.present(request,
                    () -> isCurrentSession(authToken) && targetContent == content
                            && targetInput == input
                            && isContentTargetAvailable(targetContent)
                            && isViewAvailable(targetInput),
                    () -> {
                        renderWeather(weather);
                    }));
        }, e -> {
            main.post(() -> {
                if (!isCurrentSession(authToken)) return;
                if (handleUnauthorized(e)) {
                    return;
                }
                presentationGate.present(request,
                        () -> targetContent == content
                                && targetInput == input
                                && isContentTargetAvailable(targetContent)
                                && isViewAvailable(targetInput),
                        () -> Ui.toast(this,
                                e.getMessage() == null ? "天气查询失败" : e.getMessage()));
            });
        }, () -> {
            main.post(lease::close);
        });
    }

    private void renderWeather(JSONObject weather) {
        content.removeAllViews();
        WeatherView.Result weatherView = new WeatherView(
                this,
                weatherViewActions(),
                new WeatherView.State(
                        weather,
                        weatherQueryText(weather),
                        defaultWeatherLocation())
        ).build();
        content.addView(weatherView.root());
        scrollContentToTop();
    }

    private WeatherView.Actions weatherViewActions() {
        return new WeatherView.Actions() {
            @Override
            public void bindClick(View view, Runnable action) {
                MainActivity.this.bindClick(view, clicked -> action.run());
            }

            @Override
            public void back() {
                showAiCenter();
            }

            @Override
            public void search(EditText input) {
                loadWeather(input);
            }
        };
    }

    private String weatherQueryText(JSONObject weather) {
        return WeatherView.queryText(weather);
    }

    private String weatherDisplayPlace(JSONObject weather) {
        return WeatherView.displayPlace(weather);
    }

    private int weatherIconResource(String weather) {
        return WeatherView.iconResource(weather);
    }

    private void scrollContentToTop() {
        if (mainScroll != null) {
            mainScroll.post(() -> mainScroll.scrollTo(0, 0));
        }
    }

    private void showLoveRules() {
        beginScreen(ScreenRoute.LOVE_RULES);
        content.removeAllViews();
        setHeader("情侣守则", isCoupleMember() ? "两个人都可以维护"
                : (canManageItems() ? "管理员和超管可以修改" : "守则只允许查看"));
        if (aiComposerDock != null) {
            aiComposerDock.removeAllViews();
            aiComposerDock.setVisibility(View.GONE);
        }

        LoveRulesView.Result loveRules = new LoveRulesView(
                this, loveRulesViewActions(), new LoveRulesView.State(canManageItems())).build();
        content.addView(loveRules.root());
        loadLoveRules();
    }

    private void loadLoveRules() {
        String authToken = sessionStore.getToken();
        loadList("love-rules-list", authToken, () -> apiClient.loveRules(authToken), this::renderLoveRules);
    }

    private void renderLoveRules(JSONArray rules) {
        LoveRulesView view = new LoveRulesView(
                this, loveRulesViewActions(), new LoveRulesView.State(canManageItems()));
        LoveRulesView.Result loveRules = view.build();
        view.render(loveRules.list(), rules);
        content.removeAllViews();
        content.addView(loveRules.root());
    }

    private void showLoveRuleEditor(JSONObject rule) {
        if (!canManageItems()) {
            Ui.toast(this, "只有管理员和超级管理员可以修改守则");
            return;
        }
        LoveRulesTarget target = captureLoveRulesTarget();
        new LoveRuleEditorDialog(this, rule, new LoveRuleEditorDialog.Actions() {
            @Override
            public void save(LoveRuleEditorDialog.Value value) {
                saveLoveRule(target, rule, value);
            }

            @Override
            public void invalid(String message) {
                if (isCurrentLoveRulesTarget(target)) {
                    Ui.toast(MainActivity.this, message);
                }
            }
        }).show();
    }

    private void confirmLoveRuleDelete(JSONObject rule) {
        LoveRulesTarget target = captureLoveRulesTarget();
        new LoveRuleDeleteDialog(this, rule,
                ruleId -> deleteLoveRule(target, ruleId)).show();
    }

    private LoveRulesView.Actions loveRulesViewActions() {
        return new LoveRulesView.Actions() {
            @Override
            public void bindClick(View view, Runnable action) {
                MainActivity.this.bindClick(view, clicked -> action.run());
            }

            @Override
            public void back() {
                showAiCenter();
            }

            @Override
            public void refresh() {
                loadLoveRules();
            }

            @Override
            public void add() {
                showLoveRuleEditor(null);
            }

            @Override
            public void edit(JSONObject rule) {
                showLoveRuleEditor(rule);
            }

            @Override
            public void delete(JSONObject rule) {
                confirmLoveRuleDelete(rule);
            }
        };
    }

    private LoveRulesTarget captureLoveRulesTarget() {
        ScreenGeneration.ScreenToken screen = currentScreen;
        LinearLayout targetContent = content;
        String authToken = sessionStore.getToken();
        if (screen == null || targetContent == null || authToken == null) {
            return null;
        }
        return new LoveRulesTarget(screen, targetContent, authToken);
    }

    private void saveLoveRule(
            LoveRulesTarget target,
            JSONObject rule,
            LoveRuleEditorDialog.Value value
    ) {
        if (!isCurrentLoveRulesTarget(target)) {
            return;
        }
        ProgressLeases.Lease lease = progressLeases.open();
        executeTask(() -> {
            if (!isCurrentLoveRulesMutationTarget(target)) {
                return;
            }
            if (rule == null) {
                apiClient.createLoveRule(value.content(), target.authToken());
            } else {
                apiClient.updateLoveRule(rule.optLong("id"), value.content(),
                        rule.optInt("sortOrder", 1), target.authToken());
            }
            main.post(() -> presentationGate.present(target.screen(),
                    () -> isCurrentLoveRulesTarget(target),
                    () -> {
                        loadLoveRules();
                        Ui.toast(this, rule == null ? "守则已添加" : "守则已更新");
                    }));
        }, e -> {
            main.post(() -> {
                if (target.authToken().equals(sessionStore.getToken()) && handleUnauthorized(e)) {
                    return;
                }
                presentationGate.present(target.screen(),
                        () -> isCurrentLoveRulesTarget(target),
                        () -> Ui.toast(this,
                                e.getMessage() == null ? "保存失败" : e.getMessage()));
            });
        }, () -> {
            main.post(lease::close);
        });
    }

    private void deleteLoveRule(LoveRulesTarget target, long ruleId) {
        if (!isCurrentLoveRulesTarget(target)) {
            return;
        }
        ProgressLeases.Lease lease = progressLeases.open();
        executeTask(() -> {
            if (!isCurrentLoveRulesMutationTarget(target)) {
                return;
            }
            apiClient.deleteLoveRule(ruleId, target.authToken());
            main.post(() -> presentationGate.present(target.screen(),
                    () -> isCurrentLoveRulesTarget(target),
                    () -> {
                        loadLoveRules();
                        Ui.toast(this, "守则已删除");
                    }));
        }, e -> {
            main.post(() -> {
                if (target.authToken().equals(sessionStore.getToken()) && handleUnauthorized(e)) {
                    return;
                }
                presentationGate.present(target.screen(),
                        () -> isCurrentLoveRulesTarget(target),
                        () -> Ui.toast(this,
                                e.getMessage() == null ? "删除失败" : e.getMessage()));
            });
        }, () -> {
            main.post(lease::close);
        });
    }

    private boolean isCurrentLoveRulesTarget(LoveRulesTarget target) {
        return target != null
                && screenGeneration.isCurrent(target.screen())
                && target.screen().route() == ScreenRoute.LOVE_RULES
                && target.targetContent() == content
                && isContentTargetAvailable(target.targetContent())
                && target.authToken().equals(sessionStore.getToken());
    }

    private boolean isCurrentLoveRulesMutationTarget(LoveRulesTarget target) {
        return target != null
                && screenGeneration.isCurrent(target.screen())
                && target.screen().route() == ScreenRoute.LOVE_RULES
                && target.authToken().equals(sessionStore.getToken());
    }

    private record LoveRulesTarget(
            ScreenGeneration.ScreenToken screen,
            LinearLayout targetContent,
            String authToken
    ) {
    }

    private void showFriendCenter() {
        beginScreen(ScreenRoute.FRIEND_CENTER);
        friendChatMutations.invalidate();
        content.removeAllViews();
        setHeader("好友消息", "搜索用户、处理好友申请和私聊消息");
        friendMessageList = null;
        friendMessageScroll = null;
        friendMessageInput = null;
        friendReplyBar = null;
        friendReplyText = null;
        friendCancelReplyButton = null;
        friendReplyTarget = null;
        friendSendButton = null;
        activeFriendId = null;
        activeFriendName = "";
        if (aiComposerDock != null) {
            aiComposerDock.removeAllViews();
            aiComposerDock.setVisibility(View.GONE);
        }

        FriendCenterView.Result friendCenter = new FriendCenterView(
                this, friendCenterView()).build();
        friendRequestView = friendCenter.requestList();
        friendListView = friendCenter.friendList();
        content.addView(friendCenter.root());
        loadFriendCenter();
    }

    private FriendCenterView.Actions friendCenterView() {
        return new FriendCenterView.Actions() {
            @Override
            public void bindClick(View view, Runnable action) {
                MainActivity.this.bindClick(view, ignored -> action.run());
            }

            @Override
            public void openSmartCenter() {
                showAiCenter();
            }

            @Override
            public void refresh() {
                loadFriendCenter();
            }

            @Override
            public void search() {
                showFriendSearchDialog();
            }

            @Override
            public void acceptFriendRequest(FriendCenterView.RequestAction action) {
                handleAcceptFriendRequest(action.friendshipId());
            }

            @Override
            public void rejectFriendRequest(FriendCenterView.RequestAction action) {
                handleRejectFriendRequest(action.friendshipId());
            }

            @Override
            public void requestFriend(FriendCenterView.UserAction action) {
                handleRequestFriend(action.userId());
            }

            @Override
            public void openChat(FriendCenterView.UserAction action) {
                showFriendChat(action.userId(), action.displayName());
            }
        };
    }

    private void loadFriendCenter() {
        ScreenGeneration.ScreenToken screen = currentScreen;
        LinearLayout targetContent = content;
        LinearLayout targetRequestView = friendRequestView;
        LinearLayout targetListView = friendListView;
        if (screen == null
                || screen.route() != ScreenRoute.FRIEND_CENTER
                || targetContent == null
                || targetRequestView == null
                || targetListView == null) {
            return;
        }
        ScreenGeneration.RequestToken request = screenGeneration.request(screen, "friend-center");
        FriendSessionGuard.Session friendSession = captureFriendSession();
        if (!friendSession.authenticated()) {
            return;
        }
        FriendUnreadCoordinator.Request unreadRequest = friendUnreadCoordinator.request();
        ProgressLeases.Lease lease = progressLeases.open();
        executeTask(() -> {
            JSONObject data = friendService().center(friendSession.authToken());
            main.post(() -> {
                if (!isCurrentFriendSession(friendSession)) {
                    return;
                }
                commitFriendUnread(unreadRequest, data.optLong("unreadCount", 0));
                presentationGate.present(request,
                        () -> screen.route() == ScreenRoute.FRIEND_CENTER
                                && targetContent == content
                                && targetRequestView == friendRequestView
                                && targetListView == friendListView
                                && isContentTargetAvailable(targetContent)
                                && isViewAvailable(targetRequestView)
                                && isViewAvailable(targetListView),
                        () -> {
                            friendCenterData = data;
                            renderFriendCenter();
                        });
            });
        }, e -> {
            main.post(() -> {
                if (handleFriendUnauthorized(e, friendSession)) {
                    return;
                }
                presentationGate.present(request,
                        () -> screen.route() == ScreenRoute.FRIEND_CENTER
                                && targetContent == content
                                && targetRequestView == friendRequestView
                                && targetListView == friendListView
                                && isContentTargetAvailable(targetContent)
                                && isViewAvailable(targetRequestView)
                                && isViewAvailable(targetListView),
                        () -> Ui.toast(this,
                                e.getMessage() == null ? "好友加载失败" : e.getMessage()));
            });
        }, () -> {
            main.post(lease::close);
        });
    }

    private void renderFriendCenter() {
        if (friendRequestView == null || friendListView == null) {
            return;
        }
        new FriendCenterView(this, friendCenterView()).renderCenter(
                friendRequestView,
                friendListView,
                friendCenterData == null ? new JSONObject() : friendCenterData);
    }

    private void showFriendSearchDialog() {
        new FriendSearchDialog(this, friendCenterView())
                .showQuery(this::searchFriendUsers);
    }

    private void searchFriendUsers(String query) {
        ScreenGeneration.ScreenToken origin = currentScreen;
        LinearLayout targetContent = content;
        if (origin == null
                || origin.route() != ScreenRoute.FRIEND_CENTER
                || targetContent == null) {
            return;
        }
        ScreenGeneration.RequestToken request = screenGeneration.request(origin, "friend-search");
        FriendSessionGuard.Session friendSession = captureFriendSession();
        if (!friendSession.authenticated()) {
            return;
        }
        ProgressLeases.Lease lease = progressLeases.open();
        executeTask(() -> {
            JSONArray users = friendService().search(query, friendSession.authToken());
            main.post(() -> presentationGate.present(request,
                    () -> isCurrentFriendSession(friendSession)
                            && origin.route() == ScreenRoute.FRIEND_CENTER
                            && targetContent == content
                            && isContentTargetAvailable(targetContent),
                    () -> showFriendSearchResults(users)));
        }, e -> {
            main.post(() -> {
                if (handleFriendUnauthorized(e, friendSession)) {
                    return;
                }
                presentationGate.present(request,
                        () -> isCurrentFriendSession(friendSession)
                                && origin.route() == ScreenRoute.FRIEND_CENTER
                                && targetContent == content
                                && isContentTargetAvailable(targetContent),
                        () -> Ui.toast(this,
                                e.getMessage() == null ? "搜索失败" : e.getMessage()));
            });
        }, () -> {
            main.post(lease::close);
        });
    }

    private void showFriendSearchResults(JSONArray users) {
        new FriendSearchDialog(this, friendCenterView()).showResults(users);
    }

    private void handleAcceptFriendRequest(long friendshipId) {
        ScreenGeneration.ScreenToken origin = currentScreen;
        LinearLayout targetContent = content;
        runFriendCenterTaskIfCurrent(origin, targetContent, authToken -> {
            friendService().accept(friendshipId, authToken);
            main.post(() -> reloadFriendCenterIfCurrent(origin, targetContent));
            return "已添加好友";
        });
    }

    private void handleRejectFriendRequest(long friendshipId) {
        ScreenGeneration.ScreenToken origin = currentScreen;
        LinearLayout targetContent = content;
        runFriendCenterTaskIfCurrent(origin, targetContent, authToken -> {
            friendService().reject(friendshipId, authToken);
            main.post(() -> reloadFriendCenterIfCurrent(origin, targetContent));
            return "已拒绝";
        });
    }

    private void handleRequestFriend(long userId) {
        ScreenGeneration.ScreenToken origin = currentScreen;
        LinearLayout targetContent = content;
        runFriendCenterTaskIfCurrent(origin, targetContent, authToken -> {
            friendService().request(userId, authToken);
            main.post(() -> reloadFriendCenterIfCurrent(origin, targetContent));
            return "好友申请已发送";
        });
    }

    private void reloadFriendCenterIfCurrent(
            ScreenGeneration.ScreenToken origin,
            LinearLayout targetContent
    ) {
        presentationGate.present(origin,
                () -> origin.route() == ScreenRoute.FRIEND_CENTER
                        && targetContent == content
                        && isContentTargetAvailable(targetContent),
                () -> {
                    loadFriendCenter();
                });
    }

    private void runFriendCenterTaskIfCurrent(
            ScreenGeneration.ScreenToken origin,
            LinearLayout targetContent,
            FriendMessageCall call
    ) {
        presentationGate.present(origin,
                () -> origin.route() == ScreenRoute.FRIEND_CENTER
                        && targetContent == content
                        && isContentTargetAvailable(targetContent),
                () -> {
                    FriendSessionGuard.Session friendSession = captureFriendSession();
                    if (!friendSession.authenticated()) {
                        return;
                    }
                    ProgressLeases.Lease lease = progressLeases.open();
                    executeTask(() -> {
                        String message = call.call(friendSession.authToken());
                        main.post(() -> showFriendTaskResultIfCurrent(
                                origin, targetContent, message));
                    }, e -> {
                        main.post(() -> {
                            if (handleFriendUnauthorized(e, friendSession)) {
                                return;
                            }
                            showFriendTaskResultIfCurrent(origin, targetContent,
                                    e.getMessage() == null ? "操作失败" : e.getMessage());
                        });
                    }, () -> {
                        main.post(lease::close);
                    });
                });
    }

    private void showFriendTaskResultIfCurrent(
            ScreenGeneration.ScreenToken origin,
            LinearLayout targetContent,
            String message
    ) {
        presentationGate.present(origin,
                () -> origin.route() == ScreenRoute.FRIEND_CENTER
                        && targetContent == content
                        && isContentTargetAvailable(targetContent),
                () -> Ui.toast(this, message));
    }

    private void showFriendChat(long userId, String displayName) {
        if (userId <= 0) {
            Ui.toast(this, "好友不存在");
            return;
        }
        ScreenGeneration.ScreenToken chatScreen = beginScreen(ScreenRoute.FRIEND_CHAT);
        activeFriendId = userId;
        activeFriendName = displayName == null || displayName.trim().isEmpty() ? "好友" : displayName;
        friendMessagesData = new JSONArray();
        friendReplyTarget = null;
        friendChatMutations.openChat(chatScreen.generation(), userId);
        content.removeAllViews();
        setHeader(activeFriendName, "好友私聊");
        if (aiComposerDock != null) {
            aiComposerDock.removeAllViews();
            aiComposerDock.setVisibility(View.GONE);
        }
        FriendChatView.Result friendChat = new FriendChatView(
                this, friendChatView(), new FriendChatView.State(activeFriendName)).build();
        friendChatTitle = friendChat.title();
        friendMessageList = friendChat.messageList();
        friendMessageScroll = friendChat.messageScroll();
        friendMessageInput = friendChat.input();
        friendReplyBar = friendChat.replyBar();
        friendReplyText = friendChat.replyText();
        friendCancelReplyButton = friendChat.cancelReplyButton();
        friendSendButton = friendChat.sendButton();
        bindClick(friendCancelReplyButton, clicked -> clearFriendReply());
        content.addView(friendChat.root());
        loadFriendMessages();
    }

    private FriendChatView.Actions friendChatView() {
        return new FriendChatView.Actions() {
            @Override
            public void bindClick(View view, Runnable action) {
                MainActivity.this.bindClick(view, clicked -> action.run());
            }

            @Override
            public void back() {
                showFriendCenter();
            }

            @Override
            public void clear() {
                ScreenGeneration.ScreenToken clearOrigin = currentScreen;
                Long clearFriendId = activeFriendId;
                LinearLayout clearContent = content;
                LinearLayout clearMessages = friendMessageList;
                ScrollView clearScroll = friendMessageScroll;
                confirm("删除聊天记录？", "只会删除你这边和该好友的聊天记录。",
                        () -> clearFriendMessagesIfCurrent(
                                clearOrigin, clearFriendId, clearContent,
                                clearMessages, clearScroll));
            }

            @Override
            public void refresh() {
                loadFriendMessages();
            }

            @Override
            public void emoji() {
                showFriendEmojiPicker();
            }

            @Override
            public void image() {
                pickFriendMedia(false);
            }

            @Override
            public void loadImage(String url, ImageView target) {
                imageLoader.load(url, target, sessionStore.getToken());
            }

            @Override
            public void previewImage(String url) {
                showFriendImagePreview(url);
            }

            @Override
            public void reply(JSONObject message) {
                beginFriendReply(message);
            }

            @Override
            public void send(TextView sendButton) {
                sendFriendMessage(sendButton);
            }
        };
    }

    private void showFriendEmojiPicker() {
        ScreenGeneration.ScreenToken origin = currentScreen;
        Long friendId = activeFriendId;
        EditText targetInput = friendMessageInput;
        if (origin == null
                || origin.route() != ScreenRoute.FRIEND_CHAT
                || friendId == null
                || targetInput == null) {
            return;
        }
        loadFriendStickersThenShow(origin, friendId, targetInput);
    }

    private void loadFriendStickersThenShow(
            ScreenGeneration.ScreenToken origin,
            Long friendId,
            EditText targetInput
    ) {
        FriendSessionGuard.Session friendSession = captureFriendSession();
        if (!friendSession.authenticated()) {
            return;
        }
        ProgressLeases.Lease lease = progressLeases.open();
        executeTask(() -> {
            JSONArray stickers = friendService().stickers(friendSession.authToken());
            main.post(() -> presentationGate.present(origin,
                    () -> isCurrentFriendSession(friendSession)
                            && isCurrentFriendEmojiTarget(origin, friendId, targetInput),
                    () -> {
                        friendStickersData = stickers;
                        showFriendEmojiPickerPage(origin, friendId, targetInput, 0);
                    }));
        }, e -> {
            main.post(() -> {
                if (handleFriendUnauthorized(e, friendSession)) {
                    return;
                }
                presentationGate.present(origin,
                        () -> isCurrentFriendEmojiTarget(origin, friendId, targetInput),
                        () -> {
                            friendStickersData = new JSONArray();
                            showFriendEmojiPickerPage(origin, friendId, targetInput, 0);
                            Ui.toast(this, "自定义表情加载失败，仍可使用系统表情");
                        });
            });
        }, () -> {
            main.post(lease::close);
        });
    }

    private void showFriendEmojiPickerPage(
            ScreenGeneration.ScreenToken origin,
            Long friendId,
            EditText targetInput,
            int requestedPage
    ) {
        new FriendEmojiPickerView(
                this,
                friendEmojiPickerView(origin, friendId, targetInput),
                new FriendEmojiPickerView.State(requestedPage),
                friendStickersData
        ).show();
    }

    private FriendEmojiPickerView.Actions friendEmojiPickerView(
            ScreenGeneration.ScreenToken origin,
            Long friendId,
            EditText targetInput
    ) {
        return new FriendEmojiPickerView.Actions() {
            @Override
            public void bindClick(View view, Runnable action) {
                MainActivity.this.bindClick(view, clicked -> action.run());
            }

            @Override
            public void select(String emoji) {
                insertFriendEmojiIfCurrent(origin, friendId, targetInput, emoji);
            }

            @Override
            public void addCustom() {
                pickFriendMedia(true);
            }

            @Override
            public void selectCustom(long stickerId) {
                sendFriendSticker(origin, friendId, stickerId);
            }

            @Override
            public void loadImage(String url, ImageView target) {
                imageLoader.load(url, target, sessionStore.getToken());
            }

            @Override
            public void showPage(int page) {
                showFriendEmojiPickerPageIfCurrent(origin, friendId, targetInput, page);
            }

            @Override
            public void shown() {
                presentationGate.present(origin,
                        () -> origin.route() == ScreenRoute.FRIEND_CHAT
                                && friendId.equals(activeFriendId)
                                && targetInput == friendMessageInput
                                && isViewAvailable(targetInput),
                        targetInput::requestFocus);
            }
        };
    }

    private void showFriendEmojiPickerPageIfCurrent(
            ScreenGeneration.ScreenToken origin,
            Long friendId,
            EditText targetInput,
            int page
    ) {
        presentationGate.present(origin,
                () -> origin.route() == ScreenRoute.FRIEND_CHAT
                        && friendId.equals(activeFriendId)
                        && targetInput == friendMessageInput
                        && isViewAvailable(targetInput),
                () -> showFriendEmojiPickerPage(origin, friendId, targetInput, page));
    }

    private boolean isCurrentFriendEmojiTarget(
            ScreenGeneration.ScreenToken origin,
            Long friendId,
            EditText targetInput
    ) {
        return origin != null
                && origin.route() == ScreenRoute.FRIEND_CHAT
                && friendId != null
                && friendId.equals(activeFriendId)
                && targetInput == friendMessageInput
                && isViewAvailable(targetInput);
    }

    private void insertFriendEmojiIfCurrent(
            ScreenGeneration.ScreenToken origin,
            Long friendId,
            EditText targetInput,
            String emoji
    ) {
        presentationGate.present(origin,
                () -> emoji != null
                        && origin.route() == ScreenRoute.FRIEND_CHAT
                        && friendId.equals(activeFriendId)
                        && targetInput == friendMessageInput
                        && isViewAvailable(targetInput),
                () -> {
                    int start = Math.max(targetInput.getSelectionStart(), 0);
                    int end = Math.max(targetInput.getSelectionEnd(), 0);
                    if (end < start) {
                        int swap = start;
                        start = end;
                        end = swap;
                    }
                    targetInput.getText().replace(start, end, emoji);
                    targetInput.setSelection(start + emoji.length());
                });
    }

    private void loadFriendMessages() {
        ScreenGeneration.ScreenToken screen = currentScreen;
        Long friendId = activeFriendId;
        LinearLayout targetContent = content;
        LinearLayout targetMessages = friendMessageList;
        ScrollView targetScroll = friendMessageScroll;
        if (screen == null
                || screen.route() != ScreenRoute.FRIEND_CHAT
                || friendId == null
                || targetContent == null
                || targetMessages == null
                || targetScroll == null) {
            return;
        }
        if (friendChatMutations.blocksLoads(screen.generation(), friendId)) {
            return;
        }
        ScreenGeneration.RequestToken request = screenGeneration.request(screen, "friend-chat-messages");
        FriendSessionGuard.Session friendSession = captureFriendSession();
        if (!friendSession.authenticated()) {
            return;
        }
        ProgressLeases.Lease lease = progressLeases.open();
        executeTask(() -> {
            JSONArray messages = friendService().messages(friendId, friendSession.authToken());
            main.post(() -> presentationGate.present(request,
                    () -> isCurrentFriendSession(friendSession)
                            && !friendChatMutations.blocksLoads(screen.generation(), friendId)
                            && isCurrentFriendChatTarget(
                            screen, friendId, targetContent, targetMessages, targetScroll),
                    () -> {
                        friendMessagesData = messages;
                        renderFriendMessages();
                        executeTask(() -> {
                            friendService().markRead(friendId, friendSession.authToken());
                            main.post(() -> {
                                if (isCurrentFriendSession(friendSession)) {
                                    refreshFriendBadge();
                                }
                            });
                        }, e -> {
                            main.post(() -> {
                                if (handleFriendUnauthorized(e, friendSession)) {
                                    return;
                                }
                                presentationGate.present(request,
                                        () -> isCurrentFriendSession(friendSession)
                                                && isCurrentFriendChatTarget(
                                                screen, friendId, targetContent,
                                                targetMessages, targetScroll),
                                        () -> Ui.toast(this,
                                                e.getMessage() == null
                                                        ? "已显示消息，未读状态稍后同步" : e.getMessage()));
                            });
                        });
                    }));
        }, e -> {
            main.post(() -> {
                if (handleFriendUnauthorized(e, friendSession)) {
                    return;
                }
                presentationGate.present(request,
                        () -> isCurrentFriendSession(friendSession)
                                && isCurrentFriendChatTarget(
                                screen, friendId, targetContent, targetMessages, targetScroll),
                        () -> Ui.toast(this,
                                e.getMessage() == null ? "消息加载失败" : e.getMessage()));
            });
        }, () -> {
            main.post(lease::close);
        });
    }

    private void clearFriendMessagesIfCurrent(
            ScreenGeneration.ScreenToken origin,
            Long friendId,
            LinearLayout targetContent,
            LinearLayout targetMessages,
            ScrollView targetScroll
    ) {
        presentationGate.present(origin,
                () -> isCurrentFriendChatTarget(
                        origin, friendId, targetContent, targetMessages, targetScroll),
                () -> clearFriendMessages(
                        origin, friendId, targetContent, targetMessages, targetScroll));
    }

    private void clearFriendMessages(
            ScreenGeneration.ScreenToken screen,
            Long friendId,
            LinearLayout targetContent,
            LinearLayout targetMessages,
            ScrollView targetScroll
    ) {
        if (screen == null
                || screen.route() != ScreenRoute.FRIEND_CHAT
                || friendId == null
                || targetContent == null
                || targetMessages == null
                || targetScroll == null) {
            Ui.toast(this, "先选择好友");
            return;
        }
        FriendChatMutationCoordinator.Mutation mutation = friendChatMutations.begin(
                screen.generation(), friendId, FriendChatMutationCoordinator.Kind.CLEAR);
        if (mutation == null) {
            Ui.toast(this, "请等待当前消息操作完成");
            return;
        }
        screenGeneration.request(screen, "friend-chat-messages");
        ScreenGeneration.RequestToken request = screenGeneration.request(screen, "friend-chat-clear");
        FriendSessionGuard.Session friendSession = captureFriendSession();
        if (!friendSession.authenticated()) {
            friendChatMutations.finish(mutation);
            return;
        }
        ProgressLeases.Lease lease = progressLeases.open();
        executeTask(() -> {
            friendService().clearMessages(friendId, friendSession.authToken());
            main.post(() -> {
                presentationGate.present(request,
                        () -> isCurrentFriendSession(friendSession)
                                && friendChatMutations.isCurrent(mutation)
                                && isCurrentFriendChatTarget(
                                screen, friendId, targetContent, targetMessages, targetScroll),
                        () -> {
                            friendChatMutations.finish(mutation);
                            refreshFriendBadge();
                            friendMessagesData = new JSONArray();
                            renderFriendMessages();
                            Ui.toast(this, "聊天记录已删除");
                            loadFriendMessages();
                        });
            });
        }, e -> {
            main.post(() -> {
                if (handleFriendUnauthorized(e, friendSession)) {
                    return;
                }
                presentationGate.present(request,
                        () -> isCurrentFriendSession(friendSession)
                                && friendChatMutations.isCurrent(mutation)
                                && isCurrentFriendChatTarget(
                                screen, friendId, targetContent, targetMessages, targetScroll),
                        () -> {
                            friendChatMutations.finish(mutation);
                            Ui.toast(this, e.getMessage() == null ? "删除失败" : e.getMessage());
                        });
            });
        }, () -> {
            main.post(lease::close);
            main.post(() -> friendChatMutations.finish(mutation));
        });
    }

    private void renderFriendMessages(boolean forceBottom) {
        LinearLayout targetMessages = friendMessageList;
        if (targetMessages == null) {
            return;
        }
        JSONArray messages = friendMessagesData == null ? new JSONArray() : friendMessagesData;
        EditText targetInput = friendMessageInput;
        ScrollView targetScroll = friendMessageScroll;
        ChatViewport.Snapshot viewport = ChatViewport.capture(targetScroll, targetMessages, forceBottom);
        new FriendChatView(this, friendChatView(), new FriendChatView.State(activeFriendName))
                .renderMessages(targetMessages, messages);
        if (targetScroll != null) {
            targetScroll.post(() -> {
                if (targetScroll == friendMessageScroll
                        && targetMessages == friendMessageList
                        && targetInput == friendMessageInput
                        && isViewAvailable(targetScroll)) {
                    ChatViewport.restore(viewport, targetScroll, targetMessages, targetInput);
                }
            });
        }
    }

    private void renderFriendMessages() {
        renderFriendMessages(false);
    }

    private void beginFriendReply(JSONObject message) {
        if (message == null || message.optLong("id", 0L) <= 0L
                || friendReplyBar == null || friendReplyText == null
                || friendMessageInput == null) {
            return;
        }
        friendReplyTarget = message;
        String preview = friendMessagePreview(message);
        friendReplyText.setText("回复“" + preview + "”");
        friendReplyBar.setVisibility(View.VISIBLE);
        friendMessageInput.requestFocus();
        friendMessageInput.setSelection(friendMessageInput.length());
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            keyboard.showSoftInput(friendMessageInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void clearFriendReply() {
        friendReplyTarget = null;
        if (friendReplyText != null) {
            friendReplyText.setText("");
        }
        if (friendReplyBar != null) {
            friendReplyBar.setVisibility(View.GONE);
        }
    }

    private String friendMessagePreview(JSONObject message) {
        String type = message.optString("type", "TEXT");
        if ("IMAGE".equals(type)) {
            return "[图片]";
        }
        if ("STICKER".equals(type)) {
            return "[表情]";
        }
        String content = message.optString("content", "").trim();
        return content.length() <= 36 ? content : content.substring(0, 36) + "…";
    }

    private void sendFriendMessage(TextView sendButton) {
        ScreenGeneration.ScreenToken screen = currentScreen;
        Long friendId = activeFriendId;
        LinearLayout targetContent = content;
        LinearLayout targetMessages = friendMessageList;
        ScrollView targetScroll = friendMessageScroll;
        EditText targetInput = friendMessageInput;
        TextView targetSendButton = sendButton;
        String draftText = targetInput == null ? "" : targetInput.getText().toString();
        String messageText = draftText.trim();
        JSONObject replyTarget = friendReplyTarget;
        Long replyToMessageId = replyTarget == null
                ? null : replyTarget.optLong("id", 0L);
        if (screen == null
                || screen.route() != ScreenRoute.FRIEND_CHAT
                || friendId == null
                || targetContent == null
                || targetMessages == null
                || targetScroll == null
                || targetInput == null
                || targetSendButton != friendSendButton) {
            Ui.toast(this, "先选择好友");
            return;
        }
        if (messageText.isEmpty()) {
            Ui.toast(this, "消息不能为空");
            return;
        }
        FriendChatMutationCoordinator.Mutation mutation = friendChatMutations.begin(
                screen.generation(), friendId, FriendChatMutationCoordinator.Kind.SEND);
        if (mutation == null) {
            Ui.toast(this, "请等待当前消息操作完成");
            return;
        }
        ScreenGeneration.RequestToken request = screenGeneration.request(screen, "friend-chat-send");
        FriendSessionGuard.Session friendSession = captureFriendSession();
        if (!friendSession.authenticated()) {
            friendChatMutations.finish(mutation);
            return;
        }
        targetSendButton.setText("…");
        targetSendButton.setEnabled(false);
        executeTask(() -> {
            JSONObject message = friendService().sendText(
                    friendId, messageText, replyToMessageId, friendSession.authToken());
            main.post(() -> presentationGate.present(request,
                    () -> isCurrentFriendSession(friendSession)
                            && friendChatMutations.isCurrent(mutation)
                            && isCurrentFriendChatTarget(
                            screen, friendId, targetContent, targetMessages, targetScroll)
                            && targetInput == friendMessageInput
                            && isViewAvailable(targetInput),
                    () -> {
                        friendChatMutations.finish(mutation);
                        if (draftText.equals(targetInput.getText().toString())) {
                            targetInput.setText("");
                        }
                        if (replyTarget == friendReplyTarget) {
                            clearFriendReply();
                        }
                        friendMessagesData.put(message);
                        renderFriendMessages(true);
                        loadFriendMessages();
                    }));
        }, e -> {
            main.post(() -> {
                if (handleFriendUnauthorized(e, friendSession)) {
                    return;
                }
                presentationGate.present(request,
                        () -> isCurrentFriendSession(friendSession)
                                && friendChatMutations.isCurrent(mutation)
                                && isCurrentFriendChatTarget(
                                screen, friendId, targetContent, targetMessages, targetScroll),
                        () -> {
                            friendChatMutations.finish(mutation);
                            Ui.toast(this, e.getMessage() == null ? "发送失败" : e.getMessage());
                        });
            });
        }, () -> {
            main.post(() -> {
                restoreFriendSendButtonIfCurrent(request, friendId, targetSendButton);
                friendChatMutations.finish(mutation);
            });
        });
    }

    private void pickFriendMedia(boolean customSticker) {
        ScreenGeneration.ScreenToken screen = currentScreen;
        Long friendId = activeFriendId;
        if (screen == null
                || screen.route() != ScreenRoute.FRIEND_CHAT
                || friendId == null
                || friendMessageList == null
                || friendMessageScroll == null) {
            Ui.toast(this, "先选择好友");
            return;
        }
        int requestCode = customSticker
                ? REQUEST_PICK_FRIEND_STICKER : REQUEST_PICK_FRIEND_IMAGE;
        FriendChatMutationCoordinator.Mutation mutation = null;
        if (!customSticker) {
            mutation = friendChatMutations.begin(
                    screen.generation(), friendId, FriendChatMutationCoordinator.Kind.SEND);
            if (mutation == null) {
                Ui.toast(this, "请等待当前消息操作完成");
                return;
            }
        }
        pendingFriendMediaPickerContext = new FriendMediaPickerContext(
                screen,
                friendId,
                content,
                friendMessageList,
                friendMessageScroll,
                requestCode,
                customSticker,
                mutation
        );
        pickImageFromDevice(requestCode);
    }

    private void uploadFriendMedia(Uri uri, FriendMediaPickerContext context) {
        if (!isCurrentFriendMediaTarget(context)) {
            friendChatMutations.finish(context.mutation());
            return;
        }
        FriendSessionGuard.Session friendSession = captureFriendSession();
        if (!friendSession.authenticated()) {
            friendChatMutations.finish(context.mutation());
            return;
        }
        ProgressLeases.Lease lease = progressLeases.open();
        Ui.toast(this, context.customSticker() ? "正在添加自定义表情" : "正在发送图片");
        executeTask(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IllegalArgumentException("无法读取图片");
                }
                if (context.customSticker()) {
                    JSONObject sticker = friendService().addSticker(
                            input, displayName(uri), mimeType(uri), friendSession.authToken());
                    main.post(() -> presentationGate.present(context.screen(),
                            () -> isCurrentFriendSession(friendSession)
                                    && isCurrentFriendMediaTarget(context),
                            () -> {
                                friendStickersData.put(sticker);
                                Ui.toast(this, "自定义表情已添加");
                                showFriendEmojiPicker();
                            }));
                } else {
                    JSONObject message = friendService().sendImage(
                            context.friendId(), input, displayName(uri), mimeType(uri),
                            friendSession.authToken());
                    main.post(() -> presentationGate.present(context.screen(),
                            () -> isCurrentFriendSession(friendSession)
                                    && friendChatMutations.isCurrent(context.mutation())
                                    && isCurrentFriendMediaTarget(context),
                            () -> {
                                friendChatMutations.finish(context.mutation());
                                appendFriendMediaMessage(message);
                            }));
                }
            }
        }, e -> {
            main.post(() -> {
                if (handleFriendUnauthorized(e, friendSession)) {
                    return;
                }
                presentationGate.present(context.screen(),
                        () -> isCurrentFriendMediaTarget(context),
                        () -> Ui.toast(this, e.getMessage() == null
                                ? (context.customSticker() ? "表情添加失败" : "图片发送失败")
                                : e.getMessage()));
            });
        }, () -> {
            main.post(lease::close);
            main.post(() -> friendChatMutations.finish(context.mutation()));
        });
    }

    private void sendFriendSticker(
            ScreenGeneration.ScreenToken screen,
            Long friendId,
            long stickerId
    ) {
        LinearLayout targetContent = content;
        LinearLayout targetMessages = friendMessageList;
        ScrollView targetScroll = friendMessageScroll;
        if (stickerId <= 0
                || !isCurrentFriendChatTarget(
                screen, friendId, targetContent, targetMessages, targetScroll)) {
            return;
        }
        FriendChatMutationCoordinator.Mutation mutation = friendChatMutations.begin(
                screen.generation(), friendId, FriendChatMutationCoordinator.Kind.SEND);
        if (mutation == null) {
            Ui.toast(this, "请等待当前消息操作完成");
            return;
        }
        FriendSessionGuard.Session friendSession = captureFriendSession();
        if (!friendSession.authenticated()) {
            friendChatMutations.finish(mutation);
            return;
        }
        ProgressLeases.Lease lease = progressLeases.open();
        executeTask(() -> {
            JSONObject message = friendService().sendSticker(
                    friendId, stickerId, friendSession.authToken());
            main.post(() -> presentationGate.present(screen,
                    () -> isCurrentFriendSession(friendSession)
                            && friendChatMutations.isCurrent(mutation)
                            && isCurrentFriendChatTarget(
                            screen, friendId, targetContent, targetMessages, targetScroll),
                    () -> {
                        friendChatMutations.finish(mutation);
                        appendFriendMediaMessage(message);
                    }));
        }, e -> {
            main.post(() -> {
                if (handleFriendUnauthorized(e, friendSession)) {
                    return;
                }
                presentationGate.present(screen,
                        () -> friendChatMutations.isCurrent(mutation)
                                && isCurrentFriendChatTarget(
                        screen, friendId, targetContent, targetMessages, targetScroll),
                        () -> Ui.toast(this,
                                e.getMessage() == null ? "表情发送失败" : e.getMessage()));
            });
        }, () -> {
            main.post(lease::close);
            main.post(() -> friendChatMutations.finish(mutation));
        });
    }

    private void appendFriendMediaMessage(JSONObject message) {
        friendMessagesData.put(message);
        renderFriendMessages(true);
        loadFriendMessages();
    }

    private boolean isCurrentFriendMediaTarget(FriendMediaPickerContext context) {
        return context != null
                && isCurrentFriendChatTarget(
                context.screen(), context.friendId(), context.content(),
                context.messages(), context.scroll());
    }

    private void showFriendImagePreview(String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        image.setMinimumWidth(Ui.dp(this, 320));
        image.setMinimumHeight(Ui.dp(this, 320));
        imageLoader.load(url, image, sessionStore.getToken());
        CommonDialogs.imagePreview(this, image);
    }

    private void restoreFriendSendButtonIfCurrent(
            ScreenGeneration.RequestToken request,
            Long friendId,
            TextView targetSendButton
    ) {
        presentationGate.present(request,
                () -> request.screen().route() == ScreenRoute.FRIEND_CHAT
                        && friendId.equals(activeFriendId)
                        && targetSendButton == friendSendButton
                        && isViewAvailable(targetSendButton),
                () -> {
                    targetSendButton.setText("↑");
                    targetSendButton.setEnabled(true);
                });
    }

    private boolean isCurrentFriendChatTarget(
            ScreenGeneration.ScreenToken screen,
            Long friendId,
            LinearLayout targetContent,
            LinearLayout targetMessages,
            ScrollView targetScroll
    ) {
        return screen.route() == ScreenRoute.FRIEND_CHAT
                && friendId.equals(activeFriendId)
                && targetContent == content
                && targetMessages == friendMessageList
                && targetScroll == friendMessageScroll
                && isContentTargetAvailable(targetContent)
                && isViewAvailable(targetMessages)
                && isViewAvailable(targetScroll);
    }

    private String friendDisplayName(JSONObject user) {
        if (user == null) {
            return "好友";
        }
        String nickname = user.optString("nickname", "");
        if (!nickname.trim().isEmpty()) {
            return nickname;
        }
        return user.optString("username", "好友");
    }

    private String friendMeta(JSONObject user) {
        if (user == null) {
            return "";
        }
        String username = user.optString("username", "");
        String role = user.optString("role", "USER");
        StringBuilder builder = new StringBuilder();
        if (!username.trim().isEmpty()) {
            builder.append("@").append(username);
        }
        if (!"USER".equals(role)) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(DisplayFormatters.role(role));
        }
        return builder.toString();
    }

    private void showCheckInDialog() {
        ScreenGeneration.ScreenToken origin = currentScreen;
        LinearLayout targetContent = content;
        String authToken = sessionStore.getToken();
        if (origin == null
                || origin.route() != ScreenRoute.HOME
                || targetContent == null
                || authToken.isEmpty()) {
            return;
        }
        CheckInCoordinator.DialogSession dialogSession = checkInCoordinator.open(authToken);
        CheckInDialogView.Result view = new CheckInDialogView(
                this,
                checkInDialogActions(
                        origin, targetContent, authToken, dialogSession),
                CheckInDialogView.State.loading()
        ).show();
        CheckInDialogContext target = new CheckInDialogContext(
                origin, targetContent, view, authToken, dialogSession);

        ScreenGeneration.RequestToken request = screenGeneration.request(origin, "check-in-load");
        ProgressLeases.Lease lease = progressLeases.open();
        executeTask(() -> {
            JSONObject wallet = walletService().load(authToken);
            main.post(() -> presentationGate.present(request,
                    () -> isCurrentCheckInTarget(target),
                    () -> renderCheckInDialog(target, wallet)));
        }, e -> {
            main.post(() -> presentationGate.present(request,
                    () -> isCurrentCheckInTarget(target),
                    () -> {
                        if (handleUnauthorized(e)) {
                            return;
                        }
                        new CheckInDialogView(
                                this,
                                checkInDialogActions(
                                        target.origin(), target.targetContent(),
                                        target.authToken(), target.dialogSession()),
                                CheckInDialogView.State.loading()
                        ).render(target.view(), CheckInDialogView.State.loadError(
                                e.getMessage()));
                    }));
        }, () -> {
            main.post(lease::close);
        });
    }

    private CheckInDialogView.Actions checkInDialogActions(
            ScreenGeneration.ScreenToken origin,
            LinearLayout targetContent,
            String authToken,
            CheckInCoordinator.DialogSession dialogSession
    ) {
        return new CheckInDialogView.Actions() {
            @Override
            public void bindClick(View view, Runnable action) {
                MainActivity.this.bindClick(view, clicked -> action.run());
            }

            @Override
            public void submit(CheckInDialogView.Result view) {
                CheckInDialogContext target = new CheckInDialogContext(
                        origin, targetContent, view, authToken, dialogSession);
                submitCheckIn(target);
            }

            @Override
            public void dismiss() {
                checkInCoordinator.dismiss(dialogSession);
            }
        };
    }

    private void renderCheckInDialog(CheckInDialogContext target, JSONObject wallet) {
        new CheckInDialogView(
                this,
                checkInDialogActions(
                        target.origin(), target.targetContent(),
                        target.authToken(), target.dialogSession()),
                CheckInDialogView.State.loading()
        ).render(target.view(), CheckInDialogView.State.ready(wallet));
    }

    private void submitCheckIn(CheckInDialogContext target) {
        if (!isCurrentCheckInTarget(target)) {
            return;
        }
        CheckInCoordinator.Submission submission =
                checkInCoordinator.beginSubmission(target.dialogSession());
        if (submission == null) {
            return;
        }
        ScreenGeneration.RequestToken request = screenGeneration.request(target.origin(), "check-in-submit");
        ProgressLeases.Lease lease = progressLeases.open();
        new CheckInDialogView(
                this,
                checkInDialogActions(
                        target.origin(), target.targetContent(),
                        target.authToken(), target.dialogSession()),
                CheckInDialogView.State.loading()
        ).render(target.view(), CheckInDialogView.State.submitting());
        executeTask(() -> {
            JSONObject wallet = walletService().checkIn(target.authToken());
            main.post(() -> presentationGate.present(request,
                    () -> checkInCoordinator.isCurrent(submission)
                            && isCurrentCheckInTarget(target),
                    () -> {
                        renderCheckInDialog(target, wallet);
                        if (target.view().dialog().getWindow() != null
                                && target.view().dialog().getWindow().getDecorView()
                                instanceof FrameLayout host) {
                            clickBurstEffect.emit(this, host, target.view().root());
                        }
                        refreshProfileAndWallet(target.authToken());
                        Ui.toast(this, "签到成功");
                    }));
        }, e -> {
            main.post(() -> presentationGate.present(request,
                    () -> checkInCoordinator.isCurrent(submission)
                            && isCurrentCheckInTarget(target),
                    () -> {
                        if (handleUnauthorized(e)) {
                            return;
                        }
                        new CheckInDialogView(
                                this,
                                checkInDialogActions(
                                        target.origin(), target.targetContent(),
                                        target.authToken(), target.dialogSession()),
                                CheckInDialogView.State.loading()
                        ).render(target.view(), CheckInDialogView.State.submitError(
                                e.getMessage()));
                    }));
        }, () -> {
            main.post(() -> {
                checkInCoordinator.finish(submission);
                lease.close();
            });
        });
    }

    private boolean isCurrentCheckInTarget(CheckInDialogContext target) {
        return target != null
                && screenGeneration.isCurrent(target.origin())
                && target.origin().route() == ScreenRoute.HOME
                && target.authToken().equals(sessionStore.getToken())
                && checkInCoordinator.isCurrent(target.dialogSession())
                && target.targetContent() == content
                && isContentTargetAvailable(target.targetContent())
                && target.view().dialog().isShowing()
                && isViewAvailable(target.view().body());
    }

    private void showAiChat() {
        beginScreen(ScreenRoute.AI_CHAT);
        aiDraftId++;
        aiConversationId = null;
        pendingAiPickerContext = null;
        aiAttachments.clear();
        buildAiChatScreen(true);
    }

    private void buildAiChatScreen(boolean showComposer) {
        setHeader("AI 聊天", "支持文字对话、图片理解和历史回顾");
        content.removeAllViews();
        aiInput = null;
        aiAttachmentList = null;
        AiChatView.Result chat = new AiChatView(
                this,
                aiChatView(),
                new AiChatView.State(false, "", aiAttachments)
        ).build();
        aiMessages = chat.messages();
        aiMessagesScroll = chat.messageScroll();
        aiInput = chat.input();
        aiAttachmentList = chat.attachmentList();
        content.addView(chat.root(), chat.root().getLayoutParams());
        if (showComposer) {
            showAiComposerDock();
        } else if (aiComposerDock != null) {
            aiComposerDock.removeAllViews();
            aiComposerDock.setVisibility(View.GONE);
        }
    }

    private void showAiComposerDock() {
        if (aiComposerDock == null) {
            return;
        }
        String currentInput = aiInput == null ? "" : aiInput.getText().toString();
        aiComposerDock.removeAllViews();
        aiComposerDock.setVisibility(View.VISIBLE);
        AiChatView.ComposerResult composer = new AiChatView(
                this,
                aiChatView(),
                new AiChatView.State(true, currentInput, aiAttachments, aiOrderingMode)
        ).buildComposer();
        aiInput = composer.input();
        aiAttachmentList = composer.attachmentList();
        aiComposerDock.addView(composer.root(), new LinearLayout.LayoutParams(-1, -2));
    }

    private AiChatView.Actions aiChatView() {
        return new AiChatView.Actions() {
            @Override
            public void bindClick(View view, Runnable action) {
                MainActivity.this.bindClick(view, clicked -> action.run());
            }

            @Override
            public void back() {
                if (aiComposerDock != null) {
                    aiComposerDock.removeAllViews();
                    aiComposerDock.setVisibility(View.GONE);
                }
                aiMessagesScroll = null;
                aiMessages = null;
                aiInput = null;
                aiAttachmentList = null;
                pendingAiPickerContext = null;
                content.removeAllViews();
                showAiCenter();
            }

            @Override
            public void history() {
                showAiHistory();
            }

            @Override
            public void pickImage() {
                pendingAiPickerContext = new AiPickerContext(
                        currentScreen, aiDraftId, aiAttachmentList);
                pickImageFromDevice(REQUEST_PICK_AI_IMAGE);
            }

            @Override
            public void send(TextView sendButton) {
                sendAiMessage(sendButton);
            }

            @Override
            public boolean setOrderingMode(boolean enabled) {
                if (activeAiStream != null) {
                    Ui.toast(MainActivity.this, "请等待当前回复完成或先停止生成");
                    return false;
                }
                if (enabled && !aiAttachments.isEmpty()) {
                    Ui.toast(MainActivity.this, "点菜模式使用文字，请先移除图片");
                    return false;
                }
                aiOrderingMode = enabled;
                return true;
            }

            @Override
            public void removeAttachment(int index) {
                if (index < 0 || index >= aiAttachments.size()) {
                    return;
                }
                aiAttachments.remove(index);
                renderAiAttachments();
            }

            @Override
            public void loadImage(String url, ImageView target) {
                imageLoader.load(url, target);
            }

            @Override
            public void downloadImage(String url) {
                MainActivity.this.downloadImage(url);
            }
        };
    }

    private void sendAiMessage(TextView sendButton) {
        if (activeAiStream != null) {
            activeAiStream.cancel();
            sendButton.setEnabled(false);
            return;
        }
        ScreenGeneration.ScreenToken screen = currentScreen;
        LinearLayout targetContent = content;
        LinearLayout targetMessages = aiMessages;
        ScrollView targetScroll = aiMessagesScroll;
        EditText targetInput = aiInput;
        TextView targetSendButton = sendButton;
        Long conversationId = aiConversationId;
        String prompt = targetInput == null ? "" : targetInput.getText().toString().trim();
        List<AiAttachment> sentAttachments = new ArrayList<>(aiAttachments);
        boolean orderingMode = aiOrderingMode;
        if (orderingMode && !sentAttachments.isEmpty()) {
            Ui.toast(this, "点菜模式使用文字，请先移除图片或切回聊天");
            return;
        }
        String authToken = sessionStore.getToken();
        if (screen == null
                || !isCurrentAiChatTarget(
                screen, targetContent, targetMessages, targetScroll, targetInput, targetSendButton)) {
            return;
        }
        if (prompt.isEmpty() && sentAttachments.isEmpty()) {
            Ui.toast(this, "先输入一句话或添加图片");
            return;
        }
        String inputError = AiInputLimits.error(prompt, sentAttachments);
        if (inputError != null) {
            Ui.toast(this, inputError);
            return;
        }
        if (!aiChatCoordinator.canBegin(screen)) {
            Ui.toast(this, "请等待当前回复完成");
            return;
        }
        ScreenGeneration.RequestToken request = screenGeneration.request(screen, "ai-chat-send");
        AiChatCoordinator.Send send = aiChatCoordinator.begin(
                request, conversationId, prompt, authToken, sentAttachments);
        if (send == null) {
            return;
        }
        targetInput.setText("");
        aiAttachments.clear();
        renderAiAttachments();
        String preview = prompt;
        if (!sentAttachments.isEmpty()) {
            preview = (preview.isEmpty() ? "" : preview + "\n") + "[已附加 " + sentAttachments.size() + " 张图片]";
        }
        addAiBubble("user", preview);
        targetSendButton.setText("■");
        targetSendButton.setContentDescription("停止生成");
        targetSendButton.setEnabled(true);
        targetSendButton.setAlpha(0.7f);
        View thinkingBubble = addAiThinkingBubble(targetMessages);
        AiStreamSession streamSession = new AiStreamSession();
        activeAiStream = streamSession;
        AiStreamState streamState = new AiStreamState();
        AiChatView chatView = new AiChatView(this, aiChatView(),
                new AiChatView.State(false, "", sentAttachments));
        AiChatView.LiveReply liveReply = chatView.addLiveReply(targetMessages);
        AiStreamUiUpdates streamUi = new AiStreamUiUpdates(main.handler(), update ->
                presentationGate.present(request,
                        () -> aiChatCoordinator.isCurrent(send)
                                && send.authToken().equals(sessionStore.getToken())
                                && isCurrentAiChatTarget(screen, targetContent, targetMessages,
                                targetScroll, targetInput, targetSendButton),
                        () -> {
                            if (update.answer().isEmpty() && update.summary().isEmpty()) return;
                            ChatViewport.Snapshot viewport = ChatViewport.capture(targetScroll, targetMessages, false);
                            removeAiThinkingBubble(targetMessages, thinkingBubble);
                            chatView.renderLiveReply(liveReply, update);
                            scrollAiMessagesToBottom(viewport);
                        }));
        executeTask(() -> {
            List<String> dataUrls = new ArrayList<>();
            for (AiAttachment attachment : sentAttachments) {
                dataUrls.add(attachment.dataUrl);
            }
            JSONObject body = orderingMode
                    ? apiClient.aiOrderingMessage(prompt, conversationId, send.authToken(), streamSession)
                    : apiClient.aiResponseStream(
                    prompt, conversationId, dataUrls, send.authToken(),
                    streamState, streamSession, streamUi::offer);
            Long responseConversationId = body.has("conversationId")
                    ? body.optLong("conversationId") : null;
            String answer = extractAiText(body);
            main.post(() -> presentationGate.present(request,
                    () -> aiChatCoordinator.isCurrent(send)
                            && send.authToken().equals(sessionStore.getToken())
                            && isCurrentAiChatTarget(
                            screen, targetContent, targetMessages,
                            targetScroll, targetInput, targetSendButton),
                    () -> {
                        streamUi.close();
                        if (responseConversationId != null) {
                            aiConversationId = responseConversationId;
                        }
                        removeAiThinkingBubble(targetMessages, thinkingBubble);
                        ChatViewport.Snapshot viewport = ChatViewport.capture(targetScroll, targetMessages, false);
                        String visibleAnswer = body.optJSONObject("ordering") != null
                                && "draft".equals(body.optJSONObject("ordering").optString("kind"))
                                ? "已整理好点菜草稿，请核对下面的数量和金额。" : answer;
                        chatView.renderLiveReply(liveReply, new AiStreamState.Update(visibleAnswer,
                                streamState.snapshot().summary(), "", true));
                        new AiOrderingView(this, new AiOrderingView.Actions() {
                            public void bindClick(View view, Runnable action) {
                                MainActivity.this.bindClick(view, clicked -> action.run());
                            }
                            public void confirm(JSONObject draft, AiOrderingView.Card card) {
                                confirmAiOrder(draft, card, screen, targetContent, targetMessages, send.authToken());
                            }
                            public void orders() { showOrders(); }
                        }).add(targetMessages, body.optJSONObject("ordering"));
                        scrollAiMessagesToBottom(viewport);
                    }));
        }, e -> {
            main.post(() -> presentationGate.present(request,
                    () -> aiChatCoordinator.isCurrent(send)
                            && send.authToken().equals(sessionStore.getToken())
                            && isCurrentAiChatTarget(
                            screen, targetContent, targetMessages,
                            targetScroll, targetInput, targetSendButton),
                    () -> {
                        if (handleUnauthorized(e)) {
                            return;
                        }
                        removeAiThinkingBubble(targetMessages, thinkingBubble);
                        if (e instanceof TaskSubmission.NotStartedException
                                && targetInput.getText().toString().isEmpty() && aiAttachments.isEmpty()) {
                            targetInput.setText(prompt);
                            targetInput.setSelection(targetInput.length());
                            aiAttachments.addAll(sentAttachments);
                            renderAiAttachments();
                        }
                        streamUi.close();
                        AiStreamState.Update partial = streamState.snapshot();
                        String status = streamSession.isCancelled() ? "已停止生成" : "回复中断："
                                + (e.getMessage() == null ? "请稍后再试" : e.getMessage());
                        chatView.renderLiveReply(liveReply, new AiStreamState.Update(
                                partial.answer(), partial.summary(), status, false));
                    }));
        }, () -> {
            main.post(() -> {
                presentationGate.present(request,
                        () -> aiChatCoordinator.isCurrent(send)
                                && send.authToken().equals(sessionStore.getToken())
                                && isCurrentAiChatTarget(
                                screen, targetContent, targetMessages,
                                targetScroll, targetInput, targetSendButton),
                        () -> {
                            targetSendButton.setText("↑");
                            targetSendButton.setContentDescription("发送消息");
                            targetSendButton.setEnabled(true);
                            targetSendButton.setAlpha(1f);
                            removeAiThinkingBubble(targetMessages, thinkingBubble);
                        });
                aiChatCoordinator.finish(send);
                streamUi.close();
                if (activeAiStream == streamSession) activeAiStream = null;
            });
        });
    }

    private void confirmAiOrder(JSONObject draft, AiOrderingView.Card card,
            ScreenGeneration.ScreenToken screen, LinearLayout targetContent,
            LinearLayout targetMessages, String authToken) {
        if (!isCurrentAiOrderCard(screen, targetContent, targetMessages, card, authToken)) {
            card.failed("页面或账号已变化，请重新生成草稿", false);
            return;
        }
        String quote = draft.optString("quoteToken");
        executeTask(() -> {
            JSONObject result = apiClient.confirmAiOrder(quote, authToken);
            refreshProfileAndWallet(authToken);
            main.post(() -> {
                if (!isCurrentSession(authToken)) return;
                onOrderDataChanged(screen);
                if (isCurrentAiOrderCard(screen, targetContent, targetMessages, card, authToken))
                    card.confirmed(result.optJSONObject("order"));
            });
        }, error -> main.post(() -> {
            if (!isCurrentAiOrderCard(screen, targetContent, targetMessages, card, authToken)) return;
            if (handleUnauthorized(error)) return;
            boolean retry = !(error instanceof ApiClient.RequestRejectedException);
            String message = retry && !(error instanceof TaskSubmission.NotStartedException)
                    ? "结果未确认，请先查看订单；重试确认会使用同一订单请求。"
                    : error.getMessage();
            card.failed(message == null ? "提交失败，请查看订单" : message, retry);
        }));
    }

    private boolean isCurrentAiOrderCard(ScreenGeneration.ScreenToken screen, LinearLayout targetContent,
            LinearLayout targetMessages, AiOrderingView.Card card, String authToken) {
        return isCurrentSession(authToken) && screenGeneration.isCurrent(screen)
                && screen.route() == ScreenRoute.AI_CHAT && targetContent == content
                && targetMessages == aiMessages && isViewAvailable(card.root)
                && isContentTargetAvailable(targetContent);
    }

    private boolean isCurrentAiChatTarget(
            ScreenGeneration.ScreenToken screen,
            LinearLayout targetContent,
            LinearLayout targetMessages,
            ScrollView targetScroll,
            EditText targetInput,
            TextView targetSendButton
    ) {
        return screen != null
                && screen.route() == ScreenRoute.AI_CHAT
                && targetContent == content
                && targetMessages == aiMessages
                && targetScroll == aiMessagesScroll
                && targetInput == aiInput
                && isContentTargetAvailable(targetContent)
                && isViewAvailable(targetMessages)
                && isViewAvailable(targetScroll)
                && isViewAvailable(targetInput)
                && isViewAvailable(targetSendButton);
    }

    private View addAiThinkingBubble(LinearLayout targetMessages) {
        ChatViewport.Snapshot viewport = ChatViewport.capture(aiMessagesScroll, targetMessages, false);
        View bubble = new AiChatView(
                this,
                aiChatView(),
                new AiChatView.State(false, "", aiAttachments)
        ).addThinkingBubble(targetMessages);
        scrollAiMessagesToBottom(viewport);
        return bubble;
    }

    private void removeAiThinkingBubble(LinearLayout targetMessages, View thinkingBubble) {
        new AiChatView(
                this,
                aiChatView(),
                new AiChatView.State(false, "", aiAttachments)
        ).removeThinkingBubble(targetMessages, thinkingBubble);
    }

    private void addAiBubble(String role, String text) {
        if (aiMessages == null) {
            return;
        }
        ChatViewport.Snapshot viewport = ChatViewport.capture(aiMessagesScroll, aiMessages, "user".equals(role));
        new AiChatView(
                this,
                aiChatView(),
                new AiChatView.State(false, "", aiAttachments)
        ).addBubble(aiMessages, role, text);
        scrollAiMessagesToBottom(viewport);
    }

    private void scrollAiMessagesToBottom(ChatViewport.Snapshot viewport) {
        ScrollView targetScroll = aiMessagesScroll;
        LinearLayout targetMessages = aiMessages;
        EditText targetInput = aiInput;
        if (targetScroll == null) {
            return;
        }
        targetScroll.post(() -> {
            if (targetScroll == aiMessagesScroll && targetMessages == aiMessages
                    && isViewAvailable(targetScroll)) {
                ChatViewport.restore(viewport, targetScroll, targetMessages, targetInput);
            }
        });
    }

    private int screenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int screenHeight() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    private String extractAiText(JSONObject body) {
        String direct = body.optString("output_text", "");
        if (!direct.isEmpty()) {
            return direct.trim();
        }
        JSONArray output = body.optJSONArray("output");
        if (output == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONArray content = item.optJSONArray("content");
            if (content == null) {
                continue;
            }
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part == null) {
                    continue;
                }
                String text = part.optString("text", "");
                if (!text.isEmpty()) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append(text);
                }
            }
        }
        return builder.toString().trim();
    }

    private void showAiHistory() {
        beginScreen(ScreenRoute.AI_HISTORY);
        aiDraftId++;
        pendingAiPickerContext = null;
        aiAttachments.clear();
        aiInput = null;
        aiAttachmentList = null;
        aiConversationId = null;
        buildAiChatScreen(false);
        setHeader("AI 历史", "打开旧对话，或开始一个新对话");
        LinearLayout historyMessages = aiMessages;
        ScreenGeneration.ScreenToken historyOrigin = currentScreen;
        String historyAuthToken = sessionStore.getToken();
        loadAiHistoryPage(historyOrigin, historyMessages, historyAuthToken, true);
    }

    private void loadAiHistoryPage(ScreenGeneration.ScreenToken origin, LinearLayout target,
                                   String authToken, boolean reset) {
        AiHistoryCoordinator.State request = aiHistoryCoordinator.begin(authToken, reset);
        String cursor = request.nextCursor();
        renderAiHistory(origin, target, authToken);
        executeTask(() -> {
            AiHistoryState.Page page = new AiHistoryService(apiClient).load(cursor, 30, authToken);
            main.post(() -> {
                if (origin != currentScreen || target != aiMessages || !isViewAvailable(target)
                        || !authToken.equals(sessionStore.getToken())) return;
                if (aiHistoryCoordinator.loaded(request, page)) {
                    renderAiHistory(origin, target, authToken);
                }
            });
        }, error -> main.post(() -> {
            if (origin != currentScreen || target != aiMessages || !isViewAvailable(target)
                    || !authToken.equals(sessionStore.getToken())) return;
            if (aiHistoryCoordinator.failed(request, error)) {
                renderAiHistory(origin, target, authToken);
                if (handleUnauthorized(error)) return;
                Ui.toast(this, error.getMessage() == null ? "历史记录加载失败" : error.getMessage());
            }
        }));
    }

    private void renderAiHistory(ScreenGeneration.ScreenToken origin, LinearLayout target,
                                 String authToken) {
        AiHistoryCoordinator.State state = aiHistoryCoordinator.snapshot();
        new AiHistoryView(this, aiHistoryView(origin, target, authToken))
                .render(target, state.items(), state.hasMore(), state.loading());
    }

    private AiHistoryView.Actions aiHistoryView(
            ScreenGeneration.ScreenToken origin,
            LinearLayout targetMessages,
            String authToken
    ) {
        return new AiHistoryView.Actions() {
            @Override
            public void bindClick(View view, Runnable action) {
                MainActivity.this.bindClick(view, clicked -> action.run());
            }

            @Override
            public void newConversation() {
                showAiChat();
            }

            @Override
            public void open(long conversationId) {
                loadAiConversation(conversationId);
            }

            @Override
            public void delete(long conversationId) {
                confirm("删除 AI 对话？", "删除后不可恢复",
                        () -> deleteAiConversationIfCurrent(
                                origin, targetMessages, conversationId, authToken));
            }

            @Override
            public void loadMore() {
                AiHistoryCoordinator.State state = aiHistoryCoordinator.snapshot();
                if (state.loading() || !state.hasMore()) return;
                loadAiHistoryPage(origin, targetMessages, authToken, false);
            }
        };
    }

    private void deleteAiConversationIfCurrent(
            ScreenGeneration.ScreenToken origin,
            LinearLayout targetMessages,
            long conversationId,
            String authToken
    ) {
        presentationGate.present(origin,
                () -> origin.route() == ScreenRoute.AI_HISTORY
                        && targetMessages == aiMessages
                        && isViewAvailable(targetMessages)
                        && authToken.equals(sessionStore.getToken()),
                () -> {
                    ScreenGeneration.RequestToken request = screenGeneration.request(
                            origin, "ai-history-delete");
                    ProgressLeases.Lease lease = progressLeases.open();
                    executeTask(() -> {
                        apiClient.deleteAiConversation(conversationId, authToken);
                        main.post(() -> presentationGate.present(request,
                                () -> origin.route() == ScreenRoute.AI_HISTORY
                                        && targetMessages == aiMessages
                                        && isViewAvailable(targetMessages)
                                        && authToken.equals(sessionStore.getToken()),
                                () -> {
                                    reloadAiHistoryIfCurrent(origin, targetMessages);
                                    Ui.toast(this, "对话已删除");
                                }));
                    }, e -> {
                        main.post(() -> presentationGate.present(request,
                                () -> origin.route() == ScreenRoute.AI_HISTORY
                                        && targetMessages == aiMessages
                                        && isViewAvailable(targetMessages)
                                        && authToken.equals(sessionStore.getToken()),
                                () -> {
                                    if (!handleUnauthorized(e)) {
                                        Ui.toast(this, e.getMessage() == null
                                                ? "删除失败" : e.getMessage());
                                    }
                                }));
                    }, () -> {
                        main.post(lease::close);
                    });
                });
    }

    private void reloadAiHistoryIfCurrent(
            ScreenGeneration.ScreenToken origin,
            LinearLayout targetMessages
    ) {
        presentationGate.present(origin,
                () -> origin.route() == ScreenRoute.AI_HISTORY
                        && targetMessages == aiMessages
                        && isViewAvailable(targetMessages),
                () -> {
                    showAiHistory();
                });
    }

    private void addAiHistoryControls() {
        renderAiHistory(currentScreen, aiMessages, sessionStore.getToken());
    }

    private void loadAiConversation(long conversationId) {
        beginScreen(ScreenRoute.AI_CHAT);
        aiDraftId++;
        pendingAiPickerContext = null;
        aiAttachments.clear();
        aiInput = null;
        aiAttachmentList = null;
        aiConversationId = null;
        buildAiChatScreen(false);
        LinearLayout targetMessages = aiMessages;
        addAiBubble("assistant", "正在加载对话…");
        String detailAuthToken = sessionStore.getToken();
        loadList("ai-history-messages", detailAuthToken,
                () -> apiClient.aiMessages(conversationId, detailAuthToken), messages -> {
            if (targetMessages != aiMessages || !isViewAvailable(targetMessages)) {
                return;
            }
            aiConversationId = conversationId;
            targetMessages.removeAllViews();
            new AiChatView(this, aiChatView(), new AiChatView.State(false, "", aiAttachments))
                    .renderMessages(targetMessages, messages);
            showAiComposerDock();
            scrollAiMessagesToBottom(ChatViewport.capture(aiMessagesScroll, targetMessages, true));
        }, error -> {
            if (targetMessages == aiMessages && isViewAvailable(targetMessages)) {
                targetMessages.removeAllViews();
                addAiBubble("assistant", "对话加载失败："
                        + (error.getMessage() == null ? "请返回历史记录后重试" : error.getMessage()));
            }
        });
    }

    private void renderAiAttachments() {
        if (aiAttachmentList == null) {
            return;
        }
        new AiChatView(
                this,
                aiChatView(),
                new AiChatView.State(true, "", aiAttachments)
        ).renderAttachments(aiAttachmentList, aiAttachments);
    }

    private void showMe() {
        beginScreen(ScreenRoute.PROFILE);
        setHeader("我的", "账号设置、版本更新和退出登录");
        if (BuildConfig.COUPLE_TEST) addTopAction("开发者测试区", Ui.GREEN, this::showTestWorkspace);
        JSONObject user = sessionStore.getUser();
        String role = sessionStore.role();
        ProfileView.State state = new ProfileView.State(
                user == null ? "未登录" : user.optString("nickname", user.optString("username")),
                user == null ? "--" : user.optString("username"),
                DisplayFormatters.role(role),
                APP_VERSION_NAME,
                defaultWeatherLocation(),
                pushBindingDetail(),
                "SUPER_ADMIN".equals(role)
        );
        ProfileView.Actions actions = new ProfileView.Actions() {
            @Override
            public void bindClick(View view, Runnable action) {
                MainActivity.this.bindClick(view, ignored -> action.run());
            }

            @Override
            public void editWeatherLocation() {
                editDefaultWeatherLocation();
            }

            @Override
            public void checkForUpdate() {
                MainActivity.this.checkForUpdate();
            }

            @Override
            public void editNickname() {
                MainActivity.this.editNickname();
            }

            @Override
            public void editPassword() {
                MainActivity.this.editPassword();
            }

            @Override
            public void bindPush() {
                bindPushManually();
            }

            @Override
            public void openUserManagement() {
                showUserManagement();
            }

            @Override
            public void logout() {
                logoutToLogin();
            }
        };
        View profilePage = new ProfileView(this, actions, state).build();
        content.addView(profilePage);
        restorePrimaryViewport();
    }

    private String defaultWeatherLocation() {
        return sessionStore == null ? "合肥瑶海区" : sessionStore.defaultWeatherLocation();
    }

    private void editDefaultWeatherLocation() {
        new DefaultWeatherLocationDialog(this, defaultWeatherLocation(), value -> {
            String location = value.isBlank() ? "合肥瑶海区" : value;
            sessionStore.saveDefaultWeatherLocation(location);
            Ui.toast(this, "默认天气地区已更新");
            selectTab("me");
        }).show();
    }

    private void showUserManagement() {
        beginScreen(ScreenRoute.USER_ADMIN);
        activeUserManageMode = null;
        content.removeAllViews();
        setHeader("用户管理", "超管工具");
        View landing = new UserAdminView(
                this, userAdminViewActions(() -> selectTab("me"))).buildLanding();
        content.addView(landing);
    }

    private void showUserManagementList(UserAdminView.Mode mode) {
        beginScreen(ScreenRoute.USER_ADMIN);
        activeUserManageMode = mode;
        content.removeAllViews();
        setHeader(mode == UserAdminView.Mode.COINS ? "修改用户余额" : "权限与删除",
                mode == UserAdminView.Mode.COINS
                        ? "只调整青程币余额" : "调整角色或删除用户");
        UserAdminView view = new UserAdminView(
                this, userAdminViewActions(this::showUserManagement));
        UserAdminView.Result page = view.buildList(mode);
        content.addView(page.root());
        String authToken = sessionStore.getToken();
        loadList("user-admin-list", authToken, () -> apiClient.users(authToken),
                users -> view.render(page.list(), users, mode));
    }

    private void editNickname() {
        String authToken = sessionStore.getToken();
        ScreenGeneration.ScreenToken origin = currentScreen;
        JSONObject user = sessionStore.getUser();
        String nickname = user == null ? "" : user.optString("nickname", "");
        new NicknameEditorDialog(this, nickname, new NicknameEditorDialog.Actions() {
            @Override
            public void save(String nickname) {
                if (!screenGeneration.isCurrent(origin)) return;
                runTask(authToken, () -> apiClient.updateNickname(nickname, authToken), updated -> {
                    if (!sessionStore.saveUserIfToken(authToken, updated)) return;
                    refreshProfileAndWallet(authToken);
                    if (screenGeneration.isCurrent(origin)) selectTab("me");
                }, "昵称已修改，扣除 10 青程币");
            }

            @Override
            public void invalid(String message) {
                Ui.toast(MainActivity.this, message);
            }
        }).show();
    }

    private void bindPushManually() {
        String authToken = sessionStore.getToken();
        ScreenGeneration.ScreenToken origin = currentScreen;
        if (!isCurrentSession(authToken)) return;
        ProgressLeases.Lease lease = progressLeases.open();
        PushManager.bindNow(this, sessionStore, apiClient, new PushManager.BindingCallback() {
            @Override public void onResult(boolean success, String message, String registrationId) {
                lease.close();
                if (!isCurrentSession(authToken) || !screenGeneration.isCurrent(origin)) return;
                if (success) {
                    Ui.toast(MainActivity.this, message);
                    selectTab("me");
                } else {
                    CommonDialogs.pushBindingFailure(MainActivity.this, message);
                }
            }

            @Override public void onSessionExpired(String message) {
                lease.close();
                // The coordinator has atomically cleared only its captured account on this thread.
                if (!activityDestroyed && !isFinishing() && !isDestroyed() && !sessionStore.isLoggedIn()) {
                    forceLogoutToLogin(message);
                }
            }

            @Override public void onCancelled() { lease.close(); }
        });
    }

    private void editPassword() {
        String authToken = sessionStore.getToken();
        ScreenGeneration.ScreenToken origin = currentScreen;
        new PasswordEditorDialog(this, new PasswordEditorDialog.Actions() {
            @Override
            public void save(PasswordEditorDialog.Value value) {
                if (!screenGeneration.isCurrent(origin)) return;
                runTask(authToken, () -> {
                    apiClient.updatePassword(value.oldPassword(), value.newPassword(), authToken);
                    return null;
                }, ignored -> logoutToLogin(), "密码已修改，请重新登录");
            }

            @Override
            public void invalid(String message) {
                Ui.toast(MainActivity.this, message);
            }
        }).show();
    }

    private void checkForUpdate() {
        if (BuildConfig.COUPLE_LOCAL) {
            Ui.toast(this, "当前为本地联调版");
            return;
        }
        requestVersionCheck(true);
    }

    private void checkForUpdateSilently() {
        if (automaticUpdateCheckStarted) {
            return;
        }
        automaticUpdateCheckStarted = true;
        requestVersionCheck(false);
    }

    private void requestVersionCheck(boolean manual) {
        requestVersionCheck(manual, () -> apiClient.appVersion());
    }

    private void requestVersionCheck(boolean manual, TaskCall<JSONObject> fetchVersion) {
        ProgressLeases.Lease lease = manual ? progressLeases.open() : null;
        executeTask(() -> {
            if (activityDestroyed || isFinishing() || isDestroyed()) return;
            JSONObject version = fetchVersion.call();
            int latestCode = version.optInt("versionCode", APP_VERSION_CODE);
            String latestName = version.optString("versionName", APP_VERSION_NAME);
            String notes = version.optString("notes", "");
            String url = version.optString("apkUrl",
                    ApiClient.BASE_URL + "/uploads/apk/qingcheng-ordering-release.apk");
            boolean forceUpdate = version.optBoolean("forceUpdate", false);
            main.post(() -> {
                if (activityDestroyed || isFinishing() || isDestroyed()) return;
                if (latestCode > APP_VERSION_CODE) {
                    if (!isTrustedApkUrl(url)) {
                        if (manual) {
                            Ui.toast(this, "更新地址不安全，请联系管理员");
                        }
                        return;
                    }
                    CommonDialogs.updateAvailable(
                            this, latestName, notes, forceUpdate, () -> openUrl(url));
                } else if (manual) {
                    CommonDialogs.latestVersion(this, APP_VERSION_NAME);
                }
            });
        }, e -> {
            if (manual) {
                main.post(() -> {
                    if (activityDestroyed || isFinishing() || isDestroyed()) return;
                    Ui.toast(this, e.getMessage() == null ? "版本检测失败" : e.getMessage());
                });
            }
        }, () -> {
            if (lease != null) {
                main.post(lease::close);
            }
        });
    }

    static boolean isTrustedApkUrl(String url) {
        try {
            URI uri = URI.create(url == null ? "" : url.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "example.invalid".equals(uri.getHost())
                    && uri.getPort() == -1
                    && uri.getRawUserInfo() == null
                    && uri.getPath() != null
                    && ("/uploads/apk/qingcheng-ordering-release.apk".equals(uri.getPath())
                    || "/uploads/apk/qingcheng-ordering-debug.apk".equals(uri.getPath()));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void openUrl(String url) {
        if (!isTrustedApkUrl(url)) {
            Ui.toast(this, "更新地址不安全，请联系管理员");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Ui.toast(this, "无法打开下载地址");
        }
    }

    private UserAdminView.Actions userAdminViewActions(Runnable back) {
        return new UserAdminView.Actions() {
            @Override
            public void bindClick(View view, Runnable action) {
                MainActivity.this.bindClick(view, ignored -> action.run());
            }

            @Override
            public void back() {
                back.run();
            }

            @Override
            public void openCoins() {
                showUserManagementList(UserAdminView.Mode.COINS);
            }

            @Override
            public void openRoleDelete() {
                showUserManagementList(UserAdminView.Mode.ROLE_DELETE);
            }

            @Override
            public void editCoins(UserAdminView.User user) {
                showUserCoinsDialog(user);
            }

            @Override
            public void editRole(UserAdminView.User user) {
                showUserRoleDialog(user);
            }

            @Override
            public void delete(UserAdminView.User user) {
                confirmUserDelete(user);
            }
        };
    }

    private void showUserRoleDialog(UserAdminView.User user) {
        UserAdminContext context = captureUserAdminContext();
        new UserRoleDialog(this, selectedRole -> {
            if (!isCurrentUserAdminTarget(context)) {
                return;
            }
            updateManagedUserRole(context, user, selectedRole);
        }).show();
    }

    private void showUserCoinsDialog(UserAdminView.User user) {
        UserAdminContext context = captureUserAdminContext();
        new UserCoinsDialog(this, user.coins(), coins -> {
            if (!isCurrentUserAdminTarget(context)) {
                return;
            }
            updateManagedUserCoins(context, user, coins);
        }).show();
    }

    private void confirmUserDelete(UserAdminView.User user) {
        UserAdminContext context = captureUserAdminContext();
        new UserDeleteDialog(this, user.id(), user.username(), ignoredUserId -> {
            if (!isCurrentUserAdminTarget(context)) {
                return;
            }
            deleteManagedUser(context, user);
        }).show();
    }

    private UserAdminContext captureUserAdminContext() {
        ScreenGeneration.ScreenToken screen = currentScreen;
        LinearLayout targetContent = content;
        UserAdminView.Mode mode = activeUserManageMode;
        String authToken = sessionStore.getToken();
        if (screen == null || targetContent == null || mode == null || authToken == null) {
            return null;
        }
        return new UserAdminContext(screen, targetContent, mode, authToken);
    }

    private void updateManagedUserRole(
            UserAdminContext context,
            UserAdminView.User user,
            String selectedRole
    ) {
        runUserAdminMutation(context, user.id(), () ->
                        apiClient.updateUserRole(user.id(), selectedRole, context.authToken()),
                "权限已更新", () -> reloadUserManagementIfCurrent(context));
    }

    private void updateManagedUserCoins(
            UserAdminContext context,
            UserAdminView.User user,
            String coins
    ) {
        runUserAdminMutation(context, user.id(), () ->
                        apiClient.updateUserCoins(user.id(), coins, context.authToken()),
                "青程币已更新", () -> reloadUserManagementIfCurrent(context));
    }

    private void deleteManagedUser(UserAdminContext context, UserAdminView.User user) {
        runUserAdminMutation(context, user.id(), () ->
                        apiClient.deleteUser(user.id(), context.authToken()),
                "用户已删除", () -> reloadUserManagementIfCurrent(context));
    }

    private void runUserAdminMutation(
            UserAdminContext context,
            long targetUserId,
            UserAdminCall call,
            String successMessage,
            Runnable onSuccess
    ) {
        if (!isCurrentUserAdminTarget(context)) {
            return;
        }
        UserAdminMutationCoordinator.Mutation mutation =
                userAdminMutations.begin(targetUserId);
        if (mutation == null) {
            Ui.toast(this, "请等待该用户的当前操作完成");
            return;
        }
        ScreenGeneration.RequestToken request = screenGeneration.request(
                context.screen(), "user-admin-mutation");
        ProgressLeases.Lease lease = progressLeases.open();
        executeTask(() -> {
            if (!isCurrentUserAdminMutationTarget(context)) {
                return;
            }
            call.call();
            main.post(() -> presentationGate.present(request,
                    () -> userAdminMutations.isCurrent(mutation)
                            && isCurrentUserAdminTarget(context),
                    () -> {
                        onSuccess.run();
                        Ui.toast(this, successMessage);
                    }));
        }, e -> {
            main.post(() -> {
                if (context.authToken().equals(sessionStore.getToken()) && handleUnauthorized(e)) {
                    return;
                }
                presentationGate.present(request,
                        () -> userAdminMutations.isCurrent(mutation)
                                && isCurrentUserAdminTarget(context),
                        () -> Ui.toast(this, e.getMessage() == null
                                ? "操作失败" : e.getMessage()));
            });
        }, () -> {
            main.post(() -> {
                userAdminMutations.finish(mutation);
                lease.close();
            });
        });
    }

    private void reloadUserManagementIfCurrent(UserAdminContext context) {
        presentationGate.present(context.screen(),
                () -> isCurrentUserAdminTarget(context),
                () -> showUserManagementList(context.mode()));
    }

    private boolean isCurrentUserAdminTarget(UserAdminContext context) {
        return context != null
                && screenGeneration.isCurrent(context.screen())
                && context.screen().route() == ScreenRoute.USER_ADMIN
                && context.content() == content
                && context.mode() == activeUserManageMode
                && context.authToken().equals(sessionStore.getToken())
                && isContentTargetAvailable(context.content());
    }

    private boolean isCurrentUserAdminMutationTarget(UserAdminContext context) {
        return context != null
                && screenGeneration.isCurrent(context.screen())
                && context.screen().route() == ScreenRoute.USER_ADMIN
                && context.authToken().equals(sessionStore.getToken());
    }

    private void refreshProfileAndWallet() {
        refreshProfileAndWallet(sessionStore.getToken());
    }

    private WalletService walletService() {
        if (walletService == null && apiClient != null) walletService = new WalletService(apiClient);
        return walletService;
    }

    private FriendService friendService() {
        if (friendService == null && apiClient != null) friendService = new FriendService(apiClient);
        return friendService;
    }

    private WeatherService weatherService() {
        if (weatherService == null && apiClient != null) weatherService = new WeatherService(apiClient);
        return weatherService;
    }

    private void refreshProfileAndWallet(String authToken) {
        // Mutations may finish on a worker; capture UI state and sequence only on the main thread.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> refreshProfileAndWallet(authToken));
            return;
        }
        if (!isCurrentSession(authToken)) return;
        long refreshSequence = ++profileRefreshSequence;
        long walletSequence = ++walletRefreshSequence;
        walletRequest = null;
        walletScreenState.useScope(authToken, sessionStore.role());
        if (currentScreen != null && currentScreen.route() == ScreenRoute.WALLET && walletView != null) {
            walletScreenState.loading();
            walletView.render(walletScreenState.snapshot());
        }
        TextView targetSubtitle = subtitle;
        TextView targetWallet = walletText;
        executeTask(() -> {
            if (!isCurrentSession(authToken)) return;
            JSONObject user = apiClient.me(authToken);
            JSONObject wallet = apiClient.wallet(authToken);
            WalletView.State preparedWallet = WalletView.State.from(wallet);
            main.post(() -> commitProfileAndWallet(authToken, refreshSequence,
                    targetSubtitle, targetWallet, user, wallet, walletSequence, preparedWallet));
        }, failure -> {
            if (failure instanceof ApiClient.UnauthorizedException e) {
                main.post(() -> {
                    if (isCurrentSession(authToken)) {
                        handleUnauthorized(e);
                    }
                });
            }
            main.post(() -> {
                if (!isCurrentSession(authToken) || refreshSequence != profileRefreshSequence
                        || walletSequence != walletRefreshSequence) return;
                walletScreenState.failed();
                if (currentScreen != null && currentScreen.route() == ScreenRoute.WALLET
                        && walletView != null && isContentTargetAvailable(content)) {
                    walletView.render(walletScreenState.snapshot());
                }
            });
            // Background profile refresh is best effort; keep the last confirmed profile.
        });
    }

    private void commitProfileAndWallet(
            String authToken, long refreshSequence, TextView targetSubtitle, TextView targetWallet,
            JSONObject user, JSONObject wallet, long walletSequence, WalletView.State preparedWallet
    ) {
        if (!isCurrentSession(authToken) || refreshSequence != profileRefreshSequence) return;
        boolean previousManager = canManageItems();
        String previousRole = sessionStore.role();
        if (!sessionStore.saveUserIfToken(authToken, user)) return;
        boolean roleChanged = !previousRole.equals(sessionStore.role());
        if (roleChanged) {
            primaryViewportState.clear();
            walletScreenState.clear();
            homePendingOrderCount = 0L;
            orderScreenState.clear();
            if (currentScreen != null && (currentScreen.route() == ScreenRoute.ORDERS
                    || currentScreen.route() == ScreenRoute.ORDER_QUEUE)
                    && isContentTargetAvailable(content)) {
                showOrders();
            }
        }
        if (previousManager != canManageItems()) {
            catalogState.clear();
            if (currentScreen != null && currentScreen.route() == ScreenRoute.SHOP
                    && isContentTargetAvailable(content)) {
                showShop();
            }
        }
        if (walletSequence == walletRefreshSequence) {
            walletCoins = wallet.optString("qingchengCoins", "0");
            checkedInToday = wallet.optBoolean("checkedInToday", false);
            walletScreenState.useScope(authToken, sessionStore.role());
            walletScreenState.loaded(preparedWallet);
            walletRequest = null;
            if (currentScreen != null && currentScreen.route() == ScreenRoute.WALLET
                    && walletView != null && isContentTargetAvailable(content)) {
                if (roleChanged) {
                    showWallet();
                } else {
                    int scrollY = primaryViewportRestorer == null ? 0 : primaryViewportRestorer.captureY();
                    walletView.render(walletScreenState.snapshot());
                    restorePrimaryViewport(scrollY);
                }
            }
        } else if (roleChanged && currentScreen != null && currentScreen.route() == ScreenRoute.WALLET
                && isContentTargetAvailable(content)) {
            showWallet();
        }
        if (isCurrentShellTarget(targetSubtitle, targetWallet)) {
            // Screen headers belong to their route, not this cross-screen background refresh.
            if (isViewAvailable(targetWallet)) {
                targetWallet.setText("青程币 " + walletCoins);
            }
        }
        // Profile data belongs to the session, not the screen that started the request.
        refreshHomeStatus();
        if (roleChanged && currentScreen != null && currentScreen.route() == ScreenRoute.HOME) {
            refreshHomePendingOrders();
        }
    }

    private boolean isCurrentShellTarget(TextView targetSubtitle, TextView targetWallet) {
        return !activityDestroyed && !isFinishing() && !isDestroyed() && sessionStore != null
                && sessionStore.isLoggedIn()
                && targetSubtitle == subtitle
                && targetWallet == walletText;
    }

    private void logoutToLogin() {
        PushManager.unregisterFromBackend(this);
        resetToLogin();
    }

    private void resetToLogin() {
        testDeveloperToken = "";
        testDeveloperProfile = null;
        testWorkspaceBusy = false;
        handlingUnauthorized = false;
        taskRunner.invalidate();
        walletCoordinator.invalidate();
        imageLoader.clear();
        sessionStore.clear();
        showLogin();
    }

    private void forceLogoutToLogin(String message) {
        if (handlingUnauthorized) {
            return;
        }
        handlingUnauthorized = true;
        testDeveloperToken = "";
        testDeveloperProfile = null;
        testWorkspaceBusy = false;
        taskRunner.invalidate();
        walletCoordinator.invalidate();
        imageLoader.clear();
        sessionStore.clear();
        showLogin();
        Ui.toast(this, message == null || message.trim().isEmpty()
                ? "账号已在其他设备登录，请重新登录"
                : message.trim());
    }

    private boolean handleUnauthorized(Exception e) {
        if (e instanceof ApiClient.UnauthorizedException) {
            if (activityDestroyed || isFinishing() || isDestroyed()) return true;
            showProgress(false);
            forceLogoutToLogin(e.getMessage());
            return true;
        }
        return false;
    }

    private String pushBindingDetail() {
        if (!PushManager.isAvailable()) {
            return "当前安装包不支持推送";
        }
        if (!PushManager.isConfigured(this)) {
            return "当前版本推送配置缺失，请更新修复版";
        }
        String registrationId = sessionStore.pushRegistrationId();
        if (sessionStore.pushReady() && registrationId != null && !registrationId.trim().isEmpty()) {
            String trimmed = registrationId.trim();
            String suffix = trimmed.length() <= 10 ? trimmed : trimmed.substring(0, 10);
            return "已绑定 " + suffix + "，点击重新绑定";
        }
        return "尚未绑定，点击后将自动等待推送服务就绪";
    }

    private boolean isAdminRole(String role) {
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }

    private void capturePushTarget(Intent intent) {
        if (intent == null) {
            return;
        }
        handlePushTarget(intent.getStringExtra(PushManager.EXTRA_PUSH_TARGET), true);
    }

    private void handlePushTarget(String target, boolean deferIfNeeded) {
        if (target == null || target.trim().isEmpty()) {
            return;
        }
        String trimmed = target.trim();
        if ("orders".equals(trimmed) || "shopOrders".equals(trimmed)) {
            currentTab = "orders";
            if (!deferIfNeeded && content != null && sessionStore != null && sessionStore.isLoggedIn()) {
                selectTab("orders");
            }
            return;
        }
        if ("friends".equals(trimmed) || trimmed.startsWith("friend:")) {
            pendingPushTarget = trimmed;
            currentTab = "ai";
            if (!deferIfNeeded && content != null && sessionStore != null && sessionStore.isLoggedIn()) {
                selectTab("ai");
                openPendingPushTarget();
            }
            return;
        }
        if ("ai".equals(trimmed) || trimmed.startsWith("ai:")) {
            pendingPushTarget = trimmed;
            currentTab = "ai";
            if (!deferIfNeeded && content != null && sessionStore != null && sessionStore.isLoggedIn()) {
                selectTab("ai");
                openPendingPushTarget();
            }
        }
    }

    private void handlePushArrived(String target) {
        if (target == null
                || target.trim().isEmpty()
                || sessionStore == null
                || !sessionStore.isLoggedIn()) {
            return;
        }
        String trimmed = target.trim();
        if ("friends".equals(trimmed) || trimmed.startsWith("friend:")) {
            ScreenGeneration.ScreenToken screen = currentScreen;
            refreshFriendBadge();
            if (screen != null && screen.route() == ScreenRoute.FRIEND_CENTER) {
                loadFriendCenter();
            }
            if (trimmed.startsWith("friend:")
                    && screen != null
                    && screen.route() == ScreenRoute.FRIEND_CHAT
                    && activeFriendId != null) {
                long userId = parseLongSafe(trimmed.substring("friend:".length()));
                if (userId > 0 && activeFriendId.equals(userId)) {
                    loadFriendMessages();
                    return;
                }
            }
            Ui.toast(this, "收到新的好友消息");
        }
    }

    private void openPendingPushTarget() {
        if (pendingPushTarget == null || pendingPushTarget.trim().isEmpty()) {
            return;
        }
        String target = pendingPushTarget.trim();
        pendingPushTarget = "";
        if ("friends".equals(target)) {
            showFriendCenter();
            return;
        }
        if (target.startsWith("friend:")) {
            long userId = parseLongSafe(target.substring("friend:".length()));
            if (userId > 0) {
                showFriendCenter();
                openFriendFromPush(userId);
            } else {
                showFriendCenter();
            }
            return;
        }
        if ("ai".equals(target)) {
            showAiChat();
            return;
        }
        if (target.startsWith("ai:")) {
            long conversationId = parseLongSafe(target.substring("ai:".length()));
            if (conversationId > 0) {
                loadAiConversation(conversationId);
            } else {
                showAiHistory();
            }
        }
    }

    private void openFriendFromPush(long userId) {
        ScreenGeneration.ScreenToken origin = currentScreen;
        LinearLayout targetContent = content;
        LinearLayout targetFriends = friendListView;
        if (origin == null
                || origin.route() != ScreenRoute.FRIEND_CENTER
                || targetContent == null
                || targetFriends == null) {
            return;
        }
        ScreenGeneration.RequestToken request = screenGeneration.request(origin, "friend-push-open");
        FriendSessionGuard.Session friendSession = captureFriendSession();
        if (!friendSession.authenticated()) {
            return;
        }
        executeTask(() -> {
            JSONObject data = friendService().center(friendSession.authToken());
            String displayName = "";
            JSONArray friends = data.optJSONArray("friends");
            if (friends != null) {
                for (int i = 0; i < friends.length(); i++) {
                    JSONObject friend = friends.optJSONObject(i);
                    JSONObject user = friend == null ? null : friend.optJSONObject("user");
                    if (user != null && user.optLong("id") == userId) {
                        displayName = friendDisplayName(user);
                        break;
                    }
                }
            }
            String name = displayName == null || displayName.trim().isEmpty() ? "好友" : displayName;
            main.post(() -> openFriendChatFromPushIfCurrent(
                    request, origin, friendSession,
                    targetContent, targetFriends, userId, name));
        }, e -> {
            main.post(() -> {
                if (handleFriendUnauthorized(e, friendSession)) {
                    return;
                }
                showFriendPushErrorIfCurrent(
                        request, origin, friendSession, targetContent, targetFriends, e);
            });
        });
    }

    private void openFriendChatFromPushIfCurrent(
            ScreenGeneration.RequestToken request,
            ScreenGeneration.ScreenToken origin,
            FriendSessionGuard.Session friendSession,
            LinearLayout targetContent,
            LinearLayout targetFriends,
            long userId,
            String name
    ) {
        presentationGate.present(request,
                () -> isCurrentFriendSession(friendSession)
                        && origin.route() == ScreenRoute.FRIEND_CENTER
                        && targetContent == content
                        && targetFriends == friendListView
                        && isContentTargetAvailable(targetContent)
                        && isViewAvailable(targetFriends),
                () -> showFriendChat(userId, name));
    }

    private void showFriendPushErrorIfCurrent(
            ScreenGeneration.RequestToken request,
            ScreenGeneration.ScreenToken origin,
            FriendSessionGuard.Session friendSession,
            LinearLayout targetContent,
            LinearLayout targetFriends,
            Exception error
    ) {
        presentationGate.present(request,
                () -> isCurrentFriendSession(friendSession)
                        && origin.route() == ScreenRoute.FRIEND_CENTER
                        && targetContent == content
                        && targetFriends == friendListView
                        && isContentTargetAvailable(targetContent)
                        && isViewAvailable(targetFriends),
                () -> Ui.toast(this, error.getMessage() == null
                        ? "好友消息加载失败" : error.getMessage()));
    }

    private void refreshFriendBadge() {
        if (sessionStore == null || !sessionStore.isLoggedIn()) {
            return;
        }
        FriendSessionGuard.Session friendSession = captureFriendSession();
        if (!friendSession.authenticated()) {
            return;
        }
        FriendUnreadCoordinator.Request request = friendUnreadCoordinator.request();
        executeTask(() -> {
            JSONObject data = friendService().center(friendSession.authToken());
            long unread = data.optLong("unreadCount", 0);
            main.post(() -> {
                if (isCurrentFriendSession(friendSession)) {
                    commitFriendUnread(request, unread);
                }
            });
        }, ignored -> {
            // Passive unread refresh is best effort; do not interrupt the current user action.
        });
    }

    private FriendSessionGuard.Session captureFriendSession() {
        return friendSessionGuard.capture(sessionStore == null ? "" : sessionStore.getToken());
    }

    private boolean isCurrentFriendSession(FriendSessionGuard.Session friendSession) {
        return friendSessionGuard.isCurrent(friendSession,
                sessionStore == null ? "" : sessionStore.getToken());
    }

    private boolean handleFriendUnauthorized(
            Exception error,
            FriendSessionGuard.Session friendSession
    ) {
        if (!(error instanceof ApiClient.UnauthorizedException)) {
            return false;
        }
        if (isCurrentFriendSession(friendSession)) {
            handleUnauthorized(error);
        }
        return true;
    }

    private void updateFriendUnreadUi() {
        refreshHomeStatus();
        if (friendUnreadSummary != null) {
            friendUnreadSummary.setText(friendUnreadText());
            friendUnreadSummary.setTextColor(friendUnreadCount > 0 ? Ui.RED : Ui.MUTED);
        }
        if (friendSmartBadge != null) {
            friendSmartBadge.setText(unreadFriendBadgeText());
            friendSmartBadge.setVisibility(friendUnreadCount > 0 ? View.VISIBLE : View.GONE);
            friendSmartBadge.setTextColor(Ui.RED);
            friendSmartBadge.setBackground(Ui.round(RefreshUi.BLUSH, Ui.dp(this, 16)));
        }
        refreshNavSelection();
    }

    private String friendUnreadText() {
        return friendUnreadCount > 0 ? "有 " + friendUnreadCount + " 条未读好友消息" : "暂无未读好友消息";
    }

    private void registerPushArrivedReceiver() {
        if (pushReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(PushManager.ACTION_PUSH_ARRIVED);
        ContextCompat.registerReceiver(this, pushArrivedReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        pushReceiverRegistered = true;
    }

    private void unregisterPushArrivedReceiver() {
        if (!pushReceiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(pushArrivedReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        pushReceiverRegistered = false;
    }

    private long parseLongSafe(String value) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void setHeader(String text, String sub) {
        if (title != null) {
            title.setText(text);
        }
        if (subtitle != null) {
            subtitle.setText(BuildConfig.COUPLE_TEST ? "测试区 · " + sub : sub);
        }
    }

    private interface JsonArrayCall {
        JSONArray call() throws Exception;
    }

    private interface JsonObjectCall {
        JSONObject call(String authToken) throws Exception;
    }

    private interface JsonArrayRenderer {
        void render(JSONArray array) throws Exception;
    }

    private interface JsonObjectRenderer {
        void render(JSONObject object) throws Exception;
    }

    private interface TaskCall<T> {
        T call() throws Exception;
    }

    private interface TaskCommit<T> {
        void commit(T result) throws Exception;
    }

    private interface ErrorRenderer {
        void render(Exception error);
    }

    private interface FriendMessageCall {
        String call(String authToken) throws Exception;
    }

    private interface UserAdminCall {
        void call() throws Exception;
    }

    private ScreenGeneration.ScreenToken beginScreen(ScreenRoute route) {
        cancelAiStream();
        rememberPrimaryViewport();
        walletView = null;
        walletRequest = null;
        homeView = null;
        homeViewAuthToken = null;
        homePendingOrderValue = null;
        rememberOrdersViewport();
        dismissOrderQuantityDialog();
        ScreenTransitions.reset(content);
        if (route != ScreenRoute.SHOP) catalogContent = null;
        catalogRequest = null;
        if (catalogRefresh != null) {
            catalogRefresh.setRefreshing(false);
            catalogRefresh.setEnabled(route == ScreenRoute.SHOP);
        }
        currentScreen = screenGeneration.begin(route);
        return currentScreen;
    }

    private void cancelAiStream() {
        if (activeAiStream != null) {
            activeAiStream.cancel();
            activeAiStream = null;
        }
    }

    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if (primaryViewportRestorer != null) primaryViewportRestorer.onTouch(event.getActionMasked());
        return super.dispatchTouchEvent(event);
    }

    private void rememberPrimaryViewport() {
        if (primaryViewportScreen != null && primaryViewportScreen.equals(currentScreen)
                && sessionStore != null && primaryViewportRestorer != null
                && primaryViewportState.matchesScope(sessionStore.getToken(), sessionStore.role())) {
            primaryViewportState.remember(currentScreen.route(), primaryViewportRestorer.captureY());
        }
        primaryViewportScreen = null;
        if (primaryViewportRestorer != null) primaryViewportRestorer.cancel();
    }

    private void restorePrimaryViewport() {
        if (sessionStore == null || currentScreen == null) return;
        primaryViewportState.useScope(sessionStore.getToken(), sessionStore.role());
        restorePrimaryViewport(primaryViewportState.scrollY(currentScreen.route()));
    }

    private void restorePrimaryViewport(int scrollY) {
        ScreenGeneration.ScreenToken screen = currentScreen;
        if (screen == null || !PrimaryViewportState.supports(screen.route()) || mainScroll == null) return;
        if (primaryViewportRestorer == null) primaryViewportRestorer = new PrimaryViewportRestorer(mainScroll);
        primaryViewportScreen = screen;
        String token = sessionStore.getToken();
        String role = sessionStore.role();
        primaryViewportRestorer.restore(scrollY, primaryViewportRestorer.gestureSequence(),
                () -> screenGeneration.isCurrent(screen) && isCurrentSession(token)
                        && role.equals(sessionStore.role()));
    }

    private void clearPrimaryScreenState() {
        walletRefreshSequence++;
        primaryViewportState.clear();
        walletScreenState.clear();
        primaryViewportScreen = null;
        walletView = null;
        walletRequest = null;
        if (primaryViewportRestorer != null) primaryViewportRestorer.close();
        primaryViewportRestorer = null;
    }

    private void dismissOrderQuantityDialog() {
        if (activeOrderQuantityDialog != null) {
            activeOrderQuantityDialog.dismiss();
            activeOrderQuantityDialog = null;
        }
    }

    private void loadList(
            String slot,
            String authToken,
            JsonArrayCall call,
            JsonArrayRenderer renderer
    ) {
        loadList(slot, authToken, call, renderer, null);
    }

    private void loadList(
            String slot,
            String authToken,
            JsonArrayCall call,
            JsonArrayRenderer renderer,
            ErrorRenderer errorRenderer
    ) {
        ScreenGeneration.ScreenToken screen = currentScreen;
        LinearLayout target = content;
        if (screen == null || target == null || !isCurrentSession(authToken)) {
            return;
        }
        ScreenGeneration.RequestToken request = screenGeneration.request(screen, slot);
        ProgressLeases.Lease lease = progressLeases.open();
        executeTask(() -> {
            if (!isCurrentSession(authToken)) return;
            JSONArray data = call.call();
            main.post(() -> presentationGate.present(request,
                    () -> isCurrentSession(authToken)
                            && isContentTargetAvailable(target),
                    () -> {
                        try {
                            renderer.render(data);
                        } catch (Exception e) {
                            presentationGate.present(request,
                                    () -> isCurrentSession(authToken)
                                            && isContentTargetAvailable(target),
                                    () -> addEmpty(e.getMessage()));
                        }
                    }));
        }, e -> {
            main.post(() -> {
                if (!isCurrentSession(authToken) || handleUnauthorized(e)) return;
                presentationGate.present(request,
                        () -> isContentTargetAvailable(target),
                        () -> {
                            if (errorRenderer == null) {
                                addEmpty(e.getMessage() == null ? "加载失败" : e.getMessage());
                            } else {
                                errorRenderer.render(e);
                            }
                        });
            });
        }, () -> {
            main.post(lease::close);
        });
    }

    private void commitFriendUnread(
            FriendUnreadCoordinator.Request request,
            long unread
    ) {
        friendUnreadCoordinator.commit(request, unread, nextUnread -> {
            friendUnreadCount = nextUnread;
            updateFriendUnreadUi();
        });
    }

    private void loadObject(String slot, JsonObjectCall call, JsonObjectRenderer renderer) {
        ScreenGeneration.ScreenToken screen = currentScreen;
        LinearLayout target = content;
        String authToken = sessionStore.getToken();
        if (screen == null || target == null || !isCurrentSession(authToken)) {
            return;
        }
        ScreenGeneration.RequestToken request = screenGeneration.request(screen, slot);
        ProgressLeases.Lease lease = progressLeases.open();
        executeTask(() -> {
            if (!isCurrentSession(authToken)) return;
            JSONObject data = call.call(authToken);
            main.post(() -> presentationGate.present(request,
                    () -> isCurrentSession(authToken) && isContentTargetAvailable(target), () -> {
                try {
                    renderer.render(data);
                } catch (Exception e) {
                    presentationGate.present(request,
                            () -> isCurrentSession(authToken) && isContentTargetAvailable(target),
                            () -> addEmpty(e.getMessage()));
                }
            }));
        }, e -> {
            main.post(() -> {
                if (!isCurrentSession(authToken)) return;
                if (handleUnauthorized(e)) {
                    return;
                }
                presentationGate.present(request,
                        () -> isContentTargetAvailable(target),
                        () -> addEmpty(e.getMessage() == null ? "加载失败" : e.getMessage()));
            });
        }, () -> {
            main.post(lease::close);
        });
    }

    private boolean isContentTargetAvailable(LinearLayout target) {
        return target == content && isViewAvailable(target);
    }

    private boolean isViewAvailable(View target) {
        return !activityDestroyed && !isFinishing() && !isDestroyed()
                && target != null && target.isAttachedToWindow();
    }

    private boolean isCurrentSession(String authToken) {
        return !activityDestroyed && !isFinishing() && !isDestroyed()
                && sessionStore != null && authToken != null && !authToken.isEmpty()
                && authToken.equals(sessionStore.getToken());
    }

    private void refreshCurrentCatalog() {
        if (catalogRequest != null || currentScreen == null
                || currentScreen.route() != ScreenRoute.SHOP) {
            return;
        }
        loadCatalog(shopShowingMenu);
    }

    private void rememberCatalogViewport() {
        if (currentScreen != null && currentScreen.route() == ScreenRoute.SHOP
                && catalogContent != null && catalogContent.isAttachedToWindow()) {
            catalogState.rememberViewport(shopShowingMenu,
                    ShopView.captureViewport(mainScroll, catalogContent));
        }
    }

    private void loadCatalog(boolean menu) {
        ScreenGeneration.ScreenToken origin = currentScreen;
        LinearLayout target = catalogContent;
        String authToken = sessionStore.getToken();
        boolean manager = canManageItems();
        if (!catalogState.isScope(authToken, manager)) {
            showShop();
            return;
        }
        ScreenGeneration.RequestToken request = screenGeneration.request(
                origin, menu ? "shop-menu" : "shop-baby");
        catalogRequest = request;
        catalogRefresh.setRefreshing(true);
        executeTask(() -> {
            JSONArray items = menu
                    ? (manager ? apiClient.adminMenuItems(authToken) : apiClient.menuItems(authToken))
                    : apiClient.shopItems(authToken);
            CatalogRows.Prepared prepared = CatalogRows.prepare(items, menu, List.of(MENU_CATEGORIES));
            main.post(() -> presentationGate.present(request,
                    () -> authToken.equals(sessionStore.getToken())
                            && manager == canManageItems()
                            && target == catalogContent && isViewAvailable(target), () -> {
                        try {
                            renderCatalog(prepared, true);
                        } catch (Exception error) {
                            showCatalogError(target, error);
                        }
                    }));
        }, error -> {
            main.post(() -> presentationGate.present(request,
                    () -> authToken.equals(sessionStore.getToken())
                            && manager == canManageItems()
                            && target == catalogContent && isViewAvailable(target), () -> {
                        if (!handleUnauthorized(error)) showCatalogError(target, error);
                    }));
        }, () -> {
            main.post(() -> {
                if (request.equals(catalogRequest)) {
                    catalogRequest = null;
                    catalogRefresh.setRefreshing(false);
                }
            });
        });
    }

    private void renderCatalog(JSONArray items, boolean menu, boolean remember) throws Exception {
        renderCatalog(CatalogRows.prepare(items, menu, List.of(MENU_CATEGORIES)), remember);
    }

    private void renderCatalog(CatalogRows.Prepared prepared, boolean remember) throws Exception {
        boolean menu = prepared.menu();
        ShopView.Viewport viewport = catalogContent.getChildCount() == 0
                ? catalogState.viewport(menu) : ShopView.captureViewport(mainScroll, catalogContent);
        LinearLayout target = catalogContent;
        ScreenGeneration.ScreenToken origin = currentScreen;
        if (ShopView.updateCatalog(target, prepared, () -> {
            if (target == catalogContent && screenGeneration.isCurrent(origin)) {
                if (remember) catalogState.remember(menu, prepared);
                // The existing RecyclerView preserves its live anchor through DiffUtil updates.
                // Restoring a pre-diff snapshot here would undo a gesture made during the diff.
            }
        })) {
            return;
        }
        LinearLayout replacement = new LinearLayout(this);
        replacement.setOrientation(LinearLayout.VERTICAL);
        if (menu) shopView().renderMenu(replacement, prepared);
        else shopView().renderBabyShop(replacement, prepared);
        catalogContent.removeAllViews();
        catalogContent.addView(replacement, new LinearLayout.LayoutParams(-1, -2));
        if (remember) catalogState.remember(menu, prepared);
        ShopView.restoreViewport(mainScroll, replacement, viewport);
    }

    private void showCatalogError(LinearLayout target, Exception error) {
        String message = error.getMessage() == null ? "加载失败，请稍后重试" : error.getMessage();
        if (target.getChildCount() > 0) {
            Ui.toast(this, "刷新失败，已保留原内容：" + message);
            return;
        }
        ActivityContentView.addTopAction(this, target, "加载失败，点击重试", Ui.GREEN,
                (view, action) -> bindClick(view, ignored -> action.run()), () -> {
                    target.removeAllViews();
                    refreshCurrentCatalog();
                });
        Ui.toast(this, message);
    }

    private void executeTask(TaskSubmission.Work work, Consumer<Exception> failure, Runnable finished) {
        taskRunner.useExecutor(executor);
        AccountScope sessionScope = sessionStore == null ? new AccountScope("", 0) : sessionStore.accountScope();
        AccountScope scope = taskRunner.scope(sessionScope);
        taskRunner.submitForSession(scope,
                () -> !activityDestroyed && (sessionStore == null || sessionStore.isCurrent(sessionScope)), () -> {
            work.run();
            return null;
        }, ignored -> { }, failure, finished);
    }

    private AccountScope currentAccountScope() {
        return sessionStore == null ? null : sessionStore.accountScope();
    }

    private boolean activityIsAlive() { return !activityDestroyed; }

    private void executeTask(TaskSubmission.Work work, Consumer<Exception> failure) {
        executeTask(work, failure, () -> { });
    }

    private <T> void runTask(String authToken, TaskCall<T> call, TaskCommit<T> commit, String message) {
        if (!isCurrentSession(authToken)) return;
        ProgressLeases.Lease lease = progressLeases.open();
        executeTask(() -> {
            if (!isCurrentSession(authToken)) return;
            T result = call.call();
            main.post(() -> {
                if (!isCurrentSession(authToken)) return;
                try {
                    commit.commit(result);
                    Ui.toast(this, message);
                } catch (Exception e) {
                    Ui.toast(this, e.getMessage() == null ? "操作失败" : e.getMessage());
                }
            });
        }, e -> {
            main.post(() -> {
                if (!isCurrentSession(authToken)) return;
                if (handleUnauthorized(e)) {
                    return;
                }
                Ui.toast(this, e.getMessage() == null ? "操作失败" : e.getMessage());
            });
        }, () -> {
            main.post(lease::close);
        });
    }

    private void showProgress(boolean show) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> showProgress(show));
            return;
        }
        if (show) {
            compatibilityProgressLeases.addLast(progressLeases.open());
            return;
        }
        ProgressLeases.Lease lease = compatibilityProgressLeases.pollLast();
        if (lease != null) {
            lease.close();
        }
    }

    private void resetProgress() {
        compatibilityProgressLeases.clear();
        progressLeases.reset();
    }

    private void setProgressVisible(boolean visible) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> setProgressVisible(visible));
            return;
        }
        if (progress != null) {
            progress.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void bindClick(View view, View.OnClickListener listener) {
        Ui.click(view, listener,
                anchor -> clickBurstEffect.emit(this, shellFrame, anchor));
    }

    private void addTopAction(String label, int color, Runnable action) {
        ActivityContentView.addTopAction(
                this, content, label, color,
                (view, boundAction) -> bindClick(view, ignored -> boundAction.run()),
                action);
    }

    private boolean canManageItems() {
        if (isCoupleMember()) return true;
        String role = sessionStore.role();
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }

    private void pickImageFromDevice(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, requestCode);
    }

    private void uploadItemImage(Uri uri, ItemImagePickerContext context) {
        if (!itemImageCoordinator.isCurrent(context.upload(), sessionStore.getToken())
                || !isCurrentItemImageTarget(context)) {
            return;
        }
        context.status().setText("图片上传中...");
        executeTask(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IllegalArgumentException("无法读取图片");
                }
                String url = apiClient.uploadImage(input, displayName(uri), mimeType(uri),
                        context.editor().authToken());
                main.post(() -> presentItemImageResultIfCurrent(context, null, () -> {
                    context.imageUrl().setText(url);
                    imageLoader.load(url, context.preview());
                    context.status().setText("图片上传成功");
                    Ui.toast(this, "图片上传成功");
                }));
            }
        }, e -> {
            main.post(() -> presentItemImageResultIfCurrent(context, e, () -> {
                String message = e.getMessage() == null
                        ? "图片上传失败" : e.getMessage();
                context.status().setText(message);
                Ui.toast(this, message);
            }));
        });
    }

    private void presentItemImageResultIfCurrent(
            ItemImagePickerContext context,
            Exception error,
            Runnable render
    ) {
        presentationGate.present(context.editor().screen(),
                () -> context.editor().screen().route() == ScreenRoute.SHOP
                        && itemImageCoordinator.isCurrent(
                        context.upload(), sessionStore.getToken())
                        && isCurrentItemImageTarget(context),
                () -> {
                    itemImageCoordinator.finish(context.upload(), sessionStore.getToken());
                    if (error == null || !handleUnauthorized(error)) {
                        render.run();
                    }
                });
    }

    private boolean isCurrentItemImageTarget(ItemImagePickerContext context) {
        return context != null
                && context.origin().route() == ScreenRoute.SHOP
                && context.editor().authToken().equals(sessionStore.getToken())
                && isViewAvailable(context.imageUrl())
                && isViewAvailable(context.preview())
                && isViewAvailable(context.status());
    }

    private void downloadImage(String imageUrl) {
        String url = normalizeUrl(imageUrl);
        if (url.isEmpty()) {
            Ui.toast(this, "没有可下载的图片");
            return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("青程点菜图片");
            request.setDescription("正在保存图片");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "qingcheng-" + System.currentTimeMillis() + ".png");
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) {
                throw new IllegalStateException("下载服务不可用");
            }
            manager.enqueue(request);
            Ui.toast(this, "已开始下载");
        } catch (Exception e) {
            Ui.toast(this, e.getMessage() == null ? "下载失败" : e.getMessage());
        }
    }

    private String normalizeUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("/")) {
            return ApiClient.BASE_URL + trimmed;
        }
        return ApiClient.BASE_URL + "/" + trimmed;
    }

    private void attachAiImage(Uri uri, AiPickerContext pickerContext) {
        executeTask(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IllegalArgumentException("无法读取图片");
                }
                String type = mimeType(uri);
                byte[] bytes = readBytes(input, 5 * 1024 * 1024);
                String dataUrl = "data:" + type + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
                AiAttachment attachment = new AiAttachment(displayName(uri), dataUrl);
                main.post(() -> presentAiPickerResultIfCurrent(pickerContext, () -> {
                    aiAttachments.add(attachment);
                    renderAiAttachments();
                    Ui.toast(this, "图片已添加");
                }));
            }
        }, e -> {
            main.post(() -> presentAiPickerResultIfCurrent(pickerContext,
                    () -> Ui.toast(this, e.getMessage() == null
                            ? "添加图片失败" : e.getMessage())));
        });
    }

    private void presentAiPickerResultIfCurrent(
            AiPickerContext pickerContext,
            Runnable render
    ) {
        presentationGate.present(pickerContext.screen(),
                () -> pickerContext.screen().route() == ScreenRoute.AI_CHAT
                        && pickerContext.draftId() == aiDraftId
                        && pickerContext.attachmentList() == aiAttachmentList
                        && isViewAvailable(pickerContext.attachmentList()),
                render);
    }

    private String displayName(Uri uri) {
        String name = null;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    name = cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
        }
        if (name == null || name.trim().isEmpty()) {
            name = "qingcheng-image." + extensionForMime(mimeType(uri));
        }
        return name;
    }

    private String mimeType(Uri uri) {
        String type = getContentResolver().getType(uri);
        return type == null || type.trim().isEmpty() ? "image/png" : type;
    }

    private String extensionForMime(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "png";
        };
    }

    private byte[] readBytes(InputStream input, int maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException("图片不能超过 5MB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void confirm(String title, String message, Runnable action) {
        CommonDialogs.confirm(this, title, message, action);
    }

    private record AiPickerContext(
            ScreenGeneration.ScreenToken screen,
            long draftId,
            LinearLayout attachmentList
    ) {
    }

    private record LoginViewTarget(
            ScreenGeneration.ScreenToken screen,
            LoginView.Result view
    ) {
    }

    private record LoginRequestTarget(
            ScreenGeneration.ScreenToken screen,
            LoginView.Result view,
            LoginCoordinator.Request request
    ) {
    }

    private record ItemImagePickerContext(
            ScreenGeneration.ScreenToken origin,
            ItemImageCoordinator.Editor editor,
            ItemImageCoordinator.Upload upload,
            EditText imageUrl,
            ImageView preview,
            TextView status
    ) {
    }

    private record FriendMediaPickerContext(
            ScreenGeneration.ScreenToken screen,
            Long friendId,
            LinearLayout content,
            LinearLayout messages,
            ScrollView scroll,
            int requestCode,
            boolean customSticker,
            FriendChatMutationCoordinator.Mutation mutation
    ) {
    }

    private record ItemEditorContext(
            ScreenGeneration.ScreenToken origin,
            LinearLayout targetContent,
            ItemImageCoordinator.Editor editor,
            ItemEditorView.Result view,
            String authToken
    ) {
    }

    private record OrderQuantityContext(
            ScreenGeneration.ScreenToken origin,
            LinearLayout targetContent,
            String authToken,
            OrderQuantityDialog.Result view
    ) {
    }

    private record CheckInDialogContext(
            ScreenGeneration.ScreenToken origin,
            LinearLayout targetContent,
            CheckInDialogView.Result view,
            String authToken,
            CheckInCoordinator.DialogSession dialogSession
    ) {
    }

    private record UserAdminContext(
            ScreenGeneration.ScreenToken screen,
            LinearLayout content,
            UserAdminView.Mode mode,
            String authToken
    ) {
    }

    private void addEmpty(String value) {
        ActivityContentView.addEmpty(this, content, value);
    }

    private String text(EditText input) {
        return input.getText().toString().trim();
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

}
