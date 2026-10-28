package com.flockenergy.meterops.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flockenergy.meterops.dto.*;
import com.flockenergy.meterops.exception.UpstreamPortalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * HTTP client for the upstream Urja Meter Ops portal.
 * <p>
 * All methods handle authentication transparently via
 * {@link UrjaAuthService} and retry once on session expiration
 * (401/403 from upstream).
 */
@Service
public class UrjaPortalClient {

    private static final Logger log = LoggerFactory.getLogger(UrjaPortalClient.class);
    private static final String COOKIE_NAME = "__Secure-better-auth.session_token";

    private final RestClient restClient;
    private final UrjaAuthService authService;
    private final HmacSignerService hmacSigner;
    private final ObjectMapper objectMapper;

    public UrjaPortalClient(RestClient restClient,
                            UrjaAuthService authService,
                            HmacSignerService hmacSigner,
                            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.authService = authService;
        this.hmacSigner = hmacSigner;
        this.objectMapper = objectMapper;
    }

    // ----------------------------------------------------------------
    //  Meters search (paginated, basic fields only)
    // ----------------------------------------------------------------

    /**
     * Searches meters via the portal's internal search endpoint.
     *
     * @param query search term (meter ID or serial number); empty string lists all
     * @param page  1-indexed page number
     * @return raw JSON node containing {@code data}, {@code total}, {@code page}, {@code pageSize}
     */
    public JsonNode searchMeters(String query, int page) {
        return authenticatedGet("/portal/meters/search?q={q}&page={page}",
                Map.of("q", query, "page", String.valueOf(page)));
    }

    // ----------------------------------------------------------------
    //  Meter geo-coordinates
    // ----------------------------------------------------------------

