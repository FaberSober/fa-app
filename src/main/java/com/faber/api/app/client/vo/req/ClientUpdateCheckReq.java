package com.faber.api.app.client.vo.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Tauri 客户端更新检查请求。 */
@Data
public class ClientUpdateCheckReq {

    @NotBlank
    @Size(max = 64)
    private String currentVersion;

    @NotBlank
    @Size(max = 64)
    private String target;

    @Size(max = 32)
    private String channel = "stable";
}
