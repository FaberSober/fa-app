package com.faber.api.app.client.biz;

import com.faber.api.app.client.ClientReleaseConstants;
import com.faber.api.app.client.entity.ClientApp;
import com.faber.api.app.client.entity.ClientRelease;
import com.faber.api.app.client.entity.ClientReleaseArtifact;
import com.faber.api.app.client.mapper.ClientReleaseMapper;
import com.faber.core.exception.BuzzException;
import com.faber.core.web.biz.BaseBiz;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/** Desktop 客户端版本业务。 */
@Service
public class ClientReleaseBiz extends BaseBiz<ClientReleaseMapper, ClientRelease> {

    @Resource
    private ClientAppBiz clientAppBiz;

    @Lazy
    @Resource
    private ClientReleaseArtifactBiz clientReleaseArtifactBiz;

    @Override
    public boolean save(ClientRelease entity) {
        validateDraft(entity, null);
        entity.setStatus(ClientReleaseConstants.STATUS_DRAFT);
        entity.setPublishTime(null);
        return super.save(entity);
    }

    @Override
    public boolean updateById(ClientRelease entity) {
        ClientRelease current = require(entity.getId());
        if (!ClientReleaseConstants.STATUS_DRAFT.equals(current.getStatus())) {
            throw new BuzzException("已发布或已撤回的版本不允许直接修改");
        }
        if (entity.getStatus() != null && !ClientReleaseConstants.STATUS_DRAFT.equals(entity.getStatus())) {
            throw new BuzzException("发布状态请使用发布或撤回接口修改");
        }

        validateDraft(entity, entity.getId());
        entity.setStatus(ClientReleaseConstants.STATUS_DRAFT);
        entity.setPublishTime(null);
        return super.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public ClientRelease publish(Long id) {
        ClientRelease release = require(id);
        if (!ClientReleaseConstants.STATUS_DRAFT.equals(release.getStatus())) {
            throw new BuzzException("只有草稿版本可以发布");
        }

        ClientApp client = clientAppBiz.getById(release.getClientId());
        if (client == null) throw new BuzzException("客户端ID异常，请检查");
        if (!Boolean.TRUE.equals(client.getEnabled())) throw new BuzzException("客户端已停用，不能发布新版本");

        validateDraft(release, release.getId());
        List<ClientReleaseArtifact> artifacts = clientReleaseArtifactBiz.listByReleaseId(id);
        if (artifacts.isEmpty()) throw new BuzzException("发布版本至少需要一个安装包");
        for (ClientReleaseArtifact artifact : artifacts) {
            clientReleaseArtifactBiz.validateForPublish(artifact);
        }

        release.setStatus(ClientReleaseConstants.STATUS_PUBLISHED);
        release.setPublishTime(new Date());
        if (!super.updateById(release)) throw new BuzzException("发布版本失败，请重试");
        return release;
    }

    @Transactional(rollbackFor = Exception.class)
    public ClientRelease revoke(Long id) {
        ClientRelease release = require(id);
        if (!ClientReleaseConstants.STATUS_PUBLISHED.equals(release.getStatus())) {
            throw new BuzzException("只有已发布版本可以撤回");
        }

        release.setStatus(ClientReleaseConstants.STATUS_REVOKED);
        if (!super.updateById(release)) throw new BuzzException("撤回版本失败，请重试");
        return release;
    }

    public ClientRelease getCurrent(Long clientId) {
        if (clientId == null) return null;
        return getTop(lambdaQuery()
                .eq(ClientRelease::getClientId, clientId)
                .eq(ClientRelease::getChannel, ClientReleaseConstants.CHANNEL_STABLE)
                .eq(ClientRelease::getStatus, ClientReleaseConstants.STATUS_PUBLISHED)
                .orderByDesc(ClientRelease::getVersionCode)
                .orderByDesc(ClientRelease::getId));
    }

    @Override
    public void removeBatchByIds(List<Serializable> ids) {
        if (ids == null) return;
        ids.forEach(this::removeById);
    }

    @Override
    public boolean removeById(Serializable id) {
        checkDraft(require((Long) id));
        return super.removeById(id);
    }

    @Override
    public void removePerById(Serializable id) {
        checkDraft(require((Long) id));
        super.removePerById(id);
    }

    @Override
    public void removePerBatchByIds(List<Serializable> ids) {
        if (ids == null) return;
        ids.forEach(this::removePerById);
    }

    private ClientRelease require(Long id) {
        if (id == null) throw new BuzzException("版本ID不能为空");
        ClientRelease release = getById(id);
        if (release == null) throw new BuzzException("版本ID异常，请检查");
        return release;
    }

    private void checkDraft(ClientRelease release) {
        if (!ClientReleaseConstants.STATUS_DRAFT.equals(release.getStatus())) {
            throw new BuzzException("已发布或已撤回的版本不允许删除");
        }
    }

    private void validateDraft(ClientRelease entity, Long excludeId) {
        if (entity.getClientId() == null) throw new BuzzException("客户端ID不能为空");
        if (clientAppBiz.getById(entity.getClientId()) == null) throw new BuzzException("客户端ID异常，请检查");
        if (entity.getVersionName() == null || entity.getVersionName().isBlank()) {
            throw new BuzzException("版本名称不能为空");
        }
        if (entity.getVersionCode() == null || entity.getVersionCode() < 1) {
            throw new BuzzException("版本编码必须是正整数");
        }
        if (entity.getChannel() == null || entity.getChannel().isBlank()) {
            throw new BuzzException("发布渠道不能为空");
        }

        long count = lambdaQuery()
                .eq(ClientRelease::getClientId, entity.getClientId())
                .eq(ClientRelease::getVersionCode, entity.getVersionCode())
                .eq(ClientRelease::getChannel, entity.getChannel())
                .ne(excludeId != null, ClientRelease::getId, excludeId)
                .count();
        if (count > 0) throw new BuzzException("同一客户端、版本编码和渠道的发布记录已存在");
    }

}
