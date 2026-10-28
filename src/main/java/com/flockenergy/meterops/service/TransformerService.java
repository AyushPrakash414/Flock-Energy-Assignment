package com.flockenergy.meterops.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flockenergy.meterops.client.UrjaPortalClient;
import com.flockenergy.meterops.dto.PageResponse;
import com.flockenergy.meterops.dto.TransformerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Business logic for distribution transformer listing.
 * <p>
 * Fetches all pages from the portal's {@code /portal/dts} endpoint
 * and caches the result. The portal paginates transformers in pages
 * of 20; this service transparently fetches all pages to build the
 * complete list.
 */
@Service
public class TransformerService {

    private static final Logger log = LoggerFactory.getLogger(TransformerService.class);

    private final UrjaPortalClient portalClient;
    private final ObjectMapper objectMapper;

    public TransformerService(UrjaPortalClient portalClient, ObjectMapper objectMapper) {
        this.portalClient = portalClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns all transformers fetched from the portal.
     * Cached under "transformers" cache.
     */
    @Cacheable(value = "transformers", key = "'all'")
    public List<TransformerResponse> getAllTransformers() {
        log.info("Fetching all transformers from portal (cache miss)");
        List<TransformerResponse> all = new ArrayList<>();
        int page = 1;

        while (true) {
            JsonNode root = portalClient.getTransformers(page);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || !data.isArray() || data.isEmpty()) {
                break;
            }

            List<TransformerResponse> batch = objectMapper.convertValue(
                    data, new TypeReference<>() {});
            all.addAll(batch);

            int total = root.path("total").asInt(0);
            if (all.size() >= total) {
                break;
            }
            page++;
        }

        log.info("Fetched {} transformers", all.size());
        return all;
    }

    /**
     * Paginated transformer listing.
     */
    public PageResponse<TransformerResponse> listTransformers(int page, int size) {
        return PageResponse.of(getAllTransformers(), page, size);
    }
}
