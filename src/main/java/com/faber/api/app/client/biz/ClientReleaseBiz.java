package com.faber.api.app.client.biz;

import com.faber.api.app.client.ClientReleaseConstants;
import com.faber.api.app.client.entity.ClientApp;
import com.faber.api.app.client.entity.ClientRelease;
import com.faber.api.app.client.entity.ClientReleaseArtifact;
import com.faber.api.app.client.mapper.ClientReleaseMapper;
import com.faber.api.app.client.vo.req.ClientUpdateCheckReq;
import com.faber.api.app.client.vo.ret.ClientUpdateManifest;
import com.faber.api.base.admin.biz.FileSaveBiz;
import com.faber.api.base.admin.entity.FileSave;
import com.faber.core.constant.FaSetting;
import com.faber.core.exception.BuzzException;
import com.faber.core.web.biz.BaseBiz;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Desktop 客户端版本业务。 */
@Service
public class ClientReleaseBiz extends BaseBiz<ClientReleaseMapper, ClientRelease> {

    @Resource
    private ClientAppBiz clientAppBiz;

    @Lazy
    @Resource
    private ClientReleaseArtifactBiz clientReleaseArtifactBiz;

    @Resource
    private FileSaveBiz fileSaveBiz;

    @Resource
    private FaSetting faSetting;

    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^[vV]?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");

    @Override
    public boolean save(ClientRelease entity) {
        validateDraft(entity, null);
        entity.setStatus(ClientReleaseConstants.STATUS_DRAFT);
        entity.setPublishTime(null);
        return super.save(entity);
    }

