package com.heihu.workorder.service;

import com.heihu.workorder.dto.CustomFieldAttachmentResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.CustomFieldAttachment;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.repository.CustomFieldAttachmentRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.util.AttachmentMediaTypes;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Transactional
public class CustomFieldAttachmentService {
    private static final Logger log = LoggerFactory.getLogger(CustomFieldAttachmentService.class);

    private static final long MAX_ATTACHMENT_SIZE = 20L * 1024L * 1024L;
    private static final int MAX_SCOPE_LENGTH = 32;

    private final CustomFieldAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final ReportImageStorage storage;

    public CustomFieldAttachmentService(
            CustomFieldAttachmentRepository attachmentRepository,
            UserRepository userRepository,
            ReportImageStorage storage
    ) {
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.storage = storage;
    }

    public CustomFieldAttachmentResponse upload(
            Long factoryId,
            Long userId,
            String scope,
            Long fieldId,
            String fieldType,
            MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择文件");
        }
        if (file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "单个附件不能超过20MB");
        }

        String normalizedFieldType = normalizeFieldType(fieldType);
        String normalizedScope = normalizeScope(scope);
        String originalName = sanitizeOriginalFileName(file.getOriginalFilename());
        String ext = extension(originalName);
        String contentType = normalizeContentType(file.getContentType());
        if ("image".equals(normalizedFieldType)) {
            if (!isAllowedImageMetadata(ext, contentType)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "仅支持 jpg、jpeg、png、webp 图片");
            }
            validateImageSignature(file, ext);
            contentType = resolvedImageContentType(ext);
        }

        FactoryUser uploader = userRepository.findByIdAndFactoryId(userId, factoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        String storedName = "cf_" + UUID.randomUUID().toString().replace("-", "") + (StringUtils.hasText(ext) ? "." + ext : "");
        storage.store(storedName, file);
        boolean transactionOwnsCleanup = cleanupAttachmentOnRollback(storedName);

        CustomFieldAttachment attachment = new CustomFieldAttachment();
        attachment.setFactory(uploader.getFactory());
        attachment.setUploader(uploader);
        attachment.setScope(normalizedScope);
        attachment.setFieldId(fieldId);
        attachment.setFieldType(normalizedFieldType);
        attachment.setOriginalFileName(originalName);
        attachment.setStoredFileName(storedName);
        attachment.setAccessPath("/api/custom-field-attachments/pending");
        attachment.setFileSize(file.getSize());
        attachment.setContentType(StringUtils.hasText(contentType) ? contentType : "application/octet-stream");
        attachment.setCreatedAt(LocalDateTime.now());
        try {
            CustomFieldAttachment saved = attachmentRepository.save(attachment);
            saved.setAccessPath("/api/custom-field-attachments/" + saved.getId() + "/file");
            return CustomFieldAttachmentResponse.from(attachmentRepository.save(saved));
        } catch (RuntimeException failure) {
            if (!transactionOwnsCleanup) deleteUncommittedAttachment(storedName);
            throw failure;
        }
    }

    private boolean cleanupAttachmentOnRollback(String storedName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return false;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) deleteUncommittedAttachment(storedName);
            }
        });
        return true;
    }

    private void deleteUncommittedAttachment(String storedName) {
        try {
            if (!storage.delete(storedName)) log.warn("Unable to remove rolled-back attachment: {}", storedName);
        } catch (RuntimeException failure) {
            log.warn("Unable to remove rolled-back attachment: {}", storedName, failure);
        }
    }

    @Transactional(readOnly = true)
    public AttachmentDownload getDownload(Long factoryId, Long attachmentId) {
        CustomFieldAttachment attachment = attachmentRepository.findById(attachmentId)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "附件不存在"));
        return new AttachmentDownload(attachment, storage.load(attachment.getStoredFileName()));
    }

    private String normalizeScope(String scope) {
        String value = StringUtils.hasText(scope) ? scope.trim() : "custom";
        if (value.length() > MAX_SCOPE_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "scope invalid");
        }
        return value;
    }

    private String normalizeFieldType(String fieldType) {
        String value = StringUtils.hasText(fieldType) ? fieldType.trim().toLowerCase(Locale.ROOT) : "file";
        return "image".equals(value) ? "image" : "file";
    }

    private void validateImageSignature(MultipartFile file, String ext) {
        byte[] header = new byte[12];
        int length = 0;
        try (InputStream input = file.getInputStream()) {
            while (length < header.length) {
                int read = input.read(header, length, header.length - length);
                if (read < 0) break;
                if (read == 0) {
                    int next = input.read();
                    if (next < 0) break;
                    header[length++] = (byte) next;
                } else {
                    length += read;
                }
            }
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "图片读取失败");
        }
        if (!hasImageSignature(ext, header, length)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "图片文件内容不合法");
        }
    }

    private boolean hasImageSignature(String ext, byte[] header, int length) {
        if ("jpg".equals(ext) || "jpeg".equals(ext)) {
            return length >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF;
        }
        if ("png".equals(ext)) {
            return length >= 8
                    && (header[0] & 0xFF) == 0x89
                    && header[1] == 0x50
                    && header[2] == 0x4E
                    && header[3] == 0x47
                    && header[4] == 0x0D
                    && header[5] == 0x0A
                    && header[6] == 0x1A
                    && header[7] == 0x0A;
        }
        if ("webp".equals(ext)) {
            return length >= 12
                    && header[0] == 0x52
                    && header[1] == 0x49
                    && header[2] == 0x46
                    && header[3] == 0x46
                    && header[8] == 0x57
                    && header[9] == 0x45
                    && header[10] == 0x42
                    && header[11] == 0x50;
        }
        return false;
    }

    private boolean isAllowedImageMetadata(String ext, String contentType) {
        if (!isAllowedImageExt(ext)) {
            return false;
        }
        if (!StringUtils.hasText(contentType)) {
            return true;
        }
        return resolvedImageContentType(ext).equals(contentType);
    }

    private boolean isAllowedImageExt(String ext) {
        return "jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext) || "webp".equals(ext);
    }

    private String resolvedImageContentType(String ext) {
        if ("jpg".equals(ext) || "jpeg".equals(ext)) {
            return "image/jpeg";
        }
        if ("png".equals(ext)) {
            return "image/png";
        }
        if ("webp".equals(ext)) {
            return "image/webp";
        }
        return "";
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private String normalizeContentType(String contentType) {
        return AttachmentMediaTypes.normalize(contentType);
    }

    private String sanitizeOriginalFileName(String fileName) {
        String value = StringUtils.hasText(fileName) ? fileName.trim() : "attachment";
        value = value.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        value = value.replaceAll("[\\r\\n\\t\\x00]", "_").trim();
        if (!StringUtils.hasText(value)) {
            value = "attachment";
        }
        return value.length() > 255 ? value.substring(value.length() - 255) : value;
    }

    public static class AttachmentDownload {
        private final CustomFieldAttachment attachment;
        private final Resource resource;

        public AttachmentDownload(CustomFieldAttachment attachment, Resource resource) {
            this.attachment = attachment;
            this.resource = resource;
        }

        public CustomFieldAttachment getAttachment() {
            return attachment;
        }

        public Resource getResource() {
            return resource;
        }
    }
}
