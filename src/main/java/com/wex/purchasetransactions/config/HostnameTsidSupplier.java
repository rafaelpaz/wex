package com.wex.purchasetransactions.config;

import io.hypersistence.tsid.TSID;
import java.util.function.Supplier;

/**
 * Derives the TSID node ID from the HOSTNAME environment variable.
 * <p>
 * In Kubernetes, HOSTNAME is automatically set to the pod name.
 * In Docker Compose, it defaults to the container ID.
 * Locally, it falls back to node 0.
 * <p>
 * Uses bitwise masking ({@code & 0x3FF}) to map to the 10-bit node range (0–1023),
 * avoiding the {@code Math.abs(Integer.MIN_VALUE)} edge case.
 */
public class HostnameTsidSupplier implements Supplier<TSID.Factory> {

    @Override
    public TSID.Factory get() {
        String hostname = System.getenv("HOSTNAME");
        int node = (hostname != null) ? (hostname.hashCode() & 0x3FF) : 0;
        return TSID.Factory.builder().withNode(node).build();
    }
}
