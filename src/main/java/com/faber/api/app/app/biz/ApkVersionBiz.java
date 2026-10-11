package com.faber.api.app.app.biz;

import com.faber.api.app.app.entity.Apk;
import com.faber.api.app.release.AppReleaseConstants;
import com.faber.core.exception.BuzzException;
import com.faber.core.vo.msg.Ret;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.faber.api.app.app.entity.ApkVersion;
import com.faber.api.app.app.mapper.ApkVersionMapper;
import com.faber.core.web.biz.BaseBiz;

import jakarta.annotation.Resource;
import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * APP-APK版本表
 *
 * @author Farando
 * @email faberxu@gmail.com
 * @date 2023-01-18 20:31:39
 */
@Service
public class ApkVersionBiz extends BaseBiz<ApkVersionMapper,ApkVersion> {

    @Lazy
    @Resource
    ApkBiz apkBiz;

    /**
     * 列出指定APP全部的版本信息
     * @param appId
     * @return
     */
    public List<ApkVersion> listByAppId(Integer appId) {
        return lambdaQuery()
                .eq(ApkVersion::getAppId, appId)
                .orderByDesc(ApkVersion::getVersionCode)
                .orderByDesc(ApkVersion::getId)
                .list();
    }

    /** 公开版本列表仅展示已发布的 APK。 */
    public List<ApkVersion> listPublishedByAppId(Integer appId) {
        return lambdaQuery()
                .eq(ApkVersion::getAppId, appId)
                .eq(ApkVersion::getStatus, AppReleaseConstants.STATUS_PUBLISHED)
                .orderByDesc(ApkVersion::getVersionCode)
                .orderByDesc(ApkVersion::getId)
                .list();
    }

    /** CI上传APK先成为草稿；登记报告不改变APK发布状态。 */
    public List<ApkVersion> listNativeBaseCandidates(Integer appId) {
        return lambdaQuery().eq(ApkVersion::getAppId, appId)
                .in(ApkVersion::getStatus, AppReleaseConstants.STATUS_DRAFT, AppReleaseConstants.STATUS_PUBLISHED)
                .orderByDesc(ApkVersion::getId).list();
    }

    /** 批量查询多个 APP 的正式 APK，并按 APP 和版本号降序排列。 */
    public List<ApkVersion> listPublishedByAppIds(Collection<Integer> appIds) {
        return lambdaQuery()
                .in(ApkVersion::getAppId, appIds)
                .eq(ApkVersion::getStatus, AppReleaseConstants.STATUS_PUBLISHED)
                .orderByAsc(ApkVersion::getAppId)
                .orderByDesc(ApkVersion::getVersionCode)
                .orderByDesc(ApkVersion::getId)
                .list();
    }

    /**
     * 指定版本号下载数+1
     * @param id
     */
    public void addDownloadNum(Integer id) {
        ApkVersion apkVersion = getById(id);
        if (apkVersion == null) throw new BuzzException("ID异常，请检查");
        if (!AppReleaseConstants.STATUS_PUBLISHED.equals(apkVersion.getStatus())) {
            throw new BuzzException("该 APK 版本尚未发布或已撤回");
        }

        Apk apk = apkBiz.getById(apkVersion.getAppId());
        if (apk == null) throw new BuzzException("APP ID异常，请检查");

        baseMapper.addDownloadNum(id);
        apkBiz.sumDownloadNum(apk.getId());
    }

    /**
     * 获取指定app的最新版本信息
     * @param appId {@link Apk#getId()}
     * @return
     */
    public ApkVersion getLatestVersion(Integer appId) {
        return getTop(
                lambdaQuery()
                        .eq(ApkVersion::getAppId, appId)
                        .orderByDesc(ApkVersion::getVersionCode)
                        .orderByDesc(ApkVersion::getId)
        );
    }

    /** 获取指定 APP 最新的正式 APK 版本。 */
    public ApkVersion getLatestPublishedVersion(Integer appId) {
        return getTop(
                lambdaQuery()
                        .eq(ApkVersion::getAppId, appId)
                        .eq(ApkVersion::getStatus, AppReleaseConstants.STATUS_PUBLISHED)
                        .orderByDesc(ApkVersion::getVersionCode)
                        .orderByDesc(ApkVersion::getId)
        );
    }

    @Override
    public boolean save(ApkVersion entity) {
        validateNewVersion(entity);
        if (entity.getDownloadNum() == null) entity.setDownloadNum(0);
        entity.setStatus(AppReleaseConstants.STATUS_DRAFT);
        entity.setPublishTime(null);
        if (entity.getForceUpdate() == null) entity.setForceUpdate(false);
        return super.save(entity);
    }

