package com.faber.api.app.client.biz;

import com.faber.api.app.client.ClientReleaseConstants;
import com.faber.api.app.client.entity.ClientRelease;
import com.faber.api.app.client.entity.ClientReleaseArtifact;
import com.faber.api.app.client.mapper.ClientReleaseArtifactMapper;
import com.faber.core.exception.BuzzException;
import com.faber.core.web.biz.BaseBiz;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;

/** Desktop 客户端安装包业务。 */
@Service
public class ClientReleaseArtifactBiz extends BaseBiz<ClientReleaseArtifactMapper, ClientReleaseArtifact> {

    @Lazy
    @Resource
    private ClientReleaseBiz clientReleaseBiz;

    public List<ClientReleaseArtifact> listByReleaseId(Long releaseId) {
        return lambdaQuery()
                .eq(ClientReleaseArtifact::getReleaseId, releaseId)
                .orderByAsc(ClientReleaseArtifact::getPlatform)
                .orderByAsc(ClientReleaseArtifact::getArch)
                .list();
    }

    @Override
    public boolean save(ClientReleaseArtifact entity) {
        requireDraftRelease(entity.getReleaseId());
        validateArtifact(entity);
        validateUnique(entity, null);
        return super.save(entity);
    }

    public void validateForPublish(ClientReleaseArtifact entity) {
        validateArtifact(entity);
    }

    @Override
    public boolean updateById(ClientReleaseArtifact entity) {
        ClientReleaseArtifact current = require(entity.getId());
        if (entity.getReleaseId() != null && !entity.getReleaseId().equals(current.getReleaseId())) {
            throw new BuzzException("安装包所属版本不允许修改");
        }
        entity.setReleaseId(current.getReleaseId());
        requireDraftRelease(current.getReleaseId());
        validateArtifact(entity);
        validateUnique(entity, entity.getId());
        return super.updateById(entity);
    }

    @Override
    public void removeBatchByIds(List<Serializable> ids) {
        if (ids == null) return;
        ids.forEach(this::removeById);
    }

    @Override
    public boolean removeById(Serializable id) {
        ClientReleaseArtifact artifact = require(id);
        requireDraftRelease(artifact.getReleaseId());
        return super.removeById(id);
    }

    @Override
    public void removePerById(Serializable id) {
        ClientReleaseArtifact artifact = require(id);
        requireDraftRelease(artifact.getReleaseId());
        super.removePerById(id);
    }

    @Override
    public void removePerBatchByIds(List<Serializable> ids) {
        if (ids == null) return;
        ids.forEach(this::removePerById);
    }

    private ClientReleaseArtifact require(Serializable id) {
        if (id == null) throw new BuzzException("安装包ID不能为空");
        ClientReleaseArtifact artifact = getById(id);
        if (artifact == null) throw new BuzzException("安装包ID异常，请检查");
        return artifact;
    }

    private ClientRelease requireDraftRelease(Long releaseId) {
        if (releaseId == null) throw new BuzzException("版本ID不能为空");
        ClientRelease release = clientReleaseBiz.getById(releaseId);
        if (release == null) throw new BuzzException("版本ID异常，请检查");
        if (!ClientReleaseConstants.STATUS_DRAFT.equals(release.getStatus())) {
            throw new BuzzException("已发布或已撤回版本不允许修改安装包");
        }
        return release;
    }

    private void validateArtifact(ClientReleaseArtifact entity) {
        if (!ClientReleaseConstants.PLATFORMS.contains(entity.getPlatform())) {
            throw new BuzzException("不支持的操作系统平台");
        }
        if (!ClientReleaseConstants.ARCHES.contains(entity.getArch())) {
            throw new BuzzException("不支持的CPU架构");
        }
        if (entity.getFileId() == null || entity.getFileId().isBlank()) {
            throw new BuzzException("安装包文件不能为空");
        }
        if (entity.getSize() != null && entity.getSize() <= 0) {
            throw new BuzzException("安装包文件大小异常");
        }
        if (entity.getSha256() == null || !entity.getSha256().matches("^[0-9a-fA-F]{64}$")) {
            throw new BuzzException("安装包SHA-256摘要格式异常");
        }
        if (entity.getSignature() == null || entity.getSignature().isBlank()) {
            throw new BuzzException("安装包Tauri签名不能为空");
        }
    }

    private void validateUnique(ClientReleaseArtifact entity, Long excludeId) {
        long count = lambdaQuery()
                .eq(ClientReleaseArtifact::getReleaseId, entity.getReleaseId())
                .eq(ClientReleaseArtifact::getPlatform, entity.getPlatform())
                .eq(ClientReleaseArtifact::getArch, entity.getArch())
                .ne(excludeId != null, ClientReleaseArtifact::getId, excludeId)
                .count();
        if (count > 0) throw new BuzzException("同一版本的平台和架构安装包已存在");
    }
}
