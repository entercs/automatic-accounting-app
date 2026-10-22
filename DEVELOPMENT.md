# 开发与验证

产品功能和架构见 [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)，逐版主要改动见 [VERSION_HISTORY.md](VERSION_HISTORY.md)，开发约定见 [AGENTS.md](AGENTS.md)。

当前工作版本：**V1.2.3**，versionCode=16，数据库版本8。源码、构建配置和Git状态是判断当前实现的依据，不能仅凭发行版标题认定代码已推送。

## 开发环境

- JDK 17。本机安装：`/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`。
- Android SDK Platform 35、Build-Tools 34.0.0、Platform-Tools。
- Gradle 8.10、AGP 8.7.3、Kotlin 2.1.0；版本固定在构建配置中。
- 最低 Android 8.0。真机或已有模拟器均可运行。
- 本机SDK由未跟踪的local.properties或ANDROID_HOME指定，不提交绝对路径配置。
- 已下载依赖缓存在工作区外层的 `.android-gradle-cache`，可通过GRADLE_USER_HOME复用，避免重复下载。

## 常用命令

在 Android Studio 打开本项目并选择 Gradle JDK 17；命令行设置JAVA_HOME/ANDROID_HOME后可执行：

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
./gradlew lintDebug
./gradlew :app:packageLatestApk
```

最后一条命令从构建配置中的appVersion生成 `artifacts/latest/V1.2.3.apk`。在确认新包构建和测试通过后，只保留最新交付APK。发行说明注明调试签名，不把debug等字样拼入对外文件名。

## 验证重点

- JVM：支付解析、采集去重、金额计算、屏幕白名单、撤销时间窗口、本地时间与闰年/月末调整、趋势刻度与触摸日期映射。
- 模拟器：历史数据迁移、自动入账余额、事件回执、组合筛选、报销状态不影响余额、跨途径转账查询、首页撤销的真实过期和单次恢复。
- 页面：记账类型页顶固定、时间滚轮回显/确认/取消、趋势纵轴与横向连续拖动（不阻断纵向滚动）；资产与账户分组、末尾设置、筛选确认/取消、重置立即应用、删除后离开首页再返回、输入计算式后取消不改动余额。
- 清理：保留最新APK、发行说明、校验和简短验证摘要；测试截图、页面XML和构建中间文件属于过程文件。

本机最新验证摘要位于 `artifacts/latest/验证记录.md`，该目录不提交Git。对历史行为的概括保存在VERSION_HISTORY.md，避免只留在聊天里。

## 数据与安全边界

- 迁移不能清空已有账本；当前8版保留历史迁移链。
- 报销完成只改标记，不产生收入或变动余额。
- 首页撤销以单调时钟的截止时间为准，不能依靠页面存活来计时；旧提示事件不能消费新的撤销机会。
- 只有屏幕白名单应用可读取支付页面；银行仍可能因无障碍开启而拦截，不能承诺规避。
- 真机覆盖安装须先核对签名、备份账本，不要为了安装测试包卸载有数据的旧版。
- 自动记账仍需验证两笔同金额交易、退款、锁屏、后台和不同品牌手机的限制。跨平台与银行通知关联仍有未覆盖情况。

## 真机验收建议

1. 进入“我的→设置”，开启通知读取并确认“已连接”；先关闭无障碍验证通知独立工作。
2. 完成小额支付，核对金额、方向、账户、余额与识别结果。
3. 测试同额两笔、退款、锁屏和后台；漏记先补记，再提供脱敏样本补规则。
4. 测试需要报销→已报销→改回待报销，确认不重复产生收入。
5. 进入查账组合关键词、分类、支付途径与报销状态；重置确认后应直接刷新结果。

撤销计时依据参考 [Android SystemClock](https://developer.android.com/reference/android/os/SystemClock)：elapsedRealtime包含设备睡眠时间，适合计算跨页面/后台经过时间。
