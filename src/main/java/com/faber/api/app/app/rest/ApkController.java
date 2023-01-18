package com.faber.api.app.app.rest;

import com.faber.core.annotation.FaLogBiz;
import com.faber.core.web.rest.BaseController;
import com.faber.api.app.app.biz.ApkBiz;
import com.faber.api.app.app.entity.Apk;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP-APK表
 *
 * @author Farando
 * @email faberxu@gmail.com
 * @date 2023-01-18 20:31:39
 */
@FaLogBiz("APP-APK表")
@RestController
@RequestMapping("/api/app/apk")
public class ApkController extends BaseController<ApkBiz, Apk, Integer> {

}