    @Override
    public boolean updateById(ClientRelease entity) {
        ClientRelease current = require(entity.getId());
        if (!ClientReleaseConstants.STATUS_DRAFT.equals(current.getStatus())) {
            throw new BuzzException("已发布或已撤回的版本不允许直接修改");
        }
        if (entity.getStatus() != null && !ClientReleaseConstants.STATUS_DRAFT.equals(entity.getStatus())) {
            throw new BuzzException("发布状态请使用发布或撤回接口修改");
        }

        validateDraft(entity, entity.getId());
        entity.setStatus(ClientReleaseConstants.STATUS_DRAFT);
        entity.setPublishTime(null);
        return super.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public ClientRelease publish(Long id) {
        ClientRelease release = require(id);
        if (!ClientReleaseConstants.STATUS_DRAFT.equals(release.getStatus())) {
            throw new BuzzException("只有草稿版本可以发布");
        }

        ClientApp client = clientAppBiz.getById(release.getClientId());
        if (client == null) throw new BuzzException("客户端ID异常，请检查");
        if (!Boolean.TRUE.equals(client.getEnabled())) throw new BuzzException("客户端已停用，不能发布新版本");

        validateDraft(release, release.getId());
        List<ClientReleaseArtifact> artifacts = clientReleaseArtifactBiz.listByReleaseId(id);
        if (artifacts.isEmpty()) throw new BuzzException("发布版本至少需要一个安装包");
        for (ClientReleaseArtifact artifact : artifacts) {
            clientReleaseArtifactBiz.validateForPublish(artifact);
        }

        release.setStatus(ClientReleaseConstants.STATUS_PUBLISHED);
        release.setPublishTime(new Date());
        if (!super.updateById(release)) throw new BuzzException("发布版本失败，请重试");
        return release;
    }

    @Transactional(rollbackFor = Exception.class)
    public ClientRelease revoke(Long id) {
        ClientRelease release = require(id);
        if (!ClientReleaseConstants.STATUS_PUBLISHED.equals(release.getStatus())) {
            throw new BuzzException("只有已发布版本可以撤回");
        }

        release.setStatus(ClientReleaseConstants.STATUS_REVOKED);
        if (!super.updateById(release)) throw new BuzzException("撤回版本失败，请重试");
        return release;
    }

    public ClientRelease getCurrent(Long clientId) {
        if (clientId == null) return null;
        return getTop(lambdaQuery()
                .eq(ClientRelease::getClientId, clientId)
                .eq(ClientRelease::getChannel, ClientReleaseConstants.CHANNEL_STABLE)
                .eq(ClientRelease::getStatus, ClientReleaseConstants.STATUS_PUBLISHED)
                .orderByDesc(ClientRelease::getVersionCode)
                .orderByDesc(ClientRelease::getId));
    }

    public ClientUpdateManifest checkUpdate(String clientCode, ClientUpdateCheckReq request) {
        if (clientCode == null || clientCode.isBlank()) throw new BuzzException("客户端标识不能为空");
        if (request == null) throw new BuzzException("版本检查请求不能为空");
        if (request.getCurrentVersion() == null || request.getCurrentVersion().isBlank()) {
            throw new BuzzException("当前版本不能为空");
        }
        if (request.getTarget() == null || request.getTarget().isBlank()) {
            throw new BuzzException("更新目标不能为空");
        }

        String currentVersion = request.getCurrentVersion().trim();
        validateVersion(currentVersion, "当前版本格式异常");
        String[] target = resolveTarget(request.getTarget());
        String channel = request.getChannel() == null || request.getChannel().isBlank()
                ? ClientReleaseConstants.CHANNEL_STABLE
                : request.getChannel().trim();
        if (!ClientReleaseConstants.CHANNEL_STABLE.equals(channel)) {
            throw new BuzzException("暂不支持该更新渠道");
        }

        ClientApp client = clientAppBiz.lambdaQuery()
                .eq(ClientApp::getClientCode, clientCode.trim())
                .one();
        if (client == null || !Boolean.TRUE.equals(client.getEnabled())) return null;

        List<ClientRelease> releases = lambdaQuery()
                .eq(ClientRelease::getClientId, client.getId())
                .eq(ClientRelease::getChannel, channel)
                .eq(ClientRelease::getStatus, ClientReleaseConstants.STATUS_PUBLISHED)
                .orderByDesc(ClientRelease::getVersionCode)
                .orderByDesc(ClientRelease::getId)
                .list();

        for (ClientRelease release : releases) {
            if (compareVersions(release.getVersionName(), currentVersion) <= 0) continue;

            ClientReleaseArtifact artifact = clientReleaseArtifactBiz.lambdaQuery()
                    .eq(ClientReleaseArtifact::getReleaseId, release.getId())
                    .eq(ClientReleaseArtifact::getPlatform, target[0])
                    .eq(ClientReleaseArtifact::getArch, target[1])
                    .one();
            if (artifact == null) continue;

            FileSave fileSave = fileSaveBiz.getByIdWithCache(artifact.getFileId());
            if (fileSave == null) continue;

            ClientUpdateManifest result = new ClientUpdateManifest();
            result.setVersion(release.getVersionName());
            result.setNotes(release.getReleaseNotes() == null ? "" : release.getReleaseNotes());
            result.setPubDate(release.getPublishTime());
            result.setUrl(buildDownloadUrl(release.getId(), target[0], target[1]));
            result.setSignature(artifact.getSignature());
            return result;
        }
        return null;
    }

    @Override
    public void removeBatchByIds(List<Serializable> ids) {
        if (ids == null) return;
        ids.forEach(this::removeById);
    }

    @Override
    public boolean removeById(Serializable id) {
        checkDraft(require((Long) id));
        return super.removeById(id);
    }

    @Override
    public void removePerById(Serializable id) {
        checkDraft(require((Long) id));
        super.removePerById(id);
    }

    @Override
    public void removePerBatchByIds(List<Serializable> ids) {
        if (ids == null) return;
        ids.forEach(this::removePerById);
    }

    private ClientRelease require(Long id) {
        if (id == null) throw new BuzzException("版本ID不能为空");
        ClientRelease release = getById(id);
        if (release == null) throw new BuzzException("版本ID异常，请检查");
        return release;
    }

    private void checkDraft(ClientRelease release) {
        if (!ClientReleaseConstants.STATUS_DRAFT.equals(release.getStatus())) {
            throw new BuzzException("已发布或已撤回的版本不允许删除");
        }
    }

    private void validateDraft(ClientRelease entity, Long excludeId) {
        if (entity.getClientId() == null) throw new BuzzException("客户端ID不能为空");
        if (clientAppBiz.getById(entity.getClientId()) == null) throw new BuzzException("客户端ID异常，请检查");
        if (entity.getVersionName() == null || entity.getVersionName().isBlank()) {
            throw new BuzzException("版本名称不能为空");
        }
        if (entity.getVersionCode() == null || entity.getVersionCode() < 1) {
            throw new BuzzException("版本编码必须是正整数");
        }
        if (entity.getChannel() == null || entity.getChannel().isBlank()) {
            throw new BuzzException("发布渠道不能为空");
        }

        long count = lambdaQuery()
                .eq(ClientRelease::getClientId, entity.getClientId())
                .eq(ClientRelease::getVersionCode, entity.getVersionCode())
                .eq(ClientRelease::getChannel, entity.getChannel())
                .ne(excludeId != null, ClientRelease::getId, excludeId)
                .count();
        if (count > 0) throw new BuzzException("同一客户端、版本编码和渠道的发布记录已存在");
    }

    private String[] resolveTarget(String target) {
        String value = target.trim();
        int separator = value.lastIndexOf('-');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new BuzzException("更新目标格式异常");
        }

        String platform = value.substring(0, separator);
        String arch = value.substring(separator + 1);
        if (!ClientReleaseConstants.PLATFORMS.contains(platform)) {
            throw new BuzzException("不支持的操作系统平台");
        }
        if (!ClientReleaseConstants.ARCHES.contains(arch)) {
            throw new BuzzException("不支持的CPU架构");
        }
        return new String[]{platform, arch};
    }

