package com.faber.api.app.release.vo.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 客户端公开版本检查请求。 */
@Data
public class AppReleaseCheckReq {

    @NotBlank
    @Size(max = 64)
    private String appCode;

    @NotBlank
    @Size(max = 32)
    private String platform;

    @NotNull
    @Min(0)
    private Long currentVersionCode;

    @Size(max = 128)
    private String deviceId;

    @NotBlank
    @Size(max = 32)
    private String channel = "stable";
}
