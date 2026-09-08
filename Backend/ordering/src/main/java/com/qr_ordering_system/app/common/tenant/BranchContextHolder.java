package com.qr_ordering_system.app.common.tenant;

public class BranchContextHolder {

    private static final ThreadLocal<Long> BRANCH_CONTEXT = new ThreadLocal<>();

    private BranchContextHolder() {
    }

    public static void setBranchId(Long branchId) {
        BRANCH_CONTEXT.set(branchId);
    }

    public static Long getBranchId() {
        return BRANCH_CONTEXT.get();
    }

    public static void clear() {
        BRANCH_CONTEXT.remove();
    }

}


