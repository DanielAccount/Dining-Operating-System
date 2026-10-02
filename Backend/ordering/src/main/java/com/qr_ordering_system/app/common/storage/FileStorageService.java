package com.qr_ordering_system.app.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Stores uploaded files on local disk and returns the generated file name.
 *
 * The returned file name is what gets persisted in the database (e.g. in
 * MenuItem.imageUrl as "/uploads/menu-images/<fileName>").
 */
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5 MB

    private final Path storageLocation;
    private final String publicPath;

    public FileStorageService(
            @Value("${app.upload.dir:uploads/menu-images}") String uploadDir,
            @Value("${app.upload.public-path:/uploads/menu-images}") String publicPath) {
        this.storageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.publicPath = publicPath;
        ensureStorageDirectory();
    }

    /**
     * Ensures the upload directory exists.
     *
     * This runs on every write rather than only at startup: the directory can be
     * removed or unmounted while the application is running (log rotation, a tmp
     * cleaner, a container volume that mounts late). If we only checked once in
     * the constructor, every subsequent upload would fail until a restart.
     */
    private void ensureStorageDirectory() {
        if (Files.isDirectory(this.storageLocation)) {
            return;
        }
        try {
            Files.createDirectories(this.storageLocation);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not create upload directory " + this.storageLocation + ": " + e.getMessage(), e);
        }
    }

    /**
     * Validates and stores the file. Returns the public URL path of the stored file.
     */
    public String store(MultipartFile file) {
        validate(file);
        ensureStorageDirectory();

        String baseName = UUID.randomUUID().toString();
        Path staging = this.storageLocation.resolve(baseName + ".tmp").normalize();

        // Security guard: never allow writing outside the upload directory.
        if (!staging.startsWith(this.storageLocation)) {
            throw new IllegalArgumentException("Invalid file path");
        }

        try {
            Files.copy(file.getInputStream(), staging, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Include the cause: "Failed to store file" alone is impossible to
            // diagnose from the response or the log message.
            throw new IllegalStateException(
                    "Failed to store file in " + this.storageLocation + ": " + e.getMessage(), e);
        }

        // The Content-Type header is client-supplied and trivially spoofable, so
        // confirm the bytes on disk really are an image before keeping the file.
        String detectedFormat = detectFormat(staging);

        if (detectedFormat == null) {
            // Read the signature BEFORE removing the file, otherwise there is
            // nothing left to report and the user gets a useless message.
            String header = describeHeader(staging);
            deleteQuietly(staging);
            throw new IllegalArgumentException(
                    "File content is not a valid image (it starts with " + header + ")"
                            + ". The file may be renamed or corrupted"
                            + " - please re-export it as JPEG, PNG or WebP.");
        }

        // Store using the format we actually detected rather than trusting the
        // uploaded file name, so the extension on disk always matches the bytes.
        String extension = "." + detectedFormat.toLowerCase();
        Path target = this.storageLocation.resolve(baseName + extension).normalize();

        try {
            Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(staging);
            throw new IllegalStateException("Failed to finalise file", e);
        }

        return this.publicPath + "/" + target.getFileName();
    }

    /**
     * Deletes a previously stored file. Accepts either the full public path
     * ("/uploads/menu-images/abc.jpg") or just the file name ("abc.jpg").
     */
    public void delete(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }

        String fileName = storedPath.substring(storedPath.lastIndexOf('/') + 1);
        if (fileName.isBlank() || fileName.contains("..")) {
            return;
        }

        try {
            Path target = this.storageLocation.resolve(fileName).normalize();
            if (target.startsWith(this.storageLocation)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            // Orphaned files are not critical - do not fail the main operation.
        }
    }

    /** Removes a file, ignoring failures. Used for cleanup on error paths. */
    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Nothing useful to do; a stray temp file is not worth failing over.
        }
    }

    /**
     * Identifies the real image format from its signature (magic bytes).
     * Returns the canonical format name, or null when the bytes are not a
     * supported image. Only the first 12 bytes are read, so this is cheap
     * regardless of file size.
     */
    private String detectFormat(Path path) {
        byte[] h = readHeader(path, 12);
        if (h.length < 4) {
            return null;
        }

        int b0 = h[0] & 0xFF, b1 = h[1] & 0xFF, b2 = h[2] & 0xFF, b3 = h[3] & 0xFF;

        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) {
            return "JPEG";
        }
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) {
            return "PNG";
        }
        if (b0 == 'G' && b1 == 'I' && b2 == 'F' && b3 == '8') {
            return "GIF";
        }

        // WEBP: "RIFF" ???? "WEBP"
        if (h.length >= 12) {
            int b8 = h[8] & 0xFF, b9 = h[9] & 0xFF, b10 = h[10] & 0xFF, b11 = h[11] & 0xFF;
            if (b0 == 'R' && b1 == 'I' && b2 == 'F' && b3 == 'F'
                    && b8 == 'W' && b9 == 'E' && b10 == 'B' && b11 == 'P') {
                return "WEBP";
            }

            // ISO-BMFF family (HEIC / HEIF / AVIF): bytes 4-7 are the "ftyp" box.
            if (b0 == 0x66 && b1 == 0x74 && b2 == 0x79 && b3 == 0x70) {
                return "HEIC/HEIF or AVIF";
            }
        }

        // BMP
        if (b0 == 'B' && b1 == 'M') {
            return "BMP";
        }
        // TIFF (little- and big-endian)
        if ((b0 == 'I' && b1 == 'I' && b2 == 0x2A && b3 == 0x00)
                || (b0 == 'M' && b1 == 'M' && b2 == 0x00 && b3 == 0x2A)) {
            return "TIFF";
        }
        if (b0 == 0x25 && b1 == 0x50 && b2 == 0x44 && b3 == 0x46) {
            return "PDF";
        }
        if (b0 == 0x1A && b1 == 0x45 && b2 == 0xDF && b3 == 0xA3) {
            return "Matroska/WebM video";
        }
        // SVG is XML text; check for a leading "<"
        if (b0 == '<') {
            return "possibly SVG or XML text";
        }

        return null;
    }

    /** Human-readable hex prefix, so the user can see what the file really is. */
    private String describeHeader(Path path) {
        byte[] h = readHeader(path, 8);
        if (h.length == 0) {
            return "an empty file";
        }
        StringBuilder sb = new StringBuilder("0x");
        for (byte b : h) {
            sb.append(String.format("%02X", b));
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    private byte[] readHeader(Path path, int length) {
        byte[] header = new byte[length];
        try (InputStream in = Files.newInputStream(path)) {
            int read = in.readNBytes(header, 0, length);
            if (read <= 0) {
                return new byte[0];
            }
            if (read < length) {
                byte[] trimmed = new byte[read];
                System.arraycopy(header, 0, trimmed, 0, read);
                return trimmed;
            }
        } catch (IOException e) {
            return new byte[0];
        }
        return header;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File too large. Maximum size is 5 MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Only JPEG, PNG, WebP or GIF images are allowed");
        }
    }
}