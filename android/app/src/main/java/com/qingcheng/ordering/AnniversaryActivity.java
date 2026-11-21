package com.qingcheng.ordering;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.List;
import java.util.concurrent.ExecutorService;

/** Native anniversary feature entry; MainActivity only launches it. */
public class AnniversaryActivity extends Activity {
    static final int RESULT_SESSION_EXPIRED = RESULT_FIRST_USER + 1;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService worker;
    private SessionStore sessionStore;
    private AnniversaryCoordinator coordinator;
    private AnniversaryView page;
    private AnniversaryEditorDialog editor;
    private AnniversaryDraft draft;
    private long editingId;
    private long deletingId;
    private AlertDialog deleteDialog;
    private String openedToken;
    private String ownerId;
    private boolean restoreEditor;
    private boolean disposed;
    private boolean savingSucceeded;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (disposed || coordinator == null) return;
            coordinator.refreshClock();
            main.postDelayed(this, 60_000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionStore = SessionStore.shared(this);
        openedToken = readAuthToken();
        ownerId = ownerId();
        if (openedToken == null || openedToken.isEmpty()) {
            setExpiredResult();
            finish();
            return;
        }
        configureWindow();
        worker = WorkerPools.fixed("anniversary", 2, 16);
        boolean sameOwner = savedInstanceState != null && ownerId.equals(savedInstanceState.getString("owner"));
        String selected = sameOwner ? savedInstanceState.getString("selected") : null;
        boolean uncertain = sameOwner && savedInstanceState.getBoolean("submissionPending");
        if (sameOwner) editingId = savedInstanceState.getLong("editingId", 0);
        if (sameOwner && savedInstanceState.containsKey("draftTitle")) {
            draft = new AnniversaryDraft(savedInstanceState.getString("draftTitle", ""),
                    savedInstanceState.getString("draftDate", ""), savedInstanceState.getString("draftNote", ""),
                    savedInstanceState.getBoolean("draftYearly", true),
                    AnniversaryType.fromResponseId(savedInstanceState.getString("draftType")));
            restoreEditor = savedInstanceState.getBoolean("editorOpen") && !uncertain;
        }
        page = new AnniversaryView(this, new AnniversaryView.Actions() {
            @Override public void onBack() { finish(); }
            @Override public void onAdd() {
                if (editingId != 0) { editingId = 0; draft = null; }
                openEditor();
            }
            @Override public void onEdit(Anniversary record) {
                if (editor != null || !coordinator.state().canAdd() || !record.canManage()) return;
                editingId = record.id();
                draft = new AnniversaryDraft(record.title(), record.date(), record.note(), record.repeatYearly(), record.type());
                openEditor();
            }
            @Override public void onDelete(Anniversary record) { confirmDelete(record); }
            @Override public void onRefresh() { coordinator.load(); }
            @Override public void onSelectDate(String date) { coordinator.selectDate(date); }
            @Override public void onChangeMonth(int delta) { coordinator.changeMonth(delta); }
            @Override public void onToday() { coordinator.selectToday(); }
        });
        setContentView(page);
        coordinator = new AnniversaryCoordinator(createService(), taskExecutor(), main::post,
                this::readAuthToken, System::currentTimeMillis, new AnniversaryCoordinator.Listener() {
            @Override public void changed(AnniversaryCoordinator.State state) {
                if (disposed || isFinishing()) return;
                page.render(state.records(), state.month(), state.selectedDate(), state.today(),
                        state.loading(), state.error(), state.staleClock());
                page.setCanAdd(state.canAdd());
                if (editor != null) {
                    editor.setBusy(state.submitting());
                    editor.setSubmissionBlocked(state.uncertainSubmission());
                    if (state.submissionError() != null) editor.showError(state.submissionError());
                }
                if (deletingId != 0 && !state.submitting() && state.submissionError() != null) {
                    deletingId = 0;
                    Ui.toast(AnniversaryActivity.this, state.submissionError());
                    // Reconcile a rejection, removed record or unknown DELETE outcome through GET.
                    coordinator.load();
                    return;
                }
                if (restoreEditor && state.canAdd()) {
                    restoreEditor = false;
                    openEditor();
                }
            }

            @Override public void created(Anniversary record) {
                if (disposed || isFinishing()) return;
                savingSucceeded = true;
                if (editor != null) editor.dismiss();
                editor = null;
                draft = null;
                editingId = 0;
                Ui.toast(AnniversaryActivity.this, "纪念日已保存");
            }

            @Override public void updated(Anniversary record) { created(record); }

            @Override public void deleted(long id) {
                deletingId = 0;
                if (!disposed && !isFinishing()) Ui.toast(AnniversaryActivity.this, "纪念日已删除");
            }

            @Override public void notice(String message) {
                if (!disposed && !isFinishing()) Ui.toast(AnniversaryActivity.this, message);
            }

            @Override public void sessionExpired() {
                if (disposed || isFinishing()) return;
                // A late response for account A must never log out newly signed-in account B.
                if (openedToken.equals(readAuthToken())) setExpiredResult();
                finish();
            }
        }, selected, uncertain);
        coordinator.load();
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(Color.rgb(250, 249, 243));
        getWindow().setNavigationBarColor(Color.rgb(250, 249, 243));
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void setExpiredResult() {
        setResult(RESULT_SESSION_EXPIRED, new Intent().putExtra(
                AnniversarySessionResult.EXTRA_FINGERPRINT,
                AnniversarySessionResult.fingerprint(openedToken)));
    }

    /** Package seams allow Activity tests to use an isolated fake instead of the configured service. */
    String readAuthToken() {
        return sessionStore.getToken();
    }

    AnniversaryCoordinator.Service createService() {
        ApiClient api = new ApiClient(sessionStore);
        return new AnniversaryCoordinator.Service() {
            @Override public List<Anniversary> list(String authToken) throws Exception {
                return api.anniversaries(authToken);
            }
            @Override public Anniversary create(AnniversaryDraft draft, String authToken) throws Exception {
                return api.createAnniversary(draft, authToken);
            }
            @Override public Anniversary update(long id, AnniversaryDraft draft, String authToken) throws Exception {
                return api.updateAnniversary(id, draft, authToken);
            }
            @Override public void delete(long id, String authToken) throws Exception {
                api.deleteAnniversary(id, authToken);
            }
        };
    }

    java.util.concurrent.Executor taskExecutor() { return worker; }

    private String ownerId() {
        org.json.JSONObject user = sessionStore.getUser();
        return user == null ? "" : String.valueOf(user.optLong("id", 0));
    }

    private void confirmDelete(Anniversary record) {
        if (disposed || isFinishing() || editor != null || deleteDialog != null
                || !coordinator.state().canAdd() || !record.canManage()) return;
        deleteDialog = new AlertDialog.Builder(this).setTitle("删除纪念日？")
                .setMessage(record.title() + "\n" + AnniversaryDates.formatDate(record.date())
                        + "\n\n删除后，所有用户都将无法再查看这个纪念日。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    deletingId = record.id();
                    if (!coordinator.delete(record.id())) {
                        deletingId = 0;
                        Ui.toast(this, "记录已变化，请刷新后再操作。");
                        coordinator.load();
                    }
                }).create();
        deleteDialog.setOnDismissListener(ignored -> deleteDialog = null);
        deleteDialog.show();
        deleteDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Ui.DANGER);
    }

    private void openEditor() {
        if (disposed || isFinishing() || editor != null || !coordinator.state().canAdd()) return;
        if (editingId != 0) {
            boolean allowed = false;
            for (Anniversary record : coordinator.state().records()) {
                if (record.id() == editingId && record.canManage()) allowed = true;
            }
            if (!allowed) {
                draft = null;
                editingId = 0;
                Ui.toast(this, "记录已删除或你没有修改权限，请刷新查看。");
                return;
            }
        }
        if (draft == null) draft = new AnniversaryDraft("", coordinator.state().selectedDate(), "", true);
        savingSucceeded = false;
        editor = new AnniversaryEditorDialog(this, draft, new AnniversaryEditorDialog.Listener() {
            @Override public void onSave(AnniversaryDraft value) {
                draft = value;
                if (!(editingId == 0 ? coordinator.submit(value) : coordinator.update(editingId, value)) && editor != null) {
                    editor.setBusy(false);
                    editor.showError("正在更新纪念日，请稍后再保存。");
                }
            }

            @Override public void onDismiss(AnniversaryDraft value) {
                if (!savingSucceeded) draft = value;
                editor = null;
                if (!disposed && coordinator != null && coordinator.state().submissionError() != null) {
                    // The user explicitly left an uncertain save; reconcile through GET, never replay POST.
                    coordinator.load();
                }
            }
        }, editingId != 0);
        editor.show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (coordinator != null) {
            coordinator.refreshClock();
            main.removeCallbacks(clockTick);
            main.postDelayed(clockTick, 60_000L);
        }
    }

    @Override protected void onPause() {
        main.removeCallbacks(clockTick);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (coordinator != null) {
            outState.putString("owner", ownerId);
            outState.putLong("editingId", editingId);
            outState.putString("selected", coordinator.state().selectedDate());
            outState.putBoolean("submissionPending", coordinator.state().submitting()
                    || coordinator.state().uncertainSubmission());
            outState.putBoolean("editorOpen", editor != null && editor.isShowing());
            AnniversaryDraft current = editor == null ? draft : editor.getDraft();
            if (current != null) {
                outState.putString("draftTitle", current.title());
                outState.putString("draftDate", current.date());
                outState.putString("draftNote", current.note());
                outState.putBoolean("draftYearly", current.repeatYearly());
                outState.putString("draftType", current.type().id());
            }
        }
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        disposed = true;
        main.removeCallbacksAndMessages(null);
        if (coordinator != null) coordinator.close();
        if (editor != null) editor.dismiss();
        if (deleteDialog != null) deleteDialog.dismiss();
        if (worker != null) worker.shutdownNow();
        super.onDestroy();
    }
}
