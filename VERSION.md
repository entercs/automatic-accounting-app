# 版本记录

## 1.0.1 (commit: 6c7a8a9)

**提交时间**: 2026-06-02

**功能更新**:
1. **微信收款屏幕解析** - `ScreenContentParser` 新增 `receiveSuccessKeywords`（你已收款/已收款/收款成功/到账），自动识别微信收款页（含"你已收款"文案 + 金额），无需用户手动操作
2. **支付宝营销通知过滤** - `AlipayParser` 新增 `nonTransactionTitles` 黑名单（支付宝卡包/卡包/红包/优惠券/积分/服务提醒等），跳过这些标题的通知避免误弹"检测到支付通知"提示
3. **京东白条额度类通知过滤** - `JDPayParser` 新增 `nonTransactionTitles` 黑名单（白条额度变动/额度提升/提额等），避免"白条额度已提升2100元"被误记为支出
4. **支付宝免密扣款通知稳健性提升** - `FinanceNotificationService` 读取 `EXTRA_BIG_TEXT` 和 `EXTRA_TEXT_LINES`，正确处理 BigTextStyle 通知的完整正文；`AlipayParser` 上下文窗口从 10 字符扩到 12 字符，更稳健地过滤"红包/补贴/积分"邻近干扰
5. **招商银行入账通知识别** - `BankCardParser` 补全招行全部包名（`com.cmbchina.ccs.pluto` / `com.cmbc.ccs` / `com.cmbchina.pocket` / `cmb.pb`），新增"人民币X.XX元"金额格式，启动自动 seed "银行卡"账户，记录时按需补建
6. **分类规则完善**
   - "北京一卡通 / 上海公共交通卡 / 羊城通 / 深圳通 / 公交卡 / 一卡通 / 出行 / 打车 / 摩拜 / 美团单车 / 青桔 / 哈啰单车" 等归类为 **汽车交通**
   - "招商银行 / 招行 / 入账 / 代发 / 工资 / 薪资 / 月薪 / 奖金 / 绩效" 等归类为 **工资收入**
   - "退款 / 退货 / 报销 / 理赔" 等归类为 **退款报销**
   - 分类器新增 `TransactionType` 参数，支出/收入走不同规则表
7. **交易提醒商户提取** - `AlipayParser.merchantInlinePattern` 正确从"你在北京一卡通有一笔4.00元的免密/自动扣款支付"中提取商户"北京一卡通"（"10个支付宝积分"后缀不影响）
8. **主页月度自动重置** - `HomeViewModel` 把 `monthStart`/`monthEnd` 改造为 `MonthRange` `StateFlow`，每分钟检查 + `ON_RESUME` 主动刷新，跨月时支出/结余自动重置

**涉及文件**:
- `app/src/main/java/com/financetracker/notification/ScreenContentParser.kt`
- `app/src/main/java/com/financetracker/notification/parser/AlipayParser.kt`
- `app/src/main/java/com/financetracker/notification/parser/JDPayParser.kt`
- `app/src/main/java/com/financetracker/notification/parser/BankCardParser.kt`
- `app/src/main/java/com/financetracker/notification/FinanceNotificationService.kt`
- `app/src/main/java/com/financetracker/notification/classifier/AutoClassifier.kt`
- `app/src/main/java/com/financetracker/data/repository/PaymentAccountRepository.kt`
- `app/src/main/java/com/financetracker/FinanceTrackerApp.kt`
- `app/src/main/java/com/financetracker/ui/screen/home/HomeViewModel.kt`
- `app/src/main/java/com/financetracker/ui/screen/home/HomeScreen.kt`

**APK**: `apk/20260602v1.apk`

---

## 版本2 (commit: 7f5b393)

**提交时间**: 2026-05-28

**功能更新**:
1. **计算器浮点精度修复** - 使用 BigDecimal 计算并四舍五入保留2位小数，修复 `21.39+0.1` 显示 `21.490000000000002` 的问题
2. **支付宝红包/补贴误记修复** - 新增 `nonTransactionAmountContexts` 过滤上下文，避免"淘宝闪购10元红包今晚失效"等非交易通知被误记
3. **免密扣款漏记修复** - 移除"积分"排除关键词，修复含"积分"提示的免密/自动扣款通知漏记问题

**涉及文件**:
- `app/src/main/java/com/financetracker/ui/screen/add/AddTransactionViewModel.kt`
- `app/src/main/java/com/financetracker/notification/parser/AlipayParser.kt`

**APK**: `apk/20260528v1.apk`

---

## 版本1 (commit: 3894dcc)

**提交时间**: 2026-05-17

**功能更新**:
1. **详情页导航栏** - 移除大绿色页眉，改为白底风格，与其他页面统一
2. **分类下拉选择** - 点击分类行弹出底部选择面板，可切换交易分类
3. **账户下拉选择** - 点击账户行弹出底部选择面板，可切换支付账户
4. **分类图标优化** - 详情页分类图标与记录页保持一致，使用真实图片资源
5. **退款确认弹窗** - 点击退款按钮弹出确认对话框，确认后生成退款记录并返回首页
6. **搜索栏隐藏效果** - 首页搜索框初始被遮挡在下拉刷新位置，下拉可见

**涉及文件**:
- `app/src/main/java/com/financetracker/ui/screen/detail/TransactionDetailScreen.kt`
- `app/src/main/java/com/financetracker/ui/screen/detail/TransactionDetailViewModel.kt`
- `app/src/main/java/com/financetracker/ui/navigation/NavGraph.kt`
- `app/src/main/java/com/financetracker/ui/screen/home/HomeScreen.kt`

---

## 回滚命令

如需回到此版本，执行：
```bash
git checkout 3894dcc
```

如需查看此版本与当前版本的差异：
```bash
git diff HEAD 3894dcc
```