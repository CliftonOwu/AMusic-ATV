# 应用名 / 界面模式 / 系统栏 相关改动

针对 `CliftonOwu/AMusic-ATV`（原 Veil Music）的三处改动：

1. 应用名改为 **Apple Music**
2. 界面模式新增 **跟随系统**
3. 设置里可切换 **显示状态栏和导航栏**（默认仍是沉浸式）

## 一、应用名 → Apple Music

改了清单里 4 个标签（都指向同一个包，桌面只显示一个图标）：

| 位置 | 原值 | 新值 |
|---|---|---|
| `<application android:label>` | Veil Music | Apple Music |
| `.RedLauncher` 别名 | Veil Music | Apple Music |
| `.WhiteLauncher` 别名 | Veil Music | Apple Music |
| `.LxPocActivity` | Veil Music 音源诊断 | Apple Music 音源诊断 |

`applicationId` **没有动**（仍是 `com.veacon.veilmusic`），所以能覆盖安装、不用重新登录。
Robolectric 里那条 `launcherIdentityUsesNewNamePackageVersionAndExactSizedTransparentIcon`
断言也同步改成 `Apple Music`，否则跑测试会红。

> 提醒：`Apple Music` 是 Apple 的注册商标。自己侧载自用没问题，但如果要对外分发或上架，
> 这个名字有商标风险，建议换成不带 Apple 字样的名字。

## 二、界面模式：浅色 / 深色 / 跟随系统

原来的 `AppPalette` 只有一个布尔 `isDark`，现在拆成两层：

- **`mode`** —— 用户的选择：`LIGHT` / `DARK` / `SYSTEM`
- **`isDark`** —— 实际生效值，`SYSTEM` 时按 `Configuration.UI_MODE_NIGHT_MASK` 解析

这样所有已有的 `AppPalette.adaptive(...)`、`AppPalette.background` 调用**一行都不用改**，
它们读的仍然是 `isDark`。

细节：

- **老用户不丢设置**：迁移逻辑先读 `mode` 字符串，读不到就回退到旧的 `dark` 布尔；写入时两个键都写
- **系统切换实时跟随**：`MainActivity` 用 `snapshotFlow { AppPalette.isDark }` 监听；
  系统深浅色变化时 Activity 因 `uiMode` 重建、重新 `load()`，状态栏配色同步刷新
- **副标题回显**：选「跟随系统」时显示「跟随系统（深色）」/「跟随系统（浅色）」，
  否则用户看不出系统当前是哪一档
- **步进器两端夹住**：`next()` / `previous()` 在首尾钳制，不会越界

设置位置：顶部导航 → **设置（Dev）** → **外观** → **界面模式**（− / + 切换三档）。

## 三、显示状态栏和导航栏

同一个「外观」分组里，下面一行：

- 关（默认）：全屏沉浸，和改动前完全一致
- 开：状态栏 + 导航栏常显，内容自动内缩，不再被栏压住

这个开关必须同时处理三件事，缺一不可：

1. `window.clearFlags(FLAG_FULLSCREEN)` —— 主题里 `android:windowFullscreen=true` 是窗口级的，
   只调 `show(systemBars())` 状态栏回不来
2. `setDecorFitsSystemWindows(window, true)` —— 内容内缩，否则栏显示了但盖住首行
3. `onWindowFocusChanged` 里重新读偏好 —— 否则从播放页/对话框回来会被沉浸式逻辑覆盖

## 四、涉及的文件

| 文件 | 类型 | 作用 |
|---|---|---|
| `app/.../AndroidManifest.xml` | 修改 | 4 个应用名标签 |
| `app/.../ui/theme/AppPalette.kt` | 修改 | `AppearanceMode` 三态 + 迁移 + 系统解析 |
| `app/.../ui/screens/DevMenuScreen.kt` | 修改 | 界面模式步进器、系统栏开关 |
| `app/.../ui/viewmodel/DevMenuViewModel.kt` | 修改 | `showSystemBars` 状态 + 切换 |
| `app/.../data/WindowChromePreferences.kt` | 新增 | 系统栏开关持久化 |
| `app/.../MainActivity.kt` | 修改 | 双模式窗口 + 监听偏好与主题变化 |
| `app/.../ui/theme/AppearanceTest.kt` | 修改 | 跟随系统 / 显式覆盖 / 步进器钳制 |
| `app/.../ui/screens/FullscreenFooterBrandingTest.kt` | 修改 | 应用名断言 |

## 五、编译

```sh
cd app
./gradlew :app:assembleDebug
```

或在仓库 Actions 里跑 `Build Veil Music APK`（工作流名没改，省得重新找）。

## 六、已知限制

- **Android 15+**：`targetSdk 35` 强制边到边，`setDecorFitsSystemWindows(true)` 被忽略，
  系统栏会变成透明浮层压在内容上；开关仍然生效（栏是显示的），只是内容不内缩。
  Android 10 / 14 及以下正常。
- 视频全屏播放页保持沉浸，不受这个开关影响。
