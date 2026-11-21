package com.momna.modules.myday.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MyDayCardRepository extends JpaRepository<MyDayCardEntity, MyDayCardId> {
    List<MyDayCardEntity> findByMyDayIdOrderBySlotAscPositionAsc(String myDayId);
}
