package ru.crain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.crain.model.Crane;

import java.util.List;
import java.util.Optional;

public interface CraneRepository extends JpaRepository<Crane, Long> {

    Optional<Crane> findByInventoryNumber(String inventoryNumber);

    @Query("""
            select distinct c
            from Upload u
            join u.crane c
            order by c.model, c.inventoryNumber
            """)
    List<Crane> findAllWithUploads();
}
