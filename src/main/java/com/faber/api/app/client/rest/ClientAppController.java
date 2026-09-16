package com.faber.api.app.client.rest;

import com.faber.api.app.client.biz.ClientAppBiz;
import com.faber.api.app.client.entity.ClientApp;
import com.faber.core.annotation.FaLogBiz;
import com.faber.core.web.rest.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Desktop 客户端应用管理接口。 */
@FaLogBiz("Desktop客户端")
@RestController
@RequestMapping("/api/app/client/app")
public class ClientAppController extends BaseController<ClientAppBiz, ClientApp, Long> {
}
