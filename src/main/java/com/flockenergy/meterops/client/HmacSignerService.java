package com.flockenergy.meterops.client;

import com.flockenergy.meterops.exception.UpstreamPortalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Generates HMAC-SHA256 signatures required by the portal's
 * {@code /portal/export} endpoint.
 * <p>
 * The signing protocol mirrors the frontend implementation:
 * <pre>
 *   payload = method + "\n" + path + "\n" + queryString + "\n" + timestamp
 *   signature = HMAC-SHA256(signingSecret, payload)
 * </pre>
 */
@Service
public class HmacSignerService {

    private static final Logger log = LoggerFactory.getLogger(HmacSignerService.class);
    private static final String HMAC_ALGO = "HmacSHA256";

    /**
     * Result holder for a computed signature and its associated timestamp.
     */
    public record SignedRequest(String timestamp, String signature) {}

    /**
     * Signs a request to the export endpoint.
     *
     * @param method       HTTP method (e.g. "GET")
     * @param path         path component (e.g. "/portal/export")
     * @param queryString  query string without leading '?' (e.g. "page=1")
     * @param signingSecret the HMAC key obtained from {@code /portal/keys}
     * @return the timestamp and hex-encoded HMAC signature
     */
    public SignedRequest sign(String method, String path, String queryString, String signingSecret) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String payload = String.join("\n", method, path, queryString, timestamp);

            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            String signature = HexFormat.of().formatHex(hash);
            log.debug("Signed export request: timestamp={}", timestamp);
            return new SignedRequest(timestamp, signature);

        } catch (Exception e) {
            throw new UpstreamPortalException("Failed to compute HMAC signature", e);
        }
    }
}
