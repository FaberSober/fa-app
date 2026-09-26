package com.faber.api.app.release.vo.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** WGT 自动匹配及草稿创建请求。 */
public record AppReleaseAutoMatchReq(
        @NotBlank String fileId,
        @Positive Long minSupportedVersionCode,
        @Size(max = 32) String channel,
        @Size(max = 4000) String releaseNote
) {}
