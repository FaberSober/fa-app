package com.faber.api.app.app.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.faber.core.annotation.FaModalName;
import com.faber.core.bean.BaseDelEntity;
import lombok.Data;

    
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

    @ExcelProperty("ID")
    @TableId(type = IdType.AUTO)
    private Integer id;

    @ExcelProperty("应用名称")
    private String name;

    @ExcelProperty("应用包名")
    private String applicationId;

    @ExcelProperty("当前版本号")
    private Long versionCode;

    @ExcelProperty("当前版本名称")
    private String versionName;

    @ExcelProperty("图标文件ID")
    private String iconId;

}
