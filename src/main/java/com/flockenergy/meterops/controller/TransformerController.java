package com.flockenergy.meterops.controller;

import com.flockenergy.meterops.dto.PageResponse;
import com.flockenergy.meterops.dto.TransformerResponse;
import com.flockenergy.meterops.service.TransformerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for distribution transformer data.
 */
@RestController
@RequestMapping("/api/transformers")
@Tag(name = "Transformers", description = "Distribution transformer listing")
public class TransformerController {

    private final TransformerService transformerService;

    public TransformerController(TransformerService transformerService) {
        this.transformerService = transformerService;
    }

    @GetMapping
    @Operation(
            summary = "List distribution transformers",
            description = """
                    Returns a paginated list of distribution transformers
                    with code, name, feeder code, and capacity in kVA.
                    """)
    public ResponseEntity<PageResponse<TransformerResponse>> listTransformers(
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

        return ResponseEntity.ok(transformerService.listTransformers(page, size));
    }
}
