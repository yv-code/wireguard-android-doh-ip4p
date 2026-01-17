/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

import com.wireguard.util.NonNullForAll;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * DNS resolver implementation using the system's default DNS resolution.
 */
@NonNullForAll
public final class SystemDnsResolver implements DnsResolver {
    private static final SystemDnsResolver INSTANCE = new SystemDnsResolver();

    private SystemDnsResolver() {
    }

    public static SystemDnsResolver getInstance() {
        return INSTANCE;
    }

    @Override
    public List<InetAddress> resolve(final String host) throws UnknownHostException {
        return Arrays.asList(InetAddress.getAllByName(host));
    }
}
