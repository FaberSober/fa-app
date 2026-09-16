package com.faber.api.app.client.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.faber.core.annotation.FaModalName;
import com.faber.core.annotation.SqlEquals;
import com.faber.core.bean.BaseDelEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/** Desktop 客户端版本发布记录。 */
@Data
@EqualsAndHashCode(callSuper = true)
@FaModalName(name = "Desktop客户端版本")
@TableName("app_client_release")
public class ClientRelease extends BaseDelEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull
    @SqlEquals
    private Long clientId;

    @NotBlank
    @SqlEquals
    private String versionName;

    @NotNull
    @SqlEquals
    private Long versionCode;

    @NotBlank
    @SqlEquals
    private String channel = "stable";

    @NotBlank
    @SqlEquals
    private String status = "DRAFT";

    private String releaseNotes;

    private Date publishTime;
}
