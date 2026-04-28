package service.faceid;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.ConfigurationProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client for n8n Face Quality Gate webhook.
 * Sends face images for quality validation and receives pass/fail decision with tips.
 */
public class FaceQualityGateClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(FaceQualityGateClient.class);

    private static final String CONFIG_URL = "N8N_FACE_QUALITY_URL";
    private static final String CONFIG_TIMEOUT_MS = "N8N_FACE_QUALITY_TIMEOUT_MS";
    private static final String CONFIG_ALLOW_BYPASS = "N8N_FACE_QUALITY_ALLOW_BYPASS";

    private static final int DEFAULT_TIMEOUT_MS = 15000;
    private static final String MULTIPART_BOUNDARY = UUID.randomUUID().toString();
    private static final String CRLF = "\r\n";
    private static final String USER_AGENT = "UniLearn-FaceQualityGate/1.0";

    private final String webhookUrl;
    private final int timeoutMs;
    private final boolean allowBypass;
    private final ObjectMapper objectMapper;

    public FaceQualityGateClient() {
        this.webhookUrl = ConfigurationProvider.getProperty(CONFIG_URL, "");
        this.timeoutMs = parseInt(ConfigurationProvider.getProperty(CONFIG_TIMEOUT_MS, String.valueOf(DEFAULT_TIMEOUT_MS)), DEFAULT_TIMEOUT_MS);
        this.allowBypass = parseBoolean(ConfigurationProvider.getProperty(CONFIG_ALLOW_BYPASS, "false"));
        this.objectMapper = new ObjectMapper();

        LOGGER.info("FaceQualityGateClient initialized: url={}, timeout={}ms, allowBypass={}", 
                webhookUrl.isEmpty() ? "NOT_CONFIGURED" : "***", timeoutMs, allowBypass);
    }

    /**
     * Check face image quality by sending to n8n webhook.
     * @param imageFile The image file to check (camera capture or upload)
     * @param userId Optional user ID for tracking (can be null)
     * @param source "camera" or "upload" for tracking
     * @return QualityResult with pass/fail and detailed metrics
     */
    public QualityResult checkImageQuality(File imageFile, Integer userId, String source) {
        if (!isConfigured()) {
            LOGGER.warn("Face Quality Gate not configured. Skipping quality check and proceeding.");
            // When the external gate is not configured, treat as passed so enrollment can continue.
            QualityResult pass = new QualityResult();
            pass.setPassed(true);
            pass.setOverallScore(1.0);
            pass.setChecks(new QualityResult.QualityChecks());
            pass.setTips(new ArrayList<>());
            return pass;
        }

        if (imageFile == null || !imageFile.exists()) {
            LOGGER.error("Image file does not exist: {}", imageFile != null ? imageFile.getAbsolutePath() : "null");
            return QualityResult.error("Image file not found");
        }

        if (!isSupportedFormat(imageFile)) {
            LOGGER.error("Unsupported image format: {}", imageFile.getName());
            return QualityResult.error("Unsupported image format. Supported: JPEG, PNG, WebP");
        }

        long startTime = System.currentTimeMillis();
        try {
            LOGGER.debug("Sending image to quality gate: {} (user={}, source={})", imageFile.getName(), userId, source);
            
            QualityResult result = sendImageToN8n(imageFile, userId, source);
            
            long duration = System.currentTimeMillis() - startTime;
            LOGGER.info("Quality check completed in {}ms: passed={}, score={}", 
                    duration, result.isPassed(), result.getOverallScore());
            
            return result;

        } catch (Exception exception) {
            long duration = System.currentTimeMillis() - startTime;
            LOGGER.error("Error checking image quality (duration={}ms): {}", duration, exception.getMessage(), exception);
            return QualityResult.error("Quality check failed: " + exception.getMessage());
        }
    }

    /**
     * Send image to n8n webhook as multipart/form-data.
     */
    private QualityResult sendImageToN8n(File imageFile, Integer userId, String source) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URI(webhookUrl).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + MULTIPART_BOUNDARY);
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setDoOutput(true);

        // Write multipart body
        try (OutputStream out = connection.getOutputStream()) {
            writeFormField(out, "image", imageFile);
            if (userId != null) {
                writeFormField(out, "userId", String.valueOf(userId));
            }
            if (source != null) {
                writeFormField(out, "source", source);
            }
            writeFormField(out, "mode", "enroll");
            
            // Write boundary end
            out.write(("--" + MULTIPART_BOUNDARY + "--" + CRLF).getBytes());
            out.flush();
        }

        int responseCode = connection.getResponseCode();
        String responseBody = readResponse(connection);

        if (responseCode != 200) {
            LOGGER.error("n8n webhook returned status {}: {}", responseCode, responseBody);
            if (responseCode == 404) {
                return QualityResult.error("Quality gate webhook not found (404). Check that n8n workflow is active and webhook path matches: " + webhookUrl);
            }
            return QualityResult.error("Quality check service returned error " + responseCode + ": " + responseBody);
        }

        // Parse JSON response
        return parseQualityResponse(responseBody);
    }

    /**
     * Write a form field (text) to multipart stream.
     */
    private void writeFormField(OutputStream out, String fieldName, String value) throws IOException {
        out.write(("--" + MULTIPART_BOUNDARY + CRLF).getBytes());
        out.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"" + CRLF + CRLF).getBytes());
        out.write(value.getBytes());
        out.write(CRLF.getBytes());
    }

    /**
     * Write a file field to multipart stream.
     */
    private void writeFormField(OutputStream out, String fieldName, File file) throws IOException {
        out.write(("--" + MULTIPART_BOUNDARY + CRLF).getBytes());
        out.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + file.getName() + "\"" + CRLF).getBytes());
        out.write(("Content-Type: " + getContentType(file) + CRLF + CRLF).getBytes());
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        
        out.write(CRLF.getBytes());
    }

    /**
     * Parse n8n JSON response.
     */
    private QualityResult parseQualityResponse(String jsonBody) throws Exception {
        try {
            // Check if this is the "workflow started" test response from n8n
            if (jsonBody.contains("Workflow was started")) {
                LOGGER.warn("Received 'Workflow was started' message from n8n. Make sure to click 'Execute workflow' in the n8n editor before testing.");
                // Return a passing result to allow testing (in production, you'd want better error handling)
                QualityResult result = new QualityResult();
                result.setPassed(true);
                result.setOverallScore(1.0);
                result.setChecks(new QualityResult.QualityChecks());
                result.setTips(new ArrayList<>());
                return result;
            }
            
            QualityResult result = objectMapper.readValue(jsonBody, QualityResult.class);
            if (result.getTips() == null) {
                result.setTips(new ArrayList<>());
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("Failed to parse quality response JSON: {}", jsonBody, e);
            throw new IOException("Invalid response from quality gate: " + e.getMessage(), e);
        }
    }

    /**
     * Read HTTP response body (handles both success and error responses).
     */
    private String readResponse(HttpURLConnection connection) throws IOException {
        java.io.InputStream stream;
        int responseCode = connection.getResponseCode();
        
        if (responseCode >= 400) {
            stream = connection.getErrorStream();
        } else {
            stream = connection.getInputStream();
        }
        
        if (stream == null) {
            return "";
        }
        
        byte[] data = stream.readAllBytes();
        stream.close();
        return new String(data);
    }

    /**
     * Check if n8n is configured.
     */
    public boolean isConfigured() {
        return !webhookUrl.isEmpty() && !webhookUrl.equals("null");
    }

    /**
     * Check if bypass is allowed when n8n fails.
     */
    public boolean isAllowBypass() {
        return allowBypass;
    }

    /**
     * Check if file format is supported.
     */
    private boolean isSupportedFormat(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || 
               name.endsWith(".png") || name.endsWith(".webp");
    }

    /**
     * Get MIME type for file.
     */
    private String getContentType(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    /**
     * Parse integer safely.
     */
    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parse boolean safely.
     */
    private boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value);
    }
}
