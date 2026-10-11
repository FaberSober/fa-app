package com.faber.api.app.release.biz;

import com.faber.core.exception.BuzzException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.luben.zstd.Zstd;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class WgtCompatibilityTest {
    @TempDir Path directory;
    private static final String VAPOR_MANIFEST = """
            {"id":"__UNI__17534C7","version":{"name":"0.0.40","code":"40"},
             "uni-app-x":{"vapor":true,"compilerVersion":"5.31","minRuntimeVersion":"5.31"},
             "app-android":{"distribute":{"modules":{"uni-network":{}}}}}
            """;

    @Test void readsRealExportWhenProvided() throws Exception {
        String sample = System.getProperty("wgt.sample");
        org.junit.jupiter.api.Assumptions.assumeTrue(sample != null);
        var metadata = new AppReleasePackageBiz().readWgtMetadata(new java.io.File(sample));
        assertEquals("UNI_APP_X_VAPOR", metadata.runtimeType());
        assertEquals("ANDROID", metadata.platform());
        assertEquals("5.31", metadata.compilerVersion());
        assertFalse(metadata.modules().isEmpty());
    }

    @Test void publishAcceptsPersistedMetadataAndStillRejectsChangedMetadataOrFile() throws Exception {
        for (boolean vapor : new boolean[]{false, true}) {
            var file = vapor ? archive(vaporEntries(), true) : archive(Map.of("manifest.json",
                    "{\"id\":\"__UNI__17534C7\",\"version\":{\"name\":\"0.0.40\",\"code\":\"40\"}}"), false);
            var service = org.mockito.Mockito.spy(new AppReleasePackageBiz());
            service.appReleaseBiz = org.mockito.Mockito.mock(AppReleaseBiz.class);
            service.fileSaveBiz = org.mockito.Mockito.mock(com.faber.api.base.admin.biz.FileSaveBiz.class);
            var metadata = service.readWgtMetadata(file.toFile());
            var release = new com.faber.api.app.release.entity.AppRelease();
            release.setId(1L); release.setStatus("DRAFT");
            release.setVersionCode(metadata.versionCode()); release.setVersionName(metadata.versionName());
            release.setRuntimeType(metadata.runtimeType()); release.setPlatform(metadata.platform());
            // 模拟上传后存入数据库，再于发布时重新读取JSON。
            String stored = VaporCompatibilityBiz.JSON.writeValueAsString(metadata);
            release.setWgtMetadataJson(stored);
            var pkg = new com.faber.api.app.release.entity.AppReleasePackage();
            pkg.setPlatform(metadata.platform()); pkg.setPackageType("WGT"); pkg.setFileId("wgt");
            pkg.setSize(Files.size(file)); pkg.setSha256(cn.hutool.crypto.digest.DigestUtil.sha256Hex(file.toFile()));
            var saved = new com.faber.api.base.admin.entity.FileSave();
            saved.setId("wgt"); saved.setPlatform("local-test"); saved.setSize(Files.size(file));
            org.mockito.Mockito.when(service.appReleaseBiz.getById(1L)).thenReturn(release);
            org.mockito.Mockito.doReturn(List.of(pkg)).when(service).listByReleaseId(1L);
            org.mockito.Mockito.when(service.fileSaveBiz.getByIdWithCache("wgt")).thenReturn(saved);
            org.mockito.Mockito.when(service.fileSaveBiz.getFileObj(saved)).thenReturn(file.toFile());
            assertDoesNotThrow(() -> service.validatePublishPackages(1L));
            var changed = (ObjectNode) VaporCompatibilityBiz.JSON.readTree(stored);
            changed.put("dcloudAppId", "__UNI__OTHER");
            release.setWgtMetadataJson(changed.toString());
            assertThrows(BuzzException.class, () -> service.validatePublishPackages(1L));
            release.setWgtMetadataJson(stored);
            pkg.setSha256("0".repeat(64));
            assertThrows(BuzzException.class, () -> service.validatePublishPackages(1L));
        }
    }

    @Test void retainsLegacyZipFormat() throws Exception {
        var file = archive(Map.of("manifest.json", "{\"id\":\"__UNI__17534C7\",\"version\":{\"name\":\"1.2.0\",\"code\":\"40\"}}"), false);
        var metadata = new AppReleasePackageBiz().readWgtMetadata(file.toFile());
        assertEquals("APP_PLUS", metadata.runtimeType());
        assertEquals("APP_PLUS", metadata.platform());
        assertEquals(40L, metadata.versionCode());
    }

    @Test void readsOfficialVaporFormatWithoutExtractingEntries() throws Exception {
        var file = archive(vaporEntries(), true);
        var metadata = new AppReleasePackageBiz().readWgtMetadata(file.toFile());
        assertEquals("UNI_APP_X_VAPOR", metadata.runtimeType());
        assertEquals("ANDROID", metadata.platform());
        assertEquals("__UNI__17534C7", metadata.dcloudAppId());
        assertEquals(List.of("uni-network"), metadata.modules());
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test void rejectsVaporDisguisedAsLegacyZip() throws Exception {
        var file = archive(vaporEntries(), false);
        assertThrows(BuzzException.class, () -> new AppReleasePackageBiz().readWgtMetadata(file.toFile()));
    }

    @Test void rejectsUnsupportedRuntimeAndMissingBytecode() throws Exception {
        var entries = vaporEntries();
        entries.put("www/manifest.json", VAPOR_MANIFEST.replace("5.31", "5.30"));
        var oldRuntime = archive(entries, true);
        assertThrows(BuzzException.class, () -> new AppReleasePackageBiz().readWgtMetadata(oldRuntime.toFile()));
        entries = vaporEntries();
        entries.remove("www/bytes/app.bytes");
        var noBytecode = archive(entries, true);
        assertThrows(BuzzException.class, () -> new AppReleasePackageBiz().readWgtMetadata(noBytecode.toFile()));
    }

    @Test void rejectsPathTraversalAndCorruptArchive() throws Exception {
        var traversal = archive(Map.of("../manifest.json", "{}"), false);
        assertThrows(java.io.IOException.class, () -> WgtArchive.open(traversal.toFile()));
        var valid = archive(Map.of("manifest.json", "test-content"), false);
        byte[] bytes = Files.readAllBytes(valid);
        Files.write(valid, java.util.Arrays.copyOf(bytes, bytes.length - 12));
        assertThrows(java.io.IOException.class, () -> WgtArchive.open(valid.toFile()));
    }

    @Test void requiresMatchingNativeEvidenceAndModuleSuperset() throws Exception {
        ObjectNode build = evidence();
        ObjectNode base = build.deepCopy();
        base.withArray("modules").add("uni-install-wgt");
        assertTrue(VaporCompatibilityBiz.compatible(base, build));
        for (String key : new String[]{"nativeRequirementsHash", "sdkSha256", "cliVersion", "appId"}) {
            ObjectNode mismatch = base.deepCopy();
            mismatch.put(key, key.equals("cliVersion") ? "5.31.2026100911-alpha" : key.equals("appId") ? "__UNI__OTHER" : "b".repeat(64));
            assertFalse(VaporCompatibilityBiz.compatible(mismatch, build), key);
        }
        assertFalse(VaporCompatibilityBiz.compatible(build, build));
        base.set("modules", VaporCompatibilityBiz.JSON.createArrayNode().add("uni-install-wgt"));
        assertFalse(VaporCompatibilityBiz.compatible(base, build));
    }

    @Test void buildReportCannotOverridePackageMetadata() throws Exception {
        var metadata = new AppReleasePackageBiz().readWgtMetadata(archive(vaporEntries(), true).toFile());
        var report = evidence();
        VaporCompatibilityBiz.validateBuildReport(report, metadata);
        report.put("versionCode", "41");
        var wrongVersion = report;
        assertThrows(BuzzException.class, () -> VaporCompatibilityBiz.validateBuildReport(wrongVersion, metadata));
        report = evidence();
        report.remove("nativeRequirementsHash");
        var missing = report;
        assertThrows(BuzzException.class, () -> VaporCompatibilityBiz.validateBuildReport(missing, metadata));
    }

    @Test void nativeReportMustMatchPublishedApkAndCannotBeReplaced() throws Exception {
        var service = new VaporCompatibilityBiz();
        service.apkBiz = org.mockito.Mockito.mock(com.faber.api.app.app.biz.ApkBiz.class);
        service.apkVersionBiz = org.mockito.Mockito.mock(com.faber.api.app.app.biz.ApkVersionBiz.class);
        service.fileSaveBiz = org.mockito.Mockito.mock(com.faber.api.base.admin.biz.FileSaveBiz.class);
        var app = new com.faber.api.app.app.entity.Apk(); app.setId(1); app.setDcloudAppId("__UNI__17534C7");
        var version = new com.faber.api.app.app.entity.ApkVersion();
        version.setVersionCode(39L); version.setVersionName("0.0.39"); version.setFileId("apk"); version.setNativeBaseReportFileId("base");
        org.mockito.Mockito.when(service.apkBiz.getById(1)).thenReturn(app);
        org.mockito.Mockito.when(service.apkVersionBiz.listPublishedByAppId(1)).thenReturn(List.of(version));
        org.mockito.Mockito.when(service.apkVersionBiz.listNativeBaseCandidates(1)).thenReturn(List.of(version));
        Path apk = Files.writeString(directory.resolve("base.apk"), "original signed APK fixture");
        var apkFile = new com.faber.api.base.admin.entity.FileSave(); apkFile.setId("apk");
        var reportFile = new com.faber.api.base.admin.entity.FileSave(); reportFile.setId("base");
        Path reportPath = directory.resolve("native-base.json");
        org.mockito.Mockito.when(service.fileSaveBiz.getById("apk")).thenReturn(apkFile);
        org.mockito.Mockito.when(service.fileSaveBiz.getFileObj(apkFile)).thenReturn(apk.toFile());
        org.mockito.Mockito.when(service.fileSaveBiz.getById("base")).thenReturn(reportFile);
        org.mockito.Mockito.when(service.fileSaveBiz.getFileObj(reportFile)).thenReturn(reportPath.toFile());
        var report = evidence(); report.put("versionCode", "39").put("versionName", "0.0.39");
        report.put("sha256", cn.hutool.crypto.digest.DigestUtil.sha256Hex(apk.toFile()));
        report.withArray("modules").add("uni-install-wgt");
        byte[] original = VaporCompatibilityBiz.JSON.writeValueAsBytes(report);
        Files.write(reportPath, original);
        assertEquals("base", service.register(1, multipart(original)).nativeBaseReportFileId());
        var wrongApk = report.deepCopy().put("sha256", "b".repeat(64));
        assertThrows(BuzzException.class, () -> service.register(1, multipart(VaporCompatibilityBiz.JSON.writeValueAsBytes(wrongApk))));
        var changed = report.deepCopy().put("nativeRequirementsHash", "b".repeat(64));
        assertThrows(BuzzException.class, () -> service.register(1, multipart(VaporCompatibilityBiz.JSON.writeValueAsBytes(changed))));
        var unknownApk = report.deepCopy().put("versionCode", "99");
        assertThrows(BuzzException.class, () -> service.register(1, multipart(VaporCompatibilityBiz.JSON.writeValueAsBytes(unknownApk))));
        var noInstaller = report.deepCopy(); noInstaller.set("modules", VaporCompatibilityBiz.JSON.createArrayNode().add("uni-network"));
        assertThrows(BuzzException.class, () -> service.register(1, multipart(VaporCompatibilityBiz.JSON.writeValueAsBytes(noInstaller))));
        var release = new com.faber.api.app.release.entity.AppRelease();
        release.setAppId(1); release.setRuntimeType("UNI_APP_X_VAPOR"); release.setPlatform("ANDROID");
        release.setNativeBaseReportFileId("base");
        release.setWgtBuildReportJson(VaporCompatibilityBiz.JSON.writeValueAsString(evidence()));
        assertTrue(service.compatibleWithApk(release, 39));
        assertFalse(service.compatibleWithApk(release, 99));
        release.setWgtBuildReportJson(VaporCompatibilityBiz.JSON.writeValueAsString(evidence().put("nativeRequirementsHash", "b".repeat(64))));
        assertFalse(service.compatibleWithApk(release, 39));
    }

    @Test void wgtBuildReportCanBeUploadedWithoutNativeBaseButInvalidReportsAreRejected() throws Exception {
        var service = new VaporCompatibilityBiz();
        var release = new com.faber.api.app.release.entity.AppRelease();
        release.setRuntimeType("UNI_APP_X_VAPOR");
        release.setPlatform("ANDROID");
        var file = archive(vaporEntries(), true);
        var metadata = new AppReleasePackageBiz().readWgtMetadata(file.toFile());
        service.prepare(release, metadata, null, multipart(Files.readAllBytes(file)));
        service.validateRelease(release);
        assertTrue(service.compatibleWithApk(release, 39));
        assertNull(release.getMinSupportedVersionCode());
        assertNull(release.getWgtBuildReportJson());
        release.setNativeBaseReportFileId("base");
        assertThrows(BuzzException.class, () -> service.validateRelease(release));
        assertThrows(BuzzException.class, () -> service.prepare(release, metadata, null, multipart(Files.readAllBytes(file))));
        release.setNativeBaseReportFileId(null);
        release.setWgtBuildReportJson("{}");
        assertThrows(BuzzException.class, () -> service.validateRelease(release));
        release.setWgtBuildReportJson(VaporCompatibilityBiz.JSON.writeValueAsString(evidence()));
        service.validateRelease(release);
        assertTrue(service.compatibleWithApk(release, 39));
        assertTrue(service.compatibleWithApk(release, 99));
        byte[] bytes = Files.readAllBytes(file);
        var report = evidence().put("sha256", cn.hutool.crypto.digest.DigestUtil.sha256Hex(bytes)).put("size", bytes.length);
        service.prepare(release, metadata, multipart(VaporCompatibilityBiz.JSON.writeValueAsBytes(report)), multipart(bytes));
        assertNotNull(release.getWgtBuildReportJson());
        report.put("sha256", "b".repeat(64));
        assertThrows(BuzzException.class, () -> service.prepare(release, metadata,
                multipart(VaporCompatibilityBiz.JSON.writeValueAsBytes(report)), multipart(bytes)));
    }

    @Test void draftMinimumApkAutomaticallyBindsRegisteredReportAndCanBeCleared() {
        var service = new AppReleaseBiz();
        service.apkVersionBiz = org.mockito.Mockito.mock(com.faber.api.app.app.biz.ApkVersionBiz.class);
        var apk = new com.faber.api.app.app.entity.ApkVersion();
        apk.setVersionCode(39L); apk.setNativeBaseReportFileId("registered-base");
        org.mockito.Mockito.when(service.apkVersionBiz.listPublishedByAppId(1)).thenReturn(List.of(apk));
        var release = new com.faber.api.app.release.entity.AppRelease();
        release.setAppId(1); release.setRuntimeType("UNI_APP_X_VAPOR"); release.setWgtBuildReportJson("report");
        release.setMinSupportedVersionCode(39L); release.setNativeBaseReportFileId("untrusted-id");
        service.configureWgtDraftCompatibility(release);
        assertEquals("registered-base", release.getNativeBaseReportFileId());
        release.setMinSupportedVersionCode(null);
        service.configureWgtDraftCompatibility(release);
        assertNull(release.getNativeBaseReportFileId());
        release.setMinSupportedVersionCode(99L);
        assertThrows(BuzzException.class, () -> service.configureWgtDraftCompatibility(release));
        release.setMinSupportedVersionCode(39L); release.setWgtBuildReportJson(null);
        service.configureWgtDraftCompatibility(release);
        assertNull(release.getNativeBaseReportFileId());
    }

    private org.springframework.mock.web.MockMultipartFile multipart(byte[] bytes) {
        return new org.springframework.mock.web.MockMultipartFile("file", "native-base.json", "application/json", bytes);
    }

    private ObjectNode evidence() throws Exception {
        var report = (ObjectNode) VaporCompatibilityBiz.JSON.readTree(VAPOR_MANIFEST);
        report.remove(List.of("id", "version", "uni-app-x", "app-android"));
        report.put("appId", "__UNI__17534C7").put("versionName", "0.0.40").put("versionCode", "40")
                .put("runtimeType", "UNI_APP_X_VAPOR").put("platform", "ANDROID")
                .put("cliVersion", "5.31.2026093020-alpha").put("compilerVersion", "5.31").put("minRuntimeVersion", "5.31")
                .put("sha256", "a".repeat(64)).put("sdkSha256", "a".repeat(64)).put("nativeRequirementsHash", "a".repeat(64)).put("size", 123);
        report.putArray("modules").add("uni-network");
        return report;
    }

    private Map<String, String> vaporEntries() {
        var entries = new LinkedHashMap<String, String>();
        entries.put("www/manifest.json", VAPOR_MANIFEST);
        entries.put("www/app-service.js", "startup");
        entries.put("www/app-config.js", "config");
        entries.put("www/bytes/app.bytes", "bytecode");
        return entries;
    }

    private Path archive(Map<String, String> entries, boolean vapor) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                byte[] content = entry.getValue().getBytes(StandardCharsets.UTF_8);
                var item = new ZipEntry(entry.getKey());
                if (vapor) {
                    CRC32 crc = new CRC32(); crc.update(content);
                    item.setMethod(ZipEntry.STORED); item.setSize(content.length); item.setCrc(crc.getValue());
                }
                zip.putNextEntry(item);
                zip.write(content);
                zip.closeEntry();
            }
        }
        return Files.write(directory.resolve(java.util.UUID.randomUUID() + ".wgt"), vapor ? Zstd.compress(bytes.toByteArray()) : bytes.toByteArray());
    }
}
