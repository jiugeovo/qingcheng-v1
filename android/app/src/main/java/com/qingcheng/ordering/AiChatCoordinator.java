package com.qingcheng.ordering;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class AiChatCoordinator {
    static final String SEND_SLOT = "ai-chat-send";

    record AttachmentSnapshot(String name, String dataUrl) {
        AttachmentSnapshot {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(dataUrl, "dataUrl");
        }
    }

    record Send(
            ScreenGeneration.RequestToken request,
            Long conversationId,
            String prompt,
            String authToken,
            List<AttachmentSnapshot> attachments
    ) {
        Send {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(prompt, "prompt");
            authToken = authToken == null ? "" : authToken;
            attachments = List.copyOf(Objects.requireNonNull(attachments, "attachments"));
        }
    }

    private final ScreenGeneration generations;
    private Send active;

    AiChatCoordinator(ScreenGeneration generations) {
        this.generations = Objects.requireNonNull(generations, "generations");
    }

    synchronized boolean canBegin(ScreenGeneration.ScreenToken screen) {
        return screen != null
                && screen.route() == ScreenRoute.AI_CHAT
                && generations.isCurrent(screen)
                && (active == null || !generations.isCurrent(active.request().screen()));
    }

    synchronized Send begin(
            ScreenGeneration.RequestToken request,
            Long conversationId,
            String prompt,
            String authToken,
            List<AiAttachment> attachments
    ) {
        if (!isEligible(request) || !canBegin(request.screen())) {
            return null;
        }

        active = new Send(
                request,
                conversationId,
                prompt,
                authToken,
                snapshot(attachments));
        return active;
    }

    synchronized boolean isCurrent(Send send) {
        return send != null && send.equals(active) && isEligible(send.request());
    }

    synchronized boolean finish(Send send) {
        if (send == null || !send.equals(active)) {
            return false;
        }
        active = null;
        return isEligible(send.request());
    }

    private boolean isEligible(ScreenGeneration.RequestToken request) {
        return request != null
                && request.screen().route() == ScreenRoute.AI_CHAT
                && SEND_SLOT.equals(request.slot())
                && generations.isCurrent(request);
    }

    private static List<AttachmentSnapshot> snapshot(List<AiAttachment> attachments) {
        Objects.requireNonNull(attachments, "attachments");
        List<AttachmentSnapshot> snapshots = new ArrayList<>(attachments.size());
        for (AiAttachment attachment : attachments) {
            Objects.requireNonNull(attachment, "attachment");
            snapshots.add(new AttachmentSnapshot(attachment.name, attachment.dataUrl));
        }
        return snapshots;
    }
}
