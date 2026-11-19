# 搜狗输入法 Hook

面向搜狗输入法（`com.sohu.inputmethod.sogouoem`，OPPO 定制版）的 Xposed / LSPosed 模块。
基于 [libxposed](https://github.com/libxposed/api)（modern Xposed API）实现，纯模块形态：**没有桌面图标、没有任何界面**，安装后在 LSPosed 模块页启用即可。

## 功能

| id | 说明 | Hook 点 |
| --- | --- | --- |
| `hide-ai-writer` | 隐藏工具栏上的「AI 写作」入口（那支彩色渐变笔） | `AiWriteManager#u()Z` 恒 `false`（兜底：`e20#b()Z` 恒 `true`、`PersonalizedContentView#updatePersonalizedContent(Z)` 后把 `mAiImage` 置 `GONE`） |
| `clipboard-unlimited` | 剪贴板解除 300 条上限，标题由 `（x/300）` 改为 `（x/∞）` | `AbstractDao#delete(Object)`（仅剪贴板 DAO 且调用方为 `clipboard.y#z` 时跳过）、`ClipboardCandidateView#setClipboardCount(II)` |
| `smoke-test` | 注入自检：只写日志、不安装任何钩子 | — |

## 环境要求

- 已 root 并安装 LSPosed（开发验证环境：LSPosed 2.2.1，Xposed API 102）
- `minSdk 26`，`compileSdk / targetSdk 37`
- 目标应用版本见下一节，目前仅在该版本验证通过

## 已验证版本

| 项 | 值 |
| --- | --- |
| 应用 | 搜狗输入法（OPPO 定制版，设备内路径 `/my_product/del-app/SogouInput_U_Product_Del`） |
| APK 文件名 | `搜狗输入法定制版_10.32.0622.2511060930.apk` |
| versionName | `10.32.0622.2511060930` |
| versionCode | `2527` |
| 大小 | 96,586,214 B |
| SHA-1 | `CB8B8F091BC687FAC2E19C7E8F582F5D24C110D3` |

该版本内所有 Hook 目标已逐一核对存在：

- 类：`AiWriteManager`、`e20`、`PersonalizedContentView`、`d20`、`PersonalizedContentType`、`clipboard.y`、`clipboard.db.ClipboardItemDao`、`greendao.AbstractDao`、`ClipboardCandidateView`
- 方法：`AiWriteManager#u()Z`、`e20#b()Z`、`PersonalizedContentView#updatePersonalizedContent(Z)V`、`d20#c()`、`clipboard.y#z(String)V`、`AbstractDao#delete(Object)V`、`ClipboardCandidateView#setClipboardCount(II)V`
- 字段：`mAiImage`、`mPersonalizedDataRepository`、`mCount`、`mCountText`

**目前仅在该版本验证通过**。其它版本（尤其是其它渠道包）混淆名可能不同，需按「版本适配」重新核对后再使用。

## 快速开始

```powershell
.\gradlew.bat :app:assembleRelease
```

1. 安装 `app/build/outputs/apk/release/app-release.apk`；
2. LSPosed → 模块 → 启用本模块（显示名以 `strings.xml` 的 `app_name` 为准）；
3. 作用域已固定为 `com.sohu.inputmethod.sogouoem`（`module.prop` 中 `staticScope=true`）；
4. 强制停止或重启输入法进程，让它重新加载模块；
5. LSPosed 日志过滤 `SGHook`，应看到三个功能各自的安装记录。

## 实现原理

### 1. 隐藏「AI 写作」入口

工具栏那支彩色笔的 `contentDescription` 是 `string/openplatform_ai_writer`（zh-CN「AI写作」），显示链路：

```
PersonalizedContentView.updatePersonalizedContent(Z)
    ├─ AiIcon 分支：mAiImage.setVisibility(VISIBLE)，背景取 mRootFunctionItem.q，可点击
    └─ AiNone 分支：mAiImage.setVisibility(GONE)，收起 AI 气泡
类型来源：d20.c()（读缓存字段） ← d20.g() 写入 ← AiWriteManager.u()Z
u() = !e20.b() && 入口开关(Settings.Secure "aiwriter_input_entrance_enabled") && (e != null && e.y5())
e20.b() 读 SP "oppo_ai_writer_data" → "oppo_close_ai_writer"（其自带的关闭开关）
```

按三层依次尝试，任一层成功即停止：

| 层 | Hook 点 | 动作 |
| --- | --- | --- |
| 1 | `com.sogou.bu.oem.oppo.function.ai.AiWriteManager#u()Z` | 恒 `false` → `d20.g()` 直接置 `AiNone` |
| 2 | `e20#b()Z` | 恒 `true` |
| 3 | `PersonalizedContentView#updatePersonalizedContent(Z)` | 执行后若类型仍为 `AiIcon`，把 `mAiImage` 置 `GONE` |

钩子在 `onPackageReady` 阶段安装，早于任何键盘视图创建，因此不存在「缓存值已计算」的时序问题。

### 2. 剪贴板 300 条上限

```
// 真正的裁剪：插入后按时间升序查全表，超过 300 条删最旧的一条
com.sohu.inputmethod.clipboard.y#z(String)      // static synthetic，由 clipboard.m#call() 调起
    insertOrReplace(item)
    list = queryBuilder().orderAsc(Properties.b).list()
    if (list.size() > 0x12c) dao.delete(list.get(0))

// 标题文案：string/lk = "（%d/%d）"，第二个参数写死 0x12c
com.sohu.inputmethod.clipboard.ClipboardCandidateView#setClipboardCount(II)
```

| Hook 点 | 动作 |
| --- | --- |
| `org.greenrobot.greendao.AbstractDao#delete(Object)` | 仅当「DAO 实例是 `clipboard.db.ClipboardItemDao`」且「调用栈里出现 `clipboard.y#z`」时跳过。手动删除、清空、其它 DAO 的删除均不受影响 |
| `ClipboardCandidateView#setClipboardCount(II)` | 原逻辑执行后把 `mCountText` 改写为 `（x/∞）` 并 `invalidate()` |

> `ClipboardKeyboard#v(int)` 里的 300 是「单条文本转快捷短语的字数上限」（提示「文本过长,无法移入快捷短语」），与条数上限无关，未改动。

## 工程结构

```
app/src/main/java/com/color/sg/hook
├── HookConfig.kt            作用域包名、进程白名单、日志标签
├── SogouHookModule.kt       模块入口：生命周期回调 + 包/进程过滤
├── core/
│   ├── HookFeature.kt       功能契约（id / description / isEnabled / install）
│   ├── FeatureRegistry.kt   功能注册表
│   ├── HookScope.kt         运行时上下文：findClass / hook / unhookAll / 日志
│   └── HookLog.kt           日志出口（框架日志 + logcat）
└── features/
    ├── HideAiWriterFeature.kt        隐藏「AI 写作」入口
    ├── ClipboardUnlimitedFeature.kt  剪贴板解除 300 条上限
    └── SmokeTestFeature.kt           注入自检
```

新增功能：在 `features/` 实现 `HookFeature`，用 `scope.findClass(...)` + `scope.hook(...)` 注册钩子，再到 `FeatureRegistry.features` 里登记。
`HookScope.hook` 默认 `PRIORITY_DEFAULT` + `ExceptionMode.PROTECTIVE`，Hooker 抛异常只记日志，不会影响输入法本身。

## 打包期元数据

模块靠三个文件被框架识别，位于 `app/src/main/resources/META-INF/xposed/`：

| 文件 | 作用 | 当前值 |
| --- | --- | --- |
| `module.prop` | 模块配置 | `minApiVersion=101`、`targetApiVersion=101`、`staticScope=true`、`exceptionMode=protective` |
| `scope.list` | 作用域包名 | `com.sohu.inputmethod.sogouoem` |
| `java_init.list` | Java 入口类 | `com.color.sg.hook.SogouHookModule` |

`app/build.gradle.kts` 中的 `packaging.resources.merges += "META-INF/xposed/*"` 保证这些文件原样进包。
修改作用域时，`scope.list` 与 `HookConfig.TARGET_PACKAGE` 需要同步修改。

## 构建说明

- 依赖只有 `compileOnly(libs.libxposed.api)`：框架 API 运行时由 Xposed 提供，不打进 APK；模块本身没有 UI，不引入 androidx。
- `release` 绑定 `signingConfigs.debug`，产物可直接安装；正式分发请替换为自己的签名。
- R8 规则（`app/src/main/keepRules/rules.keep`）采用 **只裁剪、不优化、不混淆**（`-dontoptimize` / `-dontobfuscate`），
  并保留入口类与模块自身代码。**不要**给入口类加 `allowoptimization`：实测优化会改坏静态初始化路径，导致钩子静默不安装。

## 版本适配

所有 Hook 点依赖混淆名（`clipboard.y`、`clipboard.m`、`e20`、`d20`）或私有字段名（`mCount`、`mCountText`、`mAiImage`）。
目标应用更新后，如果日志里出现「不存在 / 可能是版本差异」，用 dex 查询工具（例如 dexsql）重新定位：

```powershell
dexsql query  -i app.apk --sql "SELECT method, long_value FROM method_using_numbers WHERE long_value=300 AND method LIKE 'Lcom/sohu%'"
dexsql decode -i app.apk --method 'Lcom/sohu/inputmethod/clipboard/y;->z(Ljava/lang/String;)V'
dexsql decode -i app.apk --class  'Lcom/sogou/bu/oem/oppo/function/ai/AiWriteManager;'
```

对应常量都在两个 Feature 文件的 `companion object` 里，改名字即可。

## 已知限制

- 只在 `10.32.0622.2511060930`（versionCode 2527）上验证通过；其它版本/渠道包需要重新核对类名与字段名。
- 「AI 写作」入口在隐藏后，其 AI 气泡（`AiMessage`）同样不再出现；若只想隐藏图标，可禁用第 1、2 层，仅保留第 3 层。

## 免责声明

本项目仅供学习与个人研究使用，请勿用于商业用途或任何违反服务条款的场景；使用风险由使用者自行承担。
