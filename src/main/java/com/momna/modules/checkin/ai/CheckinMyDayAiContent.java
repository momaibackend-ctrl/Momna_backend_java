package com.momna.modules.checkin.ai;

import java.util.List;

public record CheckinMyDayAiContent(
    String headline,
    String summary,
    List<String> actions
) {}
