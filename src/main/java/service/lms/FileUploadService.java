package service.lms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import validation.LmsValidator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class FileUploadService {

    private static final Logger LOG = LoggerFactory.getLogger(FileUploadService.class);
    public static final String UPLOAD_DIR = System.getProperty("user.home") + File.separator + "unilearn-uploads";

    public FileUploadService() {
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public String saveFile(File sourceFile, String originalName) throws IOException {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("Source file does not exist");
        }

        long size = sourceFile.length();
        if (size > LmsValidator.MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds maximum size of 50 MB");
        }

        String storedName = UUID.randomUUID() + "_" + sanitize(originalName);
        Path target = Path.of(UPLOAD_DIR, storedName);
        Files.copy(sourceFile.toPath(), target, StandardCopyOption.REPLACE_EXISTING);

        LOG.info("File saved: {} ({} bytes)", storedName, size);
        return storedName;
    }

    public File getFile(String storedName) {
        File f = new File(UPLOAD_DIR, storedName);
        return f.exists() ? f : null;
    }

    public boolean deleteFile(String storedName) {
        if (storedName == null || storedName.isEmpty()) return false;
        File f = new File(UPLOAD_DIR, storedName);
        return f.exists() && f.delete();
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
