package com.faber.api.app.release.rest;

import com.faber.api.app.release.biz.AppReleaseBiz;
import com.faber.api.app.release.vo.req.AppReleaseCheckReq;
import com.faber.api.app.release.vo.ret.AppReleaseCheckRet;
import com.faber.core.annotation.LogNoRet;
import com.faber.core.config.annotation.IgnoreUserToken;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReleaseResponsePolicyTest {

    @Test
    void publicCheckEndpointsDoNotCacheOrLogDownloadCredentials() throws Exception {
        AppReleaseController controller = new AppReleaseController();
        AppReleaseBiz biz = mock(AppReleaseBiz.class);
        ReflectionTestUtils.setField(controller, "baseBiz", biz);
        AppReleaseCheckReq request = new AppReleaseCheckReq();
        when(biz.checkApk(request)).thenReturn(AppReleaseCheckRet.noUpdate());
        when(biz.checkWgt(request)).thenReturn(AppReleaseCheckRet.noUpdate());
        for (String name : new String[]{"check", "checkApk", "checkWgt"}) {
            var method = AppReleaseController.class.getMethod(name, AppReleaseCheckReq.class, HttpServletResponse.class);
            MockHttpServletResponse response = new MockHttpServletResponse();
            method.invoke(controller, request, response);
            assertEquals("private, no-store", response.getHeader("Cache-Control"));
            assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
            assertNotNull(method.getAnnotation(LogNoRet.class));
            assertNotNull(method.getAnnotation(IgnoreUserToken.class));
        }
    }
}
