package com.pharmacy.service;

import com.pharmacy.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp", "svg");
    private final Path root;

    public FileStorageService(AppProperties props) {
        this.root = Path.of(props.uploadDir()).toAbsolutePath().normalize();
    }

    /** Stores an uploaded image and returns its public URL path (e.g. /uploads/products/abc.png), or null if empty. */
    public String storeImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) return null;
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!IMAGE_EXT.contains(ext)) throw new BusinessException("error.imageType");
        try {
            Path dir = root.resolve(folder);
            Files.createDirectories(dir);
            String fileName = UUID.randomUUID() + "." + ext;
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            }
            return "/uploads/" + folder + "/" + fileName;
        } catch (IOException e) {
            throw new BusinessException("error.upload");
        }
    }

    /** Resolves a stored /uploads/... path to a file on disk (used when embedding the logo in PDFs). */
    public Path resolve(String publicPath) {
        if (publicPath == null || !publicPath.startsWith("/uploads/")) return null;
        Path p = root.resolve(publicPath.substring("/uploads/".length())).normalize();
        return p.startsWith(root) && Files.exists(p) ? p : null;
    }
}
