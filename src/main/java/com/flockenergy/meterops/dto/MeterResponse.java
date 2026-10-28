package com.flockenergy.meterops.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Complete meter record returned by both list and detail endpoints.
 * When returned in a list, {@code hierarchy} and {@code geo} are populated
 * from the cached export data.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeterResponse(
        String meterId,
        String serialNo,
        String make,
        String phaseType,
        String installStatus,
        String installType,
        String build,
        String dtCode,
        HierarchyDto hierarchy,
        GeoLocationDto geo
) {}
