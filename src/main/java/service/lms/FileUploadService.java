package service.lms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.storage.SupabaseStorageService;
import validation.LmsValidator;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class FileUploadService {

    private static final Logger LOG = LoggerFactory.getLogger(FileUploadService.class);
    private static final String LMS_ROOT = "lms-content";
    private static final String EVALUATION_ROOT = "evaluation-documents";
    private static final List<File> LEGACY_UPLOAD_DIRS = List.of(
            new File(System.getProperty("user.home"), "unilearn-uploads"),
            new File(System.getProperty("user.dir"), "uploads"),
            new File(System.getProperty("user.dir"), "uploads/cvs")
    );

    private final SupabaseStorageService storageService = new SupabaseStorageService();

    public String saveFile(File sourceFile, String originalName) throws IOException, InterruptedException {
        validateUpload(sourceFile);
        String storedReference = storageService.storeManagedFile(
                sourceFile,
                LMS_ROOT,
                originalName,
                "content",
                UUID.randomUUID().toString()
        );
        LOG.info("File uploaded to Supabase: {}", storageService.extractDisplayName(storedReference));
        return storedReference;
    }

    public String saveEvaluationDocument(File sourceFile, String originalName) throws IOException, InterruptedException {
        validateUpload(sourceFile);
        String storedReference = storageService.storeManagedFile(
                sourceFile,
                EVALUATION_ROOT,
                originalName,
                "document-request",
                UUID.randomUUID().toString()
        );
        LOG.info("Evaluation document uploaded to Supabase: {}", storageService.extractDisplayName(storedReference));
        return storedReference;
    }

    public File getFile(String storedName) throws IOException, InterruptedException {
        return storageService.resolveFile(storedName, LEGACY_UPLOAD_DIRS);
    }

    public boolean deleteFile(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return false;
        }
        return storageService.deleteManagedFile(storedName, LEGACY_UPLOAD_DIRS);
    }

    public String extractDisplayName(String storedName) {
        return storageService.extractDisplayName(storedName);
    }

    private void validateUpload(File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("Source file does not exist");
        }

        long size = sourceFile.length();
        if (size > LmsValidator.MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds maximum size of 50 MB");
        }

        if (!storageService.isConfigured()) {
            throw new IllegalStateException("Supabase Storage is not configured.");
        }
    }
}
