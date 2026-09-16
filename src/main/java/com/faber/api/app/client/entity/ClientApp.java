package com.faber.api.app.client.entity;

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

/** Desktop 客户端应用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@FaModalName(name = "Desktop客户端")
@TableName("app_client")
public class ClientApp extends BaseDelEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank
    @SqlEquals
    private String clientCode;

    @NotBlank
    @SqlSearch
    private String name;

    @NotBlank
    @SqlEquals
    private String identifier;

    @NotNull
    @SqlEquals
    private Boolean enabled = true;

    private String remark;
}
