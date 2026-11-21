package com.momna.modules.myday.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "my_day_cards", schema = "momna")
@IdClass(MyDayCardId.class)
public class MyDayCardEntity {
    @Id
    @Column(name = "my_day_id")
    private String myDayId;

    @Id
    @Column(name = "card_id")
    private String cardId;

    @Column(nullable = false)
    private String slot;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false) private String title;
    @Column(nullable = false) private String microcopy;
    @Column(name = "what_to_do", nullable = false) private String whatToDo;
    @Column(name = "why_today", nullable = false) private String whyToday;
    @Column(name = "how_to_do", nullable = false) private String howToDo;
    @Column(name = "if_skipped", nullable = false) private String ifSkipped;
    @Column(nullable = false) private String support;

    @Column(nullable = false)
    private String status;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    protected MyDayCardEntity() {}

    public MyDayCardEntity(String myDayId, String cardId, String slot, int position, String title,
                           String microcopy, String whatToDo, String whyToday, String howToDo,
                           String ifSkipped, String support) {
        this.myDayId = myDayId;
        this.cardId = cardId;
        this.slot = slot;
        this.position = position;
        this.title = title;
        this.microcopy = microcopy;
        this.whatToDo = whatToDo;
        this.whyToday = whyToday;
        this.howToDo = howToDo;
        this.ifSkipped = ifSkipped;
        this.support = support;
        this.status = "PENDING";
    }

    public void changeStatus(String status, Instant at) {
        this.status = status;
        this.statusChangedAt = at;
    }

    public String getMyDayId() { return myDayId; }
    public String getCardId() { return cardId; }
    public String getSlot() { return slot; }
    public int getPosition() { return position; }
    public String getTitle() { return title; }
    public String getMicrocopy() { return microcopy; }
    public String getWhatToDo() { return whatToDo; }
    public String getWhyToday() { return whyToday; }
    public String getHowToDo() { return howToDo; }
    public String getIfSkipped() { return ifSkipped; }
    public String getSupport() { return support; }
    public String getStatus() { return status; }
    public Instant getStatusChangedAt() { return statusChangedAt; }
}
