package com.flockenergy.meterops.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Full network hierarchy for a meter — zone through distribution transformer.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HierarchyDto(
        HierarchyNodeDto zone,
        HierarchyNodeDto circle,
        HierarchyNodeDto division,
        HierarchyNodeDto subdivision,
        HierarchyNodeDto substation,
        HierarchyNodeDto feeder,
        HierarchyNodeDto dt
) {}
