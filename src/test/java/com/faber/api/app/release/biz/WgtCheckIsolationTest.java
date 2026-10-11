package com.faber.api.app.release.biz;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.faber.api.app.app.biz.ApkBiz;
import com.faber.api.app.app.entity.Apk;
import com.faber.api.app.release.entity.AppRelease;
import com.faber.api.app.release.mapper.AppReleaseMapper;
import com.faber.api.app.release.vo.req.AppReleaseCheckReq;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WgtCheckIsolationTest {
    AppReleaseBiz biz;
    AppReleaseMapper mapper;

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), AppRelease.class);
        mapper = mock(AppReleaseMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        biz = spy(new AppReleaseBiz());
        biz.apkBiz = mock(ApkBiz.class);
        var app = new Apk(); app.setId(1);
        when(biz.apkBiz.getByShortCode("test")).thenReturn(app);
        doReturn(new LambdaQueryChainWrapper<>(mapper)).when(biz).lambdaQuery();
    }

    @Test void legacyRequestsKeepDefaultRuntimeAndApkFallback() {
        var req = request("APP_PLUS", null);
        assertFalse(biz.checkWgt(req).getHasUpdate());
        var query = captured();
        assertTrue(query.getSqlSegment().contains("runtime_type"));
        assertTrue(query.getSqlSegment().contains("platform"));
        assertTrue(query.getParamNameValuePairs().containsValue("APP_PLUS"));
        assertTrue(query.getParamNameValuePairs().containsValue(39L));
    }

    @Test void vaporUsesExplicitResourceCodeAndAndroidRuntimeFilters() {
        var req = request("ANDROID", 42L); req.setRuntimeType("UNI_APP_X_VAPOR");
        assertFalse(biz.checkWgt(req).getHasUpdate());
        var query = captured();
        assertTrue(query.getSqlSegment().contains("runtime_type"));
        assertTrue(query.getSqlSegment().contains("platform"));
        assertTrue(query.getParamNameValuePairs().containsValue("UNI_APP_X_VAPOR"));
        assertTrue(query.getParamNameValuePairs().containsValue("ANDROID"));
        assertTrue(query.getParamNameValuePairs().containsValue(42L));
        assertFalse(query.getParamNameValuePairs().containsValue(39L));
    }

    @Test void rejectsMissingVaporResourceCodeAndUnsupportedRuntimePlatform() {
        var req = request("ANDROID", null); req.setRuntimeType("UNI_APP_X_VAPOR");
        assertFalse(biz.checkWgt(req).getHasUpdate());
        req.setCurrentWgtVersionCode(42L); req.setPlatform("APP_PLUS");
        assertFalse(biz.checkWgt(req).getHasUpdate());
        req.setRuntimeType("APP_PLUS"); req.setPlatform("ANDROID");
        assertFalse(biz.checkWgt(req).getHasUpdate());
        verify(mapper, never()).selectList(any(Wrapper.class));
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AppRelease> captured() {
        var captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(captor.capture());
        return (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AppRelease>) captor.getValue();
    }

    private AppReleaseCheckReq request(String platform, Long resourceCode) {
        var req = new AppReleaseCheckReq(); req.setAppCode("test"); req.setPlatform(platform);
        req.setCurrentVersionCode(39L); req.setCurrentWgtVersionCode(resourceCode);
        return req;
    }
}
