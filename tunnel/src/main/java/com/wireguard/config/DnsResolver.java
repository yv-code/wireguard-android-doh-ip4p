/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

import com.wireguard.util.NonNullForAll;

import java.net.InetAddress;
import java.util.List;

/**
 * Interface for DNS resolution implementations.
 */
@NonNullForAll
public interface DnsResolver {
    /**
     * Resolve a hostname to a list of IP addresses.
     *
     * @param host the hostname to resolve
     * @return a list of resolved IP addresses
     * @throws Exception if resolution fails
     */
    List<InetAddress> resolve(String host) throws Exception;
}
