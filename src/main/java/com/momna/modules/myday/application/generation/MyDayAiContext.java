package com.momna.modules.myday.application.generation;

import com.momna.modules.myday.application.template.MyDayTemplateKey;
import com.momna.platform.ai.AiContracts.ContextEnvelope;
import com.momna.platform.ai.AiContracts.PolicyDecision;
import com.momna.platform.ai.AiContracts.Request;
import com.momna.platform.ai.AiErrorCode;
import com.momna.platform.ai.AiGatewayException;
import com.momna.platform.ai.AiPorts;
import com.momna.platform.safety.CanonicalSafetyService;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The context and invocation policy of the My Day gateway. A generation registers its snapshot (the user,
 * for the budget, and the template fingerprint) for the duration of one call. Only the My Day purpose and
 * the My Day prompts are allowed; the privacy policy is the anonymous template key.
 */
public class MyDayAiContext implements AiPorts.ContextAccess, AiPorts.InvocationPolicyGuard {
    public static final String PURPOSE = MyDayTemplateKey.PURPOSE;

    private record Snapshot(String subjectUserId, String fingerprint) {}

    private final ConcurrentHashMap<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    public void register(String snapshotId, String subjectUserId, MyDayTemplateKey key) {
        if (snapshots.putIfAbsent(snapshotId, new Snapshot(subjectUserId, key.fingerprint())) != null) {
            throw new IllegalStateException("My Day AI snapshot is already registered");
        }
    }

    public void release(String snapshotId) {
        snapshots.remove(snapshotId);
    }

    /** Snapshots of calls still in flight; zero between calls. */
    public int activeSnapshots() {
        return snapshots.size();
    }

    @Override
    public ContextEnvelope resolve(Request request) {
        var snapshot = snapshots.get(request.contextSnapshotId());
        if (snapshot == null) throw new AiGatewayException(AiErrorCode.CONTEXT_NOT_FOUND, "My Day AI context is missing");
        return new ContextEnvelope(request.contextSnapshotId(), 1, snapshot.subjectUserId(), PURPOSE, 1, 1,
            MyDayPromptCatalog.OUTPUT_SCHEMA_VERSION, snapshot.fingerprint(), 1, snapshot.fingerprint());
    }

    @Override
    public PolicyDecision authorize(Request request, ContextEnvelope context) {
        boolean allowed = PURPOSE.equals(request.purpose()) && request.promptKey().startsWith("myday.cards.");
        return new PolicyDecision(allowed, MyDayTemplateKey.KEY_VERSION, CanonicalSafetyService.CATALOG_VERSION,
            allowed ? "MY_DAY_ANONYMOUS_TEMPLATE" : "PURPOSE_NOT_ALLOWED");
    }
}
