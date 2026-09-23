package com.faber.api.app.release.vo.ret;

import com.faber.api.app.release.AppReleaseConstants;
import lombok.Data;

/** 客户端公开版本检查响应。 */
@Data
public class AppReleaseCheckRet {

    private Boolean hasUpdate;
    private String updateType;
    private Long releaseId;
    private Long versionCode;
    private String versionName;
    private Boolean forceUpdate;
    private Long minSupportedVersionCode;
    private String fileId;
    private String downloadUrl;
    private Long size;
    private String sha256;
    private String releaseNote;

    public static AppReleaseCheckRet noUpdate() {
        AppReleaseCheckRet result = new AppReleaseCheckRet();
        result.setHasUpdate(false);
        result.setUpdateType(AppReleaseConstants.UPDATE_NONE);
        return result;
    }
}
