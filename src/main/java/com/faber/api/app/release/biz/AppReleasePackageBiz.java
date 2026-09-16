package com.faber.api.app.release.biz;

import com.faber.api.app.release.entity.AppReleasePackage;
import com.faber.api.app.release.mapper.AppReleasePackageMapper;
import com.faber.core.web.biz.BaseBiz;
import org.springframework.stereotype.Service;

/** 应用版本发布包业务。 */
@Service
public class AppReleasePackageBiz extends BaseBiz<AppReleasePackageMapper, AppReleasePackage> {
}
