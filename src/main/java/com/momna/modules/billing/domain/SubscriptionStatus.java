package com.momna.modules.billing.domain;

public enum SubscriptionStatus {
    TRIAL, ACTIVE, GRACE_PERIOD, BILLING_RETRY,
    CANCELLED_ACTIVE_UNTIL_END, EXPIRED, REVOKED, REFUNDED, NEEDS_RECONCILIATION
}
