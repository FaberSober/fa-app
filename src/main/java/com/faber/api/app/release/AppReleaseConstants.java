package com.faber.api.app.release;

import java.util.Set;

/** 通用应用发布模型的固定值。 */
public final class AppReleaseConstants {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_REVOKED = "REVOKED";

    public static final String PLATFORM_ANDROID = "ANDROID";
    public static final String PLATFORM_IOS = "IOS";
    public static final String PLATFORM_APP_PLUS = "APP_PLUS";
    public static final String PLATFORM_MP_WEIXIN = "MP_WEIXIN";
    public static final String PLATFORM_H5 = "H5";

    public static final String PACKAGE_APK = "APK";
    public static final String PACKAGE_IPA = "IPA";
    public static final String PACKAGE_WGT = "WGT";
    public static final String PACKAGE_FULL = "FULL";

    public static final String UPDATE_NONE = "NONE";
    public static final String UPDATE_WGT = "WGT";
    public static final String UPDATE_FULL = "FULL";

    public static final Set<String> STATUSES = Set.of(STATUS_DRAFT, STATUS_PUBLISHED, STATUS_REVOKED);
    public static final Set<String> PLATFORMS = Set.of(
            PLATFORM_ANDROID, PLATFORM_IOS, PLATFORM_APP_PLUS, PLATFORM_MP_WEIXIN, PLATFORM_H5
    );
    public static final Set<String> PACKAGE_TYPES = Set.of(PACKAGE_APK, PACKAGE_IPA, PACKAGE_WGT, PACKAGE_FULL);

    private AppReleaseConstants() {
    }
}
