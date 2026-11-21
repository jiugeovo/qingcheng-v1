package com.qingcheng.ordering;

import java.util.Objects;

final class ItemImageCoordinator {
    record Editor(ScreenGeneration.ScreenToken screen, long editorId, String authToken) {
        Editor {
            Objects.requireNonNull(screen, "screen");
            authToken = authToken == null ? "" : authToken;
        }
    }

    record Upload(Editor editor, long uploadId) {
        Upload {
            Objects.requireNonNull(editor, "editor");
        }
    }

    private final ScreenGeneration generations;
    private Editor currentEditor;
    private Upload activeUpload;
    private long nextEditorId;
    private long nextUploadId;

    ItemImageCoordinator(ScreenGeneration generations) {
        this.generations = Objects.requireNonNull(generations, "generations");
    }

    synchronized Editor open(ScreenGeneration.ScreenToken screen, String authToken) {
        if (screen == null || screen.route() != ScreenRoute.SHOP || !generations.isCurrent(screen)) {
            return null;
        }
        currentEditor = new Editor(screen, ++nextEditorId, authToken);
        activeUpload = null;
        return currentEditor;
    }

    synchronized Upload begin(Editor editor) {
        if (!isCurrentEditor(editor)) {
            return null;
        }
        activeUpload = new Upload(editor, ++nextUploadId);
        return activeUpload;
    }

    synchronized boolean isCurrent(Upload upload, String authToken) {
        return upload != null
                && upload.equals(activeUpload)
                && isCurrentEditor(upload.editor())
                && upload.editor().authToken().equals(authToken == null ? "" : authToken);
    }

    synchronized boolean isCurrent(Editor editor, String authToken) {
        return isCurrentEditor(editor)
                && editor.authToken().equals(authToken == null ? "" : authToken);
    }

    synchronized boolean finish(Upload upload, String authToken) {
        if (!isCurrent(upload, authToken)) {
            return false;
        }
        activeUpload = null;
        return true;
    }

    synchronized boolean cancel(Upload upload) {
        if (upload == null || !upload.equals(activeUpload)) {
            return false;
        }
        activeUpload = null;
        return true;
    }

    synchronized void cancelActive(Editor editor) {
        if (isCurrentEditor(editor)) {
            activeUpload = null;
        }
    }

    synchronized void close(Editor editor) {
        if (editor != null && editor.equals(currentEditor)) {
            currentEditor = null;
            activeUpload = null;
        }
    }

    synchronized boolean hasActiveUpload(Editor editor) {
        return isCurrentEditor(editor)
                && activeUpload != null
                && editor.equals(activeUpload.editor());
    }

    private boolean isCurrentEditor(Editor editor) {
        return editor != null
                && editor.equals(currentEditor)
                && editor.screen().route() == ScreenRoute.SHOP
                && generations.isCurrent(editor.screen());
    }
}
