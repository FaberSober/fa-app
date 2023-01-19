package com.faber.api.app.app.biz;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.faber.api.app.app.entity.Apk;
import com.faber.api.app.app.entity.ApkVersion;
import com.faber.api.app.app.mapper.ApkMapper;
import com.faber.api.app.app.vo.ApkInfo;
import com.faber.api.base.admin.biz.FileSaveBiz;
import com.faber.api.base.admin.entity.FileSave;
import com.faber.core.config.validator.validator.Vg;
import com.faber.core.exception.BuzzException;
import com.faber.core.vo.msg.Ret;
import com.faber.core.web.biz.BaseBiz;
import jodd.io.FileUtil;
import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.IconFace;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;

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

    @Resource
    ApkVersionBiz apkVersionBiz;

    public Apk getApkInfo(String fileId) throws IOException {
        File file = fileSaveBiz.getFileObj(fileId);
        ApkFile apkFile = new ApkFile(file);
        ApkMeta apkMeta = apkFile.getApkMeta();

        Apk apkInfo = new Apk();
        apkInfo.setFileId(fileId);
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

        Apk apk = this.getApkByApplicationId(apkInfo.getApplicationId());
        if (apk != null) {
            apkInfo.setShortCode(apk.getShortCode());
        } else {
            apkInfo.setShortCode(RandomUtil.randomString(4));
        }

        return apkInfo;
    }

    public Apk getApkByApplicationId(String applicationId) {
        long count = lambdaQuery().eq(Apk::getApplicationId, applicationId).count();
        if (count > 2) throw new BuzzException("有多个相同包名的应用，请联系管理员");
        if (count == 1) {
            return lambdaQuery().eq(Apk::getApplicationId, applicationId).one();
        }
        return null;
    }

    public Apk create(Apk entity) {
        // step 1: update apk info
        Apk apk = this.getApkByApplicationId(entity.getApplicationId());
        if (apk == null) {
            // check shortCode unique
            long count = lambdaQuery().eq(Apk::getShortCode, entity.getShortCode()).count();
            if (count > 0) throw new BuzzException("有多个相同短链的应用，请更换短链");

            // create apk
            super.save(entity);
            apk = entity;
        } else {
            // update
            // check shortCode unique
            long count = lambdaQuery()
                    .eq(Apk::getShortCode, entity.getShortCode())
                    .ne(Apk::getId, apk.getId())
                    .count();
            if (count > 1) throw new BuzzException("该短链已存在，请更换短链");

            apk.setName(entity.getName());
            apk.setVersionCode(entity.getVersionCode());
            apk.setVersionName(entity.getVersionName());
            apk.setFileId(entity.getFileId());
            apk.setIconId(entity.getIconId());
            super.updateById(apk);
        }

        // step 2: add apk version info
        ApkVersion apkVersion = new ApkVersion();
        BeanUtil.copyProperties(entity, apkVersion);
        apkVersion.setAppId(apk.getId());
        apkVersionBiz.save(apkVersion);

        return apk;
    }

    @Override
    public boolean updateById(Apk entity) {
        long count = lambdaQuery()
                .eq(Apk::getShortCode, entity.getShortCode())
                .ne(Apk::getId, entity.getId())
                .count();
        if (count > 1) throw new BuzzException("该短链已存在，请更换短链");
        return super.updateById(entity);
    }
}
