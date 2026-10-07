package com.momna.modules.couple.contract;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface RelationshipSpaceContract {
    CompletionStage<List<RelationshipSpaceRef>> listForUser(String userId);

    CompletionStage<Boolean> authorize(
        RelationshipActor actor,
        String action
    );

    record RelationshipSpaceRef(String relationshipSpaceId) {}

    record RelationshipActor(
        String actorUserId,
        String relationshipSpaceId
    ) {}
}
