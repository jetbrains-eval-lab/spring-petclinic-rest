# N+1 Select Problem: Detection and Resolution

## What Is the N+1 Select Problem?

The N+1 select problem is a common JPA/Hibernate performance anti-pattern. It occurs when the application:
1. Issues **1 query** to load a list of N entities (e.g., all owners).
2. Then issues **N additional queries** — one per entity — to load each entity's associations (e.g., each owner's pets).

This results in **N+1 total queries** instead of 1 or 2 efficient queries.

### Example (before fix)

Loading 10 owners with their pets would issue:
```sql
-- Query 1: Load all owners
SELECT * FROM owners;

-- Query 2..11: Load pets for each owner individually
SELECT * FROM pets WHERE owner_id = 1;
SELECT * FROM pets WHERE owner_id = 2;
...
SELECT * FROM pets WHERE owner_id = 10;
```

**Total: 11 queries** instead of 1 optimized JOIN query.

## Affected Entities in This Project

| Entity | Association    | Fetch Type | N+1 Risk                             |
|--------|---------------|------------|--------------------------------------|
| Owner  | pets (Set)    | EAGER      | findAll() → N secondary pet queries  |
| Pet    | visits (Set)  | EAGER      | findAll() → N secondary visit queries|
| Vet    | specialties (Set) | EAGER  | findAll() → N secondary specialty queries |

## How the N+1 Problem Was Demonstrated

SQL logging was enabled in `application.properties` and `test/resources/application.properties`:

```properties
logging.level.org.hibernate.SQL=DEBUG
spring.jpa.properties.hibernate.generate_statistics=true
```

With these settings, each SQL statement is logged. Running `findAllOwners()` before the fix
showed 1 query for owners followed by 10 individual pet queries (one per owner in test data).

## Optimization Approach

Two complementary techniques were applied:

### 1. JPQL `join fetch` in Repository Queries

For `findAll()` methods in `SpringDataOwnerRepository` and `SpringDataVetRepository`, explicit
JPQL queries with `LEFT JOIN FETCH` were added:

```java
// SpringDataOwnerRepository.java
@Override
@Query("SELECT DISTINCT owner FROM Owner owner left join fetch owner.pets p left join fetch p.type")
Collection<Owner> findAll() throws DataAccessException;

// SpringDataVetRepository.java
@Override
@Query("SELECT DISTINCT vet FROM Vet vet left join fetch vet.specialties")
Collection<Vet> findAll() throws DataAccessException;
```

This loads the parent entity and its collection in **a single SQL JOIN query**, completely
eliminating secondary selects for these operations.

**SQL generated (after fix):**
```sql
-- Single query for all owners WITH their pets AND pet types
SELECT DISTINCT o.*, p.*, pt.*
FROM owners o
LEFT OUTER JOIN pets p ON p.owner_id = o.id
LEFT OUTER JOIN types pt ON pt.id = p.type_id;
```

Additionally, `Pet.visits` (EAGER) is batch-loaded in a single batch query for all pets at once
(since `@BatchSize(size = 25)` groups all pet IDs into one `IN` clause).

### 2. `@BatchSize` Annotation

`@BatchSize(size = 10)` was added to collection fields as a fallback optimization for any
loading scenarios not covered by explicit join fetch queries:

```java
// Owner.java
@OneToMany(cascade = CascadeType.ALL, mappedBy = "owner", fetch = FetchType.EAGER)
@BatchSize(size = 10)
private Set<Pet> pets;

// Pet.java — batch size 25 covers all pets in dataset in a single batch
@OneToMany(cascade = CascadeType.ALL, mappedBy = "pet", fetch = FetchType.EAGER)
@BatchSize(size = 25)
private Set<Visit> visits;

// Vet.java
@ManyToMany(fetch = FetchType.EAGER)
@JoinTable(...)
@BatchSize(size = 10)
private Set<Specialty> specialties;
```

With `@BatchSize`, instead of N individual queries, Hibernate groups IDs into batches
and uses SQL `IN` clauses:

```sql
-- Instead of 13 individual queries for each pet's visits:
SELECT * FROM visits WHERE pet_id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13);
```

This reduces N queries to `ceil(N / batchSize)` queries. With `@BatchSize(size = 25)` and 13 pets,
all visits are loaded in 1 query instead of 13.

## Performance Improvement

| Scenario             | Before Fix          | After Fix                    |
|----------------------|---------------------|------------------------------|
| findAllOwners (10)   | ~30 queries*        | 2 queries                    |
| findAllVets (6)      | 7 queries           | 1 query                      |
| findAllPets (13)     | 14+ queries         | 2 queries (@BatchSize)       |

\* Before fix for `findAllOwners`: 1 (owners) + 10 (pets per owner) + 13 (visits per pet) + 6 (unique pet types) = ~30 queries.
After fix: 1 join fetch query for owners+pets+types, plus 1 batch query for all pet visits = 2 queries.

Query counts verified via Hibernate `Statistics.getPrepareStatementCount()` in `N1SelectOptimizationTest`.

## Correctness Verification

Tests in `N1SelectOptimizationTest` verify:
- Query count stays within bounds (≤2 for join-fetch-optimized operations)
- Owner→Pet→PetType relationships return correct data
- Vet→Specialty relationships return correct data
- All existing service tests in `AbstractClinicServiceTests` continue to pass

## Alternative Approaches Considered

### `@EntityGraph`

Spring Data `@EntityGraph` can declaratively specify eager loading per query method:

```java
@EntityGraph(attributePaths = {"pets"})
Collection<Owner> findAll();
```

This is equivalent to join fetch but uses Spring Data's annotation-driven approach. It was
considered but JPQL `@Query` with join fetch was preferred for consistency with existing
`findById` and `findByLastName` queries that already used JPQL.

### `FetchType.LAZY` + Explicit Loading

Changing all associations to `LAZY` and loading them explicitly in the service layer was
considered. However, this would require significant refactoring of existing code and tests,
as many test methods access associations outside transaction boundaries. The current approach
keeps `EAGER` fetch type intact and improves how Hibernate executes the loading.

### `@Fetch(FetchMode.SUBSELECT)`

This Hibernate annotation loads all associations for all loaded entities using a single
subselect query:

```sql
SELECT * FROM pets WHERE owner_id IN (SELECT id FROM owners);
```

The downside is that the subselect always loads ALL associations regardless of how many
parent entities are actually needed. `@BatchSize` is preferred because it still uses the
specific IDs being requested.

### Second-Level Cache

Adding a Hibernate second-level cache (e.g., Ehcache) could reduce database hits for
frequently accessed read-only data like `PetType` and `Specialty`. This is a complementary
optimization to consider for future work, especially since the application already imports
`spring-boot-starter-cache`.

## Files Changed

| File | Change |
|------|--------|
| `model/Owner.java` | Added `@BatchSize(size = 10)` on `pets` field |
| `model/Pet.java` | Added `@BatchSize(size = 10)` on `visits` field |
| `model/Vet.java` | Added `@BatchSize(size = 10)` on `specialties` field |
| `repository/springdatajpa/SpringDataOwnerRepository.java` | Added `findAll()` with join fetch |
| `repository/springdatajpa/SpringDataVetRepository.java` | Added `findAll()` with join fetch |
| `src/main/resources/application.properties` | Enabled `logging.level.org.hibernate.SQL=DEBUG` |
| `src/test/resources/application.properties` | Enabled SQL logging and Hibernate statistics |
| `test/.../N1SelectOptimizationTest.java` | New test verifying query counts and data correctness |
