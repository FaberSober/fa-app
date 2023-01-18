package com.faber.api.app.app.biz;

import com.faber.api.app.app.entity.Apk;
import com.faber.api.app.app.mapper.ApkMapper;
import com.faber.api.app.app.vo.ApkInfo;
import com.faber.api.base.admin.biz.FileSaveBiz;
import com.faber.api.base.admin.entity.FileSave;
import com.faber.core.web.biz.BaseBiz;
import jodd.io.FileUtil;
import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.IconFace;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * APP-APK表
 *
 * @author Farando
 * @email faberxu@gmail.com
 * @date 2023-01-18 20:31:39
 */
@Service
public class ApkBiz extends BaseBiz<ApkMapper,Apk> {

    @Resource
    FileSaveBiz fileSaveBiz;

    public Apk getApkInfo(String fileId) throws IOException {
        File file = fileSaveBiz.getFileObj(fileId);
        ApkFile apkFile = new ApkFile(file);
        ApkMeta apkMeta = apkFile.getApkMeta();

        Apk apkInfo = new Apk();
        apkInfo.setName(apkMeta.getName());
        apkInfo.setApplicationId(apkMeta.getPackageName());
        apkInfo.setVersionCode(apkMeta.getVersionCode());
        apkInfo.setVersionName(apkMeta.getVersionName());

        List<IconFace> icons = apkFile.getAllIcons();
        if (icons != null && !icons.isEmpty()) {
            IconFace icon = icons.get(0);
            File iconFile = FileUtil.createTempFile("icon", ".png", FileUtil.createTempDirectory());
            FileUtil.writeBytes(iconFile, icon.getData());
            FileSave fileSave = fileSaveBiz.upload(iconFile);
            apkInfo.setIconId(fileSave.getId());
        }

        return apkInfo;
    }

}