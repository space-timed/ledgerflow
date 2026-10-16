package com.ledgerflow.webhook.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class WebhookSigner {

    public static final String SIGNATURE_HEADER = "X-LedgerFlow-Signature";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private WebhookSigner() {
    }

    public static String sign(String payload, String secretKey, long timestamp) {
        String dataToSign = timestamp + "." + (payload != null ? payload : "");
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] rawHmac = mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
            String hexSignature = HexFormat.of().formatHex(rawHmac);

            return "t=" + timestamp + ",v1=" + hexSignature;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC signature", e);
        }
    }

    public static boolean verify(String payload, String secretKey, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.contains("t=") || !signatureHeader.contains("v1=")) {
            return false;
        }

        try {
            String[] parts = signatureHeader.split(",");
            long timestamp = 0;
            String receivedHex = null;

            for (String part : parts) {
                if (part.startsWith("t=")) {
                    timestamp = Long.parseLong(part.substring(2));
                } else if (part.startsWith("v1=")) {
                    receivedHex = part.substring(3);
                }
            }

            if (timestamp == 0 || receivedHex == null) {
                return false;
            }

            String expectedHeader = sign(payload, secretKey, timestamp);
            String expectedHex = expectedHeader.substring(expectedHeader.indexOf("v1=") + 3);

            // Constant-time byte comparison to prevent timing side-channel attacks
            return MessageDigest.isEqual(
                    receivedHex.getBytes(StandardCharsets.UTF_8),
                    expectedHex.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }
}
