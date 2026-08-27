package com.instrument.android.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * バッテリー最適化が有効なときに表示する一回限りの警告ダイアログ。
 *
 * このアプリは BLE を通じてガスを常時監視するため、バッテリー最適化の除外が推奨される。
 * 除外されていない場合、バックグラウンドでの BLE 接続が Android に切断される可能性がある。
 *
 * @param onOpenSettings ユーザーが「設定を開く」を選択したときのコールバック
 * @param onDismiss      ユーザーが「後で」を選択したときのコールバック
 */
@Composable
fun BatteryOptimizationDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("バッテリー最適化の確認") },
        text = {
            Text(
                "このアプリは BLE を通じてガス濃度を常時監視します。\n\n" +
                    "バッテリー最適化が有効なままだと、バックグラウンドで BLE 接続が " +
                    "切断され、アラームが正常に動作しない場合があります。\n\n" +
                    "設定 → バッテリー → アプリ からこのアプリを「制限なし」に変更することを推奨します。",
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text("設定を開く")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("後で")
            }
        },
    )
}
