package com.qingcheng.ordering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Main-thread state owner. Network work is injected; no Activity, View or persistent credentials. */
final class AnniversaryCoordinator {
    interface MainDispatcher { void post(Runnable action); }
    interface TokenSource { String get(); }
    interface Clock { long now(); }
    interface Service {
        List<Anniversary> list(String authToken) throws Exception;
        Anniversary create(AnniversaryDraft draft, String authToken) throws Exception;
        default Anniversary update(long id, AnniversaryDraft draft, String authToken) throws Exception {
            throw new UnsupportedOperationException();
        }
        default void delete(long id, String authToken) throws Exception {
            throw new UnsupportedOperationException();
        }
    }

    interface Listener {
        void changed(State state);
        void created(Anniversary record);
        void sessionExpired();
        default void updated(Anniversary record) { }
        default void deleted(long id) { }
        default void notice(String message) { }
    }

    record State(List<Anniversary> records, String month, String selectedDate, String today,
                 boolean loaded, boolean loading, boolean submitting, String error,
                 String submissionError, boolean staleClock, boolean uncertainSubmission) {
        boolean canAdd() {
            return loaded && !loading && !submitting && !uncertainSubmission;
        }
    }

    private final Service service;
    private final Executor worker;
    private final MainDispatcher main;
    private final TokenSource currentToken;
    private final Clock clock;
    private final Listener listener;
    private final String authToken;
    private List<Anniversary> records = Collections.emptyList();
    private String today;
    private String dataToday;
    private String selectedDate;
    private boolean loaded;
    private boolean loading;
    private boolean submitting;
    private boolean uncertainSubmission;
    private boolean closed;
    private long generation;
    private String error;
    private String submissionError;

    AnniversaryCoordinator(Service service, Executor worker, MainDispatcher main,
                           TokenSource currentToken, Clock clock, Listener listener,
                           String selectedDate, boolean uncertainSubmission) {
        this.service = Objects.requireNonNull(service);
        this.worker = Objects.requireNonNull(worker);
        this.main = Objects.requireNonNull(main);
        this.currentToken = Objects.requireNonNull(currentToken);
        this.clock = Objects.requireNonNull(clock);
        this.listener = Objects.requireNonNull(listener);
        this.authToken = token();
        this.today = AnniversaryDates.today(clock.now());
        this.selectedDate = AnniversaryDates.isValidDate(selectedDate) ? selectedDate : today;
        this.uncertainSubmission = uncertainSubmission;
    }

    State state() {
        return new State(records, AnniversaryDates.monthOf(selectedDate), selectedDate, today,
                loaded, loading, submitting, error, submissionError,
                dataToday != null && !today.equals(dataToday), uncertainSubmission);
    }

    void load() {
        if (!active() || loading || submitting) return;
        today = AnniversaryDates.today(clock.now());
        String requestedToday = today;
        long request = ++generation;
        loading = true;
        error = null;
        publish();
        execute(request, () -> {
            try {
                if (!authToken.equals(token())) { postSessionChanged(request); return; }
                List<Anniversary> result = Collections.unmodifiableList(new ArrayList<>(service.list(authToken)));
                main.post(() -> {
                    if (!accept(request)) return;
                    today = AnniversaryDates.today(clock.now());
                    records = result;
                    loaded = true;
                    loading = false;
                    dataToday = requestedToday;
                    submissionError = null;
                    // Do not silently retry a POST whose result was lost. A GET reconciles first.
                    if (uncertainSubmission) {
                        uncertainSubmission = false;
                        listener.notice("上次操作结果未确认，已刷新列表，请检查记录后再操作。");
                    }
                    publish();
                });
            } catch (Exception failure) {
                main.post(() -> failed(request, failure, false));
            }
        });
    }

    boolean submit(AnniversaryDraft input) { return save(0, input); }

    boolean update(long id, AnniversaryDraft input) {
        return manageable(id) && save(id, input);
    }

    private boolean manageable(long id) {
        for (Anniversary record : records) if (record.id() == id) return record.canManage();
        return false;
    }

    boolean delete(long id) {
        if (!active() || !state().canAdd() || !manageable(id)) return false;
        long request = ++generation;
        submitting = true;
        submissionError = null;
        publish();
        execute(request, () -> {
            try {
                if (!authToken.equals(token())) { postSessionChanged(request); return; }
                service.delete(id, authToken);
                main.post(() -> {
                    if (!accept(request)) return;
                    List<Anniversary> remaining = new ArrayList<>();
                    for (Anniversary record : records) if (record.id() != id) remaining.add(record);
                    records = Collections.unmodifiableList(remaining);
                    submitting = false;
                    error = null;
                    submissionError = null;
                    publish();
                    listener.deleted(id);
                });
            } catch (Exception failure) {
                main.post(() -> failed(request, failure, true));
            }
        });
        return true;
    }

