package com.qingcheng.ordering;

final class CheckInCoordinator {
    record DialogSession(long id, String authToken) {
        DialogSession {
            authToken = authToken == null ? "" : authToken;
        }
    }

    record Submission(DialogSession dialog, long id) {
    }

    private DialogSession currentDialog;
    private Submission activeSubmission;
    private long generation;

    synchronized DialogSession open(String authToken) {
        currentDialog = new DialogSession(++generation, authToken);
        activeSubmission = null;
        return currentDialog;
    }

    synchronized boolean isCurrent(DialogSession dialog) {
        return dialog != null && dialog.equals(currentDialog);
    }

    synchronized Submission beginSubmission(DialogSession dialog) {
        if (!isCurrent(dialog) || activeSubmission != null) {
            return null;
        }
        activeSubmission = new Submission(dialog, ++generation);
        return activeSubmission;
    }

    synchronized boolean isCurrent(Submission submission) {
        return submission != null
                && submission.equals(activeSubmission)
                && isCurrent(submission.dialog());
    }

    synchronized boolean finish(Submission submission) {
        if (!isCurrent(submission)) {
            return false;
        }
        activeSubmission = null;
        return true;
    }

    synchronized void dismiss(DialogSession dialog) {
        if (isCurrent(dialog)) {
            currentDialog = null;
            activeSubmission = null;
        }
    }

    synchronized void invalidate() {
        generation++;
        currentDialog = null;
        activeSubmission = null;
    }
}
