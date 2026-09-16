package com.faber.api.app.client.biz;

import com.faber.api.app.client.entity.ClientApp;
import com.faber.api.app.client.mapper.ClientAppMapper;
import com.faber.core.web.biz.BaseBiz;
import org.springframework.stereotype.Service;

/** Desktop 客户端应用业务。 */
@Service
public class ClientAppBiz extends BaseBiz<ClientAppMapper, ClientApp> {
}
