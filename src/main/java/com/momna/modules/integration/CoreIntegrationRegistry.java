package com.momna.modules.integration;

import java.util.List;
import java.util.Set;

public final class CoreIntegrationRegistry {
    private CoreIntegrationRegistry() {}

    public static final Set<String> UNRESOLVED_CONTRACT_GAPS = Set.of();

    public static final List<Spec> SPECS = List.of(
        spec(IntegrationConsumer.ONBOARDING, IntegrationDecision.KEEP,
            Set.of("MOMNA-326","MOMNA-327","MOMNA-990","MOMNA-991","MOMNA-1024"),
            "Onboarding flow and answer collection",
            Set.of("core.lifecycle","core.fields","core.flows","core.context"),
            "router and seven onboarding schemas", false),
        spec(IntegrationConsumer.CHECKIN, IntegrationDecision.KEEP,
            Set.of("MOMNA-651","MOMNA-652","MOMNA-653","MOMNA-654","MOMNA-667","MOMNA-668","MOMNA-669","MOMNA-670","MOMNA-671","MOMNA-1025"),
            "Check-in sessions, answers, weighted scoring and day signals",
            Set.of("core.flows","core.context","platform.safety","core.timeline"),
            "session and scoring schema", false),
        spec(IntegrationConsumer.CALENDAR, IntegrationDecision.KEEP,
            Set.of("MOMNA-1006","MOMNA-1009","MOMNA-1014","MOMNA-1035","MOMNA-1036","MOMNA-1037","MOMNA-1039","MOMNA-1040","MOMNA-1042","MOMNA-1043","MOMNA-1044"),
            "Cycle facts, predictions, pregnancy dating and fertility observations",
            Set.of("core.lifecycle","core.fields","core.context","modules.content","platform.notifications","platform.localization","core.timeline"),
            "Calendar ready-object and writes", false),
        spec(IntegrationConsumer.MYDAY, IntegrationDecision.KEEP,
            Set.of("MOMNA-106","MOMNA-320","MOMNA-989","MOMNA-1027"),
            "Durable user/local-day artifact and card progress",
            Set.of("core.context","core.privacy","platform.safety","platform.safety-ai","platform.localization","platform.cache","core.timeline"),
            "My Day and progress", false),
        spec(IntegrationConsumer.DIARY, IntegrationDecision.KEEP,
            Set.of("MOMNA-537","MOMNA-541","MOMNA-542","MOMNA-543","MOMNA-544","MOMNA-545","MOMNA-553","MOMNA-1026"),
            "Raw personal diary entries and assets",
            Set.of("platform.storage","core.privacy","core.lifecycle","core.timeline","core.data-lifecycle"),
            "Diary CRUD and export", false),
        spec(IntegrationConsumer.MEDICAL, IntegrationDecision.KEEP,
            Set.of("MOMNA-538","MOMNA-539","MOMNA-546","MOMNA-547","MOMNA-548","MOMNA-549","MOMNA-550","MOMNA-554","MOMNA-1026"),
            "User-confirmed structured medical facts and suggestion lifecycle",
            Set.of("core.privacy","platform.safety","platform.safety-ai","platform.storage","core.timeline","core.data-lifecycle"),
            "suggestion confirm/edit/dismiss and doctor export", false),
        spec(IntegrationConsumer.COUPLE, IntegrationDecision.KEEP,
            Set.of("MOMNA-734","MOMNA-735","MOMNA-736","MOMNA-737","MOMNA-738","MOMNA-739","MOMNA-740","MOMNA-741","MOMNA-742","MOMNA-743","MOMNA-744","MOMNA-745","MOMNA-746","MOMNA-747","MOMNA-748","MOMNA-749","MOMNA-750","MOMNA-751","MOMNA-878","MOMNA-1028"),
            "Relationship-space state and Couple/Buddy business rules",
            Set.of("core.lifecycle","core.context","core.privacy","platform.safety-ai","platform.cache","platform.storage","core.timeline"),
            "connection-scoped Couple/Buddy", false),
        spec(IntegrationConsumer.MYWORLD, IntegrationDecision.KEEP,
            Set.of("MOMNA-803","MOMNA-805","MOMNA-806","MOMNA-807","MOMNA-808","MOMNA-810","MOMNA-811","MOMNA-812","MOMNA-813","MOMNA-814","MOMNA-835","MOMNA-836","MOMNA-860","MOMNA-882","MOMNA-1034"),
            "Interests, branches, progress, scoring and state machine",
            Set.of("core.context","core.privacy","platform.safety-ai","platform.jobs","platform.cache","core.timeline"),
            "branch/progress APIs", false),
        spec(IntegrationConsumer.CONTENT, IntegrationDecision.MERGE,
            Set.of("MOMNA-198","MOMNA-201","MOMNA-204","MOMNA-207","MOMNA-870","MOMNA-898","MOMNA-899","MOMNA-900","MOMNA-1030"),
            "Deep Focus selection rules and ContentObject organization",
            Set.of("core.context","platform.localization","platform.cache"),
            "ContentObject representations", false),
        spec(IntegrationConsumer.SCANNER, IntegrationDecision.REFRAME,
            Set.of("MOMNA-874","MOMNA-1033"),
            "Assessment artifact and scanner domain decisions",
            Set.of("core.context","core.privacy","platform.safety","platform.safety-ai","platform.storage","core.timeline"),
            "first contract is Core-native", true),
        spec(IntegrationConsumer.AGENT, IntegrationDecision.REFRAME,
            Set.of("MOMNA-862","MOMNA-866","MOMNA-870","MOMNA-874","MOMNA-878","MOMNA-882","MOMNA-886","MOMNA-890","MOMNA-1033"),
            "Agent sessions, modes and orchestration",
            Set.of("core.context","core.privacy","platform.safety","platform.safety-ai","core.timeline"),
            "first contract is Core-native", true)
    );

    private static Spec spec(
        IntegrationConsumer consumer,
        IntegrationDecision decision,
        Set<String> qiraKeys,
        String domainLogicOwner,
        Set<String> canonicalDependencies,
        String clientContract,
        boolean coreNative
    ) {
        var stages = coreNative
            ? Set.of(
                IntegrationMigrationStage.INVENTORIED,
                IntegrationMigrationStage.CONTRACT_MAPPED,
                IntegrationMigrationStage.CORE_NATIVE
            )
            : Set.of(
                IntegrationMigrationStage.INVENTORIED,
                IntegrationMigrationStage.CONTRACT_MAPPED,
                IntegrationMigrationStage.PARITY_VERIFIED,
                IntegrationMigrationStage.CUTOVER
            );
        return new Spec(
            consumer, decision, qiraKeys, domainLogicOwner,
            canonicalDependencies, stages, clientContract
        );
    }

    public record Spec(
        IntegrationConsumer consumer,
        IntegrationDecision decision,
        Set<String> qiraKeys,
        String domainLogicOwner,
        Set<String> canonicalDependencies,
        Set<IntegrationMigrationStage> migrationStages,
        String clientContract
    ) {}
}
