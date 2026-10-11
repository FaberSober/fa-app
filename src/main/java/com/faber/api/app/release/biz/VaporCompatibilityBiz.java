package com.faber.api.app.release.biz;

import cn.hutool.crypto.digest.DigestUtil;
import com.faber.api.app.app.biz.ApkBiz;
import com.faber.api.app.app.biz.ApkVersionBiz;
import com.faber.api.app.app.entity.ApkVersion;
import com.faber.api.app.release.entity.AppRelease;
import com.faber.api.base.admin.biz.FileSaveBiz;
import com.faber.api.base.admin.entity.FileSave;
import com.faber.core.exception.BuzzException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Objects;

/** CI证据与已发布APK绑定；构建报告不能替代原始WGT校验。 */
@Service
public class VaporCompatibilityBiz {
    static final String VAPOR = "UNI_APP_X_VAPOR";
    static final ObjectMapper JSON = new ObjectMapper();
    @Resource ApkBiz apkBiz;
    @Resource ApkVersionBiz apkVersionBiz;
    @Resource FileSaveBiz fileSaveBiz;

    @Transactional(rollbackFor = Exception.class)
    public NativeBaseRet register(Integer appId, MultipartFile file) throws IOException {
        JsonNode report = readReport(file);
        validateEvidence(report);
        var app = apkBiz.getById(appId);
        if (app == null || !Objects.equals(app.getDcloudAppId(), report.path("appId").asText())) fail("基座报告AppID与应用不一致");
        long code = report.path("versionCode").asLong(-1);
        ApkVersion version = apkVersionBiz.listNativeBaseCandidates(appId).stream()
                .filter(v -> Objects.equals(code, v.getVersionCode())).findFirst()
                .orElseThrow(() -> new BuzzException("基座报告必须绑定本应用已上传且未撤回的APK版本"));
        if (!Objects.equals(version.getVersionName(), report.path("versionName").asText())) fail("基座报告版本名称与APK不一致");
        FileSave apk = fileSaveBiz.getById(version.getFileId());
        if (apk == null || !sha256(apk).equals(report.path("sha256").asText())) fail("基座报告与原始APK的SHA-256不一致");
        if (!modules(report).contains("uni-install-wgt")) fail("APK基座缺少uni-install-wgt原生模块，请先重新构建APK");
        if (version.getNativeBaseReportFileId() != null) {
            JsonNode current = readReport(version.getNativeBaseReportFileId());
            if (!current.equals(report)) fail("已注册的APK基座报告不可替换");
            return new NativeBaseRet(appId, code, version.getNativeBaseReportFileId());
        }
        FileSave saved = fileSaveBiz.upload(file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) fileSaveBiz.cleanupUploadedFile(saved);
            }
        });
        if (!apkVersionBiz.lambdaUpdate().eq(ApkVersion::getId, version.getId())
                .isNull(ApkVersion::getNativeBaseReportFileId)
                .set(ApkVersion::getNativeBaseReportFileId, saved.getId()).update()) fail("基座报告注册冲突，请重新查询");
        return new NativeBaseRet(appId, code, saved.getId());
    }

    void prepare(AppRelease release, AppReleasePackageBiz.WgtMetadata metadata, MultipartFile buildReport,
                 MultipartFile wgt) throws IOException {
        if (buildReport == null && release.getNativeBaseReportFileId() == null) return;
        JsonNode report = readReport(buildReport);
        validateBuildReport(report, metadata);
        try (var input = wgt.getInputStream()) {
            if (!DigestUtil.sha256Hex(input).equals(report.path("sha256").asText())
                    || wgt.getSize() != report.path("size").asLong()) fail("WGT构建报告与原始包不一致");
        }
        release.setWgtBuildReportJson(JSON.writeValueAsString(report));
        validateRelease(release);
    }

    void validateRelease(AppRelease release) throws IOException {
        if (!VAPOR.equals(release.getRuntimeType())) return;
        // 人工上传以实际WGT为准；只有附带CI报告时才校验原生构建证据。
        if (release.getNativeBaseReportFileId() == null && release.getWgtBuildReportJson() == null) return;
        if (release.getMinSupportedVersionCode() == null || release.getNativeBaseReportFileId() == null
                || release.getWgtBuildReportJson() == null) fail("Vapor发布需要最低APK版本、已注册的基座报告和WGT构建报告");
        ApkVersion base = apkVersionBiz.listPublishedByAppId(release.getAppId()).stream()
                .filter(v -> Objects.equals(v.getNativeBaseReportFileId(), release.getNativeBaseReportFileId())
                        && Objects.equals(v.getVersionCode(), release.getMinSupportedVersionCode())).findFirst()
                .orElseThrow(() -> new BuzzException("原生基座报告未绑定最低兼容APK版本"));
        JsonNode nativeReport = readReport(base.getNativeBaseReportFileId());
        FileSave apk = fileSaveBiz.getById(base.getFileId());
        if (apk == null || !sha256(apk).equals(nativeReport.path("sha256").asText())) fail("原生APK与已注册的基座报告不一致");
        JsonNode build = JSON.readTree(release.getWgtBuildReportJson());
        var app = apkBiz.getById(release.getAppId());
        if (app == null || !Objects.equals(app.getDcloudAppId(), build.path("appId").asText())) fail("应用登记的DCloud AppID与WGT构建报告不一致");
        if (!compatible(nativeReport, build)) fail("Vapor原生配置、SDK、CLI或模块与APK基座不兼容，请发布新的APK");
        if (release.getVersionCode() <= base.getVersionCode()) fail("WGT资源版本必须高于原生APK基座的资源版本");
    }

    boolean compatibleWithApk(AppRelease release, long apkCode) {
        if (release.getNativeBaseReportFileId() == null && release.getWgtBuildReportJson() == null) return true;
        try {
            ApkVersion installed = apkVersionBiz.listPublishedByAppId(release.getAppId()).stream()
                    .filter(v -> Objects.equals(v.getVersionCode(), apkCode)).findFirst().orElse(null);
            return installed != null && installed.getNativeBaseReportFileId() != null
                    && compatible(readReport(installed.getNativeBaseReportFileId()), JSON.readTree(release.getWgtBuildReportJson()));
        } catch (IOException | RuntimeException error) { return false; }
    }

    static void validateBuildReport(JsonNode report, AppReleasePackageBiz.WgtMetadata metadata) {
        validateEvidence(report);
        if (!Objects.equals(metadata.dcloudAppId(), report.path("appId").asText())
                || !Objects.equals(metadata.versionName(), report.path("versionName").asText())
                || !Objects.equals(metadata.versionCode().toString(), report.path("versionCode").asText())
                || !Objects.equals(metadata.compilerVersion(), report.path("compilerVersion").asText())
                || !Objects.equals(metadata.minRuntimeVersion(), report.path("minRuntimeVersion").asText())
                || !modules(report).equals(new HashSet<>(metadata.modules()))
                || report.path("size").asLong(-1) < 1) fail("WGT报告与包内元数据不一致");
    }

    static boolean compatible(JsonNode nativeReport, JsonNode build) {
        validateEvidence(nativeReport);
        validateEvidence(build);
        for (String key : new String[]{"appId", "runtimeType", "platform", "cliVersion", "sdkSha256", "nativeRequirementsHash"}) {
            if (!nativeReport.path(key).equals(build.path(key))) return false;
        }
        return modules(nativeReport).contains("uni-install-wgt") && modules(nativeReport).containsAll(modules(build));
    }

    static void validateEvidence(JsonNode report) {
        if (report == null || !report.isObject() || !VAPOR.equals(report.path("runtimeType").asText())
                || !"ANDROID".equals(report.path("platform").asText())
                || !report.path("appId").asText().matches("__UNI__[A-Za-z0-9]+")
                || !report.path("versionCode").asText().matches("[1-9][0-9]*")
                || report.path("versionName").asText().isBlank()
                || !report.path("cliVersion").asText().matches("5\\.31\\.[0-9]{10}-alpha")
                || !report.path("compilerVersion").asText().equals("5.31")
                || !report.path("minRuntimeVersion").asText().equals("5.31")) fail("仅支持已验证的Android Vapor 5.31报告");
        for (String key : new String[]{"sha256", "sdkSha256", "nativeRequirementsHash"}) {
            if (!report.path(key).asText().matches("[a-f0-9]{64}")) fail("构建报告缺少有效的" + key);
        }
        modules(report);
    }

    private static HashSet<String> modules(JsonNode report) {
        JsonNode values = report.path("modules");
        if (!values.isArray() || values.isEmpty() || values.size() > 256) fail("构建报告缺少原生模块清单");
        var modules = new HashSet<String>();
        for (JsonNode value : values) {
            if (!value.isTextual() || !value.asText().matches("[A-Za-z0-9_-]{1,128}") || !modules.add(value.asText())) fail("构建报告模块清单无效");
        }
        return modules;
    }

    private JsonNode readReport(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > 1024 * 1024) throw new BuzzException("请选择不超过1MiB的构建报告JSON");
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(1024 * 1024 + 1);
            if (bytes.length > 1024 * 1024) throw new BuzzException("构建报告超过1MiB");
            return JSON.readTree(bytes);
        }
    }

    private JsonNode readReport(String fileId) throws IOException {
        FileSave saved = fileSaveBiz.getById(fileId);
        if (saved == null) throw new BuzzException("原生基座报告文件不存在");
        var file = fileSaveBiz.getFileObj(saved);
        try (var input = Files.newInputStream(file.toPath())) {
            byte[] bytes = input.readNBytes(1024 * 1024 + 1);
            if (bytes.length > 1024 * 1024) throw new BuzzException("原生基座报告超过1MiB");
            return JSON.readTree(bytes);
        } finally { deleteTemporary(saved, file); }
    }

    private String sha256(FileSave saved) throws IOException {
        var file = fileSaveBiz.getFileObj(saved);
        try { return DigestUtil.sha256Hex(file); } finally { deleteTemporary(saved, file); }
    }

    private void deleteTemporary(FileSave saved, java.io.File file) throws IOException {
        if (saved.getPlatform() != null && !saved.getPlatform().startsWith("local-")) Files.deleteIfExists(file.toPath());
    }

    private static void fail(String message) { throw new BuzzException(message); }

    public record NativeBaseRet(Integer appId, Long apkVersionCode, String nativeBaseReportFileId) {}
}
