package ru.crain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.crain.model.Crane;
import ru.crain.model.Upload;

import java.util.List;
import java.util.Optional;

public interface UploadRepository extends JpaRepository<Upload, Long> {

    List<Upload> findByCraneOrderByPeriodEndDesc(Crane crane);

    Optional<Upload> findFirstByCraneOrderByPeriodEndDesc(Crane crane);

    long countByCrane(Crane crane);
}
