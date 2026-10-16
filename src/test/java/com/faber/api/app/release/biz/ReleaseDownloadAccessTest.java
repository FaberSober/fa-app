package com.faber.api.app.release.biz;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.faber.api.app.app.biz.ApkBiz;
import com.faber.api.app.app.biz.ApkVersionBiz;
import com.faber.api.app.app.entity.Apk;
import com.faber.api.app.app.entity.ApkVersion;
import com.faber.api.app.release.AppReleaseConstants;
import com.faber.api.app.release.entity.AppRelease;
import com.faber.api.app.release.entity.AppReleasePackage;
import com.faber.api.app.release.mapper.AppReleaseMapper;
import com.faber.api.app.release.vo.req.AppReleaseCheckReq;
import com.faber.api.base.admin.biz.FileSaveBiz;
import com.faber.api.base.admin.entity.FileSave;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ReleaseDownloadAccessTest {

    private AppReleaseBiz biz;
    private AppReleaseMapper mapper;
    private static final String DOWNLOAD_URL = "https://files.example.com/package?e=600&token=test-signature";

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), AppRelease.class);
        mapper = mock(AppReleaseMapper.class);
        biz = spy(new AppReleaseBiz());
        biz.apkBiz = mock(ApkBiz.class);
        biz.apkVersionBiz = mock(ApkVersionBiz.class);
        biz.appReleasePackageBiz = mock(AppReleasePackageBiz.class);
        biz.fileSaveBiz = mock(FileSaveBiz.class);
        Apk app = new Apk();
        app.setId(1);
        when(biz.apkBiz.getByShortCode("test")).thenReturn(app);
        doReturn(new LambdaQueryChainWrapper<>(mapper)).when(biz).lambdaQuery();
        FileSave file = new FileSave();
        file.setId("file-1");
        file.setSize(100L);
        when(biz.fileSaveBiz.getByIdWithCache("file-1")).thenReturn(file);
        when(biz.fileSaveBiz.getFileUrl("file-1")).thenReturn(DOWNLOAD_URL);
    }

    @Test
    void apkCheckUsesAuthorizedUrlOnlyForNewPublishedVersion() throws Exception {
        ApkVersion latest = new ApkVersion();
        latest.setId(7);
        latest.setVersionCode(101L);
        latest.setFileId("file-1");
        latest.setSha256("a".repeat(64));
        when(biz.apkVersionBiz.getLatestPublishedVersion(1)).thenReturn(latest);
        var result = biz.checkApk(request("ANDROID", null));
        assertTrue(result.getHasUpdate());
        assertEquals(DOWNLOAD_URL, result.getDownloadUrl());
        verify(biz.fileSaveBiz).getFileUrl("file-1");

        clearInvocations(biz.fileSaveBiz);
        latest.setVersionCode(100L);
        assertFalse(biz.checkApk(request("ANDROID", null)).getHasUpdate());
        verify(biz.fileSaveBiz, never()).getFileUrl(anyString());
    }

    @Test
    void wgtCheckUsesAuthorizedUrlAfterDeviceAndPackageSelection() {
        AppRelease release = release();
        release.setTargetDeviceIds("allowed-device");
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(release));
        AppReleasePackage file = new AppReleasePackage();
        file.setPackageType(AppReleaseConstants.PACKAGE_WGT);
        file.setPlatform("APP_PLUS");
        file.setFileId("file-1");
        when(biz.appReleasePackageBiz.listByReleaseId(7L)).thenReturn(List.of(file));
        var result = biz.checkWgt(request("APP_PLUS", "allowed-device"));
        assertTrue(result.getHasUpdate());
        assertEquals(DOWNLOAD_URL, result.getDownloadUrl());

        clearInvocations(biz.fileSaveBiz);
        assertFalse(biz.checkWgt(request("APP_PLUS", "other-device")).getHasUpdate());
        verify(biz.fileSaveBiz, never()).getFileUrl(anyString());
    }

    private AppRelease release() {
        AppRelease release = new AppRelease();
        release.setId(7L);
        release.setAppId(1);
        release.setVersionCode(101L);
        release.setRuntimeType("APP_PLUS");
        release.setPlatform("APP_PLUS");
        release.setChannel("stable");
        release.setStatus(AppReleaseConstants.STATUS_PUBLISHED);
        return release;
    }

    private AppReleaseCheckReq request(String platform, String deviceId) {
        AppReleaseCheckReq request = new AppReleaseCheckReq();
        request.setAppCode("test");
        request.setPlatform(platform);
        request.setDeviceId(deviceId);
        request.setCurrentVersionCode(100L);
        return request;
    }
}
