package com.momna.modules.myday.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.core.privacy.PrivacyScope;
import com.momna.core.privacy.datalifecycle.DataLifecycleOwnerAdapter;
import com.momna.core.privacy.datalifecycle.DeletionAction;
import com.momna.modules.myday.infrastructure.MyDayRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** CORE-BE-17: My Day days are HER_PRIVATE; export, archive and deletion cover the cards and progress. */
@Component
public class MyDayDataLifecycleAdapter implements DataLifecycleOwnerAdapter {
    public static final String OWNER = "MY_DAY";
    private static final String RESOURCE_TYPE = "MY_DAY";

    private final MyDayRepository days;
    private final MyDayApplicationService views;
    private final ObjectMapper mapper;

    public MyDayDataLifecycleAdapter(MyDayRepository days, MyDayApplicationService views, ObjectMapper mapper) {
        this.days = days;
        this.views = views;
        this.mapper = mapper;
    }

    @Override public String owner() { return OWNER; }

    @Override
    @Transactional(readOnly = true)
    public List<Resource> inventory(String subjectUserId, Instant snapshotAt) {
        return days.findByUserIdOrderByLocalDateAsc(subjectUserId).stream()
            .map(day -> new Resource(OWNER, RESOURCE_TYPE, day.getMyDayId(), subjectUserId,
                PrivacyScope.HER_PRIVATE, day.getGeneratedAt(), day.getGeneratedAt(), null,
                day.getTimezoneAtGeneration(), day.getLocalDate()))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Fragment exportFragment(Resource resource, Instant snapshotAt) {
        return fragment(resource);
    }

    @Override
    @Transactional(readOnly = true)
    public Fragment archiveFragment(Resource resource, Instant snapshotAt) {
        return fragment(resource);
    }

    @Override
    @Transactional
    public ActionResult applyDeletion(Resource resource, DeletionAction action, String operationKey) {
        if (action == DeletionAction.RETAIN_REQUIRED) return new ActionResult("APPLIED", "RETAINED");
        var day = days.findByMyDayIdAndUserId(resource.resourceId(), resource.subjectUserId()).orElse(null);
        if (day == null) return new ActionResult("ALREADY_APPLIED", "DELETED");
        days.delete(day);
        return new ActionResult("APPLIED", "DELETED");
    }

    @Override
    public ActionResult restore(Resource resource, ArtifactRef artifactRef, String operationKey) {
        return new ActionResult("FAILED", "RESTORE_NOT_SUPPORTED");
    }

    private Fragment fragment(Resource resource) {
        var day = days.findByMyDayIdAndUserId(resource.resourceId(), resource.subjectUserId())
            .orElseThrow(() -> new IllegalStateException("My Day resource no longer exists"));
        try {
            return new Fragment(1, "application/json", mapper.writeValueAsBytes(views.view(day)));
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot serialise My Day export", failure);
        }
    }
}
