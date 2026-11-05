package ru.crain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.crain.model.Crane;
import ru.crain.model.Metrics;

import java.util.List;

public interface MetricsRepository extends JpaRepository<Metrics, Long> {

    @Query("select m from Metrics m where m.upload.crane = :crane order by m.upload.periodEnd asc")
    List<Metrics> findByCraneOrderByPeriodEndAsc(@Param("crane") Crane crane);
}
