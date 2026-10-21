package org.codingburgas.demetra;

import org.codingburgas.demetra.services.VideoStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VideoStorageServiceTests {
    @TempDir
    Path tempDir;

    @Test
    void storesAllowedVideoWithGeneratedSafeName() {
        VideoStorageService service = new VideoStorageService(tempDir.toString());
        VideoStorageService.StoredVideo stored = service.store(
                new MockMultipartFile("video", "../../unsafe.mp4", "video/mp4", new byte[]{1, 2, 3}));

        assertEquals("video/mp4", stored.contentType());
        org.junit.jupiter.api.Assertions.assertTrue(stored.path().endsWith(".mp4"));
        org.junit.jupiter.api.Assertions.assertTrue(service.load(stored.path()).exists());
    }

    @Test
    void rejectsUnsupportedVideoType() {
        VideoStorageService service = new VideoStorageService(tempDir.toString());
        assertThrows(IllegalArgumentException.class, () -> service.store(
                new MockMultipartFile("video", "script.exe", "application/octet-stream", new byte[]{1})));
    }
}
