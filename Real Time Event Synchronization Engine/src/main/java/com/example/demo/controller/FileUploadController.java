package com.example.demo.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class FileUploadController {

    /** Upload directory relative to working dir. Configurable via application.properties */
    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    /**
     * POST /api/upload
     * Accepts a multipart file (image or video, ≤ 10 MB).
     * Saves it to `uploads/` on disk.
     * Returns a JSON body: { "url": "/uploads/<filename>", "fileType": "image/png" }
     *
     * The URL is then sent via WebSocket — no large binary over WS.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                     HttpSession session) throws IOException {

        String username = (String) session.getAttribute("username");
        if (username == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in."));

        if (file.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "No file provided."));

        // Create upload directory if missing
        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);

        // Build a unique filename to prevent collisions
        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "file"
        );
        String filename = UUID.randomUUID() + "_" + originalName;
        Path dest = dir.resolve(filename);

        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

        String fileUrl  = "/" + uploadDir + "/" + filename;
        String fileType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        return ResponseEntity.ok(Map.of("url", fileUrl, "fileType", fileType));
    }
}
