package com.financetracker.ui.component

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

class VoiceParser {
    data class Result(val amount: Double?, val categoryHint: String?, val merchant: String?)

    fun parse(spoken: String): Result {
        val normalized = spoken
            .replace("零", "0").replace("一", "1").replace("二", "2").replace("两", "2")
            .replace("三", "3").replace("四", "4").replace("五", "5")
            .replace("六", "6").replace("七", "7").replace("八", "8").replace("九", "9")
            .replace("十", "0").replace("点", ".")

        // Extract amount
        var amount: Double? = null
        var merchant: String? = null
        val amountPatterns = listOf(Regex("(\\d+\\.?\\d*)\\s*[元块钱]"), Regex("(\\d+\\.?\\d*)"))
        for (p in amountPatterns) {
            val m = p.find(normalized) ?: continue
            amount = m.groupValues[1].toDoubleOrNull()
            val idx = m.range.first
            merchant = normalized.substring(0, idx).trim().takeLast(20).ifBlank { null }
            if (amount != null) break
        }

        // Category hints
        val catMap = mapOf(
            "吃饭" to 1, "午餐" to 1, "晚餐" to 1, "外卖" to 1, "餐" to 1, "饭" to 1,
            "买东西" to 2, "淘宝" to 2, "购物" to 2,
            "加油" to 3, "打车" to 3, "地铁" to 3, "公交" to 3,
            "房租" to 4, "水电" to 4,
            "医院" to 5, "药" to 5,
            "电影" to 6, "游戏" to 6,
            "话费" to 7, "流量" to 7,
        )
        var catHint: String? = null
        for ((keyword, _) in catMap) {
            if (keyword in spoken) { catHint = keyword; break }
        }

        return Result(amount, catHint, merchant)
    }
}

@Composable
fun VoiceInputButton(onResult: (VoiceParser.Result) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: return@rememberLauncherForActivityResult
            val parsed = VoiceParser().parse(spoken)
            onResult(parsed)
        }
    }

    IconButton(onClick = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说出金额和用途")
        }
        launcher.launch(intent)
    }) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = "语音输入",
            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        )
    }
}
