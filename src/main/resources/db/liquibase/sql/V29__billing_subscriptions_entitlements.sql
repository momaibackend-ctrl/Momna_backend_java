CREATE TABLE IF NOT EXISTS billing_products (
  internal_product_id TEXT NOT NULL,
  provider TEXT NOT NULL,
  external_product_id TEXT NOT NULL,
  product_type TEXT NOT NULL,
  entitlement_policy_id TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  effective_from TIMESTAMPTZ NOT NULL,
  effective_to TIMESTAMPTZ NULL,
  metadata_version BIGINT NOT NULL,
  PRIMARY KEY (provider, external_product_id, metadata_version)
);
CREATE INDEX IF NOT EXISTS idx_billing_products_internal ON billing_products(internal_product_id, provider, metadata_version DESC);

CREATE TABLE IF NOT EXISTS billing_entitlement_policies (
  policy_id TEXT PRIMARY KEY,
  entitlement_code TEXT NOT NULL,
  version BIGINT NOT NULL,
  grace_allowed BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS billing_provider_chain_owners (
  provider TEXT NOT NULL,
  original_transaction_id TEXT NOT NULL,
  user_id UUID NOT NULL,
  claimed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(provider, original_transaction_id)
);
CREATE INDEX IF NOT EXISTS idx_billing_chain_owner_user ON billing_provider_chain_owners(user_id);

CREATE TABLE IF NOT EXISTS billing_purchase_transactions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  provider TEXT NOT NULL,
  environment TEXT NOT NULL,
  external_transaction_id TEXT NOT NULL,
  original_transaction_id TEXT NOT NULL,
  product_id TEXT NOT NULL,
  purchased_at TIMESTAMPTZ NOT NULL,
  verified_at TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL,
  evidence_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_billing_provider_transaction UNIQUE (provider, external_transaction_id)
);
CREATE INDEX IF NOT EXISTS idx_billing_purchase_chain ON billing_purchase_transactions(provider, original_transaction_id, purchased_at DESC);
CREATE INDEX IF NOT EXISTS idx_billing_purchase_user ON billing_purchase_transactions(user_id, verified_at DESC);

CREATE TABLE IF NOT EXISTS billing_subscriptions (
  user_id UUID NOT NULL,
  provider TEXT NOT NULL,
  product_id TEXT NOT NULL,
  original_transaction_id TEXT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  current_period_start TIMESTAMPTZ NOT NULL,
  current_period_end TIMESTAMPTZ NOT NULL,
  auto_renew_enabled BOOLEAN NULL,
  status TEXT NOT NULL,
  cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
  cancelled_at TIMESTAMPTZ NULL,
  last_verified_at TIMESTAMPTZ NOT NULL,
  source_of_truth_version BIGINT NOT NULL,
  PRIMARY KEY(provider, original_transaction_id)
);
CREATE INDEX IF NOT EXISTS idx_billing_subscription_user ON billing_subscriptions(user_id, current_period_end DESC);

CREATE TABLE IF NOT EXISTS billing_entitlements (
  user_id UUID NOT NULL,
  entitlement_code TEXT NOT NULL,
  status TEXT NOT NULL,
  valid_from TIMESTAMPTZ NOT NULL,
  valid_until TIMESTAMPTZ NULL,
  source_type TEXT NOT NULL,
  source_ref TEXT NOT NULL,
  reason_code TEXT NOT NULL,
  resolved_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY(user_id, entitlement_code)
);

CREATE TABLE IF NOT EXISTS billing_provider_events (
  provider TEXT NOT NULL,
  notification_id TEXT NOT NULL,
  original_transaction_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  evidence_hash TEXT NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(provider, notification_id)
);
CREATE INDEX IF NOT EXISTS idx_billing_provider_events_chain ON billing_provider_events(provider, original_transaction_id, occurred_at DESC);

COMMENT ON TABLE billing_provider_chain_owners IS 'Immutable Momna ownership claim for a provider purchase chain; prevents silent cross-account transfer.';
COMMENT ON TABLE billing_purchase_transactions IS 'Server-verified billing facts only. Raw receipts/tokens/provider secrets are forbidden.';
COMMENT ON TABLE billing_entitlements IS 'Canonical feature-access projection. Feature modules must not gate directly on billing provider state.';
