package com.faber.api.app.client.vo.ret;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

/** Tauri 客户端更新清单。 */
@Data
public class ClientUpdateManifest {

    private String version;
    private String notes;

    @JsonProperty("pub_date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    private Date pubDate;

    private String url;
    private String signature;
}
