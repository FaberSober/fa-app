package com.faber.api.app.app.rest;

import com.faber.core.annotation.FaLogBiz;
import com.faber.core.web.rest.BaseController;
import com.faber.api.app.app.biz.ApkVersionBiz;
import com.faber.api.app.app.entity.ApkVersion;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP-APK版本表
 *
 * @author Farando
 * @email faberxu@gmail.com
 * @date 2023-01-18 20:31:39
 */
@FaLogBiz("APP-APK版本表")
@RestController
@RequestMapping("/api/app/apkVersion")
public class ApkVersionController extends BaseController<ApkVersionBiz, ApkVersion, Integer> {

}