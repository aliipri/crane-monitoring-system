package ru.crain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.crain.model.Crane;
import ru.crain.model.VisualInspection;

import java.util.List;

public interface VisualInspectionRepository extends JpaRepository<VisualInspection, Long> {

    @Query("select vi from VisualInspection vi where vi.crane = :crane order by vi.inspectionDate desc")
    List<VisualInspection> findLatestByCrane(@Param("crane") Crane crane, Pageable pageable);

    @Query("select vi from VisualInspection vi where vi.crane = :crane order by vi.inspectionDate desc")
    List<VisualInspection> findByCraneOrderByInspectionDateDesc(@Param("crane") Crane crane);
}
