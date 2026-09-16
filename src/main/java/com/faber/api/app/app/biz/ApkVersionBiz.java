package com.faber.api.app.app.biz;

import com.faber.api.app.app.entity.Apk;
import com.faber.core.exception.BuzzException;
import com.faber.core.vo.msg.Ret;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.faber.api.app.app.entity.ApkVersion;
import com.faber.api.app.app.mapper.ApkVersionMapper;
import com.faber.core.web.biz.BaseBiz;

import jakarta.annotation.Resource;
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

    @Override
    public boolean save(ApkVersion entity) {
        validateNewVersion(entity);
        if (entity.getDownloadNum() == null) entity.setDownloadNum(0);
        if (entity.getForceUpdate() == null) entity.setForceUpdate(false);
        return super.save(entity);
    }

    @Override
    public boolean updateById(ApkVersion entity) {
        ApkVersion current = getById(entity.getId());
        if (current == null) throw new BuzzException("版本ID异常，请检查");
        if (entity.getVersionCode() != null && !Objects.equals(entity.getVersionCode(), current.getVersionCode())) {
            throw new BuzzException("历史版本号不允许修改");
        }
        if (entity.getAppId() != null && !Objects.equals(entity.getAppId(), current.getAppId())) {
            throw new BuzzException("历史版本所属APP不允许修改");
        }
        return super.updateById(entity);
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
