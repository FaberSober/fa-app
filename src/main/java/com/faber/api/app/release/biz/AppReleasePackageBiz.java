package com.faber.api.app.release.biz;

import com.faber.api.app.release.AppReleaseConstants;
import com.faber.api.app.release.entity.AppRelease;
import com.faber.api.app.release.entity.AppReleasePackage;
import com.faber.api.app.release.mapper.AppReleasePackageMapper;
import com.faber.core.exception.BuzzException;
import com.faber.core.web.biz.BaseBiz;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;

/** 应用版本发布包业务。 */
@Service
public class AppReleasePackageBiz extends BaseBiz<AppReleasePackageMapper, AppReleasePackage> {

    @Lazy
    @Resource
    AppReleaseBiz appReleaseBiz;

    public List<AppReleasePackage> listByReleaseId(Long releaseId) {
        return lambdaQuery()
                .eq(AppReleasePackage::getReleaseId, releaseId)
                .orderByAsc(AppReleasePackage::getPlatform)
                .orderByAsc(AppReleasePackage::getPackageType)
                .list();
    }

    @Override
    public boolean save(AppReleasePackage entity) {
        AppRelease release = requireDraftRelease(entity.getReleaseId());
        validatePackage(entity, release);
        validateUnique(entity, null);
        return super.save(entity);
    }

    @Override
    public boolean updateById(AppReleasePackage entity) {
        AppReleasePackage current = getById(entity.getId());
        if (current == null) throw new BuzzException("发布包ID异常，请检查");
        if (entity.getReleaseId() != null && !entity.getReleaseId().equals(current.getReleaseId())) {
            throw new BuzzException("发布包所属版本不允许修改");
        }
        entity.setReleaseId(current.getReleaseId());

        AppRelease release = requireDraftRelease(current.getReleaseId());
        validatePackage(entity, release);
        validateUnique(entity, entity.getId());
        return super.updateById(entity);
    }

    @Override
    public boolean removeById(Serializable id) {
        AppReleasePackage entity = getById(id);
        if (entity == null) throw new BuzzException("发布包ID异常，请检查");
        requireDraftRelease(entity.getReleaseId());
        return super.removeById(id);
    }

    private AppRelease requireDraftRelease(Long releaseId) {
        if (releaseId == null) throw new BuzzException("版本发布ID不能为空");
        AppRelease release = appReleaseBiz.getById(releaseId);
        if (release == null) throw new BuzzException("版本发布ID异常，请检查");
        if (!AppReleaseConstants.STATUS_DRAFT.equals(release.getStatus())) {
            throw new BuzzException("已发布或已撤回版本不允许修改发布包");
        }
        return release;
    }

    private void validatePackage(AppReleasePackage entity, AppRelease release) {
        if (entity.getPlatform() == null || !AppReleaseConstants.PLATFORMS.contains(entity.getPlatform())) {
            throw new BuzzException("不支持的发布平台");
        }
        if (entity.getPackageType() == null || !AppReleaseConstants.PACKAGE_TYPES.contains(entity.getPackageType())) {
            throw new BuzzException("不支持的发布包类型");
        }
        boolean validPlatformPackage = switch (entity.getPlatform()) {
            case AppReleaseConstants.PLATFORM_ANDROID ->
                    AppReleaseConstants.PACKAGE_APK.equals(entity.getPackageType())
                            || AppReleaseConstants.PACKAGE_FULL.equals(entity.getPackageType());
            case AppReleaseConstants.PLATFORM_IOS ->
                    AppReleaseConstants.PACKAGE_IPA.equals(entity.getPackageType())
                            || AppReleaseConstants.PACKAGE_FULL.equals(entity.getPackageType());
            case AppReleaseConstants.PLATFORM_APP_PLUS ->
                    AppReleaseConstants.PACKAGE_WGT.equals(entity.getPackageType())
                            || AppReleaseConstants.PACKAGE_FULL.equals(entity.getPackageType());
            case AppReleaseConstants.PLATFORM_MP_WEIXIN, AppReleaseConstants.PLATFORM_H5 ->
                    AppReleaseConstants.PACKAGE_FULL.equals(entity.getPackageType());
            default -> false;
        };
        if (!validPlatformPackage) throw new BuzzException("平台与发布包类型不匹配");

        if (AppReleaseConstants.PACKAGE_WGT.equals(entity.getPackageType())) {
            if (entity.getBaseVersionCode() == null || entity.getBaseVersionCode() < 1
                    || entity.getBaseVersionCode() >= release.getVersionCode()) {
                throw new BuzzException("WGT基础版本号必须小于目标版本号");
            }
        } else if (entity.getBaseVersionCode() != null) {
            throw new BuzzException("只有WGT发布包允许填写基础版本号");
        }
        if (entity.getFileId() == null || entity.getFileId().isBlank()) {
            throw new BuzzException("发布包文件ID不能为空");
        }
        if (entity.getSize() != null && entity.getSize() <= 0) {
            throw new BuzzException("发布包文件大小异常");
        }
        if (entity.getSha256() == null || !entity.getSha256().matches("^[0-9a-fA-F]{64}$")) {
            throw new BuzzException("发布包SHA-256摘要格式异常");
        }
    }

    private void validateUnique(AppReleasePackage entity, Long excludeId) {
        long count = lambdaQuery()
                .eq(AppReleasePackage::getReleaseId, entity.getReleaseId())
                .eq(AppReleasePackage::getPlatform, entity.getPlatform())
                .eq(AppReleasePackage::getPackageType, entity.getPackageType())
                .ne(excludeId != null, AppReleasePackage::getId, excludeId)
                .count();
        if (count > 0) throw new BuzzException("同一版本的平台和包类型已存在");
    }
}
