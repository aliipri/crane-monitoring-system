package ru.crain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.crain.model.Crane;
import ru.crain.model.ForecastResult;
import ru.crain.model.Upload;

import java.util.List;
import java.util.Optional;

public interface ForecastResultRepository extends JpaRepository<ForecastResult, Long> {

    Optional<ForecastResult> findByUpload(Upload upload);

    @Query("""
            select fr
            from ForecastResult fr
            where fr.crane = :crane
              and fr.predictionSource = 'CNN_LSTM'
            order by fr.forecastDate desc
            """)
    List<ForecastResult> findLatestByCrane(@Param("crane") Crane crane, Pageable pageable);
}
