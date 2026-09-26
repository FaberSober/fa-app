package com.faber.api.app.release.biz;

import com.faber.api.app.release.AppReleaseConstants;
import com.faber.api.app.release.entity.AppRelease;
import com.faber.api.app.release.entity.AppReleasePackage;
import com.faber.api.app.release.mapper.AppReleasePackageMapper;
import com.faber.api.base.admin.biz.FileSaveBiz;
import com.faber.api.base.admin.entity.FileSave;
import com.faber.core.exception.BuzzException;
import com.faber.core.vo.query.QueryParams;
import com.faber.core.web.biz.BaseBiz;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HexFormat;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** 应用版本发布包业务。 */
@Slf4j
@Service
public class AppReleasePackageBiz extends BaseBiz<AppReleasePackageMapper, AppReleasePackage> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_WGT_MANIFEST_SIZE = 1024 * 1024;
    private static final int MAX_WGT_MANIFEST_CANDIDATES = 64;
    private static final Pattern WGT_APP_ID_PATH_PATTERN =
            Pattern.compile("(?:^|/)apps/([^/]+)/www/manifest\\.json$", Pattern.CASE_INSENSITIVE);

    @Lazy
    @Resource
    AppReleaseBiz appReleaseBiz;

    @Resource
    FileSaveBiz fileSaveBiz;

    @Value("${fa.app.release.wgt-max-size-bytes:209715200}")
    private long maxWgtSizeBytes;

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

    @Transactional(rollbackFor = Exception.class)
    public AppReleasePackage uploadWgt(Long releaseId, MultipartFile file) throws IOException {
        return uploadWgt(releaseId, file, readWgtMetadata(file));
    }

    @Transactional(rollbackFor = Exception.class)
    public AppReleasePackage uploadWgt(Long releaseId, MultipartFile file,
                                       WgtMetadata metadata) throws IOException {
        AppRelease release = requireDraftRelease(releaseId);
        validateWgtFile(file);
        validateWgtMetadata(metadata, release);

        AppReleasePackage candidate = new AppReleasePackage();
        candidate.setReleaseId(releaseId);
        candidate.setPlatform(AppReleaseConstants.PLATFORM_APP_PLUS);
        candidate.setPackageType(AppReleaseConstants.PACKAGE_WGT);
        candidate.setBaseVersionCode(null);
        validateUnique(candidate, null);

        String sha256 = calculateSha256(file);
        FileSave fileSave = fileSaveBiz.upload(file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) return;
                try {
                    fileSaveBiz.cleanupUploadedFile(fileSave);
                } catch (RuntimeException error) {
                    log.error("回滚WGT上传后清理文件失败, fileId={}", fileSave.getId(), error);
                }
            }
        });
        candidate.setFileId(fileSave.getId());
        candidate.setSize(fileSave.getSize() != null ? fileSave.getSize() : file.getSize());
        candidate.setSha256(sha256);
        if (!save(candidate)) throw new BuzzException("WGT发布包保存失败，请重试");
        return candidate;
    }

    @Transactional(rollbackFor = Exception.class)
    public AppReleasePackage uploadWgt(Long releaseId, FileSave fileSave,
                                       WgtMetadata metadata) throws IOException {
        AppRelease release = requireDraftRelease(releaseId);
        validateWgtFile(fileSave);
        validateWgtMetadata(metadata, release);

        AppReleasePackage candidate = new AppReleasePackage();
        candidate.setReleaseId(releaseId);
        candidate.setPlatform(AppReleaseConstants.PLATFORM_APP_PLUS);
        candidate.setPackageType(AppReleaseConstants.PACKAGE_WGT);
        candidate.setBaseVersionCode(null);
        validateUnique(candidate, null);

        candidate.setFileId(fileSave.getId());
        candidate.setSize(fileSave.getSize());
        candidate.setSha256(calculateSha256(fileSave));
        if (!save(candidate)) throw new BuzzException("WGT发布包保存失败，请重试");
        return candidate;
    }

    public WgtMetadata readWgtMetadata(MultipartFile file) throws IOException {
        validateWgtFile(file);
        Path tempFile = Files.createTempFile("fa-wgt-metadata-", ".wgt");
        try {
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return readWgtMetadata(tempFile.toFile());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public WgtMetadata readWgtMetadata(FileSave fileSave) throws IOException {
        validateWgtFile(fileSave);
        File file = fileSaveBiz.getFileObj(fileSave);
        try {
            return readWgtMetadata(file);
        } finally {
            deleteRemoteTempFile(fileSave, file);
        }
    }

    private WgtMetadata readWgtMetadata(File file) throws IOException {
        try (ZipFile zip = new ZipFile(file, StandardCharsets.UTF_8)) {
            ZipEntry manifest = findManifest(zip);
            if (manifest == null) throw new BuzzException("WGT包中未找到manifest.json，无法识别资源版本");
            byte[] content;
            try (InputStream input = zip.getInputStream(manifest)) {
                content = input.readNBytes(MAX_WGT_MANIFEST_SIZE + 1);
            }
            if (content.length > MAX_WGT_MANIFEST_SIZE) {
                throw new BuzzException("WGT包内manifest.json超过1MiB，无法安全解析");
            }

            JsonNode root = OBJECT_MAPPER.readTree(content);
            String dcloudAppId = readDcloudAppId(zip, manifest, root);
            String versionName = readManifestValue(root, "versionName", "name");
            String versionCodeText = readManifestValue(root, "versionCode", "code");
            if (versionName == null || versionName.isBlank()
                    || versionCodeText == null || !versionCodeText.matches("[1-9]\\d*")) {
                throw new BuzzException("WGT包内manifest.json缺少有效的资源版本名称或版本编码");
            }
            try {
                return new WgtMetadata(dcloudAppId, versionName.trim(), Long.parseLong(versionCodeText));
            } catch (NumberFormatException error) {
                throw new BuzzException("WGT包内资源版本编码超出支持范围");
            }
        } catch (ZipException error) {
            throw new BuzzException("WGT文件不是有效的ZIP资源包");
        } catch (JsonProcessingException error) {
            throw new BuzzException("WGT包内manifest.json格式无效");
        }
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

    @Override
    public boolean saveBatch(Collection<AppReleasePackage> entityList) {
        throw new BuzzException("发布包请逐条新增");
    }

    @Override
    public boolean updateBatchById(Collection<AppReleasePackage> entityList) {
        throw new BuzzException("发布包请逐条修改");
    }

    @Override
    public boolean saveOrUpdateBatch(Collection<AppReleasePackage> entityList) {
        throw new BuzzException("发布包请逐条保存");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeBatchByIds(List<Serializable> ids) {
        if (ids != null) ids.forEach(this::removeById);
    }

    @Override
    public void removePerById(Serializable id) {
        throw new BuzzException("发布包不支持永久删除");
    }

    @Override
    public void removePerBatchByIds(List<Serializable> ids) {
        throw new BuzzException("发布包不支持永久删除");
    }

    @Override
    public void removeByQuery(QueryParams query) {
        throw new BuzzException("发布包请按ID删除");
    }

    @Override
    public void removeMine() {
        throw new BuzzException("发布包请按ID删除");
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

        if (!AppReleaseConstants.PACKAGE_WGT.equals(entity.getPackageType())
                && entity.getBaseVersionCode() != null) {
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

    private void validateWgtMetadata(WgtMetadata metadata, AppRelease release) {
        if (metadata == null || !release.getVersionName().equals(metadata.versionName())
                || !release.getVersionCode().equals(metadata.versionCode())) {
            String packageVersion = metadata == null ? "无法识别" : metadata.versionName() + "（" + metadata.versionCode() + "）";
            throw new BuzzException("WGT资源版本" + packageVersion + "与发布目标"
                    + release.getVersionName() + "（" + release.getVersionCode() + "）不一致");
        }
    }

    private ZipEntry findManifest(ZipFile zip) {
        ZipEntry rootManifest = zip.getEntry("manifest.json");
        if (rootManifest != null && !rootManifest.isDirectory()) return rootManifest;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String normalizedName = entry.getName().replace('\\', '/');
            if (!entry.isDirectory() && normalizedName.endsWith("/manifest.json")) return entry;
        }
        return null;
    }

    private String readDcloudAppId(ZipFile zip, ZipEntry selectedManifest, JsonNode selectedRoot) throws IOException {
        Set<String> appIds = new LinkedHashSet<>();
        addDcloudAppId(appIds, readManifestValue(selectedRoot, "appid", "appid"));
        // Packaged WGTs may normalize uni-app's manifest appid field to the top-level "id" field.
        addDcloudAppId(appIds, readManifestValue(selectedRoot, "id", "id"));
        addDcloudAppIdFromPath(appIds, selectedManifest.getName());

        int manifestCount = 0;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName().replace('\\', '/');
            String lowerEntryName = entryName.toLowerCase(Locale.ROOT);
            if (entry.isDirectory() || !(lowerEntryName.equals("manifest.json")
                    || lowerEntryName.endsWith("/manifest.json"))) {
                continue;
            }
            if (++manifestCount > MAX_WGT_MANIFEST_CANDIDATES) {
                throw new BuzzException("WGT包中manifest.json文件过多，无法安全识别 DCloud AppID");
            }

            addDcloudAppIdFromPath(appIds, entryName);
            if (entry.getName().equals(selectedManifest.getName()) || entry.getSize() > MAX_WGT_MANIFEST_SIZE) continue;

            byte[] content;
            try (InputStream input = zip.getInputStream(entry)) {
                content = input.readNBytes(MAX_WGT_MANIFEST_SIZE + 1);
            }
            if (content.length > MAX_WGT_MANIFEST_SIZE) continue;
            try {
                JsonNode candidateRoot = OBJECT_MAPPER.readTree(content);
                if (candidateRoot != null) {
                    addDcloudAppId(appIds, readManifestValue(candidateRoot, "appid", "appid"));
                }
            } catch (JsonProcessingException ignored) {
                // Ignore unrelated or non-JSON manifest files; the selected resource manifest is validated separately.
            }
        }

        if (appIds.size() > 1) throw new BuzzException("WGT包中包含多个不同的 DCloud AppID，无法自动匹配应用");
        return appIds.stream().findFirst().orElse(null);
    }

    private void addDcloudAppIdFromPath(Set<String> appIds, String entryName) {
        Matcher matcher = WGT_APP_ID_PATH_PATTERN.matcher(entryName.replace('\\', '/'));
        if (matcher.find()) addDcloudAppId(appIds, matcher.group(1));
    }

    private void addDcloudAppId(Set<String> appIds, String dcloudAppId) {
        if (dcloudAppId != null && !dcloudAppId.isBlank()) appIds.add(dcloudAppId.trim());
    }

    private String readManifestValue(JsonNode root, String rootKey, String nestedKey) {
        JsonNode value = root.path(rootKey);
        if (value.isMissingNode() || value.isNull() || value.isContainerNode()) {
            value = root.path("version").path(nestedKey);
        }
        if (value.isMissingNode() || value.isNull() || value.isContainerNode()) return null;
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    public record WgtMetadata(String dcloudAppId, String versionName, Long versionCode) {}

    private void validateWgtFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BuzzException("WGT文件不能为空");
        if (file.getSize() > maxWgtSizeBytes) throw new BuzzException("WGT文件大小超过上传限制");
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".wgt")) {
            throw new BuzzException("WGT文件必须使用.wgt后缀");
        }
    }

    private void validateWgtFile(FileSave fileSave) {
        if (fileSave == null) throw new BuzzException("WGT文件不存在，请重新上传");
        if (fileSave.getSize() == null || fileSave.getSize() <= 0) {
            throw new BuzzException("WGT文件大小异常，请重新上传");
        }
        if (fileSave.getSize() > maxWgtSizeBytes) throw new BuzzException("WGT文件大小超过上传限制");
        String filename = fileSave.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".wgt")) {
            throw new BuzzException("WGT文件必须使用.wgt后缀");
        }
    }

    private String calculateSha256(FileSave fileSave) throws IOException {
        File file = fileSaveBiz.getFileObj(fileSave);
        try (InputStream input = Files.newInputStream(file.toPath())) {
            return calculateSha256(input);
        } finally {
            deleteRemoteTempFile(fileSave, file);
        }
    }

    private void deleteRemoteTempFile(FileSave fileSave, File file) {
        if (fileSave != null && fileSave.getPlatform() != null
                && !fileSave.getPlatform().startsWith("local-")
                && file != null && file.exists() && !file.delete()) {
            log.warn("清理WGT临时文件失败: {}", file.getAbsolutePath());
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