    public GeoLocationDto getMeterGeo(String meterId) {
        JsonNode root = authenticatedGet("/portal/meters/{meterId}/geo",
                Map.of("meterId", meterId));
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return null;
        }
        Double lat = data.has("latitude") ? parseDouble(data.get("latitude")) : null;
        Double lng = data.has("longitude") ? parseDouble(data.get("longitude")) : null;
        return new GeoLocationDto(lat, lng);
    }

    // ----------------------------------------------------------------
    //  Meter consumption / energy readings
    // ----------------------------------------------------------------

    public List<ConsumptionReading> getMeterConsumption(String meterId) {
        JsonNode root = authenticatedGet("/portal/meters/{meterId}/energy",
                Map.of("meterId", meterId));
        JsonNode data = root.path("data");
        if (data.isMissingNode() || !data.isArray()) {
            return List.of();
        }
        return objectMapper.convertValue(data, new TypeReference<>() {});
    }

    // ----------------------------------------------------------------
    //  Transformers (paginated)
    // ----------------------------------------------------------------

    public JsonNode getTransformers(int page) {
        return authenticatedGet("/portal/dts?page={page}",
                Map.of("page", String.valueOf(page)));
    }

    // ----------------------------------------------------------------
    //  Signing secret for export
    // ----------------------------------------------------------------

    public String getSigningSecret() {
        JsonNode root = authenticatedGet("/portal/keys", Map.of());
        return root.path("data").path("signingSecret").asText();
    }

    // ----------------------------------------------------------------
    //  HMAC-signed export of ALL meters (full detail)
    // ----------------------------------------------------------------

    /**
     * Fetches the complete meter export — all 403 meters with full
     * hierarchy and geo coordinates — using the HMAC-signed endpoint.
     */
    public List<MeterResponse> exportAllMeters() {
        String secret = getSigningSecret();
        String queryString = "page=1";
        HmacSignerService.SignedRequest signed =
                hmacSigner.sign("GET", "/portal/export", queryString, secret);

        JsonNode root = authenticatedGetWithExtraHeaders(
                "/portal/export?page=1",
                Map.of(),
                Map.of("x-timestamp", signed.timestamp(), "x-signature", signed.signature()));

        JsonNode data = root.path("data");
        if (data.isMissingNode() || !data.isArray()) {
            throw new UpstreamPortalException("Export returned no data array");
        }

        return data.elements().hasNext()
                ? mapExportItems(data)
                : List.of();
    }

    // ----------------------------------------------------------------
    //  Internal helpers
    // ----------------------------------------------------------------

    /**
     * Performs an authenticated GET with automatic retry on 401/403.
     */
    private JsonNode authenticatedGet(String uriTemplate, Map<String, String> vars) {
        return authenticatedGetWithExtraHeaders(uriTemplate, vars, Map.of());
    }

    private JsonNode authenticatedGetWithExtraHeaders(
            String uriTemplate,
            Map<String, String> vars,
            Map<String, String> extraHeaders) {
        try {
            return doGet(uriTemplate, vars, extraHeaders, authService.getSessionToken());
        } catch (UpstreamPortalException e) {
            if (e.getUpstreamStatus() == 401 || e.getUpstreamStatus() == 403) {
                log.info("Session expired; re-authenticating");
                authService.invalidateSession();
                String newToken = authService.authenticate();
                return doGet(uriTemplate, vars, extraHeaders, newToken);
            }
            throw e;
        }
    }

    private JsonNode doGet(String uriTemplate,
                           Map<String, String> vars,
                           Map<String, String> extraHeaders,
                           String token) {
        var spec = restClient.get()
                .uri(uriTemplate, vars)
                .header("Cookie", COOKIE_NAME + "=" + token);

        extraHeaders.forEach(spec::header);

        return spec.exchange((request, response) -> {
            HttpStatusCode status = response.getStatusCode();
            if (status.value() == 401 || status.value() == 403) {
                throw new UpstreamPortalException(
                        "Upstream returned " + status.value(), status.value());
            }
            if (!status.is2xxSuccessful()) {
                throw new UpstreamPortalException(
                        "Upstream returned " + status.value(), status.value());
            }
            return objectMapper.readTree(response.getBody());
        });
    }

    private List<MeterResponse> mapExportItems(JsonNode dataArray) {
        return objectMapper.convertValue(dataArray, new TypeReference<List<Map<String, Object>>>() {})
                .stream()
                .map(this::mapToMeterResponse)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private MeterResponse mapToMeterResponse(Map<String, Object> raw) {
        HierarchyDto hierarchy = null;
        Object hierRaw = raw.get("hierarchy");
        if (hierRaw instanceof Map<?, ?> h) {
            hierarchy = mapHierarchy((Map<String, Object>) h);
        }

        GeoLocationDto geo = null;
        Object geoRaw = raw.get("geo");
        if (geoRaw instanceof Map<?, ?> g) {
            geo = new GeoLocationDto(
                    toDouble(g.get("lat")),
                    toDouble(g.get("lng"))
            );
        }

        return new MeterResponse(
                toString(raw.get("meterId")),
                toString(raw.get("serialNo")),
                toString(raw.get("make")),
                toString(raw.get("phaseType")),
                toString(raw.get("installStatus")),
                toString(raw.get("installType")),
                toString(raw.get("build")),
                toString(raw.get("dtCode")),
                hierarchy,
                geo
        );
    }

    @SuppressWarnings("unchecked")
    private HierarchyDto mapHierarchy(Map<String, Object> h) {
        return new HierarchyDto(
                mapNode((Map<String, Object>) h.get("zone")),
                mapNode((Map<String, Object>) h.get("circle")),
                mapNode((Map<String, Object>) h.get("division")),
                mapNode((Map<String, Object>) h.get("subdivision")),
                mapNode((Map<String, Object>) h.get("substation")),
                mapNode((Map<String, Object>) h.get("feeder")),
                mapNode((Map<String, Object>) h.get("dt"))
        );
    }

    private HierarchyNodeDto mapNode(Map<String, Object> node) {
        if (node == null) return null;
        return new HierarchyNodeDto(toString(node.get("name")), toString(node.get("code")));
    }

    private String toString(Object obj) {
        return obj == null ? null : obj.toString();
    }

    private Double toDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(obj.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private Double parseDouble(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isNumber()) return node.doubleValue();
        try { return Double.parseDouble(node.asText()); }
        catch (NumberFormatException e) { return null; }
    }
}
