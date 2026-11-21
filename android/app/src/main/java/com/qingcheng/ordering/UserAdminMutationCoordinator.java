package com.qingcheng.ordering;

final class UserAdminMutationCoordinator {
    record Mutation(long targetUserId, long sequence) {
    }

    private Mutation active;
    private long nextSequence;

    synchronized Mutation begin(long targetUserId) {
        if (targetUserId <= 0 || active != null) {
            return null;
        }
        Mutation mutation = new Mutation(targetUserId, ++nextSequence);
        active = mutation;
        return mutation;
    }

    synchronized boolean isCurrent(Mutation mutation) {
        return mutation != null && mutation.equals(active);
    }

    synchronized boolean finish(Mutation mutation) {
        if (!isCurrent(mutation)) {
            return false;
        }
        active = null;
        return true;
    }

    synchronized void reset() {
        active = null;
    }
}
