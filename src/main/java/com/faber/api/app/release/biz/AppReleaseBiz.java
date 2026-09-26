package com.faber.api.app.release.biz;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.faber.api.app.app.biz.ApkBiz;
import com.faber.api.app.app.biz.ApkVersionBiz;
import com.faber.api.app.app.entity.Apk;
import com.faber.api.app.app.entity.ApkVersion;
import com.faber.api.base.telemetry.biz.TelemetryAppBiz;
import com.faber.api.base.telemetry.entity.ClientErrorEvent;
import com.faber.api.base.telemetry.entity.TelemetryApp;
import com.faber.api.base.telemetry.enums.TelemetryClientTypeEnum;
import com.faber.api.base.telemetry.mapper.ClientErrorEventMapper;
import com.faber.api.base.admin.biz.FileSaveBiz;
import com.faber.api.base.admin.entity.FileSave;
import com.faber.api.app.release.AppReleaseConstants;
import com.faber.api.app.release.entity.AppRelease;
import com.faber.api.app.release.entity.AppReleasePackage;
import com.faber.api.app.release.mapper.AppReleaseMapper;
import com.faber.api.app.release.vo.req.AppReleaseCheckReq;
import com.faber.api.app.release.vo.req.AppReleaseAutoMatchReq;
import com.faber.api.app.release.vo.ret.AppReleaseAutoMatchRet;
import com.faber.api.app.release.vo.ret.AppReleaseAutoMatchPreviewRet;
import com.faber.api.app.release.vo.ret.AppReleaseCheckRet;
import com.faber.core.exception.BuzzException;
import com.faber.core.web.biz.BaseBiz;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;
import java.io.File;
import java.io.IOException;

/** 应用通用版本发布记录业务。 */
@Slf4j
@Service
public class AppReleaseBiz extends BaseBiz<AppReleaseMapper, AppRelease> {

    @Resource
    ApkBiz apkBiz;

    @Resource
    ApkVersionBiz apkVersionBiz;

    @Resource
    FileSaveBiz fileSaveBiz;

    @Resource
    AppReleasePackageBiz appReleasePackageBiz;

    @Resource
    TelemetryAppBiz telemetryAppBiz;

    @Resource
    ClientErrorEventMapper clientErrorEventMapper;

