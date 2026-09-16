package com.faber.api.app.release.rest;

import com.faber.api.app.release.biz.AppReleaseBiz;
import com.faber.api.app.release.entity.AppRelease;
import com.faber.core.annotation.FaLogBiz;
import com.faber.core.annotation.FaLogOpr;
import com.faber.core.enums.LogCrudEnum;
import com.faber.core.vo.msg.Ret;
import com.faber.core.web.rest.BaseController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 应用通用版本发布管理接口。 */
@FaLogBiz("应用版本发布")
@RestController
@RequestMapping("/api/app/app/release")
public class AppReleaseController extends BaseController<AppReleaseBiz, AppRelease, Long> {

    @FaLogOpr(value = "发布应用版本", crud = LogCrudEnum.U)
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
