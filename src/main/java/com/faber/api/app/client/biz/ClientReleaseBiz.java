package com.faber.api.app.client.biz;

import com.faber.api.app.client.entity.ClientRelease;
import com.faber.api.app.client.mapper.ClientReleaseMapper;
import com.faber.core.web.biz.BaseBiz;
import org.springframework.stereotype.Service;

/** Desktop 客户端版本业务。 */
@Service
public class ClientReleaseBiz extends BaseBiz<ClientReleaseMapper, ClientRelease> {
}
