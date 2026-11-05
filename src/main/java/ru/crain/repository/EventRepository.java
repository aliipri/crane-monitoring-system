package ru.crain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.crain.model.Crane;
import ru.crain.model.Event;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("select e from Event e where e.upload.crane = :crane order by e.eventTime desc")
    List<Event> findByCraneOrderByEventTimeDesc(@Param("crane") Crane crane, Pageable pageable);
}
