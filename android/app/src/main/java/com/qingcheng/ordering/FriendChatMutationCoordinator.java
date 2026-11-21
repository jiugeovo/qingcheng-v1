package com.qingcheng.ordering;

final class FriendChatMutationCoordinator {
    enum Kind {
        SEND,
        CLEAR
    }

    record Mutation(long chatGeneration, long friendId, long sequence, Kind kind) {
    }

    private long chatGeneration = -1;
    private long friendId = -1;
    private long nextSequence;
    private Mutation active;

    synchronized void openChat(long generation, long nextFriendId) {
        chatGeneration = generation;
        friendId = nextFriendId;
        active = null;
    }

    synchronized Mutation begin(long generation, long targetFriendId, Kind kind) {
        if (!owns(generation, targetFriendId) || kind == null || active != null) {
            return null;
        }
        active = new Mutation(generation, targetFriendId, ++nextSequence, kind);
        return active;
    }

    synchronized boolean isCurrent(Mutation mutation) {
        return mutation != null && mutation.equals(active);
    }

    synchronized boolean blocksLoads(long generation, long targetFriendId) {
        return owns(generation, targetFriendId)
                && active != null
                && active.kind() == Kind.CLEAR;
    }

    synchronized boolean finish(Mutation mutation) {
        if (!isCurrent(mutation)) {
            return false;
        }
        active = null;
        return true;
    }

    synchronized void invalidate() {
        chatGeneration = -1;
        friendId = -1;
        active = null;
    }

    private boolean owns(long generation, long targetFriendId) {
        return chatGeneration == generation && friendId == targetFriendId;
    }
}