    private boolean save(long id, AnniversaryDraft input) {
        if (!active() || !state().canAdd() || input == null) return false;
        AnniversaryDraft draft = input.normalized();
        if (!draft.validate().isEmpty()) return false;
        today = AnniversaryDates.today(clock.now());
        String requestedToday = today;
        long request = ++generation;
        submitting = true;
        submissionError = null;
        publish();
        execute(request, () -> {
            try {
                if (!authToken.equals(token())) { postSessionChanged(request); return; }
                Anniversary result = id == 0 ? service.create(draft, authToken) : service.update(id, draft, authToken);
                // A successful HTTP transport with an empty/malformed body is still an
                // unknown mutation outcome. Never dereference it on the main thread or
                // automatically replay the POST: reconcile with a GET first.
                if (result == null || (id != 0 && result.id() != id)) {
                    throw new java.io.IOException("保存响应无效");
                }
                main.post(() -> {
                    if (!accept(request)) return;
                    today = AnniversaryDates.today(clock.now());
                    List<Anniversary> updated = new ArrayList<>();
                    for (Anniversary record : records) {
                        if (record.id() != result.id()) updated.add(record);
                    }
                    updated.add(result);
                    records = Collections.unmodifiableList(updated);
                    selectedDate = AnniversaryDates.selectionAfterCreate(result, today);
                    submitting = false;
                    error = null;
                    submissionError = null;
                    // Existing records retain their original server clock; refresh the whole list next.
                    if (dataToday == null) dataToday = requestedToday;
                    publish();
                    if (id == 0) listener.created(result); else listener.updated(result);
                    if (state().staleClock()) load();
                });
            } catch (Exception failure) {
                main.post(() -> failed(request, failure, true));
            }
        });
        return true;
    }

    void selectDate(String date) {
        if (!active() || !AnniversaryDates.isValidDate(date) || date.equals(selectedDate)) return;
        selectedDate = date;
        publish();
    }

    void changeMonth(int delta) {
        if (!active()) return;
        String month = AnniversaryDates.shiftMonth(AnniversaryDates.monthOf(selectedDate), delta);
        if (month == null) return;
        String selected = month + "-01";
        int year = Integer.parseInt(month.substring(0, 4));
        for (String day : AnniversaryDates.monthDays(month)) {
            if (day == null || !day.startsWith(month)) continue;
            boolean found = false;
            for (Anniversary record : records) {
                if (day.equals(AnniversaryDates.occurrenceInYear(record.date(), record.repeatYearly(), year))) {
                    found = true;
                    break;
                }
            }
            if (found) { selected = day; break; }
        }
        selectDate(selected);
    }

    void selectToday() {
        if (!active()) return;
        String now = AnniversaryDates.today(clock.now());
        boolean changed = !now.equals(today) || !now.equals(selectedDate);
        today = now;
        selectedDate = today;
        if (changed) publish();
        if (state().staleClock()) load();
    }

    void refreshClock() {
        if (!active()) return;
        String now = AnniversaryDates.today(clock.now());
        if (!now.equals(today)) {
            today = now;
            publish();
        }
        if (state().staleClock()) load();
    }

    void close() {
        closed = true;
        generation++;
    }

    private void execute(long request, Runnable action) {
        boolean wasSubmission = submitting;
        TaskSubmission.execute(worker, action::run,
                failure -> main.post(() -> failed(request, failure, wasSubmission)), () -> { });
    }

    private void failed(long request, Exception failure, boolean wasSubmission) {
        if (!accept(request)) return;
        loading = false;
        submitting = false;
        if (failure instanceof ApiClient.UnauthorizedException) {
            closed = true;
            generation++;
            listener.sessionExpired();
            return;
        }
        if (wasSubmission) {
            if (failure instanceof ApiClient.RequestRejectedException
                    || failure instanceof TaskSubmission.NotStartedException) {
                // The server explicitly rejected the payload; it cannot have committed this
                // mutation, so the user may correct the draft and retry safely.
                uncertainSubmission = false;
                submissionError = failure.getMessage();
            } else {
                // A timeout may follow a committed create; never automatically resubmit a
                // mutation until a GET has reconciled the account state.
                uncertainSubmission = true;
                submissionError = "操作结果未确认，请关闭对话框并刷新列表，检查记录后再试。";
            }
        } else {
            error = loaded ? "刷新失败，保留上次记录。请检查网络后重试。" : "纪念日加载失败，请检查网络或服务是否已更新，然后重试。";
        }
        publish();
    }

    private void postSessionChanged(long request) {
        main.post(() -> { if (request == generation) active(); });
    }

    private boolean accept(long request) {
        return !closed && request == generation && active();
    }

    private boolean active() {
        if (closed) return false;
        if (authToken.isEmpty() || !authToken.equals(token())) {
            closed = true;
            generation++;
            listener.sessionExpired();
            return false;
        }
        return true;
    }

    private String token() {
        String value = currentToken.get();
        return value == null ? "" : value;
    }

    private void publish() {
        if (!closed) listener.changed(state());
    }
}