    private String buildDownloadUrl(Long releaseId, String platform, String arch) {
        String path = "/api/app/client/download/" + releaseId + "/" + platform + "-" + arch;
        if (faSetting.getUrl() == null || faSetting.getUrl().getServerHost() == null
                || faSetting.getUrl().getServerHost().isBlank()) {
            return path;
        }
        return faSetting.getUrl().getServerHost().replaceAll("/+$", "") + path;
    }

    private void validateVersion(String version, String message) {
        if (parseVersion(version) == null) throw new BuzzException(message);
    }

    private int compareVersions(String left, String right) {
        VersionParts leftParts = parseVersion(left);
        VersionParts rightParts = parseVersion(right);
        if (leftParts == null || rightParts == null) return Integer.MIN_VALUE;

        int length = Math.max(leftParts.numbers().length, rightParts.numbers().length);
        for (int i = 0; i < length; i++) {
            long leftNumber = i < leftParts.numbers().length ? leftParts.numbers()[i] : 0;
            long rightNumber = i < rightParts.numbers().length ? rightParts.numbers()[i] : 0;
            if (leftNumber != rightNumber) return Long.compare(leftNumber, rightNumber);
        }

        if (leftParts.preRelease() == null && rightParts.preRelease() == null) return 0;
        if (leftParts.preRelease() == null) return 1;
        if (rightParts.preRelease() == null) return -1;
        return comparePreRelease(leftParts.preRelease(), rightParts.preRelease());
    }

    private int comparePreRelease(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            if (i >= leftParts.length) return -1;
            if (i >= rightParts.length) return 1;
            String leftPart = leftParts[i];
            String rightPart = rightParts[i];
            boolean leftNumeric = leftPart.matches("\\d+");
            boolean rightNumeric = rightPart.matches("\\d+");
            if (leftNumeric && rightNumeric) {
                int result = Long.compare(Long.parseLong(leftPart), Long.parseLong(rightPart));
                if (result != 0) return result;
            } else if (leftNumeric != rightNumeric) {
                return leftNumeric ? -1 : 1;
            } else {
                int result = leftPart.compareTo(rightPart);
                if (result != 0) return result;
            }
        }
        return 0;
    }

    private VersionParts parseVersion(String version) {
        if (version == null) return null;
        Matcher matcher = VERSION_PATTERN.matcher(version.trim());
        if (!matcher.matches()) return null;
        String[] numberParts = matcher.group(1).split("\\.");
        long[] numbers = new long[numberParts.length];
        try {
            for (int i = 0; i < numberParts.length; i++) {
                numbers[i] = Long.parseLong(numberParts[i]);
            }
        } catch (NumberFormatException exception) {
            return null;
        }
        return new VersionParts(numbers, matcher.group(2));
    }

    private record VersionParts(long[] numbers, String preRelease) {
    }

}
