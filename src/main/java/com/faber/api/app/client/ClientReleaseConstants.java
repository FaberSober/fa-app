package com.faber.api.app.client;

import java.util.Set;

/** Desktop 客户端版本发布固定值。 */
public final class ClientReleaseConstants {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String CHANNEL_STABLE = "stable";

    public static final Set<String> PLATFORMS = Set.of("windows", "darwin", "linux");
    public static final Set<String> ARCHES = Set.of("x86_64", "aarch64");

    private ClientReleaseConstants() {
    }
}
