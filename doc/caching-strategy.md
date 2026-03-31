# Caching Strategy

## Overview

The Petclinic REST API uses Spring's declarative caching support (`@EnableCaching`) to reduce
redundant database reads for frequently accessed, relatively stable data.

## Cache Regions

| Cache name   | Cached data                                                |
|--------------|------------------------------------------------------------|
| `vets`       | All vets, individual vet by ID                             |
| `owners`     | All owners, owner by ID, owners by last name               |
| `pets`       | All pets, individual pet by ID                             |
| `petTypes`   | All pet types, pet type by ID, pet types via pet repository|
| `specialties`| All specialties, specialty by ID                           |
| `visits`     | All visits, visit by ID, visits by pet ID                  |

The default implementation is `ConcurrentMapCacheManager` (in-process, non-distributed).

## Automatic Eviction

Cache entries are evicted automatically when entities are modified:

| Operation            | Evicted cache(s)       |
|----------------------|------------------------|
| `saveVet`            | `vets`                 |
| `deleteVet`          | `vets`                 |
| `saveOwner`          | `owners`               |
| `deleteOwner`        | `owners`               |
| `savePet`            | `pets`, `owners`       |
| `deletePet`          | `pets`                 |
| `savePetType`        | `petTypes`             |
| `deletePetType`      | `petTypes`             |
| `saveSpecialty`      | `specialties`          |
| `deleteSpecialty`    | `specialties`          |
| `saveVisit`          | `visits`               |
| `deleteVisit`        | `visits`               |

Eviction uses `allEntries = true` to clear the entire cache region on any mutation, ensuring
consistency without requiring per-entry key management.

## Scheduled Invalidation

`CacheEvictionScheduler` runs periodically to evict all caches regardless of entity changes.
This provides a safety net against stale cache entries that may arise from direct database
modifications outside the application.

The interval is controlled by the property:

```properties
petclinic.cache.evict.interval=3600000
```

Default: **3 600 000 ms (1 hour)**. Set a shorter value in test or staging environments.

## Admin Cache Management Endpoint

Administrators can trigger an immediate full cache clear without restarting the application:

```
DELETE /api/cache
```

**Authentication:** Requires `ROLE_ADMIN`.

**Response:** `204 No Content` on success.

**Example (curl):**

```bash
curl -X DELETE http://localhost:9966/petclinic/api/cache \
     -u admin:admin
```
