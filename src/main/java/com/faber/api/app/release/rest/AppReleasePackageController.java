package com.faber.api.app.release.rest;

import com.faber.api.app.release.biz.AppReleasePackageBiz;
import com.faber.api.app.release.entity.AppReleasePackage;
import com.faber.core.annotation.FaLogBiz;
import com.faber.core.annotation.FaLogOpr;
import com.faber.core.config.annotation.Permission;
import com.faber.core.enums.LogCrudEnum;
import com.faber.core.vo.msg.Ret;
import com.faber.core.web.rest.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** 应用版本发布包管理接口。 */
@FaLogBiz("应用版本发布包")
@Permission(permission = "/admin/app/app/apk")
@RestController
@RequestMapping("/api/app/app/releasePackage")
public class AppReleasePackageController extends BaseController<AppReleasePackageBiz, AppReleasePackage, Long> {

    @FaLogOpr(value = "按版本查询发布包", crud = LogCrudEnum.R)
    @GetMapping("/byRelease/{releaseId}")
    public Ret<List<AppReleasePackage>> byRelease(@PathVariable Long releaseId) {
        return ok(baseBiz.listByReleaseId(releaseId));
    }

    @FaLogOpr(value = "上传WGT增量包", crud = LogCrudEnum.C)
    @PostMapping("/uploadWgt")
    public Ret<AppReleasePackage> uploadWgt(@RequestParam("releaseId") Long releaseId,
                                            @RequestParam("file") MultipartFile file) throws IOException {
        return ok(baseBiz.uploadWgt(releaseId, file));
    }
}
