package com.financetracker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.financetracker.ui.theme.TabIndicatorHeight

/**
 * 1.0.9 UI 规范: 分段式 Tab(周/月/年/自定义, 支出/收入/转账)
 *  - 未选中: 浅灰文字白底
 *  - 选中:   主色文字 + 底部 2dp 主色下划线
 *  - **禁止使用色块高亮填充**(规范第二节第 3 条)
 */
@Composable
fun <T> SegmentedTab(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val unselected = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (isSelected) primary else unselected,
                    ),
                )
                // 底部下划线指示器(选中=主色 2dp,未选中=透明保持布局)
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height(TabIndicatorHeight)
                        .background(if (isSelected) primary else Color.Transparent),
                )
            }
        }
    }
}
