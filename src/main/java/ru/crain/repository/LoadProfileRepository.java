package ru.crain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.crain.model.Crane;
import ru.crain.model.LoadProfile;

import java.util.List;

public interface LoadProfileRepository extends JpaRepository<LoadProfile, Long> {

    @Query("select lp from LoadProfile lp where lp.upload.crane = :crane order by lp.upload.periodEnd desc")
    List<LoadProfile> findLatestByCrane(@Param("crane") Crane crane, Pageable pageable);

    @Query("select lp from LoadProfile lp where lp.upload.crane = :crane order by lp.upload.periodEnd desc")
    List<LoadProfile> findByCraneOrderByPeriodEndDesc(@Param("crane") Crane crane);
}
