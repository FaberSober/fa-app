package com.faber.api.app.release.biz;

import com.faber.api.app.app.biz.ApkBiz;
import com.faber.api.app.release.AppReleaseConstants;
import com.faber.api.app.release.entity.AppRelease;
import com.faber.api.app.release.entity.AppReleasePackage;
import com.faber.api.app.release.mapper.AppReleaseMapper;
import com.faber.core.exception.BuzzException;
import com.faber.core.web.biz.BaseBiz;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/** 应用通用版本发布记录业务。 */
@Service
public class AppReleaseBiz extends BaseBiz<AppReleaseMapper, AppRelease> {

    @Resource
    ApkBiz apkBiz;

    @Resource
    AppReleasePackageBiz appReleasePackageBiz;

    @Override
    public boolean save(AppRelease entity) {
        validateDraft(entity, null);
        entity.setStatus(AppReleaseConstants.STATUS_DRAFT);
        entity.setForceUpdate(Boolean.TRUE.equals(entity.getForceUpdate()));
        entity.setPublishTime(null);
        return super.save(entity);
    }

    @Override
    public boolean updateById(AppRelease entity) {
        AppRelease current = getById(entity.getId());
        if (current == null) throw new BuzzException("版本发布ID异常，请检查");
        if (!AppReleaseConstants.STATUS_DRAFT.equals(current.getStatus())) {
            throw new BuzzException("已发布或已撤回的版本不允许直接修改");
        }
        if (entity.getStatus() != null && !AppReleaseConstants.STATUS_DRAFT.equals(entity.getStatus())) {
            throw new BuzzException("发布状态请使用发布或撤回接口修改");
        }

        validateDraft(entity, entity.getId());
        entity.setStatus(AppReleaseConstants.STATUS_DRAFT);
        entity.setPublishTime(null);
        return super.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public AppRelease publish(Long id) {
        AppRelease release = require(id);
        if (!AppReleaseConstants.STATUS_DRAFT.equals(release.getStatus())) {
            throw new BuzzException("只有草稿版本可以发布");
        }
        validateDraft(release, release.getId());

        List<AppReleasePackage> packages = appReleasePackageBiz.listByReleaseId(id);
        if (packages.isEmpty()) throw new BuzzException("发布版本至少需要一个发布包");

        release.setStatus(AppReleaseConstants.STATUS_PUBLISHED);
        release.setPublishTime(new Date());
        if (!super.updateById(release)) throw new BuzzException("发布版本失败，请重试");
        return release;
    }

    @Transactional(rollbackFor = Exception.class)
    public AppRelease revoke(Long id) {
        AppRelease release = require(id);
        if (!AppReleaseConstants.STATUS_PUBLISHED.equals(release.getStatus())) {
            throw new BuzzException("只有已发布版本可以撤回");
        }

        release.setStatus(AppReleaseConstants.STATUS_REVOKED);
        if (!super.updateById(release)) throw new BuzzException("撤回版本失败，请重试");
        return release;
    }

    @Override
    public boolean removeById(java.io.Serializable id) {
        AppRelease release = require((Long) id);
        if (!AppReleaseConstants.STATUS_DRAFT.equals(release.getStatus())) {
            throw new BuzzException("已发布或已撤回的版本不允许删除");
        }
        return super.removeById(id);
    }

    private AppRelease require(Long id) {
        if (id == null) throw new BuzzException("版本发布ID不能为空");
        AppRelease release = getById(id);
        if (release == null) throw new BuzzException("版本发布ID异常，请检查");
        return release;
    }

    private void validateDraft(AppRelease entity, Long excludeId) {
        if (entity.getAppId() == null) throw new BuzzException("APP ID不能为空");
        if (apkBiz.getById(entity.getAppId()) == null) throw new BuzzException("APP ID异常，请检查");
        if (entity.getVersionName() == null || entity.getVersionName().isBlank()) {
            throw new BuzzException("版本名称不能为空");
        }
        if (entity.getVersionCode() == null || entity.getVersionCode() < 1) {
            throw new BuzzException("版本号必须是正整数");
        }
        if (entity.getChannel() == null || entity.getChannel().isBlank()) {
            throw new BuzzException("发布渠道不能为空");
        }
        if (entity.getMinSupportedVersionCode() != null
                && (entity.getMinSupportedVersionCode() < 1
                || entity.getMinSupportedVersionCode() > entity.getVersionCode())) {
            throw new BuzzException("最低支持版本号必须是正整数且不能大于目标版本号");
        }

        long count = lambdaQuery()
                .eq(AppRelease::getAppId, entity.getAppId())
                .eq(AppRelease::getVersionCode, entity.getVersionCode())
                .eq(AppRelease::getChannel, entity.getChannel())
                .ne(excludeId != null, AppRelease::getId, excludeId)
                .count();
        if (count > 0) throw new BuzzException("同一应用、版本号和渠道的发布记录已存在");
    }
}
