package com.faber.api.app.release.rest;

import com.faber.api.app.release.biz.AppReleasePackageBiz;
import com.faber.api.app.release.entity.AppReleasePackage;
import com.faber.core.annotation.FaLogBiz;
import com.faber.core.web.rest.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 应用版本发布包管理接口。 */
@FaLogBiz("应用版本发布包")
@RestController
@RequestMapping("/api/app/app/releasePackage")
public class AppReleasePackageController extends BaseController<AppReleasePackageBiz, AppReleasePackage, Long> {
}
