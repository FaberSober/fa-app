package com.faber.api.app.app.biz;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.RandomUtil;
import com.faber.api.app.app.entity.Apk;
import com.faber.api.app.app.entity.ApkVersion;
import com.faber.api.app.app.mapper.ApkMapper;
import com.faber.api.base.admin.biz.FileSaveBiz;
import com.faber.api.base.admin.entity.FileSave;
import com.faber.core.exception.BuzzException;
import com.faber.core.web.biz.BaseBiz;
import jodd.io.FileUtil;
import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.Icon;
import net.dongliu.apk.parser.bean.IconFace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * APP-APK表
 *
 * @author Farando
 * @email faberxu@gmail.com
 * @date 2023-01-18 20:31:39
 */
@Slf4j
@Service
public class ApkBiz extends BaseBiz<ApkMapper,Apk> {

    private static final Pattern ADAPTIVE_ICON_FOREGROUND_PATTERN =
            Pattern.compile("<foreground\\b[^>]*\\bdrawable=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    @Resource
    FileSaveBiz fileSaveBiz;

    @Resource
    ApkVersionBiz apkVersionBiz;

    public Apk getApkInfo(String fileId) throws IOException {
        FileSave apkFileSave = fileSaveBiz.getById(fileId);
        File file = fileSaveBiz.getFileObj(apkFileSave);
        try {
            try (ApkFile apkFile = new ApkFile(file)) {
                ApkMeta apkMeta = apkFile.getApkMeta();

                Apk apkInfo = new Apk();
                apkInfo.setFileId(fileId);
                apkInfo.setName(apkMeta.getName());
                apkInfo.setApplicationId(apkMeta.getPackageName());
                apkInfo.setVersionCode(apkMeta.getVersionCode());
                apkInfo.setVersionName(apkMeta.getVersionName());

                IconFace icon = getApkIcon(apkFile);
                if (icon != null && icon.getData() != null) {
                    File iconFile = File.createTempFile("apk-icon-", ".png");
                    try {
                        FileUtil.writeBytes(iconFile, icon.getData());
                        FileSave fileSave = fileSaveBiz.upload(iconFile);
                        apkInfo.setIconId(fileSave.getId());
                    } finally {
                        deleteTempFile(iconFile);
                    }
                }

                Apk apk = this.getApkByApplicationId(apkInfo.getApplicationId());
                if (apk != null) {
                    apkInfo.setShortCode(apk.getShortCode());
                } else {
                    apkInfo.setShortCode(RandomUtil.randomString(4));
                }

                return apkInfo;
            }
        } finally {
            deleteRemoteTempFile(apkFileSave, file);
        }
    }

    /**
     * 只读取清单选中的图标。自适应图标只解析选中的 XML 和前景图，特殊格式再回退到全量解析。
     */
    private IconFace getApkIcon(ApkFile apkFile) throws IOException {
        Icon icon = apkFile.getIconFile();
        if (icon == null || icon.getPath() == null
                || !icon.getPath().toLowerCase(Locale.ROOT).endsWith(".xml")) {
            return icon;
        }

        String adaptiveIconXml = apkFile.transBinaryXml(icon.getPath());
        if (adaptiveIconXml != null) {
            Matcher matcher = ADAPTIVE_ICON_FOREGROUND_PATTERN.matcher(adaptiveIconXml);
            if (matcher.find()) {
                String foregroundPath = matcher.group(1);
                byte[] foregroundData = apkFile.getFileData(foregroundPath);
                if (foregroundData != null) {
                    return new Icon(foregroundPath, 0, foregroundData);
                }
            }
        }

        List<IconFace> icons = apkFile.getAllIcons();
        return icons == null || icons.isEmpty() ? null : icons.get(0);
    }

    private void deleteTempFile(File file) {
        if (file != null && file.exists() && !file.delete()) {
            log.warn("清理APK图标临时文件失败: {}", file.getAbsolutePath());
        }
    }

    private void deleteRemoteTempFile(FileSave fileSave, File file) {
        if (fileSave != null && fileSave.getPlatform() != null
                && !fileSave.getPlatform().startsWith("local-")) {
            deleteTempFile(file);
        }
    }

    public Apk getApkByApplicationId(String applicationId) {
        long count = lambdaQuery().eq(Apk::getApplicationId, applicationId).count();
        if (count > 2) throw new BuzzException("有多个相同包名的应用，请联系管理员");
        if (count == 1) {
            return lambdaQuery().eq(Apk::getApplicationId, applicationId).one();
        }
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public Apk create(Apk entity) {
        FileSave apkFileSave = fileSaveBiz.getById(entity.getFileId());
        validateApkFile(entity.getFileId(), apkFileSave);
        entity.setSize(apkFileSave.getSize());

        // step 1: update apk info
        Apk apk = this.getApkByApplicationId(entity.getApplicationId());
        if (apk == null) {
            validateVersionCode(entity.getVersionCode());
            validateShortCodeUnique(entity.getShortCode(), null);

            // create apk
            if (!super.save(entity)) throw new BuzzException("保存APP信息失败，请重试");
            apk = entity;
        } else {
            validateVersionIncrease(apk, entity.getVersionCode());
            validateShortCodeUnique(entity.getShortCode(), apk.getId());

            apk.setName(entity.getName());
            apk.setVersionCode(entity.getVersionCode());
            apk.setVersionName(entity.getVersionName());
            apk.setFileId(entity.getFileId());
            apk.setSize(apkFileSave.getSize());
            apk.setIconId(entity.getIconId());
            apk.setShortCode(entity.getShortCode());
            apk.setRemark(entity.getRemark());
            if (!super.updateById(apk)) throw new BuzzException("更新APP信息失败，请重试");
        }

        // step 2: add apk version info
        ApkVersion apkVersion = new ApkVersion();
        BeanUtil.copyProperties(entity, apkVersion);
        apkVersion.setAppId(apk.getId());
        apkVersion.setForceUpdate(false); // 默认不强制更新
        apkVersionBiz.save(apkVersion);

        return apk;
    }

    @Override
    public boolean updateById(Apk entity) {
        validateShortCodeUnique(entity.getShortCode(), entity.getId());
        return super.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public Apk apiUpload(MultipartFile file, Integer appId, String remark) throws IOException {
        Apk apk = super.getById(appId);
        if (apk == null) throw new BuzzException("AppId Not Found");

        if (ObjUtil.notEqual(apk.getCrtUser(), getCurrentUserId())) {
            throw new BuzzException("Wrong AppId, Please Check.");
        }

        FileSave apkFileSave = fileSaveBiz.upload(file);
        Apk apkFileInfo = this.getApkInfo(apkFileSave.getId());

        // 0. check info
        if (ObjUtil.notEqual(apkFileInfo.getApplicationId(), apk.getApplicationId())) {
            throw new BuzzException("ApplicationId Not Equal, Please Check.");
        }
        validateVersionIncrease(apk, apkFileInfo.getVersionCode());

        // 1. update apk info
        apk.setName(apkFileInfo.getName());
        apk.setVersionCode(apkFileInfo.getVersionCode());
        apk.setVersionName(apkFileInfo.getVersionName());
        apk.setFileId(apkFileInfo.getFileId());
        apk.setSize(apkFileSave.getSize());
        apk.setIconId(apkFileInfo.getIconId());
        apk.setRemark(remark);
        this.updateById(apk);

        // 2. save version info
        ApkVersion apkVersion = new ApkVersion();
        apkVersion.setAppId(apk.getId());
        apkVersion.setApplicationId(apk.getApplicationId());
        apkVersion.setName(apkFileInfo.getName());
        apkVersion.setVersionCode(apkFileInfo.getVersionCode());
        apkVersion.setVersionName(apkFileInfo.getVersionName());
        apkVersion.setFileId(apkFileInfo.getFileId());
        apkVersion.setSize(apkFileSave.getSize());
        apkVersion.setIconId(apkFileInfo.getIconId());
        apkVersion.setRemark(remark);
        apkVersion.setForceUpdate(false);
        apkVersionBiz.save(apkVersion);

        return apk;
    }

    public Apk getByShortCode(String shortCode) {
        long count = lambdaQuery().eq(Apk::getShortCode, shortCode).count();
        if (count != 1) throw new BuzzException("获取APP信息失败，请检查短码是否正确");

        return lambdaQuery().eq(Apk::getShortCode, shortCode).one();
    }

    @Transactional
    public void addLastDownloadNum(Integer id) {
        // 最新版本下载数+1
        ApkVersion apkVersion = apkVersionBiz.getLatestVersion(id);
        if (apkVersion != null) {
            apkVersionBiz.addDownloadNum(apkVersion.getId());
            return;
        }

        // 当前apk下载数+1
        baseMapper.sumDownloadNum(id);
    }

    public void sumDownloadNum(Integer id) {
        baseMapper.sumDownloadNum(id);
    }

    /**
     * 获取APK最新版本
     *
     * @param id 编号
     * @return {@link Apk}
     */
    public Apk getApkLastRelease(Integer id) {
        Apk apk = getById(id);
        if (apk == null) throw new BuzzException("APP ID异常，请检查");

        ApkVersion apkVersion = apkVersionBiz.getLatestVersion(id);
        apk.setForceUpdate(apkVersion != null && Boolean.TRUE.equals(apkVersion.getForceUpdate()));

        return apk;
    }

    private void validateApkFile(String fileId, FileSave fileSave) {
        if (fileSave == null) throw new BuzzException("APK文件不存在，请重新上传");
        if (fileSave.getSize() == null || fileSave.getSize() <= 0) {
            throw new BuzzException("APK文件大小异常，请重新上传");
        }
        if (fileId == null || fileId.isBlank()) throw new BuzzException("APK文件ID不能为空");
    }

    private void validateVersionCode(Long versionCode) {
        if (versionCode == null || versionCode < 1) {
            throw new BuzzException("版本号必须是正整数");
        }
    }

    private void validateVersionIncrease(Apk apk, Long versionCode) {
        validateVersionCode(versionCode);
        if (apk.getVersionCode() != null && versionCode <= apk.getVersionCode()) {
            throw new BuzzException("新版本号必须大于当前版本号");
        }

        ApkVersion latestVersion = apkVersionBiz.getLatestVersion(apk.getId());
        if (latestVersion != null && latestVersion.getVersionCode() != null
                && versionCode <= latestVersion.getVersionCode()) {
            throw new BuzzException("新版本号必须大于历史最新版本号");
        }
    }

    private void validateShortCodeUnique(String shortCode, Integer excludeId) {
        if (shortCode == null || shortCode.isBlank()) throw new BuzzException("短链不能为空");

        long count = lambdaQuery()
                .eq(Apk::getShortCode, shortCode)
                .ne(excludeId != null, Apk::getId, excludeId)
                .count();
        if (count > 0) throw new BuzzException("该短链已存在，请更换短链");
    }

}
