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

/** Desktop 客户端版本的平台安装包。 */
@Data
@EqualsAndHashCode(callSuper = true)
@FaModalName(name = "Desktop客户端安装包")
@TableName("app_client_release_artifact")
public class ClientReleaseArtifact extends BaseDelEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull
    @SqlEquals
    private Long releaseId;

    @NotBlank
    @SqlEquals
    private String platform;

    @NotBlank
    @SqlEquals
    private String arch;

    @NotBlank
    private String fileId;

    private String fileName;

    private Long size;

    @NotBlank
    private String sha256;

    @NotBlank
    private String signature;
}
