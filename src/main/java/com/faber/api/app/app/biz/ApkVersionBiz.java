package com.faber.api.app.app.biz;

import com.faber.core.vo.msg.Ret;
import org.springframework.stereotype.Service;

import com.faber.api.app.app.entity.ApkVersion;
import com.faber.api.app.app.mapper.ApkVersionMapper;
import com.faber.core.web.biz.BaseBiz;

import java.util.List;

/**
 * APP-APK版本表
 *
 * @author Farando
 * @email faberxu@gmail.com
 * @date 2023-01-18 20:31:39
 */
@Service
public class ApkVersionBiz extends BaseBiz<ApkVersionMapper,ApkVersion> {

    public List<ApkVersion> listByAppId(Integer appId) {
        return lambdaQuery()
                .eq(ApkVersion::getAppId, appId)
                .orderByDesc(ApkVersion::getId)
                .list();
    }

}