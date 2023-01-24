package com.faber.api.app.app.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.faber.core.annotation.FaModalName;
import com.faber.core.bean.BaseDelEntity;
import com.faber.core.config.validator.validator.Vg;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;


/**
 * APP-APK表
 * 
 * @author Farando
 * @email faberxu@gmail.com
 * @date 2023-01-18 20:31:39
 */
@FaModalName(name = "APP-APK表")
@TableName("app_apk")
@Data
public class Apk extends BaseDelEntity {

    @Null(groups = Vg.Crud.C.class)
    @NotNull(groups = Vg.Crud.U.class)
    @ExcelProperty("ID")
    @TableId(type = IdType.AUTO)
    private Integer id;

    @NotNull
    @ExcelProperty("应用名称")
    private String name;

    @NotNull
    @ExcelProperty("应用包名")
    private String applicationId;

    @NotNull
    @ExcelProperty("当前版本号")
    private Long versionCode;

    @NotNull
    @ExcelProperty("当前版本名称")
    private String versionName;

    @NotNull
    @ExcelProperty("apk文件ID")
    private String fileId;

    @NotNull
    @ExcelProperty("图标文件ID")
    private String iconId;

    @NotNull
    @ExcelProperty("短链")
    private String shortCode;

    @ExcelProperty("最近版本信息")
    private String remark;

}
