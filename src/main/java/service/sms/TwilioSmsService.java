package service.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.ConfigurationProvider;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Twilio SMS Service for sending OTP codes via SMS.
 * Supports both Twilio Verify API (recommended) and direct Messaging API.
 */
public class TwilioSmsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TwilioSmsService.class);

    private static final String TWILIO_VERIFY_API_URL = "https://verify.twilio.com/v2/Services/%s/Verifications";
    private static final String TWILIO_VERIFY_CHECK_URL = "https://verify.twilio.com/v2/Services/%s/VerificationCheck";
    private static final String TWILIO_MESSAGING_API_URL = "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json";
    private static final String USER_AGENT = "UniLearn/1.0";
    private static final int HTTP_TIMEOUT = 10000; // 10 seconds

    private final String accountSid;
    private final String authToken;
    private final String verifySid;
    private final String fromNumber;
    private final boolean useVerifyApi;

    public TwilioSmsService() {
        this.accountSid = ConfigurationProvider.getProperty("TWILIO_ACCOUNT_SID", "");
        this.authToken = ConfigurationProvider.getProperty("TWILIO_AUTH_TOKEN", "");
        this.verifySid = ConfigurationProvider.getProperty("TWILIO_VERIFY_SERVICE_SID", "");
        this.fromNumber = ConfigurationProvider.getProperty("TWILIO_FROM_NUMBER", "");
        this.useVerifyApi = !verifySid.isEmpty();

        validateConfiguration();
    }

    /**
     * Send verification code via Twilio Verify API or direct SMS.
     * Returns the verification SID for Verify API or null for direct SMS.
     */
    public String sendVerificationCode(String phoneNumber, String... verificationDetails) {
        if (!isConfigured()) {
            LOGGER.warn("Twilio SMS Service not properly configured");
            return null;
        }

        if (phoneNumber == null || phoneNumber.isBlank()) {
            LOGGER.error("Phone number is required for SMS verification");
            return null;
        }

        try {
            if (useVerifyApi) {
                return sendVerificationViaVerifyApi(phoneNumber);
            } else {
                return sendVerificationViaSms(phoneNumber);
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to send verification code to {}: {}", phoneNumber, exception.getMessage(), exception);
            return null;
        }
    }

    /**
     * Verify OTP code via Twilio Verify API or app-side validation.
     */
    public boolean verifyCode(String phoneNumber, String code, String verifySid) {
        if (!isConfigured()) {
            LOGGER.warn("Twilio SMS Service not properly configured");
            return false;
        }

        if (phoneNumber == null || phoneNumber.isBlank() || code == null || code.isBlank()) {
            LOGGER.error("Phone number and verification code are required");
            return false;
        }

        try {
            if (useVerifyApi) {
                return verifyCodeViaVerifyApi(phoneNumber, code, this.verifySid);
            } else {
                // Verification handled by app-side OTP validation
                LOGGER.debug("Code verification will be handled by app-side validation");
                return true; // App will validate using OTP hash
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to verify code for {}: {}", phoneNumber, exception.getMessage(), exception);
            return false;
        }
    }

    /**
     * Send verification via Twilio Verify API (recommended - Twilio manages OTP).
     */
    private String sendVerificationViaVerifyApi(String phoneNumber) throws Exception {
        String url = String.format(TWILIO_VERIFY_API_URL, verifySid);
        String payload = "To=" + URLEncoder.encode(phoneNumber, StandardCharsets.UTF_8) +
                "&Channel=sms";

        return makeVerifyApiRequest(url, payload, "sendVerification");
    }

    /**
     * Verify code via Twilio Verify API.
     */
    private boolean verifyCodeViaVerifyApi(String phoneNumber, String code, String verifySid) throws Exception {
        String url = String.format(TWILIO_VERIFY_CHECK_URL, verifySid);
        String payload = "To=" + URLEncoder.encode(phoneNumber, StandardCharsets.UTF_8) +
                "&Code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);

        String response = makeVerifyApiRequest(url, payload, "verifyCode");
        if (response == null) {
            return false;
        }

        String normalized = response.replaceAll("\\s+", "").toLowerCase();
        return normalized.contains("\"status\":\"approved\"") || normalized.contains("\"valid\":true");
    }

    /**
     * Send verification via direct Twilio Messaging API.
     * Used when Verify API is not configured (app generates OTP).
     */
    private String sendVerificationViaSms(String phoneNumber) throws Exception {
        String url = String.format(TWILIO_MESSAGING_API_URL, accountSid);
        
        // Message body will be set by caller or use generic message
        String message = "Your UniLearn verification code is: [CODE]. Valid for 10 minutes.";
        String payload = "From=" + URLEncoder.encode(fromNumber, StandardCharsets.UTF_8) +
                "&To=" + URLEncoder.encode(phoneNumber, StandardCharsets.UTF_8) +
                "&Body=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

        String response = makeRequest(url, payload);
        
        // Extract SID from response if successful
        if (response != null && response.contains("\"sid\"")) {
            int sidStart = response.indexOf("\"sid\":\"") + 7;
            int sidEnd = response.indexOf("\"", sidStart);
            if (sidEnd > sidStart) {
                return response.substring(sidStart, sidEnd);
            }
        }
        return null;
    }

    /**
     * Make HTTP request to Twilio Verify API.
     */
    private String makeVerifyApiRequest(String url, String payload, String operation) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URI(url).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept-Charset", StandardCharsets.UTF_8.name());
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setConnectTimeout(HTTP_TIMEOUT);
        connection.setReadTimeout(HTTP_TIMEOUT);

        // Basic Auth: Base64(accountSid:authToken)
        String auth = accountSid + ":" + authToken;
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        connection.setRequestProperty("Authorization", authHeader);

        connection.setDoOutput(true);
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = payload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        String response = readResponse(connection);

        if (responseCode >= 200 && responseCode < 300) {
            LOGGER.debug("{} successful for Verify API", operation);
            return response;
        } else {
            LOGGER.error("{} failed with response code {}: {}", operation, responseCode, response);
            return null;
        }
    }

    /**
     * Make HTTP request to Twilio Messaging API.
     */
    private String makeRequest(String url, String payload) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URI(url).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept-Charset", StandardCharsets.UTF_8.name());
        connection.setConnectTimeout(HTTP_TIMEOUT);
        connection.setReadTimeout(HTTP_TIMEOUT);

        // Basic Auth
        String auth = accountSid + ":" + authToken;
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        connection.setRequestProperty("Authorization", authHeader);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        connection.setDoOutput(true);
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = payload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        String response = readResponse(connection);

        if (responseCode >= 200 && responseCode < 300) {
            LOGGER.debug("SMS sent successfully");
            return response;
        } else {
            LOGGER.error("SMS send failed with response code {}: {}", responseCode, response);
            return null;
        }
    }

    /**
     * Read response from HTTP connection.
     */
    private String readResponse(HttpURLConnection connection) throws Exception {
        int responseCode = connection.getResponseCode();
        java.io.InputStream stream = responseCode >= 200 && responseCode < 300
            ? connection.getInputStream()
            : connection.getErrorStream();

        if (stream == null) {
            return "";
        }

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }

    /**
     * Validate Twilio configuration.
     */
    private void validateConfiguration() {
        if (!isConfigured()) {
            LOGGER.warn("Twilio SMS Service is not fully configured. " +
                    "Set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, and either TWILIO_VERIFY_SERVICE_SID or TWILIO_FROM_NUMBER");
        }
    }

    /**
     * Check if Twilio is properly configured.
     */
    private boolean isConfigured() {
        boolean hasBasicConfig = !accountSid.isEmpty() && !authToken.isEmpty();
        boolean hasVerifyApi = !verifySid.isEmpty();
        boolean hasMessagingApi = !fromNumber.isEmpty();
        return hasBasicConfig && (hasVerifyApi || hasMessagingApi);
    }

    /**
     * Check if Verify API is being used.
     */
    public boolean isUsingVerifyApi() {
        return useVerifyApi;
    }

    /**
     * Health check for Twilio service.
     */
    public boolean isHealthy() {
        return isConfigured();
    }
}
