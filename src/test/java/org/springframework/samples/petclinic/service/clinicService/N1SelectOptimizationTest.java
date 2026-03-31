package org.springframework.samples.petclinic.service.clinicService;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests verifying that N+1 select problem is resolved for Owner-Pet and Vet-Specialty relationships.
 *
 * <p>The N+1 select problem occurs when loading a collection of entities (1 query) triggers
 * N additional queries to load each entity's associations individually. For example, loading
 * 10 owners would issue 1 query for owners plus 10 queries for each owner's pets = 11 queries total.
 *
 * <p>Optimizations applied:
 * <ul>
 *   <li>{@code @BatchSize(size = 10)} on {@code Owner.pets}, {@code Pet.visits}, and
 *       {@code Vet.specialties} - Hibernate batches secondary selects into groups of 10,
 *       reducing N queries to ceil(N/10) queries.</li>
 *   <li>Explicit JPQL {@code left join fetch} in {@code findAll()} queries for Owner and Vet
 *       repositories - loads associations in a single SQL JOIN, eliminating secondary selects
 *       entirely for these operations.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles({"spring-data-jpa", "hsqldb"})
class N1SelectOptimizationTest {

    @Autowired
    private ClinicService clinicService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    /**
     * Verifies that loading all owners does not trigger N additional queries for pets.
     *
     * <p>Before fix: findAll() issued 1 query for owners + N queries (one per owner) for pets.
     * With 10 owners in the test dataset, that would be 11 queries.
     *
     * <p>After fix (join fetch): a single SQL query with LEFT JOIN loads owners and their pets
     * together, so total queries should be 1 (or at most 2 due to DISTINCT deduplication).
     */
    @Test
    void findAllOwners_shouldNotTriggerNPlusOneForPets() {
        Collection<Owner> owners = clinicService.findAllOwners();

        assertThat(owners).isNotEmpty();
        // Verify all pets are accessible (not lazily loaded separately)
        owners.forEach(owner -> assertThat(owner.getPets()).isNotNull());

        long queryCount = statistics.getPrepareStatementCount();
        int ownerCount = owners.size();

        // With N+1, query count would be: 1 (owners) + N (pets per owner) = ownerCount + 1
        // With join fetch, query count should be 1 (single JOIN query)
        // We allow a small buffer for framework overhead queries
        assertThat(queryCount)
            .as("Expected at most 2 queries for findAllOwners (join fetch eliminates N+1), "
                + "but got %d queries for %d owners", queryCount, ownerCount)
            .isLessThanOrEqualTo(2);
    }

    /**
     * Verifies that loading all vets does not trigger N additional queries for specialties.
     *
     * <p>Before fix: findAll() issued 1 query for vets + N queries (one per vet) for specialties.
     * With 6 vets in the test dataset, that would be 7 queries.
     *
     * <p>After fix (join fetch): a single SQL query with LEFT JOIN loads vets and their specialties
     * together, so total queries should be 1.
     */
    @Test
    void findAllVets_shouldNotTriggerNPlusOneForSpecialties() {
        Collection<Vet> vets = clinicService.findVets();

        assertThat(vets).isNotEmpty();
        // Verify all specialties are accessible (not lazily loaded separately)
        vets.forEach(vet -> assertThat(vet.getSpecialties()).isNotNull());

        long queryCount = statistics.getPrepareStatementCount();
        int vetCount = vets.size();

        // With N+1, query count would be: 1 (vets) + N (specialties per vet) = vetCount + 1
        // With join fetch, query count should be 1 (single JOIN query)
        assertThat(queryCount)
            .as("Expected at most 2 queries for findAllVets (join fetch eliminates N+1), "
                + "but got %d queries for %d vets", queryCount, vetCount)
            .isLessThanOrEqualTo(2);
    }

    /**
     * Verifies that data correctness is preserved after the optimization.
     * Owner-Pet relationships and pet types must remain intact.
     */
    @Test
    void findAllOwners_shouldReturnCorrectOwnerPetRelationships() {
        Collection<Owner> owners = clinicService.findAllOwners();

        assertThat(owners).isNotEmpty();
        // Owner 1 (George Franklin) should have 1 pet named Leo (cat)
        Owner owner1 = owners.stream()
            .filter(o -> o.getId() != null && o.getId() == 1)
            .findFirst()
            .orElse(null);
        assertThat(owner1).isNotNull();
        assertThat(owner1.getFirstName()).isEqualTo("George");
        assertThat(owner1.getPets()).hasSize(1);
        assertThat(owner1.getPets().get(0).getName()).isEqualTo("Leo");
        assertThat(owner1.getPets().get(0).getType()).isNotNull();
        assertThat(owner1.getPets().get(0).getType().getName()).isEqualTo("cat");
    }

    /**
     * Verifies that vet specialty data is correct after the optimization.
     */
    @Test
    void findAllVets_shouldReturnCorrectVetSpecialtyRelationships() {
        Collection<Vet> vets = clinicService.findVets();

        assertThat(vets).isNotEmpty();
        // Vet 3 (Linda Douglas) should have 2 specialties: dentistry and surgery
        Vet vet3 = vets.stream()
            .filter(v -> v.getId() != null && v.getId() == 3)
            .findFirst()
            .orElse(null);
        assertThat(vet3).isNotNull();
        assertThat(vet3.getLastName()).isEqualTo("Douglas");
        assertThat(vet3.getNrOfSpecialties()).isEqualTo(2);
        assertThat(vet3.getSpecialties().get(0).getName()).isEqualTo("dentistry");
        assertThat(vet3.getSpecialties().get(1).getName()).isEqualTo("surgery");
    }
}
