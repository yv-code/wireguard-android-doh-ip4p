/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

import com.wireguard.util.NonNullForAll;

import java.util.LinkedHashMap;
import java.util.Map;

import androidx.annotation.Nullable;

/**
 * Static configuration class for DNS over HTTPS (DoH) settings.
 * This class provides a bridge between the UI module and the tunnel module.
 */
@NonNullForAll
public final class DohConfig {
    // Priority modes
    public static final String MODE_DOH_FIRST = "doh_first";
    public static final String MODE_SYSTEM_FIRST = "system_first";
    public static final String MODE_DOH_ONLY = "doh_only";
    public static final String MODE_SYSTEM_ONLY = "system_only";

    // Preset DoH providers
    public static final Map<String, String> PRESET_PROVIDERS;

    static {
        PRESET_PROVIDERS = new LinkedHashMap<>();
        // International providers
        PRESET_PROVIDERS.put("Cloudflare", "https://cloudflare-dns.com/dns-query");
        PRESET_PROVIDERS.put("Google", "https://dns.google/dns-query");
        PRESET_PROVIDERS.put("Quad9", "https://dns.quad9.net/dns-query");
        PRESET_PROVIDERS.put("AdGuard", "https://dns.adguard-dns.com/dns-query");
        // Chinese providers
        PRESET_PROVIDERS.put("Alibaba DNS", "https://dns.alidns.com/dns-query");
        PRESET_PROVIDERS.put("Tencent DNSPod", "https://doh.pub/dns-query");
    }

    private static boolean enabled = false;
    @Nullable private static String serverUrl = null;
    @Nullable private static String providerName = null;
    private static String priorityMode = MODE_SYSTEM_ONLY;

    @Nullable private static DohResolver cachedResolver = null;

    private DohConfig() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(final boolean enabled) {
        DohConfig.enabled = enabled;
        cachedResolver = null;
    }

    @Nullable
    public static String getServerUrl() {
        return serverUrl;
    }

    public static void setServerUrl(@Nullable final String serverUrl) {
        DohConfig.serverUrl = serverUrl;
        cachedResolver = null;
    }

    @Nullable
    public static String getProviderName() {
        return providerName;
    }

    public static void setProviderName(@Nullable final String providerName) {
        DohConfig.providerName = providerName;
    }

    public static String getPriorityMode() {
        return priorityMode;
    }

    public static void setPriorityMode(final String priorityMode) {
        DohConfig.priorityMode = priorityMode;
    }

    /**
     * Get the DoH resolver instance. Returns null if DoH is not enabled or not configured.
     */
    @Nullable
    public static DohResolver getResolver() {
        if (!enabled || serverUrl == null || serverUrl.isEmpty()) {
            return null;
        }
        if (cachedResolver == null) {
            cachedResolver = new DohResolver(serverUrl);
        }
        return cachedResolver;
    }

    /**
     * Configure DoH settings from the UI module.
     */
    public static void configure(final boolean enabled, @Nullable final String serverUrl,
                                 @Nullable final String providerName, final String priorityMode) {
        DohConfig.enabled = enabled;
        DohConfig.serverUrl = serverUrl;
        DohConfig.providerName = providerName;
        DohConfig.priorityMode = priorityMode;
        cachedResolver = null;
    }
}
