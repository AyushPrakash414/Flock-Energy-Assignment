package com.flockenergy.meterops.service;

import com.flockenergy.meterops.client.UrjaPortalClient;
import com.flockenergy.meterops.dto.*;
import com.flockenergy.meterops.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Business logic for meter listing, searching, detail retrieval,
 * and consumption data.
 * <p>
 * The service uses the HMAC-signed bulk export to build an in-memory
 * master dataset (cached via Caffeine). Individual meter detail and
 * search are served from this cache for sub-millisecond latency.
 * Consumption data is always fetched live from the portal since it
 * changes frequently.
 */
@Service
public class MeterService {

    private static final Logger log = LoggerFactory.getLogger(MeterService.class);

    private final UrjaPortalClient portalClient;

    public MeterService(UrjaPortalClient portalClient) {
        this.portalClient = portalClient;
    }

    /**
     * Returns the full list of all meters from the cached export.
     * Result is cached in the "meters-export" Caffeine cache.
     */
    @Cacheable(value = "meters-export", key = "'all'")
    public List<MeterResponse> getAllMeters() {
        log.info("Fetching full meter export from portal (cache miss)");
        return portalClient.exportAllMeters();
    }

    /**
     * Paginated, optionally searched meter listing.
     */
    public PageResponse<MeterResponse> listMeters(String search, int page, int size) {
        List<MeterResponse> all = getAllMeters();

        if (search != null && !search.isBlank()) {
            String term = search.toLowerCase(Locale.ROOT);
            all = all.stream()
                    .filter(m -> matches(m, term))
                    .toList();
        }

        return PageResponse.of(all, page, size);
    }

    /**
     * Returns the full detail for a single meter.
     *
     * @throws ResourceNotFoundException if the meter ID does not exist
     */
    public MeterResponse getMeterDetail(String meterId) {
        return getAllMeters().stream()
                .filter(m -> m.meterId().equalsIgnoreCase(meterId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Meter not found: " + meterId));
    }

    /**
     * Returns live consumption readings for a meter.
     * These are not cached because they change frequently.
     *
     * @throws ResourceNotFoundException if the meter ID does not exist
     */
    public List<ConsumptionReading> getConsumption(String meterId) {
        // Verify meter exists first
        getMeterDetail(meterId);
        return portalClient.getMeterConsumption(meterId);
    }

    /**
     * Checks if a meter matches the search term against meter ID,
     * serial number, make, or DT code.
     */
    private boolean matches(MeterResponse m, String term) {
        return containsIgnoreCase(m.meterId(), term)
                || containsIgnoreCase(m.serialNo(), term)
                || containsIgnoreCase(m.make(), term)
                || containsIgnoreCase(m.dtCode(), term);
    }

    private boolean containsIgnoreCase(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }
}
