package com.heihu.workorder.externaldata;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SourcePolicyTest {
    @TempDir Path root;
    SourceConfig http() {
        SourceConfig c=new SourceConfig(); c.code="test"; c.name="test"; c.baseUrl="https://example.com/api/v1"; c.healthPath="/health"; return c;
    }
    SourcePolicy policy() { return new SourcePolicy("1@example.com:443,1@127.0.0.1:443,1@169.254.169.254:443","1@127.0.0.1,1@169.254.169.254","","1@sample",root.toString()); }
    @Test void healthPathPreservesBasePrefix() { assertThat(policy().healthUri(http(),true).toString()).isEqualTo("https://example.com/api/v1/health"); }
    @Test void factoryCannotUseAnotherFactoryTarget() { assertThatThrownBy(() -> policy().validate(http(),2L)).hasMessageContaining("出口授权"); }
    @Test void defaultDeny() { assertThatThrownBy(() -> new SourcePolicy("","","","","").validate(http(),1L)).isInstanceOf(IllegalArgumentException.class); }
    @Test void rejectsUnsafeUrlsAndPaths() {
        for(String path:Arrays.asList("//evil.test/health","/../health","/%2e%2e/health","/health?token=x","/health#x","/a\\b")) {
            SourceConfig c=http(); c.healthPath=path; assertThatThrownBy(() -> policy().validate(c,1L)).isInstanceOf(IllegalArgumentException.class);
        }
        for(String url:Arrays.asList("file:///etc/passwd","https://user:pass@example.com","https://example.com?key=secret","https://example.com:0","http://example.com")) {
            SourceConfig c=http(); c.baseUrl=url; assertThatThrownBy(() -> policy().validate(c,1L)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void blocksLoopbackAndMetadataEvenIfExplicitlyListed() {
        assertThatThrownBy(() -> policy().resolve(1L,"127.0.0.1",443,true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy().resolve(1L,"169.254.169.254",443,true)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void apiKeyCannotSetReservedHeader() {
        for(String header:Arrays.asList("Host","Authorization","Cookie","X-Forwarded-For","Content-Length","x\r\ny")) {
            SourceConfig c=http(); c.authType="API_KEY"; c.headerName=header;
            assertThatThrownBy(() -> policy().validate(c,1L)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void sqliteRegistrationDoesNotCreateFiles() throws Exception {
        assertThatThrownBy(() -> policy().sqliteFile(1L,"sample")).isInstanceOf(java.io.IOException.class);
        assertThat(Files.exists(root.resolve("sample.db"))).isFalse();
        Files.createFile(root.resolve("sample.db"));
        assertThat(policy().sqliteFile(1L,"sample")).isEqualTo(root.resolve("sample.db").toRealPath());
        assertThatThrownBy(() -> policy().sqliteFile(2L,"sample")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy().sqliteFile(1L,"../sample")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void onlyCanonicalIpv4ForJdbc() {
        for(String host:Arrays.asList("localhost","127.1","0x7f000001","127.000.0.1","256.1.1.1","1.2.3.4;user=x"))
            assertThat(SourcePolicy.ipv4(host)).isFalse();
        assertThat(SourcePolicy.ipv4("10.20.0.5")).isTrue();
    }
    @Test void sqliteRequiresLocalAbsoluteOperationalRoot() {
        for(String path:Arrays.asList("relative-directory","\\\\server\\share","//server/share"))
            assertThatThrownBy(() -> new SourcePolicy("","","","1@sample",path).sqliteFile(1L,"sample"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("绝对路径");
    }
}
