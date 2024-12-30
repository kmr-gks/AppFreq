package com.gukos.appfreq

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.gukos.appfreq.ui.theme.AppFreqTheme


class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContentView(R.layout.activity_main)
		val listView: ListView = findViewById(R.id.appLaunchList)
		// 権限確認
		if (!hasUsageStatsPermission(this)) {
			startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
			Toast.makeText(this, "使用履歴へのアクセスを有効にしてください", Toast.LENGTH_LONG).show()
		} else {
			val appLaunchCounts = getAppLaunchCounts(this)
			if (appLaunchCounts.isNotEmpty()) {
				val listItems = appLaunchCounts.map { "${getAppNameFromPackage(this,it.key)}: ${it.value} 回" }
				val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listItems)
				listView.adapter = adapter
			} else {
				Toast.makeText(this, "データが取得できませんでした", Toast.LENGTH_LONG).show()
			}
		}
	}
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
	Text(
		text = "Hello $name!",
		modifier = modifier
	)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
	AppFreqTheme {
		Greeting("Android")
	}
}

// 使用履歴権限の確認
private fun hasUsageStatsPermission(context: Context): Boolean {
	val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
	return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
		// Android 10 (API 29)以降では `checkOp` を使用
		appOps.unsafeCheckOpNoThrow(
			AppOpsManager.OPSTR_GET_USAGE_STATS,
			Process.myUid(),
			context.packageName
		) == AppOpsManager.MODE_ALLOWED
	} else {
		// それ以前のAPIレベルでは checkOpNoThrow を使用
		@Suppress("DEPRECATION")
		appOps.checkOpNoThrow(
			AppOpsManager.OPSTR_GET_USAGE_STATS,
			Process.myUid(),
			context.packageName
		) == AppOpsManager.MODE_ALLOWED
	}
}

// 起動回数を取得
private fun getAppLaunchCounts(context: Context): Map<String, Int> {
	val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

	val endTime = System.currentTimeMillis()
	val startTime = endTime - 1000L * 60 * 60 * 24 // 過去24時間

	val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
	val appLaunchCount = mutableMapOf<String, Int>()

	val event = UsageEvents.Event()
	while (usageEvents.hasNextEvent()) {
		usageEvents.getNextEvent(event)
		if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
			val packageName=event.packageName
			appLaunchCount[packageName] = appLaunchCount.getOrDefault(packageName, 0) + 1
		}
	}
	return appLaunchCount.toSortedMap(compareByDescending { appLaunchCount[it] })
}

// パッケージ名からアプリ名を取得
private fun getAppNameFromPackage(context: Context,packageName: String): String {
	return try {
		context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)).toString()
	} catch (e: Exception) {
		"$packageName(アプリ名取得失敗)"
	}
}