package com.faber.api.app.release.biz;

import com.faber.api.app.release.AppReleaseConstants;
import com.faber.api.app.release.entity.AppRelease;
import com.faber.api.app.release.entity.AppReleasePackage;
import com.faber.api.app.release.mapper.AppReleasePackageMapper;
import com.faber.api.base.admin.biz.FileSaveBiz;
import com.faber.api.base.admin.entity.FileSave;
import com.faber.core.exception.BuzzException;
import com.faber.core.web.biz.BaseBiz;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.nio.file.Files;

/** 应用版本发布包业务。 */
@Service
public class AppReleasePackageBiz extends BaseBiz<AppReleasePackageMapper, AppReleasePackage> {

    @Lazy
    @Resource
    AppReleaseBiz appReleaseBiz;

    @Resource
    FileSaveBiz fileSaveBiz;

    public List<AppReleasePackage> listByReleaseId(Long releaseId) {
        return lambdaQuery()
                .eq(AppReleasePackage::getReleaseId, releaseId)
                .orderByAsc(AppReleasePackage::getPlatform)
                .orderByAsc(AppReleasePackage::getPackageType)
                .list();
    }

    public void validatePublishPackages(Long releaseId) {
        AppRelease release = requireDraftRelease(releaseId);
        List<AppReleasePackage> packages = listByReleaseId(releaseId);
        if (packages.isEmpty()) throw new BuzzException("发布版本至少需要一个发布包");

        for (AppReleasePackage packageInfo : packages) {
            validatePackage(packageInfo, release);
            FileSave fileSave = fileSaveBiz.getByIdWithCache(packageInfo.getFileId());
            if (fileSave == null) throw new BuzzException("发布包文件不存在，请重新上传");
            if (packageInfo.getSize() != null && fileSave.getSize() != null
                    && !packageInfo.getSize().equals(fileSave.getSize())) {
                throw new BuzzException("发布包文件大小与记录不一致，请重新上传");
            }
            if (AppReleaseConstants.PACKAGE_WGT.equals(packageInfo.getPackageType())) {
                try (InputStream input = Files.newInputStream(fileSaveBiz.getFileObj(fileSave).toPath())) {
                    String actualSha256 = calculateSha256(input);
                    if (!packageInfo.getSha256().equalsIgnoreCase(actualSha256)) {
                        throw new BuzzException("WGT文件SHA-256校验失败，请重新上传");
                    }
                } catch (IOException e) {
                    throw new BuzzException("WGT文件读取失败，请重新上传");
                }
            }
        }
    }

    public AppReleasePackage uploadWgt(Long releaseId, Long baseVersionCode, MultipartFile file) throws IOException {
        AppRelease release = requireDraftRelease(releaseId);
        validateWgtFile(file);
        validateWgtBaseVersion(baseVersionCode, release);

        AppReleasePackage candidate = new AppReleasePackage();
        candidate.setReleaseId(releaseId);
        candidate.setPlatform(AppReleaseConstants.PLATFORM_APP_PLUS);
        candidate.setPackageType(AppReleaseConstants.PACKAGE_WGT);
        candidate.setBaseVersionCode(baseVersionCode);
        validateUnique(candidate, null);

        FileSave fileSave = fileSaveBiz.upload(file);
        candidate.setFileId(fileSave.getId());
        candidate.setSize(fileSave.getSize() != null ? fileSave.getSize() : file.getSize());
        candidate.setSha256(calculateSha256(file));
        if (!save(candidate)) throw new BuzzException("WGT发布包保存失败，请重试");
        return candidate;
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
            validateWgtBaseVersion(entity.getBaseVersionCode(), release);
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
                .eq(AppReleaseConstants.PACKAGE_WGT.equals(entity.getPackageType()),
                        AppReleasePackage::getBaseVersionCode, entity.getBaseVersionCode())
                .isNull(!AppReleaseConstants.PACKAGE_WGT.equals(entity.getPackageType()),
                        AppReleasePackage::getBaseVersionCode)
                .ne(excludeId != null, AppReleasePackage::getId, excludeId)
                .count();
        if (count > 0) throw new BuzzException("同一版本的平台、包类型和基础版本已存在");
    }

    private void validateWgtBaseVersion(Long baseVersionCode, AppRelease release) {
        if (baseVersionCode == null || baseVersionCode < 1
                || baseVersionCode >= release.getVersionCode()) {
            throw new BuzzException("WGT基础版本号必须小于目标版本号");
        }
    }

    private void validateWgtFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BuzzException("WGT文件不能为空");
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".wgt")) {
            throw new BuzzException("WGT文件必须使用.wgt后缀");
        }
    }

    private String calculateSha256(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            return calculateSha256(input);
        }
    }

    private String calculateSha256(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                digest.update(buffer, 0, length);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256算法不可用", e);
        }
    }
}
