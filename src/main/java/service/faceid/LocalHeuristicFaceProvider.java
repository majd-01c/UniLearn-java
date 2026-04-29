package service.faceid;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

/**
 * Lightweight local provider used as a pluggable default implementation.
 * It uses heuristic face detection and a perceptual hash embedding.
 */
public class LocalHeuristicFaceProvider implements FaceProvider {

    private static final int MIN_IMAGE_DIMENSION = 96;
    private static final int SAMPLE_STEP = 2;
    private static final double MIN_SKIN_RATIO = 0.02;
    private static final double LARGE_CLUSTER_RATIO = 0.008;
    private static final double MIN_SECOND_FACE_RELATIVE_SIZE = 0.45;
    private static final double MATCH_THRESHOLD = 0.82;
    private static final int HASH_GRID_SIZE = 16;

    @Override
    public FaceDetectionResult detectSingleFace(BufferedImage image) {
        if (image == null) {
            return FaceDetectionResult.noFace("Unable to read image for face detection");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        if (width < MIN_IMAGE_DIMENSION || height < MIN_IMAGE_DIMENSION) {
            return FaceDetectionResult.noFace("Image is too small for reliable face detection");
        }

        int sampledWidth = Math.max(1, width / SAMPLE_STEP);
        int sampledHeight = Math.max(1, height / SAMPLE_STEP);
        boolean[][] skinMap = new boolean[sampledHeight][sampledWidth];

        int skinPixels = 0;
        for (int sy = 0; sy < sampledHeight; sy++) {
            for (int sx = 0; sx < sampledWidth; sx++) {
                int pixel = image.getRGB(Math.min(width - 1, sx * SAMPLE_STEP), Math.min(height - 1, sy * SAMPLE_STEP));
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                if (isLikelySkin(r, g, b)) {
                    skinMap[sy][sx] = true;
                    skinPixels++;
                }
            }
        }

        int totalSampled = sampledWidth * sampledHeight;
        double skinRatio = totalSampled == 0 ? 0.0 : (double) skinPixels / (double) totalSampled;
        if (skinRatio < MIN_SKIN_RATIO) {
            return FaceDetectionResult.noFace("No clear face was detected in the selected image");
        }

        int minClusterSize = Math.max(20, (int) Math.round(totalSampled * LARGE_CLUSTER_RATIO));
        int clusterCount = countLargeClusters(skinMap, minClusterSize);

        if (clusterCount == 0) {
            return FaceDetectionResult.noFace("No clear face was detected in the selected image");
        }
        if (clusterCount > 1) {
            return FaceDetectionResult.multipleFaces(clusterCount, "Multiple faces detected. Use an image with only one face");
        }

        return FaceDetectionResult.success();
    }

    @Override
    public String extractEmbedding(BufferedImage image) {
        if (image == null) {
            return "";
        }

        int[] luminance = new int[HASH_GRID_SIZE * HASH_GRID_SIZE];
        int width = image.getWidth();
        int height = image.getHeight();

        int index = 0;
        long sum = 0;
        for (int gy = 0; gy < HASH_GRID_SIZE; gy++) {
            int yStart = (gy * height) / HASH_GRID_SIZE;
            int yEnd = Math.max(yStart + 1, ((gy + 1) * height) / HASH_GRID_SIZE);

            for (int gx = 0; gx < HASH_GRID_SIZE; gx++) {
                int xStart = (gx * width) / HASH_GRID_SIZE;
                int xEnd = Math.max(xStart + 1, ((gx + 1) * width) / HASH_GRID_SIZE);

                long blockSum = 0;
                int count = 0;
                for (int y = yStart; y < yEnd; y++) {
                    for (int x = xStart; x < xEnd; x++) {
                        int pixel = image.getRGB(Math.min(width - 1, x), Math.min(height - 1, y));
                        int r = (pixel >> 16) & 0xFF;
                        int g = (pixel >> 8) & 0xFF;
                        int b = pixel & 0xFF;
                        int gray = (int) Math.round((0.299 * r) + (0.587 * g) + (0.114 * b));
                        blockSum += gray;
                        count++;
                    }
                }

                int avgGray = count == 0 ? 0 : (int) (blockSum / count);
                luminance[index++] = avgGray;
                sum += avgGray;
            }
        }

        double mean = (double) sum / (double) luminance.length;
        StringBuilder hex = new StringBuilder(luminance.length / 4);

        for (int i = 0; i < luminance.length; i += 4) {
            int nibble = 0;
            for (int j = 0; j < 4; j++) {
                boolean bitSet = luminance[i + j] >= mean;
                if (bitSet) {
                    nibble |= (1 << (3 - j));
                }
            }
            hex.append(Integer.toHexString(nibble));
        }

        return hex.toString();
    }

    @Override
    public FaceComparisonResult compareEmbeddings(String enrolledEmbedding, String probeEmbedding) {
        if (enrolledEmbedding == null || enrolledEmbedding.isBlank()) {
            return FaceComparisonResult.failed("No enrolled face template found for this account");
        }
        if (probeEmbedding == null || probeEmbedding.isBlank()) {
            return FaceComparisonResult.failed("Unable to extract a valid face template from the provided image");
        }

        String enrolled = enrolledEmbedding.trim().toLowerCase();
        String probe = probeEmbedding.trim().toLowerCase();
        if (enrolled.length() != probe.length()) {
            return FaceComparisonResult.failed("Face template mismatch. Re-enroll Face ID and try again");
        }

        int diffBits = 0;
        int totalBits = enrolled.length() * 4;

        for (int i = 0; i < enrolled.length(); i++) {
            int a = Character.digit(enrolled.charAt(i), 16);
            int b = Character.digit(probe.charAt(i), 16);
            if (a < 0 || b < 0) {
                return FaceComparisonResult.failed("Corrupted face template data");
            }
            diffBits += Integer.bitCount(a ^ b);
        }

        double similarity = 1.0 - ((double) diffBits / (double) totalBits);
        double clampedSimilarity = Math.max(0.0, Math.min(1.0, similarity));
        boolean matched = clampedSimilarity >= MATCH_THRESHOLD;

        return new FaceComparisonResult(
                matched,
                clampedSimilarity,
                matched ? "Face verification successful" : "Face does not match the enrolled template"
        );
    }

    private int countLargeClusters(boolean[][] skinMap, int minClusterSize) {
    int rows = skinMap.length;
    int cols = rows == 0 ? 0 : skinMap[0].length;
    boolean[][] visited = new boolean[rows][cols];

    // Store cluster centroids to merge nearby ones
    java.util.List<int[]> clusterCentroids = new java.util.ArrayList<>();
    java.util.List<Integer> clusterSizes = new java.util.ArrayList<>();

    int[] dx = {1, -1, 0, 0, 1, 1, -1, -1};
    int[] dy = {0, 0, 1, -1, 1, -1, 1, -1};

    for (int y = 0; y < rows; y++) {
        for (int x = 0; x < cols; x++) {
            if (!skinMap[y][x] || visited[y][x]) continue;

            int clusterSize = 0;
            long sumX = 0, sumY = 0;
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            queue.add(new int[]{x, y});
            visited[y][x] = true;

            while (!queue.isEmpty()) {
                int[] point = queue.poll();
                int px = point[0], py = point[1];
                clusterSize++;
                sumX += px;
                sumY += py;

                for (int i = 0; i < 8; i++) {
                    int nx = px + dx[i];
                    int ny = py + dy[i];
                    if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue;
                    if (visited[ny][nx] || !skinMap[ny][nx]) continue;
                    visited[ny][nx] = true;
                    queue.add(new int[]{nx, ny});
                }
            }

            if (clusterSize >= minClusterSize) {
                int cx = (int) (sumX / clusterSize);
                int cy = (int) (sumY / clusterSize);
                clusterCentroids.add(new int[]{cx, cy});
                clusterSizes.add(clusterSize);
            }
        }
    }

    // Merge clusters whose centroids are close to each other
    // to avoid counting fragmented regions from the same face.
    int mergeDistanceThreshold = Math.max(rows, cols) / 3; // 33% of image dimension
    boolean[] merged = new boolean[clusterCentroids.size()];
    java.util.List<Integer> mergedFaceSizes = new java.util.ArrayList<>();

    for (int i = 0; i < clusterCentroids.size(); i++) {
        if (merged[i]) continue;
        int mergedSize = clusterSizes.get(i);
        int[] ci = clusterCentroids.get(i);

        for (int j = i + 1; j < clusterCentroids.size(); j++) {
            if (merged[j]) continue;
            int[] cj = clusterCentroids.get(j);
            int dx2 = ci[0] - cj[0];
            int dy2 = ci[1] - cj[1];
            double dist = Math.sqrt(dx2 * dx2 + dy2 * dy2);
            if (dist < mergeDistanceThreshold) {
                merged[j] = true; // same face, merge it
                mergedSize += clusterSizes.get(j);
            }
        }

        mergedFaceSizes.add(mergedSize);
    }

    if (mergedFaceSizes.isEmpty()) {
        return 0;
    }

    int largestFace = 0;
    for (int size : mergedFaceSizes) {
        largestFace = Math.max(largestFace, size);
    }

    // Ignore secondary regions that are too small to be a second real face.
    int secondFaceMinSize = Math.max(minClusterSize, (int) Math.round(largestFace * MIN_SECOND_FACE_RELATIVE_SIZE));
    int significantFaces = 0;
    for (int size : mergedFaceSizes) {
        if (size >= secondFaceMinSize) {
            significantFaces++;
        }
    }

    return significantFaces;
}

    private boolean isLikelySkin(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));

            return r > 60
            && g > 30
            && b > 15
            && (max - min) > 10        // reduced from 15
            && Math.abs(r - g) > 10    // reduced from 15
            && r > g
            && r > b;
    }
}
