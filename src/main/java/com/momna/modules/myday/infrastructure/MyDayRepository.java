package com.momna.modules.myday.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MyDayRepository extends JpaRepository<MyDayEntity, String> {
    Optional<MyDayEntity> findByUserIdAndLocalDate(String userId, LocalDate localDate);
    Optional<MyDayEntity> findByMyDayIdAndUserId(String myDayId, String userId);
    List<MyDayEntity> findByUserIdOrderByLocalDateAsc(String userId);
}
