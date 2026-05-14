package service.job_offer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;

/**
 * Extracts clean text from CV PDF files.
 */
public class CvParserService {

    private static final int MAX_EXTRACTED_CHARS = 15000;

    public String extractTextFromPdf(File pdfFile) throws IOException {
        if (pdfFile == null || !pdfFile.exists() || !pdfFile.isFile()) {
            throw new IOException("CV file not found.");
        }

        String fileName = pdfFile.getName().toLowerCase();
        if (!fileName.endsWith(".pdf")) {
            throw new IOException("Only PDF CV files are supported.");
        }

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String rawText = stripper.getText(document);
            return cleanExtractedText(rawText);
        }
    }

    private String cleanExtractedText(String rawText) {
        if (rawText == null) {
            return "";
        }

        String cleaned = rawText
                .replaceAll("\\p{Cntrl}", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.length() > MAX_EXTRACTED_CHARS) {
            cleaned = cleaned.substring(0, MAX_EXTRACTED_CHARS);
        }

        return cleaned;
    }
}
