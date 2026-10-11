# APP 更新流程说明

本文说明普通 uni-app（APP-PLUS）和 Android uni-app X Vapor 的发布契约。旧客户端流程及架构决策见 [ADR-app-003](adrs/ADR-app-003-app-plus-package-publishing.md)，Vapor进度见 `mobile-x/docs/plans/PLAN-mobile-x-012-wgt-release-and-update-1011.md`。下文原有APK/WGT规则适用于APP-PLUS；Vapor扩展见下一节。

## Android Vapor WGT 扩展

- 后端自动识别普通ZIP与Zstandard封装的Vapor ZIP；Vapor目前只接受Android、编译器/最低Runtime `5.31`、`www/`下stored条目、完整启动资源及字节码。不以扩展名或调用方声明判断运行时。
- `app_release.runtime_type/platform` 默认 `APP_PLUS/APP_PLUS`，历史数据无需重建。唯一键扩展为应用+运行时+平台+资源编码+渠道。普通uni-app上传、下载、检查和撤回接口保持兼容。
- `POST release/registerNativeBaseReport`：multipart `appId`、`file`（APK Jenkins `.verification.json`），校验报告AppID、已上传且未撤回APK版本及原始文件SHA-256；仅包含安装模块的APK可注册，返回 `nativeBaseReportFileId`、`apkVersionCode`。APK原生报告与WGT报告不同，不能互相代替，注册后不可替换。
- 报告包含 `appId`（DCloud ID）、`runtimeType=UNI_APP_X_VAPOR`、`platform=ANDROID`、资源版本、`compilerVersion/minRuntimeVersion=5.31`、完整 `cliVersion`、`sdkSha256`、`nativeRequirementsHash`、`modules` 和原始包 `sha256`；WGT报告另含 `size`。报告只能由可信CI产生，指纹包含源Android配置、UTS原生插件与宿主提交，不能人工声明兼容。
- `POST release/createWgtDraft`：人工上传只需 `appId/file`，更新说明可选，留空自动生成。服务端自动识别运行时、平台、版本并验证AppID，最低APK和报告均非人工上传必填项。Jenkins仍可提交 `buildReport/nativeBaseReportFileId/minSupportedVersionCode`，一旦提交报告就必须完整且匹配已发布APK，保留原有原生指纹/SDK/CLI及模块兼容校验。显式声明运行时/平台与实际包不一致时拒绝。旧自动匹配入口仍仅支持普通uni-app。
- `GET release/forPublish/{id}`、`GET releasePackage/byRelease/{id}`、上述上传/注册及 `POST release/publish/{id}` 支持现有FaApiToken。发布重新读取原始WGT核对版本、平台、运行时、摘要和报告；已撤回的资源也计入修复版本上限，同运行时/平台/渠道的修复版本不得降低。
- `POST release/checkWgt`：旧请求未传 `runtimeType` 时默认APP_PLUS，资源编码缺失时沿用旧APK编码回退。Vapor必须明确传 `runtimeType=UNI_APP_X_VAPOR`、`platform=ANDROID`、`currentVersionCode`（原生APK）、`currentWgtVersionCode`（实际资源）；缺失资源编码或平台不符时不下发；附带CI报告的发布另要求已注册且兼容的APK，人工上传不依赖报告注册。灰度、最低APK、草稿/发布/撤回规则继续生效。更新响应增加 `runtimeType/platform/nativeBaseReportFileId/minRuntimeVersion`。
- 管理页支持运行时选择、报告注册/上传和发布确认。解析所得版本及基座信息不可通过普通编辑改写；原生能力变化需要先发布新APK。客户端的实际安装、摘要验证、重启另由WG01-02b/WG03验收，本后端变更不赋予旧APK安装能力。

升级脚本：`sql/fa-app/mysql/1.0.13_app_ddl.sql` 和 `sql/fa-app/postgre/1.0.13_app_ddl.sql`。上线前确认迁移成功，先回归旧uni-app，再验证Vapor草稿/发布与隔离。

APK上传仍创建草稿，基座报告可在上传后注册，注册不会发布APK。使用CI报告流程时，请在管理端发布该APK后再创建Vapor WGT草稿；撤回APK后该基座不再参与该流程的WGT下发。人工上传仅能从WGT验证资源清单，无法据此确认实际APK内的原生模块及UTS插件兼容性，新增原生能力仍需重建APK。SDK中安装模块的实际标识是 `uni-install-wgt`（对应API `uni.installWgt`），以配套编译器的模块表和SDK AAR为准。

## 1. 更新类型

APK 和 WGT 是同一应用下的两条独立发布线，版本编码分别维护，不能互相比较。

| 更新类型 | 用途 | 版本来源 | 发布数据 | 客户端版本来源 |
|---|---|---|---|---|
| APK | 原生代码、权限、插件、SDK 或启动配置变化 | APK 清单 | `app_apk_version` | `plus.runtime.versionCode` |
| WGT | 页面、脚本和静态资源更新 | WGT 内 `manifest.json` | `app_release`、`app_release_package` | `plus.runtime.getProperty` |

WGT 可以设置可选的最低兼容 APK 版本：

- 留空：适用于该应用的所有 APK 版本。
- 填写版本 `N`：仅当 `当前 APK versionCode >= N` 时允许更新。
- WGT 不精确绑定某个 APK，也不要求 APK 版本编码小于 WGT 资源版本编码。

## 2. 发布流程

### 2.1 发布 APK

1. 在“APK 安装包”中上传 APK。
2. 后台解析应用包名、版本名称和版本编码，并保存 APK 历史版本。
3. 填写更新说明，按需设置强制更新。
4. APK 首次参与更新检查时计算并保存 SHA-256，后续检查直接复用。

