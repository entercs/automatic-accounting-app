# 版本记录

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