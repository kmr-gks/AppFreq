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
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge


class MainActivity : ComponentActivity() {

	var durationMillis: Long = 1000L * 60 * 60 * 24

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContentView(R.layout.activity_main)
		val listView: ListView = findViewById(R.id.appLaunchList)
		val spinner: Spinner = findViewById(R.id.spinner_term)

		// スピナに選択肢を設定
		ArrayAdapter.createFromResource(
			this,
			R.array.duration_options,
			R.xml.my_spinner_item
		).also { adapter ->
			adapter.setDropDownViewResource(R.xml.my_spinner_dropdown_item)
			spinner.adapter = adapter
		}

		// スピナの選択イベントを設定
		spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
				updateFrequencyData(position)
				val appLaunchCounts = getAppLaunchCounts(this@MainActivity)
				if (appLaunchCounts.isNotEmpty()) {
					val listItems = appLaunchCounts.map { "${getAppNameFromPackage(this@MainActivity,it.key)}: ${it.value} 回" }
					val adapter = ArrayAdapter(this@MainActivity, R.xml.my_list_item, listItems)
					listView.adapter = adapter
				} else {
					Toast.makeText(this@MainActivity, "データが取得できませんでした", Toast.LENGTH_LONG).show()
				}
			}
			override fun onNothingSelected(parent: AdapterView<*>) {}
		}

		// 権限確認
		if (!hasUsageStatsPermission(this)) {
			startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
			Toast.makeText(this, "使用履歴へのアクセスを有効にしてください", Toast.LENGTH_LONG).show()
		} else {
			val appLaunchCounts = getAppLaunchCounts(this)
			if (appLaunchCounts.isNotEmpty()) {
				val listItems = appLaunchCounts.map { "${getAppNameFromPackage(this,it.key)}: ${it.value} 回" }
				val adapter = ArrayAdapter(this, R.xml.my_list_item, listItems)
				listView.adapter = adapter
			} else {
				Toast.makeText(this, "データが取得できませんでした", Toast.LENGTH_LONG).show()
			}
		}
	}

	private fun updateFrequencyData(position: Int) {
		when(position) {
			0 -> {
				// a day
				durationMillis = 1000L * 60 * 60 * 24
			}
			1 -> {
				// a week
				durationMillis = 1000L * 60 * 60 * 24 * 7
			}
			2 -> {
				// a month
				durationMillis = 1000L * 60 * 60 * 24 * 30
			}
			3 -> {
				// a year
				durationMillis = 1000L * 60 * 60 * 24 * 365
			}
			else -> {
				durationMillis = 1000L * 60 * 60 * 24 * 365 * 10
			}
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
		val startTime = endTime - durationMillis // 過去24時間

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
}

// パッケージ名からアプリ名を取得
private fun getAppNameFromPackage(context: Context,packageName: String): String {
	return try {
		context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)).toString()
	} catch (e: Exception) {
		"$packageName(アプリ名取得失敗)"
	}
}