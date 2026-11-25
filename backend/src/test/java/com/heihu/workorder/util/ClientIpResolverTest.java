package com.heihu.workorder.util;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {
    @Test void unconfiguredPeersUseTheSocketAddressRegardlessOfForwardingHeaders() {
        MockHttpServletRequest request = request("192.0.2.10", "198.51.100.20");
        request.addHeader("CF-Connecting-IP", "203.0.113.20");
        request.addHeader("X-Real-IP", "203.0.113.21");
        assertThat(new ClientIpResolver("").getClientIp(request)).isEqualTo("192.0.2.10");
        assertThat(IpAddressUtils.getClientIp(request)).isEqualTo("192.0.2.10");
    }

    @Test void configuredRulesDoNotTrustAnUnlistedSocketPeer() {
        assertThat(new ClientIpResolver("192.0.2.10").getClientIp(request("192.0.2.11", "198.51.100.20")))
                .isEqualTo("192.0.2.11");
    }

    @Test void theNearestUntrustedClientIsChosenAfterRemovingExplicitProxyHops() {
        ClientIpResolver resolver = new ClientIpResolver("192.0.2.10,192.0.2.11/32");
        MockHttpServletRequest request = request("192.0.2.10", "203.0.113.20, 198.51.100.20, 192.0.2.11");
        assertThat(resolver.getClientIp(request)).isEqualTo("198.51.100.20");
    }

    @Test void privateClientsAreNotAutomaticallyTreatedAsProxies() {
        assertThat(new ClientIpResolver("127.0.0.1").getClientIp(request("127.0.0.1", "10.99.0.20")))
                .isEqualTo("10.99.0.20");
    }

    @Test void anAllTrustedChainFallsBackToTheSocketPeer() {
        assertThat(new ClientIpResolver("192.0.2.10,192.0.2.11").getClientIp(request("192.0.2.10", "192.0.2.11")))
                .isEqualTo("192.0.2.10");
    }

    @Test void explicitIpv6CidrsResolveAnUntrustedIpv6Client() {
        assertThat(new ClientIpResolver("2001:db8:1::/64").getClientIp(request("2001:db8:1::10", "2001:db8:2::20")))
                .isEqualTo("2001:db8:2:0:0:0:0:20");
    }

    @Test void ipv6LoopbackKeepsTheExistingCanonicalBucket() {
        assertThat(new ClientIpResolver("").getClientIp(request("::1", null))).isEqualTo("127.0.0.1");
        assertThat(new ClientIpResolver("::1").getClientIp(request("::1", "198.51.100.20"))).isEqualTo("198.51.100.20");
    }

    @ParameterizedTest @ValueSource(strings = {"unknown", "", "198.51.100.20,", "198.51.100.20:443", "example.invalid"})
    void malformedForwardingHeadersFallBackWithoutResolvingNames(String header) {
        assertThat(new ClientIpResolver("192.0.2.10").getClientIp(request("192.0.2.10", header)))
                .isEqualTo("192.0.2.10");
    }

    @Test void oversizedForwardingHeadersAreNotParsed() {
        assertThat(new ClientIpResolver("192.0.2.10").getClientIp(request("192.0.2.10",
                String.join("", Collections.nCopies(4097, "x"))))).isEqualTo("192.0.2.10");
    }

    @Test void excessiveForwardingHopCountsAreNotParsed() {
        assertThat(new ClientIpResolver("192.0.2.10").getClientIp(request("192.0.2.10",
                String.join(",", Collections.nCopies(65, "198.51.100.20"))))).isEqualTo("192.0.2.10");
    }

    @ParameterizedTest @ValueSource(strings = {"example.invalid", "0.0.0.0/0", "::/0", "192.0.2.10/33",
            "2001:db8::/129", "192.0.2.10/not-a-prefix", "192.0.2.10/32/extra"})
    void invalidOrUnboundedProxyConfigurationIsRejected(String configuration) {
        assertThatThrownBy(() -> new ClientIpResolver(configuration)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.network.trusted-proxies");
    }

    private MockHttpServletRequest request(String peer, String forwarded) {
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setRemoteAddr(peer);
        if (forwarded != null) request.addHeader("X-Forwarded-For", forwarded);
        return request;
    }
}
