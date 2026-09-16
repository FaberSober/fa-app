package com.faber.api.app.release.biz;

import com.faber.api.app.app.biz.ApkBiz;
import com.faber.api.app.app.entity.Apk;
import com.faber.api.base.admin.biz.FileSaveBiz;
import com.faber.api.base.admin.entity.FileSave;
import com.faber.api.app.release.AppReleaseConstants;
import com.faber.api.app.release.entity.AppRelease;
import com.faber.api.app.release.entity.AppReleasePackage;
import com.faber.api.app.release.mapper.AppReleaseMapper;
import com.faber.api.app.release.vo.req.AppReleaseCheckReq;
import com.faber.api.app.release.vo.ret.AppReleaseCheckRet;
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
    FileSaveBiz fileSaveBiz;

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

    public AppReleaseCheckRet check(AppReleaseCheckReq request) {
        validateCheckRequest(request);

        Apk app = apkBiz.getByShortCode(request.getAppCode().trim());
        String platform = request.getPlatform().trim();
        String channel = request.getChannel().trim();
        long currentVersionCode = request.getCurrentVersionCode();
        if (!AppReleaseConstants.PLATFORMS.contains(platform)) throw new BuzzException("不支持的发布平台");

        List<AppRelease> releases = lambdaQuery()
                .eq(AppRelease::getAppId, app.getId())
                .eq(AppRelease::getChannel, channel)
                .eq(AppRelease::getStatus, AppReleaseConstants.STATUS_PUBLISHED)
                .gt(AppRelease::getVersionCode, currentVersionCode)
                .orderByDesc(AppRelease::getVersionCode)
                .orderByDesc(AppRelease::getId)
                .list();

        for (AppRelease release : releases) {
            List<AppReleasePackage> packages = appReleasePackageBiz.listByReleaseId(release.getId());
            AppReleasePackage releasePackage = selectPackage(platform, currentVersionCode, release, packages);
            if (releasePackage == null) continue;

            FileSave fileSave = fileSaveBiz.getByIdWithCache(releasePackage.getFileId());
            if (fileSave == null) continue;

            return toCheckResult(release, releasePackage, fileSave,
                    currentVersionCode < defaultValue(release.getMinSupportedVersionCode(), 0L));
        }
        return AppReleaseCheckRet.noUpdate();
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

    private void validateCheckRequest(AppReleaseCheckReq request) {
        if (request == null) throw new BuzzException("版本检查请求不能为空");
        if (request.getAppCode() == null || request.getAppCode().isBlank()) {
            throw new BuzzException("应用标识不能为空");
        }
        if (request.getPlatform() == null || request.getPlatform().isBlank()) {
            throw new BuzzException("发布平台不能为空");
        }
        if (request.getCurrentVersionCode() == null || request.getCurrentVersionCode() < 0) {
            throw new BuzzException("当前版本号不能小于0");
        }
        if (request.getChannel() == null || request.getChannel().isBlank()) {
            throw new BuzzException("发布渠道不能为空");
        }
    }

    private AppReleasePackage selectPackage(String platform, long currentVersionCode,
                                            AppRelease release, List<AppReleasePackage> packages) {
        boolean forceFull = release.getMinSupportedVersionCode() != null
                && currentVersionCode < release.getMinSupportedVersionCode();
        if (forceFull) return findFullPackage(platform, packages);

        if (AppReleaseConstants.PLATFORM_APP_PLUS.equals(platform)) {
            AppReleasePackage wgt = findPackage(packages, AppReleaseConstants.PACKAGE_WGT, currentVersionCode);
            if (wgt != null) return wgt;
        }
        return findFullPackage(platform, packages);
    }

    private AppReleasePackage findFullPackage(String platform, List<AppReleasePackage> packages) {
        String packageType = switch (platform) {
            case AppReleaseConstants.PLATFORM_ANDROID -> AppReleaseConstants.PACKAGE_APK;
            case AppReleaseConstants.PLATFORM_IOS -> AppReleaseConstants.PACKAGE_IPA;
            default -> AppReleaseConstants.PACKAGE_FULL;
        };
        AppReleasePackage packageInfo = findPackage(packages, packageType, null);
        return packageInfo != null ? packageInfo : findPackage(packages, AppReleaseConstants.PACKAGE_FULL, null);
    }

    private AppReleasePackage findPackage(List<AppReleasePackage> packages, String packageType, Long baseVersionCode) {
        return packages.stream()
                .filter(item -> packageType.equals(item.getPackageType()))
                .filter(item -> baseVersionCode == null || baseVersionCode.equals(item.getBaseVersionCode()))
                .findFirst()
                .orElse(null);
    }

    private AppReleaseCheckRet toCheckResult(AppRelease release, AppReleasePackage releasePackage,
                                             FileSave fileSave, boolean forceFull) {
        AppReleaseCheckRet result = new AppReleaseCheckRet();
        result.setHasUpdate(true);
        result.setUpdateType(AppReleaseConstants.PACKAGE_WGT.equals(releasePackage.getPackageType())
                ? AppReleaseConstants.UPDATE_WGT : AppReleaseConstants.UPDATE_FULL);
        result.setReleaseId(release.getId());
        result.setVersionCode(release.getVersionCode());
        result.setVersionName(release.getVersionName());
        result.setBaseVersionCode(releasePackage.getBaseVersionCode());
        result.setForceUpdate(forceFull || Boolean.TRUE.equals(release.getForceUpdate()));
        result.setMinSupportedVersionCode(release.getMinSupportedVersionCode());
        result.setFileId(releasePackage.getFileId());
        result.setDownloadUrl(fileSaveBiz.getFileUrl(releasePackage.getFileId()));
        result.setSize(releasePackage.getSize() != null ? releasePackage.getSize() : fileSave.getSize());
        result.setSha256(releasePackage.getSha256());
        result.setReleaseNote(release.getReleaseNote());
        return result;
    }

    private long defaultValue(Long value, long defaultValue) {
        return value == null ? defaultValue : value;
    }
}
