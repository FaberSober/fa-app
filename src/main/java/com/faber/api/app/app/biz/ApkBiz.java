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
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
    private static final Pattern UNI_APP_MANIFEST_PATH_PATTERN =
            Pattern.compile("^assets/apps/([^/]+)/www/manifest\\.json$");

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
                apkInfo.setDcloudAppId(readDcloudAppId(file));
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

    public Apk getByDcloudAppId(String dcloudAppId) {
        String normalizedDcloudAppId = normalizeDcloudAppId(dcloudAppId);
        if (normalizedDcloudAppId == null) throw new BuzzException("DCloud AppID 不能为空");

        List<Apk> apps = lambdaQuery().eq(Apk::getDcloudAppId, normalizedDcloudAppId).list();
        if (apps.isEmpty()) throw new BuzzException("没有找到绑定此 DCloud AppID 的 APK 应用，请先编辑应用并填写 DCloud AppID");
        if (apps.size() > 1) throw new BuzzException("DCloud AppID 关联了多个 APK 应用，请联系管理员处理");
        Apk app = apps.get(0);
        if (!normalizedDcloudAppId.equals(app.getDcloudAppId())) {
            throw new BuzzException("没有找到绑定此 DCloud AppID 的 APK 应用，请检查 AppID 大小写");
        }
        return app;
    }

    @Transactional(rollbackFor = Exception.class)
    public Apk create(Apk entity) {
        FileSave apkFileSave = fileSaveBiz.getById(entity.getFileId());
        validateApkFile(entity.getFileId(), apkFileSave);
        entity.setSize(apkFileSave.getSize());
        String dcloudAppId = normalizeDcloudAppId(entity.getDcloudAppId());
        entity.setDcloudAppId(dcloudAppId);

        // step 1: update apk info
        Apk apk = this.getApkByApplicationId(entity.getApplicationId());
        if (apk == null) {
            validateVersionCode(entity.getVersionCode());
            validateShortCodeUnique(entity.getShortCode(), null);
            validateDcloudAppIdUnique(dcloudAppId, null);

            // create apk
            if (!super.save(entity)) throw new BuzzException("保存APP信息失败，请重试");
            apk = entity;
        } else {
            validateVersionIncrease(apk, entity.getVersionCode());
            validateShortCodeUnique(entity.getShortCode(), apk.getId());
            if (dcloudAppId != null) validateDcloudAppIdUnique(dcloudAppId, apk.getId());

            apk.setName(entity.getName());
            apk.setVersionCode(entity.getVersionCode());
            apk.setVersionName(entity.getVersionName());
            if (dcloudAppId != null) apk.setDcloudAppId(dcloudAppId);
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
        entity.setDcloudAppId(normalizeDcloudAppId(entity.getDcloudAppId()));
        validateShortCodeUnique(entity.getShortCode(), entity.getId());
        validateDcloudAppIdUnique(entity.getDcloudAppId(), entity.getId());
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
        String dcloudAppId = normalizeDcloudAppId(apkFileInfo.getDcloudAppId());
        if (dcloudAppId != null) {
            apk.setDcloudAppId(dcloudAppId);
        }
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

    private String readDcloudAppId(File apkFile) throws IOException {
        Set<String> appIds = new HashSet<>();
        try (ZipFile zipFile = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                String entryName = entries.nextElement().getName().replace('\\', '/');
                Matcher matcher = UNI_APP_MANIFEST_PATH_PATTERN.matcher(entryName);
                if (matcher.matches() && !matcher.group(1).isBlank()) {
                    appIds.add(matcher.group(1).trim());
                }
            }
        }
        if (appIds.size() > 1) throw new BuzzException("APK中包含多个 uni-app manifest，无法自动识别 DCloud AppID");
        return appIds.stream().findFirst().orElse(null);
    }

    private String normalizeDcloudAppId(String dcloudAppId) {
        if (dcloudAppId == null || dcloudAppId.isBlank()) return null;
        String normalizedDcloudAppId = dcloudAppId.trim();
        if (normalizedDcloudAppId.length() > 128) throw new BuzzException("DCloud AppID 长度不能超过128个字符");
        return normalizedDcloudAppId;
    }

    private void validateDcloudAppIdUnique(String dcloudAppId, Integer excludeId) {
        if (dcloudAppId == null) return;
        long count = lambdaQuery()
                .eq(Apk::getDcloudAppId, dcloudAppId)
                .ne(excludeId != null, Apk::getId, excludeId)
                .count();
        if (count > 0) throw new BuzzException("DCloud AppID 已关联到其他应用");
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
