package com.faber.api.app.release.rest;

import com.faber.api.app.release.biz.AppReleaseBiz;
import com.faber.api.app.release.entity.AppRelease;
import com.faber.core.annotation.FaLogBiz;
import com.faber.core.web.rest.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 应用通用版本发布管理接口。 */
@FaLogBiz("应用版本发布")
@RestController
@RequestMapping("/api/app/app/release")
public class AppReleaseController extends BaseController<AppReleaseBiz, AppRelease, Long> {
}
