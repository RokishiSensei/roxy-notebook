# 参与开发

感谢有兴趣一起改这个项目。下面是最少必要的信息。

## 环境

| 项目 | 版本 |
| --- | --- |
| JDK | 21 |
| Gradle | 9.8.1（用仓库自带的 `gradlew`，不要用系统 Gradle） |
| Android SDK | Platform 37 + Build-Tools 37.0.0 |
| Kotlin | 2.4.21（由 AGP 9 内置，没有单独的 `kotlin-android` 插件） |

## 构建与测试

```bash
./gradlew :app:testDebugUnitTest    # 244 个单元测试
./gradlew :app:lintDebug            # 应当 0 项
./gradlew :app:assembleDebug        # 产出 app/build/outputs/apk/debug/
```

Windows 用 `gradlew.bat`。首次构建需要联网下载 Gradle 发行包与依赖。

如果 Android SDK 不在默认位置，在项目根目录建 `local.properties`：

```properties
sdk.dir=/path/to/Android/Sdk
```

## 签名

`keystore/` 目录**不在仓库里**（`.gitignore` 已排除）。没有它时 release 构建会自动退回 debug 签名，功能完全一样，只是不能用于正式发布。要打自己的正式包就自己生成一个：

```bash
keytool -genkeypair -v -keystore keystore/my-release.jks \
  -alias mykey -keyalg RSA -keysize 2048 -validity 10000
```

然后写 `keystore/keystore.properties`：

```properties
storeFile=keystore/my-release.jks
storePassword=...
keyAlias=mykey
keyPassword=...
```

## 代码约定

- **用户可见文案一律中文**，同时补 `app/src/main/res/values-en/strings.xml` 的英文翻译
- **新增纯逻辑必须带单元测试**（放在 `app/src/test/java/com/lexi/wordbook/`）。仓库里 20 多个测试类就是这么积累起来的
- **lint 保持 0 项**，编译器警告保持 0 条
- 排序 / 唯一性 / 备份兼容这类规则改动时，**先写一个会失败的测试**，再改实现 —— 这类 bug 靠手点很难发现
- 尽量把逻辑抽成纯函数（例如 `shelfDestination`、`wordUnits`、`cleanHeaderIndex`），这样能在 JVM 测试里直接覆盖，不用起模拟器

## 单元测试的小坑

- 需要 `SharedPreferences` 的测试用 Robolectric，并且必须写 `@Config(sdk = [34], application = Application::class)`：Robolectric 目前最高只到 SDK 36，而本工程 `targetSdk 37`
- 用到 `org.json` 的测试也要 Robolectric（纯 JVM 下它是 stub，会抛 "not mocked"）
- 涉及 `HttpURLConnection` 的逻辑抽到 `AiWire.kt` 这类纯函数文件里，避免测试真的发请求

## 提交 PR

1. 从 `main` 开分支
2. 保证 `testDebugUnitTest` 与 `lintDebug` 都是绿的
3. PR 说明里写清楚**改了什么、为什么**；如果是修 bug，附上复现步骤
4. 改了用户可见行为的话，顺手更新 `README.md` 的功能表与版本记录

## 图标

启动图标由 `tools/make_icon.py` 从 `tools/src/roxy-source.png` 生成。原图是第三方素材、**没有放进仓库**（也把 `tools/src/` 加进了 `.gitignore`），所以 clone 之后脚本缺输入文件是正常的，不影响构建 —— 生成的 `mipmap-*` 已经提交了。

要换成自己的图：把图片放到 `tools/src/roxy-source.png`，然后

```bash
python tools/make_icon.py
```

## 许可

代码以 [MIT](LICENSE) 发布。注意启动图标来自第三方插画，**不在 MIT 授权范围内** —— 二次分发前请换成自己的图。
