package org.codingburgas.demetra.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class VideoStorageService {
    private static final long MAX_SIZE = 100L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("video/mp4", "video/webm");
    private final Path storageDirectory;

    public VideoStorageService(@Value("${demetra.upload.video-dir:uploads/videos}") String directory) {
        this.storageDirectory = Paths.get(directory).toAbsolutePath().normalize();
    }

    public StoredVideo store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Позволени са само MP4 и WebM видеа.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("Видеото не може да бъде по-голямо от 100 MB.");
        }
        String extension = "video/mp4".equals(contentType) ? ".mp4" : ".webm";
        String fileName = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(storageDirectory);
            Files.copy(file.getInputStream(), storageDirectory.resolve(fileName));
            return new StoredVideo(fileName, StringUtils.cleanPath(file.getOriginalFilename() == null ? fileName : file.getOriginalFilename()), contentType);
        } catch (IOException ex) {
            throw new IllegalStateException("Видеото не може да бъде записано.", ex);
        }
    }

    public Resource load(String fileName) {
        try {
            Path path = safePath(fileName);
            Resource resource = new UrlResource(path.toUri());
            return resource.exists() && resource.isReadable() ? resource : null;
        } catch (IOException ex) {
            return null;
        }
    }

    public void delete(String fileName) {
        if (fileName == null || fileName.isBlank()) return;
        try {
            Files.deleteIfExists(safePath(fileName));
        } catch (IOException ignored) {
            // Storage cleanup must not roll back the audited database decision.
        }
    }

    private Path safePath(String fileName) {
        Path path = storageDirectory.resolve(fileName).normalize();
        if (!path.getParent().equals(storageDirectory)) {
            throw new IllegalArgumentException("Невалидно име на видео файл.");
        }
        return path;
    }

    public record StoredVideo(String path, String originalFileName, String contentType) {}
}
