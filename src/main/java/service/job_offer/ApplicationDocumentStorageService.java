package service.job_offer;

import service.storage.SupabaseStorageService;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Stores and retrieves job application documents from Supabase Storage.
 * Legacy local files remain readable for existing database records.
 */
public class ApplicationDocumentStorageService {

    private static final String LOCAL_UPLOADS_DIR = "uploads/cvs";
    private final SupabaseStorageService storageService;

    public ApplicationDocumentStorageService() {
        this(new SupabaseStorageService());
    }

    ApplicationDocumentStorageService(SupabaseStorageService storageService) {
        this.storageService = storageService;
    }

    public boolean isSupabaseConfigured() {
        return storageService.isConfigured();
    }

    public String storeCv(File source, Integer studentId, Integer offerId) throws IOException, InterruptedException {
        validateSourceFile(source);
        return storageService.storeManagedFile(
                source,
                "job-applications",
                source.getName(),
                offerId == null ? "unknown-offer" : "offer-" + offerId,
                studentId == null ? "unknown-student" : "student-" + studentId
        );
    }

    public File resolveCvFile(String storedValue) throws IOException, InterruptedException {
        return storageService.resolveFile(storedValue, legacyCandidateDirs());
    }

    public String extractDisplayName(String storedValue) {
        return storageService.extractDisplayName(storedValue);
    }

    private void validateSourceFile(File source) throws IOException {
        if (source == null || !source.exists() || !source.isFile()) {
            throw new IOException("Selected CV file does not exist.");
        }
    }

    private List<File> legacyCandidateDirs() {
        return List.of(
                new File(System.getProperty("user.dir"), LOCAL_UPLOADS_DIR),
                new File(System.getProperty("user.dir")),
                new File(System.getProperty("user.home"), "Downloads"),
                new File(System.getProperty("user.home"), "Desktop"),
                new File(System.getProperty("user.home"), "Documents")
        );
    }
}
