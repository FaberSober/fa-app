package com.faber.api.app.release.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.faber.core.annotation.FaModalName;
import com.faber.core.annotation.SqlEquals;
import com.faber.core.annotation.SqlSearch;
import com.faber.core.bean.BaseDelEntity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    /** WGT 可选最低兼容 APK versionCode；为空表示不限制。 */
    private Long minSupportedVersionCode;

    /** 按安装标识稳定分流的百分比，100表示全量。 */
    @Min(0)
    @Max(100)
    private Integer rolloutPercent = 100;

    /** 逗号或换行分隔的安装标识白名单。 */
    @Size(max = 4000)
    private String targetDeviceIds;

    /** 是否按生产环境异常数自动撤回当前版本。 */
    private Boolean autoRollback = false;

    @Min(1)
    @Max(1_000_000)
    private Integer rollbackErrorThreshold = 10;

    @Min(1)
    @Max(1_440)
    private Integer rollbackWindowMinutes = 15;

    private String releaseNote;

    private Date publishTime;
}
