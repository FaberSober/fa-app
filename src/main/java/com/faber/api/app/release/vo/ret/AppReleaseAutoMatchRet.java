package com.faber.api.app.release.vo.ret;

import com.faber.api.app.release.entity.AppRelease;

/** WGT 自动匹配应用后创建的发布草稿响应。 */
public record AppReleaseAutoMatchRet(
        AppRelease release,
        String appName,
        String applicationId,
        String dcloudAppId
) {}
