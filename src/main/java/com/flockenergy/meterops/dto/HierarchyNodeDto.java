package com.flockenergy.meterops.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents one level in the electricity distribution network hierarchy.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HierarchyNodeDto(String name, String code) {}
