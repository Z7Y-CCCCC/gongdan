package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "app.upload.storage-type", havingValue = "local", matchIfMissing = true)
public class LocalReportImageStorage implements ReportImageStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalReportImageStorage.class);

    private final Path root;

    public LocalReportImageStorage(@Value("${app.upload.report-image-dir:uploads/report-images}") String reportImageDir) {
        this.root = Paths.get(reportImageDir).toAbsolutePath().normalize();
    }

    @Override
    public void store(String storedFileName, MultipartFile file) {
        Path target = resolveStoredFile(storedFileName);
        try {
            Files.createDirectories(root);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "图片保存失败");
        }
    }

    @Override
    public Resource load(String storedFileName) {
        Path file = resolveStoredFile(storedFileName);
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "图片文件不存在");
            }
            return resource;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "图片文件不存在");
        }
    }

    @Override
    public boolean delete(String storedFileName) {
        Path file = resolveStoredFile(storedFileName);
        try {
            return Files.deleteIfExists(file) || !Files.exists(file);
        } catch (IOException ex) {
            log.warn("Delete report image failed [storedFileName={}]", storedFileName, ex);
            return false;
        }
    }

    private Path resolveStoredFile(String storedFileName) {
        Path file = root.resolve(storedFileName).normalize();
        if (!file.startsWith(root)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "图片不存在");
        }
        return file;
    }
}
