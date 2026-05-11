package service.storage;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SupabaseStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupabaseStorageService.class);

    private static final String SUPABASE_URL = "SUPABASE_URL";
    private static final String SUPABASE_SECRET_KEY = "SUPABASE_SECRET_KEY";
    private static final String SUPABASE_BUCKET = "SUPABASE_BUCKET";
    private static final String STORAGE_PREFIX = "supabase:";
    private static final String CRLF = "\r\n";

    private final String supabaseUrl;
    private final String supabaseSecretKey;
    private final String supabaseBucket;
    private final HttpClient httpClient;

    public SupabaseStorageService() {
        this(ConfigurationProvider.getProperty(SUPABASE_URL),
                ConfigurationProvider.getProperty(SUPABASE_SECRET_KEY),
                ConfigurationProvider.getProperty(SUPABASE_BUCKET, "documents"));
    }

    public SupabaseStorageService(String supabaseUrl, String supabaseSecretKey, String supabaseBucket) {
        this.supabaseUrl = trimToNull(supabaseUrl);
        this.supabaseSecretKey = trimToNull(supabaseSecretKey);
        this.supabaseBucket = trimToNull(supabaseBucket);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public boolean isConfigured() {
        return supabaseUrl != null && supabaseSecretKey != null && supabaseBucket != null;
    }

    public String storeManagedFile(File source, String rootFolder, String originalName, String... pathSegments)
            throws IOException, InterruptedException {
        validateSourceFile(source);
        requireConfigured();

        String objectPath = buildManagedObjectPath(rootFolder, originalName, pathSegments);
        uploadToSupabase(source.toPath(), objectPath);
        return STORAGE_PREFIX + objectPath;
    }

    public File resolveFile(String storedValue, List<File> legacyCandidateDirs) throws IOException, InterruptedException {
        String normalized = trimToNull(storedValue);
        if (normalized == null) {
            throw new IllegalArgumentException("No file attached.");
        }

        if (isSupabaseReference(normalized)) {
            requireConfigured();
            return downloadFromSupabase(stripPrefix(normalized));
        }

        File localFile = resolveLegacyLocalFile(normalized, legacyCandidateDirs);
        if (localFile != null && localFile.exists()) {
            return localFile;
        }

        throw new IllegalArgumentException("File not found: " + storedValue);
    }

    public boolean deleteManagedFile(String storedValue, List<File> legacyCandidateDirs) {
        String normalized = trimToNull(storedValue);
        if (normalized == null) {
            return false;
        }

        if (isSupabaseReference(normalized)) {
            if (!isConfigured()) {
                return false;
            }
            return deleteFromSupabase(stripPrefix(normalized));
        }

        File legacyFile = resolveLegacyLocalFile(normalized, legacyCandidateDirs);
        return legacyFile != null && legacyFile.exists() && legacyFile.delete();
    }

    public String extractDisplayName(String storedValue) {
        String normalized = trimToNull(storedValue);
        if (normalized == null) {
            return "No file attached";
        }

        String rawName = isSupabaseReference(normalized) ? stripPrefix(normalized) : normalized;
        return Path.of(rawName.replace("\\", "/")).getFileName().toString();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Supabase Storage is not configured.");
        }
    }

    private String buildManagedObjectPath(String rootFolder, String originalName, String... pathSegments) {
        List<String> segments = new ArrayList<>();
        String normalizedRoot = sanitizeSegment(rootFolder);
        if (normalizedRoot != null) {
            segments.add(normalizedRoot);
        }

        if (pathSegments != null) {
            for (String segment : pathSegments) {
                String normalizedSegment = sanitizeSegment(segment);
                if (normalizedSegment != null) {
                    segments.add(normalizedSegment);
                }
            }
        }

        String safeOriginalName = sanitizeFileName(originalName);
        segments.add(UUID.randomUUID() + "_" + safeOriginalName);
        return String.join("/", segments);
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
                Path tempFile = Files.createTempFile("unilearn-storage-", suffix);
                Files.write(tempFile, response.body());
                tempFile.toFile().deleteOnExit();
                return tempFile.toFile();
            }

            lastError = new IOException("Supabase download failed (" + response.statusCode() + ")");
        }

        throw lastError != null ? lastError : new IOException("Supabase download failed.");
    }

    private boolean deleteFromSupabase(String objectPath) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildUploadUrl(objectPath)))
                    .header("Authorization", "Bearer " + supabaseSecretKey)
                    .header("apikey", supabaseSecretKey)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception exception) {
            LOGGER.warn("Unable to delete Supabase object {}", objectPath, exception);
            return false;
        }
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

    private File resolveLegacyLocalFile(String storedValue, List<File> candidateDirs) {
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

        if (candidateDirs != null) {
            for (File dir : candidateDirs) {
                if (dir == null) {
                    continue;
                }
                File candidate = new File(dir, fileNameOnly);
                if (candidate.exists()) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private void validateSourceFile(File source) throws IOException {
        if (source == null || !source.exists() || !source.isFile()) {
            throw new IOException("Selected file does not exist.");
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

    private String sanitizeSegment(String value) {
        String candidate = trimToNull(value);
        if (candidate == null) {
            return null;
        }
        return candidate.replaceAll("[^a-zA-Z0-9._/-]", "_").replace("\\", "/");
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
