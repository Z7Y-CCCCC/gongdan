package com.heihu.workorder.externaldata;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import com.heihu.workorder.exception.ApiException;
import org.springframework.http.HttpStatus;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Key ring permits rotation without losing older saved secrets. Never serializes plaintext. */
@Component
public class SourceSecrets {
    private final Map<String, byte[]> keys = new HashMap<>();
    private final String activeId;
    private final SecureRandom random = new SecureRandom();
    public SourceSecrets(@Value("${app.external-data.keys:}") String configured,
                         @Value("${app.external-data.active-key:}") String activeId) {
        this.activeId = activeId;
        if (!configured.trim().isEmpty()) {
            try {
                for (String entry : configured.split(",")) {
                    String[] pair = entry.trim().split(":", 2);
                    if (pair.length != 2 || !pair[0].matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException();
                    byte[] key = Base64.getDecoder().decode(pair[1]);
                    if (key.length != 32 || keys.put(pair[0], key) != null) throw new IllegalArgumentException();
                }
                if (!keys.containsKey(activeId)) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw new IllegalStateException("外部数据源密钥配置无效（需命名的 32 字节 Base64 密钥）"); }
        }
    }
    public boolean ready() { return keys.containsKey(activeId); }
    public String encrypt(String plain, String context) {
        if (!ready()) throw unavailable();
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keys.get(activeId), "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return activeId + "." + Base64.getEncoder().encodeToString(nonce) + "." +
                Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) { throw unavailable(); }
    }
    public String decrypt(String encoded, String context) {
        if (encoded == null) return "";
        try {
            String[] parts = encoded.split("\\.");
            byte[] key = keys.get(parts[0]);
            if (parts.length != 3 || key == null) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[1])));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException | IndexOutOfBoundsException e) { throw unavailable(); }
    }
    private ApiException unavailable() { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "外部数据源密钥不可用，请联系运维配置或恢复密钥"); }
}