    @Override
    public boolean updateById(ApkVersion entity) {
        ApkVersion current = getById(entity.getId());
        if (current == null) throw new BuzzException("版本ID异常，请检查");
        if (current.getNativeBaseReportFileId() != null
                && ((entity.getFileId() != null && !Objects.equals(entity.getFileId(), current.getFileId()))
                || (entity.getApplicationId() != null && !Objects.equals(entity.getApplicationId(), current.getApplicationId()))
                || (entity.getVersionName() != null && !Objects.equals(entity.getVersionName(), current.getVersionName())))) {
            throw new BuzzException("已绑定原生基座报告的APK文件、包名及版本名称不可修改，请上传新APK");
        }
        if (entity.getStatus() != null && !Objects.equals(entity.getStatus(), current.getStatus())) {
            throw new BuzzException("发布状态请使用发布或撤回操作修改");
        }
        if (entity.getVersionCode() != null && !Objects.equals(entity.getVersionCode(), current.getVersionCode())) {
            throw new BuzzException("历史版本号不允许修改");
        }
        if (entity.getAppId() != null && !Objects.equals(entity.getAppId(), current.getAppId())) {
            throw new BuzzException("历史版本所属APP不允许修改");
        }
        return super.updateById(entity);
    }

    public ApkVersion publish(Integer id) {
        ApkVersion version = requireVersion(id);
        if (!AppReleaseConstants.STATUS_DRAFT.equals(version.getStatus())) {
            throw new BuzzException("只有草稿 APK 可以发布");
        }
        ApkVersion latestPublished = getLatestPublishedVersion(version.getAppId());
        if (latestPublished != null && version.getVersionCode() <= latestPublished.getVersionCode()) {
            throw new BuzzException("只能发布高于当前已发布版本号的 APK");
        }

        boolean updated = lambdaUpdate()
                .eq(ApkVersion::getId, id)
                .eq(ApkVersion::getStatus, AppReleaseConstants.STATUS_DRAFT)
                .set(ApkVersion::getStatus, AppReleaseConstants.STATUS_PUBLISHED)
                .set(ApkVersion::getPublishTime, new Date())
                .update();
        if (!updated) throw new BuzzException("发布版本失败，请刷新后重试");
        return getById(id);
    }

    public ApkVersion revoke(Integer id) {
        requireVersion(id);
        boolean updated = lambdaUpdate()
                .eq(ApkVersion::getId, id)
                .eq(ApkVersion::getStatus, AppReleaseConstants.STATUS_PUBLISHED)
                .set(ApkVersion::getStatus, AppReleaseConstants.STATUS_REVOKED)
                .update();
        if (!updated) throw new BuzzException("只有已发布 APK 可以撤回");
        return getById(id);
    }

    @Override
    public boolean removeById(Serializable id) {
        throw new BuzzException("APK 版本历史不允许删除");
    }

    @Override
    public void removeBatchByIds(List<Serializable> ids) {
        throw new BuzzException("APK 版本历史不允许删除");
    }

    @Override
    public void removePerById(Serializable id) {
        throw new BuzzException("APK 版本历史不允许删除");
    }

    @Override
    public void removePerByIds(Collection<? extends Serializable> ids) {
        throw new BuzzException("APK 版本历史不允许删除");
    }

    @Override
    public void removePerBatchByIds(List<Serializable> ids) {
        throw new BuzzException("APK 版本历史不允许删除");
    }

    @Override
    public void removeByQuery(com.faber.core.vo.query.QueryParams query) {
        throw new BuzzException("APK 版本历史不允许删除");
    }

    @Override
    public void removeMine() {
        throw new BuzzException("APK 版本历史不允许删除");
    }

    private ApkVersion requireVersion(Integer id) {
        if (id == null) throw new BuzzException("版本ID不能为空");
        ApkVersion version = getById(id);
        if (version == null) throw new BuzzException("版本ID异常，请检查");
        return version;
    }

    private void validateNewVersion(ApkVersion entity) {
        if (entity.getAppId() == null) throw new BuzzException("APP ID不能为空");
        if (entity.getVersionCode() == null || entity.getVersionCode() < 1) {
            throw new BuzzException("版本号必须是正整数");
        }
        if (entity.getFileId() == null || entity.getFileId().isBlank()) {
            throw new BuzzException("版本文件ID不能为空");
        }

        Apk apk = apkBiz.getById(entity.getAppId());
        if (apk == null) throw new BuzzException("APP ID异常，请检查");
        if (entity.getApplicationId() != null && !Objects.equals(entity.getApplicationId(), apk.getApplicationId())) {
            throw new BuzzException("版本包名与APP包名不一致");
        }

        ApkVersion latestVersion = getLatestVersion(entity.getAppId());
        if (latestVersion != null && latestVersion.getVersionCode() != null
                && entity.getVersionCode() <= latestVersion.getVersionCode()) {
            throw new BuzzException("版本号必须大于历史最新版本号");
        }
    }

}
