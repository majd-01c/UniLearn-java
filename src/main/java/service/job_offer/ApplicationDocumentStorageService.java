package service.job_offer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.ConfigurationProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Stores and retrieves job application documents from Supabase Storage when configured.
 * Falls back to local disk storage for environments without Supabase credentials.
 */
public class ApplicationDocumentStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationDocumentStorageService.class);

    private static final String SUPABASE_URL = "SUPABASE_URL";
    private static final String SUPABASE_SECRET_KEY = "SUPABASE_SECRET_KEY";
    private static final String SUPABASE_BUCKET = "SUPABASE_BUCKET";

    private static final String STORAGE_PREFIX = "supabase:";
    private static final String LOCAL_UPLOADS_DIR = "uploads/cvs";
    private static final String CRLF = "\r\n";

    private final String supabaseUrl;
    private final String supabaseSecretKey;
    private final String supabaseBucket;
    private final HttpClient httpClient;

    public ApplicationDocumentStorageService() {
        this(
                ConfigurationProvider.getProperty(SUPABASE_URL),
                ConfigurationProvider.getProperty(SUPABASE_SECRET_KEY),
                ConfigurationProvider.getProperty(SUPABASE_BUCKET, "documents")
        );
    }

    ApplicationDocumentStorageService(String supabaseUrl, String supabaseSecretKey, String supabaseBucket) {
        this.supabaseUrl = trimToNull(supabaseUrl);
        this.supabaseSecretKey = trimToNull(supabaseSecretKey);
        this.supabaseBucket = trimToNull(supabaseBucket);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public boolean isSupabaseConfigured() {
        return supabaseUrl != null && supabaseSecretKey != null && supabaseBucket != null;
    }

    public String storeCv(File source, Integer studentId, Integer offerId) throws IOException, InterruptedException {
        validateSourceFile(source);

        if (!isSupabaseConfigured()) {
            return storeLocally(source);
        }

        String objectPath = buildObjectPath(source.getName(), studentId, offerId);
        uploadToSupabase(source.toPath(), objectPath);
        return STORAGE_PREFIX + objectPath;
    }

    public File resolveCvFile(String storedValue) throws IOException, InterruptedException {
        String normalized = trimToNull(storedValue);
        if (normalized == null) {
            throw new IllegalArgumentException("No CV file attached.");
        }

        if (isSupabaseReference(normalized)) {
            if (!isSupabaseConfigured()) {
                throw new IllegalStateException("Supabase is not configured for stored CV reference.");
            }
            return downloadFromSupabase(stripPrefix(normalized));
        }

        File localFile = resolveLegacyLocalFile(normalized);
        if (localFile != null && localFile.exists()) {
            return localFile;
        }

        throw new IllegalArgumentException("CV file not found: " + storedValue);
    }

    public String extractDisplayName(String storedValue) {
        String normalized = trimToNull(storedValue);
        if (normalized == null) {
            return "No CV attached";
        }

        String rawName = isSupabaseReference(normalized) ? stripPrefix(normalized) : normalized;
        return Path.of(rawName.replace("\\", "/")).getFileName().toString();
    }

    private void uploadToSupabase(Path source, String objectPath) throws IOException, InterruptedException {
        String boundary = "----UniLearnBoundary" + UUID.randomUUID();
        byte[] body = buildMultipartBody(boundary, source, objectPath);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildUploadUrl(objectPath)))
                .header("Authorization", "Bearer " + supabaseSecretKey)
                .header("apikey", supabaseSecretKey)
                .header("x-upsert", "false")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Supabase upload failed (" + response.statusCode() + "): " + response.body());
        }
    }

    private File downloadFromSupabase(String objectPath) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = null;
        IOException lastError = null;

        for (String url : buildDownloadUrls(objectPath)) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + supabaseSecretKey)
                    .header("apikey", supabaseSecretKey)
                    .GET()
                    .build();

            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String suffix = determineSuffix(objectPath);
                Path tempFile = Files.createTempFile("unilearn-cv-", suffix);
                Files.write(tempFile, response.body());
                tempFile.toFile().deleteOnExit();
                return tempFile.toFile();
            }

            lastError = new IOException("Supabase download failed (" + response.statusCode() + ")");
        }

        throw lastError != null ? lastError : new IOException("Supabase download failed.");
    }

    private List<String> buildDownloadUrls(String objectPath) {
        String encodedBucket = encodeSegment(supabaseBucket);
        String encodedPath = encodePath(objectPath);
        return List.of(
                supabaseUrl + "/storage/v1/object/authenticated/" + encodedBucket + "/" + encodedPath,
                supabaseUrl + "/storage/v1/object/" + encodedBucket + "/" + encodedPath
        );
    }

    private String buildUploadUrl(String objectPath) {
        return supabaseUrl + "/storage/v1/object/" + encodeSegment(supabaseBucket) + "/" + encodePath(objectPath);
    }

    private byte[] buildMultipartBody(String boundary, Path source, String objectPath) throws IOException {
        String contentType = detectContentType(source);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"cacheControl\"" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        output.write(("3600" + CRLF).getBytes(StandardCharsets.UTF_8));

        output.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"metadata\"" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        output.write(("{}" + CRLF).getBytes(StandardCharsets.UTF_8));

        output.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + escapeQuoted(extractDisplayName(objectPath)) + "\"" + CRLF).getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Type: " + contentType + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        output.write(Files.readAllBytes(source));
        output.write(CRLF.getBytes(StandardCharsets.UTF_8));
        output.write(("--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private String buildObjectPath(String originalName, Integer studentId, Integer offerId) {
        String sanitizedName = sanitizeFileName(originalName);
        String studentSegment = studentId == null ? "unknown-student" : "student-" + studentId;
        String offerSegment = offerId == null ? "unknown-offer" : "offer-" + offerId;
        return "job-applications/" + offerSegment + "/" + studentSegment + "/" + UUID.randomUUID() + "_" + sanitizedName;
    }

    private String storeLocally(File source) throws IOException {
        String sanitizedName = sanitizeFileName(source.getName());
        String targetName = System.currentTimeMillis() + "_" + sanitizedName;
        Path targetDir = Path.of(System.getProperty("user.dir"), LOCAL_UPLOADS_DIR);
        Files.createDirectories(targetDir);

        Path targetPath = targetDir.resolve(targetName);
        Files.copy(source.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath.toAbsolutePath().toString();
    }

    private File resolveLegacyLocalFile(String storedValue) {
        File direct = new File(storedValue);
        if (direct.exists()) {
            return direct;
        }

        String normalized = storedValue.replace("\\", File.separator).replace("/", File.separator);
        File normalizedFile = new File(normalized);
        if (normalizedFile.exists()) {
            return normalizedFile;
        }

        String fileNameOnly = new File(normalized).getName();
        if (fileNameOnly.isEmpty()) {
            return null;
        }

        List<File> candidateDirs = List.of(
                new File(System.getProperty("user.dir"), LOCAL_UPLOADS_DIR),
                new File(System.getProperty("user.dir")),
                new File(System.getProperty("user.home"), "Downloads"),
                new File(System.getProperty("user.home"), "Desktop"),
                new File(System.getProperty("user.home"), "Documents")
        );

        for (File dir : candidateDirs) {
            File candidate = new File(dir, fileNameOnly);
            if (candidate.exists()) {
                return candidate;
            }
        }

        return null;
    }

    private void validateSourceFile(File source) throws IOException {
        if (source == null || !source.exists() || !source.isFile()) {
            throw new IOException("Selected CV file does not exist.");
        }
    }

    private String detectContentType(Path source) {
        try {
            String contentType = Files.probeContentType(source);
            if (contentType != null && !contentType.isBlank()) {
                return contentType;
            }
        } catch (IOException exception) {
            LOGGER.debug("Could not detect content type for {}", source, exception);
        }
        return "application/octet-stream";
    }

    private String sanitizeFileName(String originalName) {
        String candidate = trimToNull(originalName);
        if (candidate == null) {
            return "document.bin";
        }
        return candidate.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String determineSuffix(String objectPath) {
        String fileName = extractDisplayName(objectPath);
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(dotIndex) : ".bin";
    }

    private String escapeQuoted(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isSupabaseReference(String value) {
        return value.startsWith(STORAGE_PREFIX);
    }

    private String stripPrefix(String value) {
        return value.substring(STORAGE_PREFIX.length());
    }

    private String encodePath(String path) {
        String[] segments = path.split("/");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                builder.append('/');
            }
            builder.append(encodeSegment(segments[i]));
        }
        return builder.toString();
    }

    private String encodeSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
