package com.flockenergy.meterops.dto;

/**
 * A single time-stamped consumption reading for a meter.
 */
public record ConsumptionReading(
        String timestamp,
        String kwh,
        String kvah,
        String voltR
) {}
