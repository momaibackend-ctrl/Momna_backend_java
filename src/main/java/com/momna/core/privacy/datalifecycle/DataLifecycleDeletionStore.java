package com.momna.core.privacy.datalifecycle;

import com.momna.core.privacy.PrivacyScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DataLifecycleDeletionStore {
    private final JdbcTemplate jdbc;

    public DataLifecycleDeletionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Plan createPlan(
        String subjectUserId,
        String actorUserId,
        Instant snapshotAt,
        String contractVersion,
        String idempotencyKey,
        String traceId,
        List<Action> actions
    ) {
        var existing = findPlanByIdempotencyKey(idempotencyKey);
        if (existing != null) return existing;

        var planId = UUID.randomUUID();
        jdbc.update(
            """
            insert into momna.data_lifecycle_deletion_plans(
              plan_id,subject_user_id,actor_user_id,snapshot_at,contract_version,
              idempotency_key,trace_id,created_at
            ) values (?,?,?,?,?,?,?,now())
            """,
            planId, subjectUserId, actorUserId, Timestamp.from(snapshotAt),
            contractVersion, idempotencyKey, traceId
        );

        for (int i = 0; i < actions.size(); i++) {
            var a = actions.get(i);
            var r = a.resource();
            jdbc.update(
                """
                insert into momna.data_lifecycle_deletion_plan_actions(
                  plan_id,action_index,resource_owner,resource_type,resource_id,subject_user_id,
                  privacy_scope,retention_anchor_at,valid_from,valid_until,timezone_at_event,
                  local_date_at_event,content_id,content_version,content_schema_version,
                  action,reason_code,policy_key,policy_version
                ) values (?,?,?,?,?,?,?,?,?,?,?,?,null,null,null,?,?,?,?)
                """,
                planId, i, r.owner(), r.resourceType(), r.resourceId(), r.subjectUserId(),
                r.privacyScope().name(), Timestamp.from(r.retentionAnchorAt()),
                timestamp(r.validFrom()), timestamp(r.validUntil()), r.timezoneAtEvent(),
                r.localDateAtEvent(), a.action().name(), a.reasonCode(),
                a.policyKey(), a.policyVersion()
            );
        }
        return findPlan(planId);
    }

    @Transactional(readOnly = true)
    public Plan findPlan(UUID planId) {
        var rows = jdbc.query(
            "select * from momna.data_lifecycle_deletion_plans where plan_id=?",
            (rs, rowNum) -> new Plan(
                rs.getObject("plan_id", UUID.class),
                rs.getString("subject_user_id"),
                rs.getString("actor_user_id"),
                rs.getTimestamp("snapshot_at").toInstant(),
                rs.getString("contract_version"),
                rs.getString("idempotency_key"),
                rs.getString("trace_id"),
                List.of()
            ),
            planId
        );
        if (rows.isEmpty()) return null;
        var p = rows.getFirst();
        return new Plan(
            p.planId(), p.subjectUserId(), p.actorUserId(), p.snapshotAt(),
            p.contractVersion(), p.idempotencyKey(), p.traceId(), actions(planId)
        );
    }

    @Transactional(readOnly = true)
    public Plan findPlanByIdempotencyKey(String key) {
        var ids = jdbc.query(
            "select plan_id from momna.data_lifecycle_deletion_plans where idempotency_key=?",
            (rs, rowNum) -> rs.getObject(1, UUID.class),
            key
        );
        return ids.isEmpty() ? null : findPlan(ids.getFirst());
    }

    @Transactional(readOnly = true)
    public boolean checkpointExists(
        UUID operationId,
        String owner,
        String resourceType,
        String resourceId,
        String action
    ) {
        var value = jdbc.queryForObject(
            """
            select count(*) from momna.data_lifecycle_checkpoints
            where operation_id=? and owner=? and resource_type=? and resource_id=? and action=?
            """,
            Long.class,
            operationId, owner, resourceType, resourceId, action
        );
        return value != null && value > 0;
    }

    @Transactional
    public void saveCheckpoint(
        UUID operationId,
        String owner,
        String resourceType,
        String resourceId,
        String action,
        String resultCode,
        Instant at
    ) {
        jdbc.update(
            """
            insert into momna.data_lifecycle_checkpoints(
              operation_id,owner,resource_type,resource_id,action,result_code,updated_at
            ) values (?,?,?,?,?,?,?)
            on conflict(operation_id,owner,resource_type,resource_id,action) do nothing
            """,
            operationId, owner, resourceType, resourceId, action, resultCode, Timestamp.from(at)
        );
    }

    @Transactional
    public void saveReceipt(
        UUID operationId,
        UUID planId,
        String subjectUserId,
        int appliedActionCount,
        List<Action> retained,
        Instant at
    ) {
        jdbc.update(
            """
            insert into momna.data_lifecycle_deletion_receipts(
              operation_id,plan_id,subject_user_id,completed_at,applied_action_count
            ) values (?,?,?,?,?)
            on conflict(operation_id) do nothing
            """,
            operationId, planId, subjectUserId, Timestamp.from(at), appliedActionCount
        );

        for (int i = 0; i < retained.size(); i++) {
            var a = retained.get(i);
            var r = a.resource();
            jdbc.update(
                """
                insert into momna.data_lifecycle_retained_exceptions(
                  operation_id,exception_index,resource_owner,resource_type,resource_id,
                  subject_user_id,privacy_scope,retention_anchor_at,valid_from,valid_until,
                  timezone_at_event,local_date_at_event,content_id,content_version,
                  content_schema_version,reason_code,policy_key,policy_version
                ) values (?,?,?,?,?,?,?,?,?,?,?,?,null,null,null,?,?,?)
                on conflict(operation_id,exception_index) do nothing
                """,
                operationId, i, r.owner(), r.resourceType(), r.resourceId(), r.subjectUserId(),
                r.privacyScope().name(), Timestamp.from(r.retentionAnchorAt()),
                timestamp(r.validFrom()), timestamp(r.validUntil()), r.timezoneAtEvent(),
                r.localDateAtEvent(), a.reasonCode(), a.policyKey(), a.policyVersion()
            );
        }
    }

    private List<Action> actions(UUID planId) {
        return jdbc.query(
            """
            select * from momna.data_lifecycle_deletion_plan_actions
            where plan_id=? order by action_index
            """,
            (rs, rowNum) -> new Action(
                resource(rs),
                DeletionAction.valueOf(rs.getString("action")),
                rs.getString("reason_code"),
                rs.getString("policy_key"),
                rs.getInt("policy_version")
            ),
            planId
        );
    }

    private DataLifecycleOwnerAdapter.Resource resource(ResultSet rs) throws SQLException {
        var validFrom = rs.getTimestamp("valid_from");
        var validUntil = rs.getTimestamp("valid_until");
        return new DataLifecycleOwnerAdapter.Resource(
            rs.getString("resource_owner"),
            rs.getString("resource_type"),
            rs.getString("resource_id"),
            rs.getString("subject_user_id"),
            PrivacyScope.valueOf(rs.getString("privacy_scope")),
            rs.getTimestamp("retention_anchor_at").toInstant(),
            validFrom == null ? null : validFrom.toInstant(),
            validUntil == null ? null : validUntil.toInstant(),
            rs.getString("timezone_at_event"),
            rs.getObject("local_date_at_event", LocalDate.class)
        );
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    public record Plan(
        UUID planId,
        String subjectUserId,
        String actorUserId,
        Instant snapshotAt,
        String contractVersion,
        String idempotencyKey,
        String traceId,
        List<Action> actions
    ) {}

    public record Action(
        DataLifecycleOwnerAdapter.Resource resource,
        DeletionAction action,
        String reasonCode,
        String policyKey,
        int policyVersion
    ) {}
}
