package com.faber.api.app.release.entity;

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

/** 应用版本对应的平台发布包。 */
@Data
@EqualsAndHashCode(callSuper = true)
@FaModalName(name = "应用版本发布包")
@TableName("app_release_package")
public class AppReleasePackage extends BaseDelEntity {

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
    private String packageType;

    /** WGT 增量包对应的客户端基础 versionCode，完整包为空。 */
    private Long baseVersionCode;

    @NotBlank
    private String fileId;

    private Long size;

    @NotBlank
    private String sha256;
}
