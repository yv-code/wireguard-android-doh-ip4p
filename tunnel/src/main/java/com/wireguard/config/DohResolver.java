/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

import com.wireguard.util.NonNullForAll;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import androidx.annotation.Nullable;

/**
 * DNS resolver implementation using DNS over HTTPS (DoH) according to RFC 8484.
 */
@NonNullForAll
public final class DohResolver implements DnsResolver {
    private static final int DNS_TYPE_A = 1;
    private static final int DNS_TYPE_AAAA = 28;
    private static final int DNS_CLASS_IN = 1;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final String ACCEPT_HEADER = "application/dns-message";

    private final String serverUrl;

    public DohResolver(final String serverUrl) {
        this.serverUrl = serverUrl;
    }

    @Override
    public List<InetAddress> resolve(final String host) throws Exception {
        final List<InetAddress> results = new ArrayList<>();

        // Try to resolve both A (IPv4) and AAAA (IPv6) records
        try {
            results.addAll(resolveType(host, DNS_TYPE_A));
        } catch (final Exception ignored) {
            // IPv4 resolution failed, continue to try IPv6
        }

        try {
            results.addAll(resolveType(host, DNS_TYPE_AAAA));
        } catch (final Exception ignored) {
            // IPv6 resolution failed
        }

        if (results.isEmpty()) {
            throw new Exception("DoH resolution failed for host: " + host);
        }

        return results;
    }

    private List<InetAddress> resolveType(final String host, final int type) throws Exception {
        final byte[] query = buildDnsQuery(host, type);
        final String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(query);
        final URL url = new URL(serverUrl + "?dns=" + encoded);

        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", ACCEPT_HEADER);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setUseCaches(false);

            final int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("DoH server returned HTTP " + responseCode);
            }

            final byte[] response = readAll(conn.getInputStream());
            return parseDnsResponse(response, type);
        } finally {
            conn.disconnect();
        }
    }

    private byte[] buildDnsQuery(final String host, final int type) {
        final ByteBuffer buffer = ByteBuffer.allocate(512);
        final Random random = new Random();

        // Transaction ID (2 bytes)
        buffer.putShort((short) random.nextInt(65536));

        // Flags (2 bytes): standard query, recursion desired
        buffer.putShort((short) 0x0100);

        // Question count (2 bytes)
        buffer.putShort((short) 1);

        // Answer count (2 bytes)
        buffer.putShort((short) 0);

        // Authority count (2 bytes)
        buffer.putShort((short) 0);

        // Additional count (2 bytes)
        buffer.putShort((short) 0);

        // Question section: domain name
        final String[] labels = host.split("\\.");
        for (final String label : labels) {
            final byte[] labelBytes = label.getBytes(StandardCharsets.US_ASCII);
            buffer.put((byte) labelBytes.length);
            buffer.put(labelBytes);
        }
        buffer.put((byte) 0); // Root label

        // Question type (2 bytes)
        buffer.putShort((short) type);

        // Question class (2 bytes): IN
        buffer.putShort((short) DNS_CLASS_IN);

        final byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        return result;
    }

    private List<InetAddress> parseDnsResponse(final byte[] response, final int expectedType) throws Exception {
        final List<InetAddress> addresses = new ArrayList<>();
        final ByteBuffer buffer = ByteBuffer.wrap(response);

        if (response.length < 12) {
            throw new Exception("DNS response too short");
        }

        // Skip transaction ID
        buffer.getShort();

        // Flags
        final short flags = buffer.getShort();
        final int rcode = flags & 0x0F;
        if (rcode != 0) {
            throw new Exception("DNS error code: " + rcode);
        }

        // Question count
        final int qdcount = buffer.getShort() & 0xFFFF;

        // Answer count
        final int ancount = buffer.getShort() & 0xFFFF;

        // Skip authority and additional counts
        buffer.getShort();
        buffer.getShort();

        // Skip question section
        for (int i = 0; i < qdcount; i++) {
            skipName(buffer);
            buffer.getShort(); // type
            buffer.getShort(); // class
        }

        // Parse answer section
        for (int i = 0; i < ancount; i++) {
            skipName(buffer);
            final int type = buffer.getShort() & 0xFFFF;
            buffer.getShort(); // class
            buffer.getInt(); // TTL
            final int rdlength = buffer.getShort() & 0xFFFF;

            if (type == expectedType) {
                final byte[] rdata = new byte[rdlength];
                buffer.get(rdata);
                try {
                    final InetAddress address = InetAddress.getByAddress(rdata);
                    if ((expectedType == DNS_TYPE_A && address instanceof Inet4Address) ||
                        (expectedType == DNS_TYPE_AAAA && address instanceof Inet6Address)) {
                        addresses.add(address);
                    }
                } catch (final Exception ignored) {
                    // Invalid address data, skip
                }
            } else {
                // Skip RDATA for other record types
                buffer.position(buffer.position() + rdlength);
            }
        }

        return addresses;
    }

    private void skipName(final ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            final int len = buffer.get() & 0xFF;
            if (len == 0) {
                break;
            } else if ((len & 0xC0) == 0xC0) {
                // Pointer - skip one more byte
                buffer.get();
                break;
            } else {
                buffer.position(buffer.position() + len);
            }
        }
    }

    private byte[] readAll(final InputStream input) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        return output.toByteArray();
    }
}
