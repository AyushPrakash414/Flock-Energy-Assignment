package com.flockenergy.meterops.controller;

import com.flockenergy.meterops.dto.ConsumptionReading;
import com.flockenergy.meterops.dto.MeterResponse;
import com.flockenergy.meterops.dto.PageResponse;
import com.flockenergy.meterops.service.MeterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for meter operations.
 * Exposes clean, documented endpoints for listing, searching,
 * detail retrieval, and consumption data.
 */
@RestController
@RequestMapping("/api/meters")
@Tag(name = "Meters", description = "Meter information, search, and consumption readings")
public class MeterController {

    private final MeterService meterService;

    public MeterController(MeterService meterService) {
        this.meterService = meterService;
    }

    @GetMapping
    @Operation(
            summary = "List or search meters",
            description = """
                    Returns a paginated list of meters with full detail
                    (hierarchy, geo, installation info). Optionally filter by
                    a search term that matches against meter ID, serial number,
                    make, or DT code.
                    """)
    public ResponseEntity<PageResponse<MeterResponse>> listMeters(
            @Parameter(description = "Search term (meter ID, serial, make, or DT code)")
            @RequestParam(required = false) String search,
            @Parameter(description = "Page number (1-indexed)")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Items per page")
            @RequestParam(defaultValue = "20") int size) {

        if (page < 1) {
            throw new IllegalArgumentException("Page must be >= 1, got: " + page);
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Size must be between 1 and 100, got: " + size);
        }

        return ResponseEntity.ok(meterService.listMeters(search, page, size));
    }

    @GetMapping("/{meterId}")
    @Operation(
            summary = "Get meter details",
            description = """
                    Returns the complete record for a single meter including
                    nameplate information, network hierarchy (zone through DT),
                    and geographic coordinates.
                    """)
    public ResponseEntity<MeterResponse> getMeterDetail(
            @Parameter(description = "Meter ID (e.g. J100002)")
            @PathVariable String meterId) {

        return ResponseEntity.ok(meterService.getMeterDetail(meterId));
    }

    @GetMapping("/{meterId}/consumption")
    @Operation(
            summary = "Get meter consumption readings",
            description = """
                    Returns time-stamped consumption readings for a meter
                    including kWh, kVAh, and voltage V(R). Data is fetched
                    live from the upstream portal (not cached).
                    """)
    public ResponseEntity<Map<String, Object>> getMeterConsumption(
            @Parameter(description = "Meter ID (e.g. J100002)")
            @PathVariable String meterId) {

        List<ConsumptionReading> readings = meterService.getConsumption(meterId);
        return ResponseEntity.ok(Map.of(
                "meterId", meterId,
                "readings", readings,
                "count", readings.size()
        ));
    }
}
