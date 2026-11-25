package com.heihu.workorder.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.apache.http.conn.util.InetAddressUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolves only a bounded XFF chain from explicitly trusted socket peers; never performs hostname DNS. */
@Component
public final class ClientIpResolver {
    private static final int MAX_HEADER_LENGTH = 4096;
    private static final int MAX_FORWARDED_HOPS = 64;
    private final List<Network> trusted;

    public ClientIpResolver(@Value("${app.network.trusted-proxies:}") String configuration) {
        if (configuration == null || configuration.trim().isEmpty()) {
            trusted = Collections.emptyList();
            return;
        }
        if (configuration.length() > 8192) throw invalidConfiguration();
        List<Network> networks = new ArrayList<>();
        for (String entry : configuration.split(",", -1)) {
            if (!entry.trim().isEmpty()) networks.add(Network.parse(entry.trim()));
        }
        if (networks.size() > 128) throw invalidConfiguration();
        trusted = Collections.unmodifiableList(networks);
    }

    public String getClientIp(HttpServletRequest request) {
        if (request == null) return null;
        Address peer = Address.parse(request.getRemoteAddr());
        String fallback = peer == null ? "-" : peer.bucket;
        if (peer == null || !isTrusted(peer)) return fallback;
        String header = request.getHeader("X-Forwarded-For");
        if (header == null || header.length() > MAX_HEADER_LENGTH || header.trim().isEmpty()) return fallback;
        String[] entries = header.split(",", -1);
        if (entries.length > MAX_FORWARDED_HOPS) return fallback;
        List<Address> chain = new ArrayList<>();
        for (String entry : entries) {
            Address address = Address.parse(entry);
            if (address == null) return fallback;
            chain.add(address);
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            Address address = chain.get(i);
            if (!isTrusted(address)) return address.bucket;
        }
        // An all-trusted chain does not establish a distinct untrusted client.
        return fallback;
    }

    private boolean isTrusted(Address address) {
        for (Network network : trusted) if (network.contains(address.bytes)) return true;
        return false;
    }

    private static IllegalArgumentException invalidConfiguration() {
        return new IllegalArgumentException("app.network.trusted-proxies requires explicit IP addresses or bounded CIDRs; hostnames and /0 are not allowed");
    }

    private static final class Address {
        final byte[] bytes;
        final String bucket;
        Address(InetAddress address) {
            bytes = address.getAddress();
            bucket = address.isLoopbackAddress() && bytes.length == 16 ? "127.0.0.1" : address.getHostAddress();
        }
        static Address parse(String value) {
            if (value == null) return null;
            String literal = value.trim();
            if (!InetAddressUtils.isIPv4Address(literal) && !InetAddressUtils.isIPv6Address(literal)) return null;
            try {
                // InetAddress sees only validated numeric literals, not hostnames.
                return new Address(InetAddress.getByName(literal));
            } catch (UnknownHostException invalid) { return null; }
        }
    }

    private static final class Network {
        final byte[] address;
        final int bits;
        Network(byte[] address, int bits) { this.address = address; this.bits = bits; }
        static Network parse(String value) {
            String[] parts = value.split("/", -1);
            if (parts.length > 2) throw invalidConfiguration();
            Address address = Address.parse(parts[0]);
            if (address == null) throw invalidConfiguration();
            int maximum = address.bytes.length * 8, bits = maximum;
            if (parts.length == 2) {
                if (!parts[1].matches("[0-9]{1,3}")) throw invalidConfiguration();
                bits = Integer.parseInt(parts[1]);
            }
            if (bits < 1 || bits > maximum) throw invalidConfiguration();
            return new Network(address.bytes, bits);
        }
        boolean contains(byte[] candidate) {
            if (address.length != candidate.length) return false;
            int full = bits / 8;
            for (int i = 0; i < full; i++) if (address[i] != candidate[i]) return false;
            int remainder = bits % 8;
            int mask = remainder == 0 ? 0 : (0xff << (8 - remainder)) & 0xff;
            return remainder == 0 || (address[full] & mask) == (candidate[full] & mask);
        }
    }
}
