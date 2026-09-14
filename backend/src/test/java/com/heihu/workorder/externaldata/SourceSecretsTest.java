package com.heihu.workorder.externaldata;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;
class SourceSecretsTest {
    private String key(int value) { byte[] b=new byte[32]; java.util.Arrays.fill(b,(byte)value); return Base64.getEncoder().encodeToString(b); }
    @Test void encryptionIsRandomAndBoundToTenantAndSource() {
        SourceSecrets s=new SourceSecrets("v1:"+key(1),"v1");
        String a=s.encrypt("very-secret","1:5"), b=s.encrypt("very-secret","1:5");
        assertThat(a).isNotEqualTo(b).doesNotContain("very-secret");
        assertThat(s.decrypt(a,"1:5")).isEqualTo("very-secret");
        assertThatThrownBy(() -> s.decrypt(a,"2:5")).hasMessageContaining("密钥不可用");
        assertThatThrownBy(() -> s.decrypt(a,"1:6")).hasMessageContaining("密钥不可用");
    }
    @Test void rotationRetainsOldKeys() {
        String old=new SourceSecrets("v1:"+key(1),"v1").encrypt("secret","1:1");
        SourceSecrets rotated=new SourceSecrets("v1:"+key(1)+",v2:"+key(2),"v2");
        assertThat(rotated.decrypt(old,"1:1")).isEqualTo("secret");
        assertThat(rotated.encrypt("new","1:1")).startsWith("v2.");
        assertThatThrownBy(() -> new SourceSecrets("v2:"+key(2),"v2").decrypt(old,"1:1")).hasMessageContaining("密钥不可用");
    }
    @Test void missingKeyFailsClosedWithoutBreakingCoreStartup() {
        SourceSecrets s=new SourceSecrets("","");
        assertThat(s.ready()).isFalse(); assertThatThrownBy(() -> s.encrypt("x","1:1")).hasMessageContaining("密钥不可用");
    }
    @Test void rejectsInvalidKeyConfiguration() {
        assertThatThrownBy(() -> new SourceSecrets("bad:value","bad")).isInstanceOf(IllegalStateException.class);
    }
}
