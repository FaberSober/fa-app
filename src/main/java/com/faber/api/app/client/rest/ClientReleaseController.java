package com.faber.api.app.client.rest;

import com.faber.api.app.client.biz.ClientReleaseBiz;
import com.faber.api.app.client.entity.ClientRelease;
import com.faber.core.annotation.FaLogBiz;
import com.faber.core.annotation.FaLogOpr;
import com.faber.core.enums.LogCrudEnum;
import com.faber.core.vo.msg.Ret;
import com.faber.core.web.rest.BaseController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Desktop 客户端版本管理接口。 */
@FaLogBiz("Desktop客户端版本")
@RestController
@RequestMapping("/api/app/client/release")
public class ClientReleaseController extends BaseController<ClientReleaseBiz, ClientRelease, Long> {

    @FaLogOpr(value = "发布Desktop客户端版本", crud = LogCrudEnum.U)
    @PostMapping("/publish/{id}")
    public Ret<ClientRelease> publish(@PathVariable Long id) {
        return ok(baseBiz.publish(id));
    }

    @FaLogOpr(value = "撤回Desktop客户端版本", crud = LogCrudEnum.U)
    @PostMapping("/revoke/{id}")
    public Ret<ClientRelease> revoke(@PathVariable Long id) {
        return ok(baseBiz.revoke(id));
    }

    @FaLogOpr(value = "查询Desktop客户端当前版本", crud = LogCrudEnum.R)
    @GetMapping("/current/{clientId}")
    public Ret<ClientRelease> current(@PathVariable Long clientId) {
        return ok(baseBiz.getCurrent(clientId));
    }
}
