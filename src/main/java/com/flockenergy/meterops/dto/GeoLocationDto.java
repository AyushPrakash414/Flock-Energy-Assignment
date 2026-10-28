package com.flockenergy.meterops.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Geographic coordinates for a meter.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeoLocationDto(
        @JsonProperty("lat") Double latitude,
        @JsonProperty("lng") Double longitude
) {}
