# 状态栏 / 导航栏显示选项

给 `CliftonOwu/AMusic-ATV`（Veil Music）加了一个可切换的开关：**默认仍是原来的全屏沉浸，用户可以在设置里把状态栏和导航栏显示出来。**

## 一、选项在哪

顶部导航 → **设置（Dev）** → **外观** → **「显示状态栏和导航栏」**

- 关（默认）：全屏沉浸，和改动前完全一致
- 开：状态栏 + 导航栏常显，内容自动内缩，不再被栏压住

开关状态存在 `SharedPreferences`（`window_chrome_prefs`），重启、断电都保持。

## 二、改了哪些文件

| 文件 | 类型 | 作用 |
|---|---|---|
| `app/.../data/WindowChromePreferences.kt` | 新增 | 开关的持久化 + `StateFlow`，默认 false |
| `app/.../MainActivity.kt` | 修改 | `applyImmersiveWindow()` → `applyWindowChrome()`，两种模式；监听偏好实时应用 |
| `app/.../ui/viewmodel/DevMenuViewModel.kt` | 修改 | `DevMenuState.showSystemBars` + `toggleSystemBars()` |
| `app/.../ui/screens/DevMenuScreen.kt` | 修改 | 「外观」里加一个 `Toggle` 行 |
| `.github/workflows/build-apk.yml` | 新增 | 云端出包（可选，本地能编就删掉） |

## 三、为什么不能只改一处

这个项目的沉浸式是**主题 + 代码**两层叠加的，和之前那个 `am_atv` 一样有个坑：

```xml
<!-- themes.xml：android:windowFullscreen=true 是 FLAG_FULLSCREEN，窗口创建时就生效 -->
<item name="android:windowFullscreen">true</item>
```

```kotlin
// MainActivity：再把 systemBars hide 一次，而且 onWindowFocusChanged 每次回来都重设
WindowCompat.setDecorFitsSystemWindows(window, false)
getInsetsController(...).hide(systemBars())
```

所以补丁里做了三件必要的事：

1. **`window.clearFlags(FLAG_FULLSCREEN)`** —— 只用 `WindowInsetsControllerCompat.show()` 是没用的，状态栏根本回不来；
2. **`setDecorFitsSystemWindows(window, true)`** —— 内容内缩到系统栏以内，否则栏显示了但会盖住首行内容；
3. **`onWindowFocusChanged` 里重新读偏好** —— 否则从别的页面/对话框回来，沉浸式逻辑会把这个开关覆盖掉。

栏的颜色跟着 `AppPalette.isDark` 走（深色主题黑栏白图标，浅色主题白栏黑图标），不会突然冒出一条和界面打架的色块。

## 四、如何编译

本地（需要 JDK 17 + Android SDK 36）：

```sh
cd app
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或者推仓库后跑 Actions：`Build Veil Music APK` → Run workflow，产物在 Artifacts。

> release 构建用的是 `signingConfigs.debug`，所以自己编的包能覆盖安装自己编的包；覆盖作者发布的正式包则要先卸载（签名不同）。

## 五、已知限制

- **Android 15+ 无效**：`targetSdk 35` 在 Android 15 上强制边到边，`setDecorFitsSystemWindows(true)` 会被忽略，栏会变成透明浮层压在内容上。此时开关仍然生效（栏是显示的），只是内容不会内缩。Android 10 / 14 及以下完全正常。
- 视频全屏播放页没有动，那里本来就该沉浸。
- 开关在「设置 → 外观」里，不是独立页面；如果你想要一个开机向导里的选项，可以再说。
