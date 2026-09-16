package com.faber.api.app.client.rest;

import com.faber.api.app.client.biz.ClientReleaseArtifactBiz;
import com.faber.api.app.client.entity.ClientReleaseArtifact;
import com.faber.core.annotation.FaLogBiz;
import com.faber.core.web.rest.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Desktop 客户端安装包管理接口。 */
@FaLogBiz("Desktop客户端安装包")
@RestController
@RequestMapping("/api/app/client/releaseArtifact")
public class ClientReleaseArtifactController extends BaseController<ClientReleaseArtifactBiz, ClientReleaseArtifact, Long> {
}
