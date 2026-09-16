package com.faber.api.app.release.rest;

import com.faber.api.app.release.biz.AppReleasePackageBiz;
import com.faber.api.app.release.entity.AppReleasePackage;
import com.faber.core.annotation.FaLogBiz;
import com.faber.core.annotation.FaLogOpr;
import com.faber.core.enums.LogCrudEnum;
import com.faber.core.vo.msg.Ret;
import com.faber.core.web.rest.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** 应用版本发布包管理接口。 */
@FaLogBiz("应用版本发布包")
@RestController
@RequestMapping("/api/app/app/releasePackage")
public class AppReleasePackageController extends BaseController<AppReleasePackageBiz, AppReleasePackage, Long> {

    @FaLogOpr(value = "上传WGT增量包", crud = LogCrudEnum.C)
    @PostMapping("/uploadWgt")
    public Ret<AppReleasePackage> uploadWgt(@RequestParam("releaseId") Long releaseId,
                                            @RequestParam("baseVersionCode") Long baseVersionCode,
                                            @RequestParam("file") MultipartFile file) throws IOException {
        return ok(baseBiz.uploadWgt(releaseId, baseVersionCode, file));
    }
}