### 2.2 发布 WGT

1. 在应用的“客户端更新”中选择“上传 WGT 并创建草稿”。
2. 上传 `.wgt` 文件，后台从 `manifest.json` 读取资源版本。
3. 填写更新说明；最低兼容 APK 可以留空或从 APK 历史中选择。
4. 确认目标资源版本、最低兼容 APK、渠道和灰度规则后发布。
5. 发布后的 WGT 才会进入客户端检查；草稿和已撤回版本不会下发。

涉及原生能力变化时必须发布 APK。仅修改前端资源时可以发布 WGT。

## 3. 客户端检查顺序

```mermaid
flowchart TD
    A[应用启动或进入前台] --> B[读取当前 APK 版本]
    B --> C[调用 checkApk]
    C -->|没有 APK 更新| H[读取当前 WGT 资源版本]
    C -->|存在 APK 更新| D[提示 APK 更新]
    D -->|安装成功| E[结束本次检查并等待应用重启]
    D -->|跳过非强制更新| H
    D -->|强制更新失败| F[保留当前版本并提示失败]
    H --> I[调用 checkWgt]
    I --> J{存在兼容的更高资源版本}
    J -->|否| K[结束]
    J -->|是| L[下载并校验 SHA-256]
    L -->|校验或安装失败| M[保留当前资源版本并提示失败]
    L -->|安装成功| N[重启应用加载新资源]
```

执行规则：

1. APK 始终优先检查和提示。
2. APK 安装成功后，本次流程不再安装 WGT；重新启动后重新检查。
3. 用户跳过非强制 APK 后，仍继续检查满足当前 APK 条件的 WGT。
4. 强制 APK 不允许取消；下载、校验或安装失败时不继续 WGT 流程。
5. WGT 安装成功后主动重启应用。
6. 前台恢复和首页兜底检查共享进行中状态和冷却时间，避免重复弹窗。

## 4. 服务端版本选择

### 4.1 APK 选择

接口：`POST /api/app/app/release/checkApk`

服务端读取应用最新的 APK 历史版本，仅在目标 APK 版本编码大于客户端原生版本时返回更新。响应类型为 `FULL`。

### 4.2 WGT 选择

接口：`POST /api/app/app/release/checkWgt`

服务端按以下条件筛选 WGT：

1. 应用、渠道匹配。
2. 状态为已发布。
3. 目标 WGT 资源版本高于客户端当前资源版本。
4. 最低兼容 APK 为空，或者客户端 APK 版本达到最低要求。
5. 命中灰度比例或设备白名单。

满足条件的版本按资源版本编码倒序选择。没有兼容 WGT 时返回无更新，不回退 APK；APK 是否更新只由 `checkApk` 决定。

## 5. 检查接口契约

两个接口使用相同的请求结构：

```json
{
  "appCode": "faMobile",
  "platform": "APP_PLUS",
  "currentVersionCode": 5,
  "currentWgtVersionCode": 8,
  "channel": "stable",
  "deviceId": "installation-id"
}
```

字段说明：

| 字段 | 必填 | 说明 |
|---|---|---|
| `appCode` | 是 | 对应应用短码 `app_apk.short_code` |
| `platform` | 是 | APP-PLUS 客户端传 `APP_PLUS` |
| `currentVersionCode` | 是 | 当前原生 APK 版本编码 |
| `currentWgtVersionCode` | WGT 检查需要 | 当前已安装的 WGT 资源版本编码 |
| `channel` | 是 | 发布渠道，默认 `stable` |
| `deviceId` | 灰度时需要 | 稳定的客户端安装标识 |

存在更新时的主要响应字段：

```json
{
  "hasUpdate": true,
  "updateType": "WGT",
  "releaseId": 12,
  "versionCode": 9,
  "versionName": "0.0.9",
  "forceUpdate": false,
  "minSupportedVersionCode": 5,
  "downloadUrl": "/api/base/admin/fileSave/getFile/file-id",
  "size": 438272,
  "sha256": "64位SHA-256摘要",
  "releaseNote": "修复已知问题"
}
```

无更新时返回 `hasUpdate=false`、`updateType=NONE`。APK 响应使用 `updateType=FULL`，WGT 响应使用 `updateType=WGT`。

## 6. 典型场景

| 场景 | 处理结果 |
|---|---|
| 只有 APK 更新 | 提示 APK，安装成功后等待重启 |
| 只有兼容 WGT 更新 | 下载、校验并安装 WGT，然后重启 |
| APK 和 WGT 同时更新 | 先提示 APK；安装 APK 后重启再检查 WGT |
| 用户跳过非强制 APK | 继续检查并允许安装兼容 WGT |
| 当前 APK 低于 WGT 最低要求 | 不下发该 WGT，也不自动回退 APK |
| WGT 已是当前资源版本 | 不重复下发 |
| WGT 已撤回 | 不再下发，并继续查找其他可用版本 |
| SHA-256 校验失败 | 不安装，保留当前版本并提示失败 |

## 7. 兼容与边界

- 旧 `/api/app/app/release/check` 暂时保留，仅按 WGT 检查处理；新客户端使用两个独立接口。
- `app_release_package.baseVersionCode` 是历史字段，新发布不再填写，也不用于精确 APK 匹配。
- Android APP-PLUS 支持应用内安装 APK；iOS 原生包更新交由 App Store 或企业分发。
- 微信小程序使用平台更新管理器，H5 使用静态资源清单和刷新机制，均不进入本流程。
