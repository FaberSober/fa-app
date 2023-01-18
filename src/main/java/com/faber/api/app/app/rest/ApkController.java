package com.faber.api.app.app.rest;

import com.faber.api.app.app.vo.ApkInfo;
import com.faber.core.annotation.FaLogBiz;
import com.faber.core.annotation.FaLogOpr;
import com.faber.core.enums.LogCrudEnum;
import com.faber.core.vo.msg.Ret;
import com.faber.core.web.rest.BaseController;
import com.faber.api.app.app.biz.ApkBiz;
import com.faber.api.app.app.entity.Apk;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * APP-APK表
 *
 * @author Farando
 * @email faberxu@gmail.com
 * @date 2023-01-18 20:31:39
 */
@FaLogBiz("APP-APK表")
@RestController
@RequestMapping("/api/app/app/apk")
public class ApkController extends BaseController<ApkBiz, Apk, Integer> {

    @FaLogOpr(value = "解析APK信息", crud = LogCrudEnum.R)
    @RequestMapping(value = "/getApkInfo/{fileId}", method = RequestMethod.GET)
    @ResponseBody
    public Ret<Apk> getApkInfo(@PathVariable("fileId") String fileId) throws IOException {
        Apk o = baseBiz.getApkInfo(fileId);
        return ok(o);
    }

}