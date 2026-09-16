package com.faber.api.app.client.rest;

import com.faber.api.app.client.biz.ClientReleaseBiz;
import com.faber.api.app.client.vo.req.ClientUpdateCheckReq;
import com.faber.api.app.client.vo.ret.ClientUpdateManifest;
import com.faber.core.annotation.FaLogBiz;
import com.faber.core.annotation.FaLogOpr;
import com.faber.core.annotation.LogNoRet;
import com.faber.core.config.annotation.IgnoreUserToken;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/** Desktop 客户端公开更新接口。 */
@FaLogBiz("Desktop客户端更新")
@RestController
@RequestMapping("/api/app/client")
public class ClientUpdateController {

    @Resource
    private ClientReleaseBiz clientReleaseBiz;

    @IgnoreUserToken
    @LogNoRet
    @GetMapping("/update/{clientCode}")
    public ResponseEntity<ClientUpdateManifest> checkUpdate(
            @PathVariable String clientCode,
            @RequestParam("current_version") String currentVersion,
            @RequestParam("target") String target,
            @RequestParam(value = "channel", defaultValue = "stable") String channel) {
        ClientUpdateCheckReq request = new ClientUpdateCheckReq();
        request.setCurrentVersion(currentVersion);
        request.setTarget(target);
        request.setChannel(channel);
        ClientUpdateManifest manifest = clientReleaseBiz.checkUpdate(clientCode, request);
        return manifest == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(manifest);
    }

    @FaLogOpr("下载Desktop客户端安装包")
    @IgnoreUserToken
    @LogNoRet
    @GetMapping("/download/{releaseId}/{target}")
    public void download(@PathVariable Long releaseId, @PathVariable String target) throws IOException {
        clientReleaseBiz.download(releaseId, target);
    }
}
