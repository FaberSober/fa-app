package com.faber.api.app.release.biz;

import com.faber.api.app.release.entity.AppRelease;
import com.faber.api.app.release.mapper.AppReleaseMapper;
import com.faber.core.web.biz.BaseBiz;
import org.springframework.stereotype.Service;

/** 应用通用版本发布记录业务。 */
@Service
public class AppReleaseBiz extends BaseBiz<AppReleaseMapper, AppRelease> {
}
