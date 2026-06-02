package org.springframework.samples.petclinic.service.clinicService;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * <p> Integration test using the 'Spring Data' profile.
 *
 * @author Michael Isvy
 * @see AbstractClinicServiceTests AbstractClinicServiceTests for more details. </p>
 */

@SpringBootTest
@ActiveProfiles({"spring-data-jpa", "hsqldb"})
@TestPropertySource(properties = {
    "spring.cache.type=none"
})
class ClinicServiceSpringDataJpaTests extends AbstractClinicServiceTests {

    @Autowired
    EntityManager entityManager;

    @Override
    void clearCache() {
        entityManager.clear();
    }
}