    @Override
    public boolean save(AppRelease entity) {
        normalizeDefaults(entity);
        validateDraft(entity, null);
        entity.setStatus(AppReleaseConstants.STATUS_DRAFT);
        entity.setPublishTime(null);
        return super.save(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public AppRelease createWgtDraft(Integer appId, Long minSupportedVersionCode, String channel,
                                    String releaseNote, MultipartFile file) throws IOException {
        AppReleasePackageBiz.WgtMetadata metadata = appReleasePackageBiz.readWgtMetadata(file);
        Apk app = apkBiz.getById(appId);
        if (app == null) throw new BuzzException("APP ID异常，请检查");
        return createWgtDraft(app, minSupportedVersionCode, channel, releaseNote, file, metadata);
    }

    @Transactional(rollbackFor = Exception.class)
    public AppReleaseAutoMatchRet createWgtDraftByWgt(String channel, String releaseNote,
                                                       MultipartFile file) throws IOException {
        AppReleasePackageBiz.WgtMetadata metadata = appReleasePackageBiz.readWgtMetadata(file);
        if (metadata.dcloudAppId() == null || metadata.dcloudAppId().isBlank()) {
            throw new BuzzException("WGT清单中缺少 DCloud AppID，无法自动匹配应用");
        }
        Apk app = apkBiz.getByDcloudAppId(metadata.dcloudAppId());
        AppRelease release = createWgtDraft(app, null, channel, releaseNote, file, metadata);
        return new AppReleaseAutoMatchRet(release, app.getName(), app.getApplicationId(), app.getDcloudAppId());
    }

    public AppReleaseAutoMatchPreviewRet matchWgtAppByFileId(String fileId) throws IOException {
        FileSave fileSave = fileSaveBiz.getById(fileId);
        if (fileSave == null) throw new BuzzException("WGT文件不存在，请重新上传");

        AppReleasePackageBiz.WgtMetadata metadata = appReleasePackageBiz.readWgtMetadata(fileSave);
        Apk app = requireAppForWgt(metadata);
        return new AppReleaseAutoMatchPreviewRet(
                app.getId(),
                app.getName(),
                app.getApplicationId(),
                app.getDcloudAppId(),
                app.getVersionCode() == null ? null : app.getVersionCode().toString(),
                app.getVersionName(),
                metadata.versionName(),
                metadata.versionCode().toString()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public AppReleaseAutoMatchRet createWgtDraftFromFile(AppReleaseAutoMatchReq request) throws IOException {
        FileSave fileSave = fileSaveBiz.getById(request.fileId());
        if (fileSave == null) throw new BuzzException("WGT文件不存在，请重新上传");

        AppReleasePackageBiz.WgtMetadata metadata = appReleasePackageBiz.readWgtMetadata(fileSave);
        Apk app = requireAppForWgt(metadata);
        validateMinimumSupportedApkVersion(app, request.minSupportedVersionCode());

        AppRelease release = createWgtDraftRecord(
                app, request.minSupportedVersionCode(), request.channel(), request.releaseNote(), metadata);
        appReleasePackageBiz.uploadWgt(release.getId(), fileSave, metadata);
        return new AppReleaseAutoMatchRet(release, app.getName(), app.getApplicationId(), app.getDcloudAppId());
    }

    private Apk requireAppForWgt(AppReleasePackageBiz.WgtMetadata metadata) {
        if (metadata.dcloudAppId() == null || metadata.dcloudAppId().isBlank()) {
            throw new BuzzException("WGT清单中缺少 DCloud AppID，无法自动匹配应用");
        }
        return apkBiz.getByDcloudAppId(metadata.dcloudAppId());
    }

    private void validateMinimumSupportedApkVersion(Apk app, Long minSupportedVersionCode) {
        if (minSupportedVersionCode == null) return;
        boolean currentVersion = minSupportedVersionCode.equals(app.getVersionCode());
        boolean historyVersion = apkVersionBiz.listByAppId(app.getId()).stream()
                .anyMatch(version -> minSupportedVersionCode.equals(version.getVersionCode()));
        if (!currentVersion && !historyVersion) {
            throw new BuzzException("最低支持 APK 版本不属于匹配应用的历史版本");
        }
    }

    private AppRelease createWgtDraft(Apk app, Long minSupportedVersionCode, String channel,
                                      String releaseNote, MultipartFile file,
                                      AppReleasePackageBiz.WgtMetadata metadata) throws IOException {
        AppRelease release = createWgtDraftRecord(app, minSupportedVersionCode, channel, releaseNote, metadata);
        appReleasePackageBiz.uploadWgt(release.getId(), file, metadata);
        return release;
    }

    private AppRelease createWgtDraftRecord(Apk app, Long minSupportedVersionCode, String channel,
                                            String releaseNote,
                                            AppReleasePackageBiz.WgtMetadata metadata) {
        validateWgtAppId(app, metadata);
        AppRelease release = new AppRelease();
        release.setAppId(app.getId());
        release.setVersionName(metadata.versionName());
        release.setVersionCode(metadata.versionCode());
        release.setChannel(channel == null || channel.isBlank() ? "stable" : channel.trim());
        release.setReleaseNote(trimToNull(releaseNote));
        release.setMinSupportedVersionCode(minSupportedVersionCode);
        if (!save(release)) throw new BuzzException("创建WGT发布草稿失败，请重试");
        return release;
    }

    private void validateWgtAppId(Apk app, AppReleasePackageBiz.WgtMetadata metadata) {
        String registeredDcloudAppId = app.getDcloudAppId();
        if (registeredDcloudAppId != null && !registeredDcloudAppId.isBlank()
                && metadata.dcloudAppId() != null && !metadata.dcloudAppId().isBlank()
                && !registeredDcloudAppId.equals(metadata.dcloudAppId())) {
            throw new BuzzException("WGT中的 DCloud AppID 与所选 APK 应用不一致");
        }
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

        normalizeDefaults(entity);
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

        appReleasePackageBiz.validatePublishPackages(id);

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

    public AppReleaseCheckRet checkApk(AppReleaseCheckReq request) throws IOException {
        validateCheckRequest(request);

        Apk app = apkBiz.getByShortCode(request.getAppCode().trim());
        long currentVersionCode = request.getCurrentVersionCode();
        ApkVersion latestVersion = apkVersionBiz.getLatestVersion(app.getId());
        if (latestVersion == null || latestVersion.getVersionCode() == null
                || latestVersion.getVersionCode() <= currentVersionCode) {
            return AppReleaseCheckRet.noUpdate();
        }

        FileSave fileSave = fileSaveBiz.getByIdWithCache(latestVersion.getFileId());
        if (fileSave == null) return AppReleaseCheckRet.noUpdate();

        String sha256 = latestVersion.getSha256();
        if (sha256 == null || !sha256.matches("^[0-9a-fA-F]{64}$")) {
            sha256 = calculateApkSha256(fileSave);
            latestVersion.setSha256(sha256);
            apkVersionBiz.updateById(latestVersion);
        }

        AppReleaseCheckRet result = new AppReleaseCheckRet();
        result.setHasUpdate(true);
        result.setUpdateType(AppReleaseConstants.UPDATE_FULL);
        result.setReleaseId(latestVersion.getId().longValue());
        result.setVersionCode(latestVersion.getVersionCode());
        result.setVersionName(latestVersion.getVersionName());
        result.setForceUpdate(Boolean.TRUE.equals(latestVersion.getForceUpdate()));
        result.setFileId(latestVersion.getFileId());
        result.setDownloadUrl(fileSaveBiz.getFileUrl(latestVersion.getFileId()));
        result.setSize(latestVersion.getSize() != null ? latestVersion.getSize() : fileSave.getSize());
        result.setSha256(sha256);
        result.setReleaseNote(latestVersion.getRemark());
        return result;
    }

    public AppReleaseCheckRet checkWgt(AppReleaseCheckReq request) {
        validateCheckRequest(request);

        Apk app = apkBiz.getByShortCode(request.getAppCode().trim());
        String platform = request.getPlatform().trim();
        String channel = request.getChannel().trim();
        long currentVersionCode = request.getCurrentVersionCode();
        if (!AppReleaseConstants.PLATFORM_APP_PLUS.equals(platform)) return AppReleaseCheckRet.noUpdate();
        long currentWgtVersionCode = defaultValue(request.getCurrentWgtVersionCode(), currentVersionCode);

        List<AppRelease> releases = lambdaQuery()
                .eq(AppRelease::getAppId, app.getId())
                .eq(AppRelease::getChannel, channel)
                .eq(AppRelease::getStatus, AppReleaseConstants.STATUS_PUBLISHED)
                .gt(AppRelease::getVersionCode, currentWgtVersionCode)
                .orderByDesc(AppRelease::getVersionCode)
                .orderByDesc(AppRelease::getId)
                .list();

        for (AppRelease release : releases) {
            if (!matchesDeviceScope(release, request.getDeviceId())) continue;
            if (release.getMinSupportedVersionCode() != null
                    && currentVersionCode < release.getMinSupportedVersionCode()) continue;
            List<AppReleasePackage> packages = appReleasePackageBiz.listByReleaseId(release.getId());
            AppReleasePackage releasePackage = findPackage(packages, AppReleaseConstants.PACKAGE_WGT);
            if (releasePackage == null) continue;

            FileSave fileSave = fileSaveBiz.getByIdWithCache(releasePackage.getFileId());
            if (fileSave == null) continue;

            return toCheckResult(release, releasePackage, fileSave);
        }
        return AppReleaseCheckRet.noUpdate();
    }

    /** 每分钟检查启用自动回滚的发布版本，达到生产异常阈值后撤回版本。 */
    @Scheduled(fixedDelayString = "${fa.app.release.rollback-check-interval-ms:60000}")
    @Transactional(rollbackFor = Exception.class)
    public void checkAutoRollback() {
        List<AppRelease> releases = lambdaQuery()
                .eq(AppRelease::getStatus, AppReleaseConstants.STATUS_PUBLISHED)
                .eq(AppRelease::getAutoRollback, true)
                .list();
        Date now = new Date();
        for (AppRelease release : releases) {
            long errorCount = countProductionErrors(release, now);
            int threshold = defaultValue(release.getRollbackErrorThreshold(), 10);
            if (errorCount < threshold) continue;

            try {
                revoke(release.getId());
                log.warn("应用版本达到自动回滚异常阈值，已撤回。releaseId={}, versionCode={}, errorCount={}, threshold={}",
                        release.getId(), release.getVersionCode(), errorCount, threshold);
            } catch (RuntimeException error) {
                log.warn("应用版本自动回滚失败。releaseId={}, versionCode={}",
                        release.getId(), release.getVersionCode(), error);
            }
        }
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
                && entity.getMinSupportedVersionCode() < 1) {
            throw new BuzzException("最低兼容APK版本必须是正整数");
        }
        int rolloutPercent = defaultValue(entity.getRolloutPercent(), 100);
        if (rolloutPercent < 0 || rolloutPercent > 100) {
            throw new BuzzException("灰度比例必须在0到100之间");
        }
        validateTargetDeviceIds(entity.getTargetDeviceIds());
        int rollbackErrorThreshold = defaultValue(entity.getRollbackErrorThreshold(), 10);
        int rollbackWindowMinutes = defaultValue(entity.getRollbackWindowMinutes(), 15);
        if (rollbackErrorThreshold < 1 || rollbackErrorThreshold > 1_000_000) {
            throw new BuzzException("自动回滚异常阈值必须在1到1000000之间");
        }
        if (rollbackWindowMinutes < 1 || rollbackWindowMinutes > 1_440) {
            throw new BuzzException("自动回滚窗口必须在1到1440分钟之间");
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
        if (!AppReleaseConstants.PLATFORMS.contains(request.getPlatform().trim())) {
            throw new BuzzException("不支持的发布平台");
        }
        if (request.getCurrentVersionCode() == null || request.getCurrentVersionCode() < 0) {
            throw new BuzzException("当前版本号不能小于0");
        }
        if (request.getCurrentWgtVersionCode() != null && request.getCurrentWgtVersionCode() < 0) {
            throw new BuzzException("当前WGT资源版本号不能小于0");
        }
        if (request.getChannel() == null || request.getChannel().isBlank()) {
            throw new BuzzException("发布渠道不能为空");
        }
        if (request.getDeviceId() != null && request.getDeviceId().length() > 128) {
            throw new BuzzException("设备标识长度不能超过128");
        }
    }

    private void normalizeDefaults(AppRelease entity) {
        entity.setForceUpdate(Boolean.TRUE.equals(entity.getForceUpdate()));
        entity.setRolloutPercent(defaultValue(entity.getRolloutPercent(), 100));
        entity.setAutoRollback(Boolean.TRUE.equals(entity.getAutoRollback()));
        entity.setRollbackErrorThreshold(defaultValue(entity.getRollbackErrorThreshold(), 10));
        entity.setRollbackWindowMinutes(defaultValue(entity.getRollbackWindowMinutes(), 15));
    }

    private boolean matchesDeviceScope(AppRelease release, String deviceId) {
        String normalizedDeviceId = trimToNull(deviceId);
        String targetDeviceIds = trimToNull(release.getTargetDeviceIds());
        if (targetDeviceIds != null) {
            if (normalizedDeviceId == null) return false;
            for (String targetDeviceId : targetDeviceIds.split("[,\\s]+")) {
                if (normalizedDeviceId.equals(targetDeviceId)) return true;
            }
            return false;
        }

        int rolloutPercent = defaultValue(release.getRolloutPercent(), 100);
        if (rolloutPercent >= 100) return true;
        if (rolloutPercent <= 0 || normalizedDeviceId == null) return false;

        String seed = release.getAppId() + "|" + release.getVersionCode() + "|"
                + release.getChannel() + "|" + normalizedDeviceId;
        return Math.floorMod(seed.hashCode(), 100) < rolloutPercent;
    }

    private void validateTargetDeviceIds(String targetDeviceIds) {
        if (targetDeviceIds == null || targetDeviceIds.isBlank()) return;
        if (targetDeviceIds.length() > 4_000) throw new BuzzException("设备范围配置不能超过4000个字符");
        for (String deviceId : targetDeviceIds.split("[,\\s]+")) {
            if (deviceId.length() > 128) throw new BuzzException("设备标识长度不能超过128");
        }
    }

    private long countProductionErrors(AppRelease release, Date now) {
        Apk app = apkBiz.getById(release.getAppId());
        if (app == null || app.getShortCode() == null || app.getShortCode().isBlank()) return 0;

        TelemetryApp telemetryApp = telemetryAppBiz.lambdaQuery()
                .eq(TelemetryApp::getAppCode, app.getShortCode())
                .eq(TelemetryApp::getClientType, TelemetryClientTypeEnum.MOBILE)
                .one();
        if (telemetryApp == null) return 0;

        long windowMillis = defaultValue(release.getRollbackWindowMinutes(), 15) * 60_000L;
        Date startTime = new Date(now.getTime() - windowMillis);
        if (release.getPublishTime() != null && release.getPublishTime().after(startTime)) {
            startTime = release.getPublishTime();
        }
        return clientErrorEventMapper.selectCount(new LambdaQueryWrapper<ClientErrorEvent>()
                .eq(ClientErrorEvent::getAppId, telemetryApp.getId())
                .eq(ClientErrorEvent::getClientType, TelemetryClientTypeEnum.MOBILE)
                .eq(ClientErrorEvent::getEnvironment, "production")
                .eq(ClientErrorEvent::getRelease, release.getVersionName())
                .ge(ClientErrorEvent::getOccurTime, startTime)
                .lt(ClientErrorEvent::getOccurTime, now));
    }

    private AppReleasePackage findPackage(List<AppReleasePackage> packages, String packageType) {
        return packages.stream()
                .filter(item -> packageType.equals(item.getPackageType()))
                .findFirst()
                .orElse(null);
    }

    private AppReleaseCheckRet toCheckResult(AppRelease release, AppReleasePackage releasePackage,
                                             FileSave fileSave) {
        AppReleaseCheckRet result = new AppReleaseCheckRet();
        result.setHasUpdate(true);
        result.setUpdateType(AppReleaseConstants.PACKAGE_WGT.equals(releasePackage.getPackageType())
                ? AppReleaseConstants.UPDATE_WGT : AppReleaseConstants.UPDATE_FULL);
        result.setReleaseId(release.getId());
        result.setVersionCode(release.getVersionCode());
        result.setVersionName(release.getVersionName());
        result.setForceUpdate(Boolean.TRUE.equals(release.getForceUpdate()));
        result.setMinSupportedVersionCode(release.getMinSupportedVersionCode());
        result.setFileId(releasePackage.getFileId());
        result.setDownloadUrl(fileSaveBiz.getFileUrl(releasePackage.getFileId()));
        result.setSize(releasePackage.getSize() != null ? releasePackage.getSize() : fileSave.getSize());
        result.setSha256(releasePackage.getSha256());
        result.setReleaseNote(release.getReleaseNote());
        return result;
    }

    private String calculateApkSha256(FileSave fileSave) throws IOException {
        File file = fileSaveBiz.getFileObj(fileSave);
        try {
            return DigestUtil.sha256Hex(file);
        } finally {
            if (fileSave.getPlatform() != null && !fileSave.getPlatform().startsWith("local-")
                    && file != null && file.exists() && !file.delete()) {
                log.warn("清理APK校验临时文件失败: {}", file.getAbsolutePath());
            }
        }
    }

    private long defaultValue(Long value, long defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int defaultValue(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
