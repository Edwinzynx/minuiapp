package tred.minos

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var screenTimeText: TextView
    private lateinit var screenTimeDetails: TextView
    private lateinit var lastUpdated: TextView
    private lateinit var permissionStatus: TextView
    private lateinit var todayTab: TextView
    private lateinit var weekTab: TextView
    private lateinit var setGoals: TextView
    private lateinit var dailyGoalText: TextView
    private lateinit var weeklyGoalText: TextView
    private lateinit var dailyProgress: TextView
    private lateinit var weeklyProgress: TextView
    private lateinit var goalStatus: TextView
    private var isShowingWeeklyStats = false
    private lateinit var homeAppsRecycler: RecyclerView
    private lateinit var allAppsRecycler: RecyclerView
    private lateinit var appDrawer: NestedScrollView
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<NestedScrollView>
    private lateinit var customizeApps: TextView
    private lateinit var closeDrawer: TextView
    private lateinit var coordinatorLayout: CoordinatorLayout
    
    private lateinit var homeAppsAdapter: HomeAppsAdapter
    private lateinit var allAppsAdapter: AllAppsAdapter
    private lateinit var sharedPrefs: SharedPreferences
    
    private val allApps = mutableListOf<AppInfo>()
    private val homeApps = mutableListOf<AppInfo>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Hide system UI for full immersion
        hideSystemUI()
        
        // Initialize components
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)
        sharedPrefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        
        setupUI()
        setupRecyclerViews()
        setupBottomSheet()
        updateDateTime()
        checkUsageStatsPermission()
        updateScreenTime()
        updateGoals()
        loadApps()
        
        // Update time every 30 seconds for real-time feel
        startTimeUpdater()
        
        // Check if we're the default launcher
        checkDefaultLauncher()
    }
    
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }
    
    private fun setupUI() {
        timeText = findViewById(R.id.timeText)
        dateText = findViewById(R.id.dateText)
        screenTimeText = findViewById(R.id.screenTimeText)
        screenTimeDetails = findViewById(R.id.screenTimeDetails)
        lastUpdated = findViewById(R.id.lastUpdated)
        permissionStatus = findViewById(R.id.permissionStatus)
        todayTab = findViewById(R.id.todayTab)
        weekTab = findViewById(R.id.weekTab)
        setGoals = findViewById(R.id.setGoals)
        dailyGoalText = findViewById(R.id.dailyGoalText)
        weeklyGoalText = findViewById(R.id.weeklyGoalText)
        dailyProgress = findViewById(R.id.dailyProgress)
        weeklyProgress = findViewById(R.id.weeklyProgress)
        goalStatus = findViewById(R.id.goalStatus)
        homeAppsRecycler = findViewById(R.id.homeAppsRecycler)
        allAppsRecycler = findViewById(R.id.allAppsRecycler)
        appDrawer = findViewById(R.id.appDrawer)
        customizeApps = findViewById(R.id.customizeApps)
        closeDrawer = findViewById(R.id.closeDrawer)
        coordinatorLayout = findViewById(R.id.coordinatorLayout)
        
        customizeApps.setOnClickListener { showCustomizeDialog() }
        closeDrawer.setOnClickListener { bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN }
        permissionStatus.setOnClickListener { requestUsageStatsPermission() }
        setGoals.setOnClickListener { showGoalsDialog() }
        
        // Setup screen time tabs
        todayTab.setOnClickListener { switchToTodayTab() }
        weekTab.setOnClickListener { switchToWeekTab() }
    }
    
    private fun setupRecyclerViews() {
        homeAppsAdapter = HomeAppsAdapter(homeApps) { appInfo ->
            launchApp(appInfo.packageName)
        }
        homeAppsRecycler.layoutManager = LinearLayoutManager(this)
        homeAppsRecycler.adapter = homeAppsAdapter
        
        allAppsAdapter = AllAppsAdapter(allApps) { appInfo ->
            launchApp(appInfo.packageName)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
        allAppsRecycler.layoutManager = LinearLayoutManager(this)
        allAppsRecycler.adapter = allAppsAdapter
    }
    
    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(appDrawer)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.isHideable = true
        
        // Add swipe left gesture detection to the entire coordinator layout
        coordinatorLayout.setOnTouchListener(SwipeLeftGestureListener(this) {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        })
        
        // Also add a click listener for testing
        coordinatorLayout.setOnLongClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
            true
        }
    }
    
    private fun checkUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        val hasPermission = mode == AppOpsManager.MODE_ALLOWED
        
        permissionStatus.visibility = if (hasPermission) View.GONE else View.VISIBLE
        
        return hasPermission
    }
    
    private fun requestUsageStatsPermission() {
        AlertDialog.Builder(this, R.style.TerminalDialogTheme)
            .setTitle("$ grant usage_stats")
            .setMessage("Screen time tracking requires usage access permission.\n\nThis allows the launcher to display real-time screen time data.")
            .setPositiveButton("grant") { _, _ ->
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("cancel", null)
            .show()
    }
    
    private fun showGoalsDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // Daily goal input
        val dailyLabel = TextView(this).apply {
            text = "Daily Goal (hours):"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.terminal_text))
            typeface = android.graphics.Typeface.MONOSPACE
        }
        
        val dailyInput = EditText(this).apply {
            hint = "4"
            setText(getDailyGoalHours().toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.terminal_text))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.terminal_comment))
            typeface = android.graphics.Typeface.MONOSPACE
        }
        
        // Weekly goal input
        val weeklyLabel = TextView(this).apply {
            text = "Weekly Goal (hours):"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.terminal_text))
            typeface = android.graphics.Typeface.MONOSPACE
        }
        
        val weeklyInput = EditText(this).apply {
            hint = "25"
            setText(getWeeklyGoalHours().toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.terminal_text))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.terminal_comment))
            typeface = android.graphics.Typeface.MONOSPACE
        }
        
        dialogView.addView(dailyLabel)
        dialogView.addView(dailyInput)
        dialogView.addView(weeklyLabel)
        dialogView.addView(weeklyInput)
        
        AlertDialog.Builder(this, R.style.TerminalDialogTheme)
            .setTitle("$ configure screen_time --limits")
            .setView(dialogView)
            .setPositiveButton("apply") { _, _ ->
                val dailyHours = dailyInput.text.toString().toIntOrNull() ?: 4
                val weeklyHours = weeklyInput.text.toString().toIntOrNull() ?: 25
                
                sharedPrefs.edit()
                    .putInt("daily_goal_hours", dailyHours)
                    .putInt("weekly_goal_hours", weeklyHours)
                    .apply()
                
                updateGoals()
                Toast.makeText(this, "Goals updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("cancel", null)
            .show()
    }
    
    private fun getDailyGoalHours(): Int {
        return sharedPrefs.getInt("daily_goal_hours", 4)
    }
    
    private fun getWeeklyGoalHours(): Int {
        return sharedPrefs.getInt("weekly_goal_hours", 25)
    }
    
    private fun updateGoals() {
        val dailyGoalHours = getDailyGoalHours()
        val weeklyGoalHours = getWeeklyGoalHours()
        
        dailyGoalText.text = "${dailyGoalHours}h 0m"
        weeklyGoalText.text = "${weeklyGoalHours}h 0m"
        
        // Calculate progress
        if (checkUsageStatsPermission()) {
            val todayUsage = getTodayUsageMinutes()
            val weekUsage = getWeekUsageMinutes()
            
            val dailyProgressPercent = ((todayUsage / 60.0) / dailyGoalHours * 100).toInt()
            val weeklyProgressPercent = ((weekUsage / 60.0) / weeklyGoalHours * 100).toInt()
            
            dailyProgress.text = "[${dailyProgressPercent}%]"
            weeklyProgress.text = "[${weeklyProgressPercent}%]"
            
            // Update progress colors based on status
            when {
                dailyProgressPercent >= 100 -> {
                    dailyProgress.setTextColor(ContextCompat.getColor(this, R.color.terminal_error))
                    goalStatus.text = "daily limit exceeded"
                    goalStatus.setTextColor(ContextCompat.getColor(this, R.color.terminal_error))
                }
                dailyProgressPercent >= 80 -> {
                    dailyProgress.setTextColor(ContextCompat.getColor(this, R.color.terminal_string))
                    goalStatus.text = "approaching daily limit"
                    goalStatus.setTextColor(ContextCompat.getColor(this, R.color.terminal_string))
                }
                else -> {
                    dailyProgress.setTextColor(ContextCompat.getColor(this, R.color.terminal_number))
                    goalStatus.text = "on track for today"
                    goalStatus.setTextColor(ContextCompat.getColor(this, R.color.terminal_comment))
                }
            }
            
            when {
                weeklyProgressPercent >= 100 -> {
                    weeklyProgress.setTextColor(ContextCompat.getColor(this, R.color.terminal_error))
                }
                weeklyProgressPercent >= 80 -> {
                    weeklyProgress.setTextColor(ContextCompat.getColor(this, R.color.terminal_string))
                }
                else -> {
                    weeklyProgress.setTextColor(ContextCompat.getColor(this, R.color.terminal_number))
                }
            }
        } else {
            dailyProgress.text = "[--]"
            weeklyProgress.text = "[--]"
            goalStatus.text = "grant permission to track"
            goalStatus.setTextColor(ContextCompat.getColor(this, R.color.terminal_comment))
        }
    }
    
    private fun getTodayUsageMinutes(): Long {
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()
            
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            
            var totalTime = 0L
            for (stats in usageStats) {
                if (stats.packageName != packageName) {
                    totalTime += stats.totalTimeInForeground
                }
            }
            
            TimeUnit.MILLISECONDS.toMinutes(totalTime)
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun getWeekUsageMinutes(): Long {
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -7)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()
            
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            
            var totalTime = 0L
            for (stats in usageStats) {
                if (stats.packageName != packageName) {
                    totalTime += stats.totalTimeInForeground
                }
            }
            
            TimeUnit.MILLISECONDS.toMinutes(totalTime)
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun updateDateTime() {
        val calendar = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEE MMM dd yyyy", Locale.getDefault())
        
        timeText.text = timeFormat.format(calendar.time)
        dateText.text = dateFormat.format(calendar.time)
    }
    
    private fun updateScreenTime() {
        if (!checkUsageStatsPermission()) {
            screenTimeText.text = "permission required"
            screenTimeDetails.text = "tap [grant] above"
            lastUpdated.text = "updated: never"
            return
        }
        
        if (isShowingWeeklyStats) {
            updateWeeklyScreenTime()
            return
        }
        
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()
            
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            
            var totalTime = 0L
            for (stats in usageStats) {
                if (stats.packageName != packageName) { // Exclude our launcher
                    totalTime += stats.totalTimeInForeground
                }
            }
            
            val hours = TimeUnit.MILLISECONDS.toHours(totalTime)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(totalTime) % 60
            
            screenTimeText.text = "${hours}h ${minutes}m today"
            
            // Update last updated time
            val updateTime = SimpleDateFormat("HH:mm", Locale.getDefault())
            lastUpdated.text = "updated: ${updateTime.format(Date())}"
            
        } catch (e: Exception) {
            screenTimeText.text = "error fetching data"
            lastUpdated.text = "updated: error"
        }
    }

    private fun updateWeeklyScreenTime() {
        if (!checkUsageStatsPermission()) {
            screenTimeText.text = "permission required"
            screenTimeDetails.text = "tap [grant] above"
            lastUpdated.text = "updated: never"
            return
        }
        
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val calendar = Calendar.getInstance()
            
            // Get start of week (7 days ago)
            calendar.add(Calendar.DAY_OF_YEAR, -7)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()
            
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            
            var totalTime = 0L
            for (stats in usageStats) {
                if (stats.packageName != packageName) { // Exclude our launcher
                    totalTime += stats.totalTimeInForeground
                }
            }
            
            val totalHours = TimeUnit.MILLISECONDS.toHours(totalTime)
            val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalTime) % 60
            val avgHours = totalHours / 7
            val avgMinutes = (TimeUnit.MILLISECONDS.toMinutes(totalTime) / 7) % 60
            
            screenTimeText.text = "${totalHours}h ${totalMinutes}m this week"
            screenTimeDetails.text = "avg: ${avgHours}h ${avgMinutes}m/day"
            
            // Update last updated time
            val updateTime = SimpleDateFormat("HH:mm", Locale.getDefault())
            lastUpdated.text = "updated: ${updateTime.format(Date())}"
            
        } catch (e: Exception) {
            screenTimeText.text = "error fetching data"
            screenTimeDetails.text = "check permissions"
            lastUpdated.text = "updated: error"
        }
    }
    
    private fun startTimeUpdater() {
        val handler = android.os.Handler()
        val runnable = object : Runnable {
            override fun run() {
                updateDateTime()
                updateScreenTime()
                updateGoals() // Update goals progress in real-time
                handler.postDelayed(this, 30000) // 30 seconds for real-time updates
            }
        }
        handler.post(runnable)
    }
    
    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        
        val apps = packageManager.queryIntentActivities(intent, 0)
        allApps.clear()
        
        apps.filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            .forEach { app ->
                allApps.add(
                    AppInfo(
                        name = app.loadLabel(packageManager).toString(),
                        packageName = app.activityInfo.packageName,
                        icon = app.loadIcon(packageManager)
                    )
                )
            }
        
        loadHomeApps()
        allAppsAdapter.notifyDataSetChanged()
    }
    
    private fun loadHomeApps() {
        val savedApps = sharedPrefs.getStringSet("home_apps", getDefaultHomeApps())
        homeApps.clear()
        
        savedApps?.forEach { packageName ->
            allApps.find { it.packageName == packageName }?.let { appInfo ->
                homeApps.add(appInfo)
            }
        }
        
        // If no apps are saved, add default apps
        if (homeApps.isEmpty()) {
            allApps.filter { getDefaultHomeApps().contains(it.packageName) }
                .forEach { homeApps.add(it) }
        }
        
        homeAppsAdapter.notifyDataSetChanged()
    }
    
    private fun getDefaultHomeApps(): Set<String> {
        return setOf(
            "com.android.chrome",
            "com.whatsapp",
            "com.spotify.music",
            "com.instagram.android",
            "com.android.settings",
            "com.google.android.apps.photos"
        )
    }
    
    private fun showCustomizeDialog() {
        val appNames = allApps.map { it.name }.toTypedArray()
        val selectedApps = BooleanArray(allApps.size) { index ->
            homeApps.any { it.packageName == allApps[index].packageName }
        }
        
        AlertDialog.Builder(this, R.style.TerminalDialogTheme)
            .setTitle("$ configure ~/apps (select any number)")
            .setMultiChoiceItems(appNames, selectedApps) { _, which, isChecked ->
                selectedApps[which] = isChecked
            }
            .setPositiveButton("apply") { _, _ ->
                val newHomeApps = mutableSetOf<String>()
                selectedApps.forEachIndexed { index, isSelected ->
                    if (isSelected) {
                        newHomeApps.add(allApps[index].packageName)
                    }
                }
                
                sharedPrefs.edit()
                    .putStringSet("home_apps", newHomeApps)
                    .apply()
                
                loadHomeApps()
            }
            .setNegativeButton("cancel", null)
            .show()
    }
    
    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "Cannot launch app", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error launching app", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkDefaultLauncher() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        
        if (resolveInfo?.activityInfo?.packageName != packageName) {
            promptSetAsDefaultLauncher()
        }
    }
    
    private fun promptSetAsDefaultLauncher() {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Please set as default launcher in Settings", Toast.LENGTH_LONG).show()
        }
    }

    private fun switchToTodayTab() {
        isShowingWeeklyStats = false
        todayTab.setTextColor(ContextCompat.getColor(this, R.color.terminal_function))
        weekTab.setTextColor(ContextCompat.getColor(this, R.color.terminal_comment))
        screenTimeDetails.visibility = View.GONE
        updateScreenTime()
    }

    private fun switchToWeekTab() {
        isShowingWeeklyStats = true
        todayTab.setTextColor(ContextCompat.getColor(this, R.color.terminal_comment))
        weekTab.setTextColor(ContextCompat.getColor(this, R.color.terminal_function))
        screenTimeDetails.visibility = View.VISIBLE
        updateWeeklyScreenTime()
    }
    
    override fun onResume() {
        super.onResume()
        hideSystemUI()
        updateDateTime()
        checkUsageStatsPermission()
        updateScreenTime()
        updateGoals()
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }
    
    override fun onBackPressed() {
        if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
        // Don't call super - launchers shouldn't exit on back press
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        updateScreenTime()
        updateGoals()
    }
}
