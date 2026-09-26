package com.faber.api.app.release.vo.ret;

/** WGT 上传后匹配到的应用及版本信息。 */
public record AppReleaseAutoMatchPreviewRet(
        Integer appId,
        String appName,
        String applicationId,
        String dcloudAppId,
        String currentApkVersionCode,
        String currentApkVersionName,
        String wgtVersionName,
        String wgtVersionCode
) {}
