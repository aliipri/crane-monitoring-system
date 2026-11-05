package ru.crain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.crain.repository.CraneRepository;
import ru.crain.repository.UploadRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CraneMonitoringApplicationTests {

    @Autowired
    private CraneRepository craneRepository;

    @Autowired
    private UploadRepository uploadRepository;

    @Test
    void startsWithoutPreloadedFiles() {
        assertThat(craneRepository.count()).isZero();
        assertThat(uploadRepository.count()).isZero();
    }
}
