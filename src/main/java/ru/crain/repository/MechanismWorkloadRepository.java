package ru.crain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.crain.model.Crane;
import ru.crain.model.MechanismWorkload;

import java.util.List;

public interface MechanismWorkloadRepository extends JpaRepository<MechanismWorkload, Long> {

    @Query("""
            select mw
            from MechanismWorkload mw
            where mw.upload.crane = :crane
            order by mw.upload.periodEnd desc
            """)
    List<MechanismWorkload> findByCraneOrderByPeriodEndDesc(@Param("crane") Crane crane);
}
