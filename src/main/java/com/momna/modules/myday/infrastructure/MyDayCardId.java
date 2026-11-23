package com.momna.modules.myday.infrastructure;

import java.io.Serializable;
import java.util.Objects;

public class MyDayCardId implements Serializable {
    public String myDayId;
    public String cardId;

    public MyDayCardId() {}

    public MyDayCardId(String myDayId, String cardId) {
        this.myDayId = myDayId;
        this.cardId = cardId;
    }

    @Override public boolean equals(Object other) {
        return other instanceof MyDayCardId id && Objects.equals(myDayId, id.myDayId) && Objects.equals(cardId, id.cardId);
    }

    @Override public int hashCode() { return Objects.hash(myDayId, cardId); }
}
