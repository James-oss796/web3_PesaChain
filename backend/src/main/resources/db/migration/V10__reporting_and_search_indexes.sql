-- Prefix-search indexes for bounded operational queues. No historical data is rewritten.
CREATE INDEX audit_log_action_prefix_idx ON audit_log (lower(action) text_pattern_ops);
CREATE INDEX audit_log_target_type_prefix_idx ON audit_log (lower(target_type) text_pattern_ops);
CREATE INDEX audit_log_target_id_idx ON audit_log (target_id, occurred_at DESC) WHERE target_id IS NOT NULL;
CREATE INDEX borrower_profiles_name_prefix_idx ON borrower_profiles (lower(full_name) text_pattern_ops);
