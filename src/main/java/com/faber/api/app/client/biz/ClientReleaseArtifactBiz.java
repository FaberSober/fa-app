package com.faber.api.app.client.biz;

import com.faber.api.app.client.entity.ClientReleaseArtifact;
import com.faber.api.app.client.mapper.ClientReleaseArtifactMapper;
import com.faber.core.web.biz.BaseBiz;
import org.springframework.stereotype.Service;

/** Desktop 客户端安装包业务。 */
@Service
public class ClientReleaseArtifactBiz extends BaseBiz<ClientReleaseArtifactMapper, ClientReleaseArtifact> {
}
