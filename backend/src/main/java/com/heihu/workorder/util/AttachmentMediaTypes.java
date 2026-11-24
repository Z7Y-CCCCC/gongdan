package com.heihu.workorder.util;

import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

/** Keeps uploaded and historical metadata usable as a concrete HTTP content type. */
public final class AttachmentMediaTypes {
    private static final int MAX_CONTENT_TYPE_LENGTH = 128;

    private AttachmentMediaTypes() { }

    public static String normalize(String contentType) {
        if (!StringUtils.hasText(contentType)) return "";
        String value = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) value = value.substring(0, semicolon).trim();
        if (value.length() > MAX_CONTENT_TYPE_LENGTH) return "";
        try {
            MediaType parsed = MediaType.parseMediaType(value);
            return parsed.isConcrete() ? parsed.toString() : "";
        } catch (IllegalArgumentException invalid) {
            return "";
        }
    }

    public static MediaType forDownload(String contentType) {
        String normalized = normalize(contentType);
        return normalized.isEmpty() ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(normalized);
    }
}
