package com.flockenergy.meterops.dto;

/**
 * Distribution transformer record.
 */
public record TransformerResponse(
        String code,
        String name,
        String feederCode,
        int capacityKva
) {}
