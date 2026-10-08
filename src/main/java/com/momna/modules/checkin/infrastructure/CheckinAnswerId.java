package com.momna.modules.checkin.infrastructure;

import java.io.Serializable;

public class CheckinAnswerId implements Serializable {
    public String sessionId;
    public String itemCode;

    public CheckinAnswerId() {}
    public CheckinAnswerId(String sessionId, String itemCode) {
        this.sessionId = sessionId;
        this.itemCode = itemCode;
    }
}
