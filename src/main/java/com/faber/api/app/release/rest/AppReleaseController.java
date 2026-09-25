package com.faber.api.app.release.rest;

import com.faber.api.app.release.biz.AppReleaseBiz;
import com.faber.api.app.release.entity.AppRelease;
import com.faber.core.annotation.FaLogBiz;
import com.faber.core.annotation.FaLogOpr;
import com.faber.core.annotation.LogNoRet;
import com.faber.core.config.annotation.ApiToken;
import com.faber.core.config.annotation.IgnoreUserToken;
import com.faber.core.config.annotation.Permission;
import com.faber.core.enums.LogCrudEnum;
import com.faber.core.vo.msg.Ret;
import com.faber.core.web.rest.BaseController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import com.faber.api.app.release.vo.req.AppReleaseCheckReq;
import com.faber.api.app.release.vo.ret.AppReleaseCheckRet;

/** 应用通用版本发布管理接口。 */
@FaLogBiz("应用版本发布")
@Permission(permission = "/admin/app/app/apk")
@RestController
@RequestMapping("/api/app/app/release")
public class AppReleaseController extends BaseController<AppReleaseBiz, AppRelease, Long> {

    @IgnoreUserToken
    @LogNoRet
    @PostMapping("/check")
    public Ret<AppReleaseCheckRet> check(@Validated @RequestBody AppReleaseCheckReq request) {
        return ok(baseBiz.checkWgt(request));
    }

    @IgnoreUserToken
    @LogNoRet
    @PostMapping("/checkApk")
    public Ret<AppReleaseCheckRet> checkApk(@Validated @RequestBody AppReleaseCheckReq request) throws IOException {
        return ok(baseBiz.checkApk(request));
    }

    @IgnoreUserToken
    @LogNoRet
    @PostMapping("/checkWgt")
    public Ret<AppReleaseCheckRet> checkWgt(@Validated @RequestBody AppReleaseCheckReq request) {
        return ok(baseBiz.checkWgt(request));
    }

    @FaLogOpr(value = "上传WGT并创建发布草稿", crud = LogCrudEnum.C)
    @ApiToken
    @PostMapping("/createWgtDraft")
    public Ret<AppRelease> createWgtDraft(@RequestParam Integer appId,
                                          @RequestParam(required = false) Long minSupportedVersionCode,
                                          @RequestParam(defaultValue = "stable") String channel,
                                          @RequestParam(required = false) String releaseNote,
                                          @RequestParam("file") MultipartFile file) throws IOException {
        return ok(baseBiz.createWgtDraft(appId, minSupportedVersionCode, channel, releaseNote, file));
    }

    @FaLogOpr(value = "发布应用版本", crud = LogCrudEnum.U)
    @ApiToken
    @PostMapping("/publish/{id}")
    public Ret<AppRelease> publish(@PathVariable Long id) {
        return ok(baseBiz.publish(id));
    }

    @FaLogOpr(value = "撤回应用版本", crud = LogCrudEnum.U)
    @PostMapping("/revoke/{id}")
    public Ret<AppRelease> revoke(@PathVariable Long id) {
        return ok(baseBiz.revoke(id));
    }
}
