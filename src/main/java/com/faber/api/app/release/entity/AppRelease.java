package com.faber.api.app.release.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.faber.core.annotation.FaModalName;
import com.faber.core.annotation.SqlEquals;
import com.faber.core.annotation.SqlSearch;
import com.faber.core.bean.BaseDelEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/** 应用通用版本发布记录。 */
@Data
@EqualsAndHashCode(callSuper = true)
@FaModalName(name = "应用版本发布")
@TableName("app_release")
public class AppRelease extends BaseDelEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 兼容现有 app_apk.id。 */
    @NotNull
    @SqlEquals
    private Integer appId;

    @NotBlank
    @SqlSearch
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

    @NotNull
    private Boolean forceUpdate = false;

    private Long minSupportedVersionCode;

    private String releaseNote;

    private Date publishTime;
}
