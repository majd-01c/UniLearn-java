package service.faceid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Result from n8n face quality gate API.
 * Contains pass/fail decision and detailed quality metrics.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QualityResult {

    private boolean passed;
    private double overallScore;
    private QualityChecks checks;
    private List<String> tips;
    private String errorMessage;

    public QualityResult() {
    }

    public QualityResult(boolean passed, double overallScore, QualityChecks checks, List<String> tips) {
        this.passed = passed;
        this.overallScore = overallScore;
        this.checks = checks;
        this.tips = tips;
        this.errorMessage = null;
    }

    public static QualityResult error(String message) {
        QualityResult result = new QualityResult();
        result.passed = false;
        result.overallScore = 0.0;
        result.errorMessage = message;
        return result;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public double getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(double overallScore) {
        this.overallScore = overallScore;
    }

    public QualityChecks getChecks() {
        return checks;
    }

    public void setChecks(QualityChecks checks) {
        this.checks = checks;
    }

    public List<String> getTips() {
        return tips;
    }

    public void setTips(List<String> tips) {
        this.tips = tips;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isBlank();
    }

    /**
     * Individual quality checks from n8n.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QualityChecks {

        private boolean singleFace;
        private String lighting;  // good|ok|poor
        private String blur;      // low|medium|high
        private boolean faceCentered;
        private String occlusion; // none|minor|major

        public QualityChecks() {
        }

        public boolean isSingleFace() {
            return singleFace;
        }

        public void setSingleFace(boolean singleFace) {
            this.singleFace = singleFace;
        }

        public String getLighting() {
            return lighting;
        }

        public void setLighting(String lighting) {
            this.lighting = lighting;
        }

        public String getBlur() {
            return blur;
        }

        public void setBlur(String blur) {
            this.blur = blur;
        }

        public boolean isFaceCentered() {
            return faceCentered;
        }

        public void setFaceCentered(boolean faceCentered) {
            this.faceCentered = faceCentered;
        }

        public String getOcclusion() {
            return occlusion;
        }

        public void setOcclusion(String occlusion) {
            this.occlusion = occlusion;
        }

        @Override
        public String toString() {
            return "QualityChecks{" +
                    "singleFace=" + singleFace +
                    ", lighting='" + lighting + '\'' +
                    ", blur='" + blur + '\'' +
                    ", faceCentered=" + faceCentered +
                    ", occlusion='" + occlusion + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "QualityResult{" +
                "passed=" + passed +
                ", overallScore=" + overallScore +
                ", checks=" + checks +
                ", tips=" + tips +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
