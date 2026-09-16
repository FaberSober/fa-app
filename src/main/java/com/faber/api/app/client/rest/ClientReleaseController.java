package com.faber.api.app.client.rest;

import com.faber.api.app.client.biz.ClientReleaseBiz;
import com.faber.api.app.client.entity.ClientRelease;
import com.faber.core.annotation.FaLogBiz;
import com.faber.core.web.rest.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Desktop 客户端版本管理接口。 */
@FaLogBiz("Desktop客户端版本")
@RestController
@RequestMapping("/api/app/client/release")
public class ClientReleaseController extends BaseController<ClientReleaseBiz, ClientRelease, Long> {
}
