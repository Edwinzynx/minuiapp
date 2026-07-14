package tred.minos

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.core.content.ContextCompat
import kotlin.math.abs

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
    private lateinit var screenTimeGoalLimitText: TextView
    private lateinit var goalStatus: TextView
    private var isShowingWeeklyStats = false
    private lateinit var homeAppsRecycler: RecyclerView
    private lateinit var allAppsRecycler: RecyclerView
    private lateinit var appDrawer: NestedScrollView
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<NestedScrollView>
    private lateinit var customizeApps: TextView
    private lateinit var closeDrawer: TextView
    private lateinit var coordinatorLayout: CoordinatorLayout
    
    // Custom controls
    private lateinit var themeToggle: SwitchCompat
    private lateinit var clockCard: CardView
    private lateinit var screenTimeCard: CardView
    private lateinit var batteryCard: CardView
    private lateinit var batteryLevelText: TextView
    private lateinit var batteryTempText: TextView
    private lateinit var batteryHealthText: TextView
    
    private lateinit var homeAppsAdapter: HomeAppsAdapter
    private lateinit var allAppsAdapter: AllAppsAdapter
    private lateinit var sharedPrefs: SharedPreferences
    
    private val allApps = mutableListOf<AppInfo>()
    private val homeApps = mutableListOf<AppInfo>()
    
    private var isDarkMode = true
    
    // Activity-level gesture detector
    private lateinit var gestureDetector: GestureDetector
    
    // Battery Broadcast Receiver
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
                
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 status == BatteryManager.BATTERY_STATUS_FULL
                
                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
                
                val healthInt = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                val healthStr = when (healthInt) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over voltage"
                    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
                    else -> "unknown"
                }
                
                updateBatteryUI(batteryPct, isCharging, temp, healthStr)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPrefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        isDarkMode = sharedPrefs.getBoolean("dark_mode", true)
        
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Hide system UI for full immersion and handle light/dark icon status
        hideSystemUI()
        
        // Initialize components
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)
        
        setupUI()
        setupRecyclerViews()
        setupBottomSheet()
        setupGestures()
        updateDateTime()
        checkUsageStatsPermission()
        updateScreenTime()
        updateGoals()
        loadApps()
        
        // Update time every 30 seconds for real-time feel
        startTimeUpdater()
        
        // Prompt default launcher choice on first cold start
        val isSwitchingTheme = sharedPrefs.getBoolean("is_switching_theme", false)
        if (savedInstanceState == null && !isSwitchingTheme) {
            val hasPromptedDefault = sharedPrefs.getBoolean("has_prompted_default_launcher", false)
            if (!hasPromptedDefault && !isDefaultLauncher()) {
                showDefaultLauncherDialog()
            }
        }
        if (isSwitchingTheme) {
            sharedPrefs.edit().putBoolean("is_switching_theme", false).apply()
        }
    }
    
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null && ::gestureDetector.isInitialized) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }
    
    private fun hideSystemUI() {
        // Enable edge-to-edge layout so content goes behind status and navigation bars
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Make the status and navigation bars completely transparent
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        // Handle layout in camera cutout/notch area
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        // Control status bar and navigation bar icon colors (dark icons in Light Mode, light icons in Dark Mode)
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDarkMode
        controller.isAppearanceLightNavigationBars = !isDarkMode
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
        screenTimeGoalLimitText = findViewById(R.id.screenTimeGoalLimitText)
        goalStatus = findViewById(R.id.goalStatus)
        homeAppsRecycler = findViewById(R.id.homeAppsRecycler)
        allAppsRecycler = findViewById(R.id.allAppsRecycler)
        appDrawer = findViewById(R.id.appDrawer)
        customizeApps = findViewById(R.id.customizeApps)
        closeDrawer = findViewById(R.id.closeDrawer)
        coordinatorLayout = findViewById(R.id.coordinatorLayout)
        
        // Theme switcher toggle setup
        themeToggle = findViewById(R.id.themeToggle)
        themeToggle.isChecked = isDarkMode
        themeToggle.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit()
                .putBoolean("dark_mode", isChecked)
                .putBoolean("is_switching_theme", true)
                .apply()
            isDarkMode = isChecked
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
        
        // Clock Card click handler
        clockCard = findViewById(R.id.clockCard)
        clockCard.setOnClickListener { openClockApp() }
        
        // Screen Time Card click handler
        screenTimeCard = findViewById(R.id.screenTimeCard)
        screenTimeCard.setOnClickListener { openDigitalWellbeing() }
        
        // Battery Widget views setup
        batteryCard = findViewById(R.id.batteryCard)
        batteryLevelText = findViewById(R.id.batteryLevelText)
        batteryTempText = findViewById(R.id.batteryTempText)
        batteryHealthText = findViewById(R.id.batteryHealthText)
        batteryCard.setOnClickListener { openBatterySettings() }
        
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
        
        // Add a long click listener for testing
        coordinatorLayout.setOnLongClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
            true
        }
    }
    
    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 120
            private val SWIPE_VELOCITY_THRESHOLD = 150

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // Swipe Right -> Open Camera
                            openCameraApp()
                        } else {
                            // Swipe Left -> Open App Drawer
                            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })
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
                updateScreenTime()
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
        
        // Calculate progress
        if (checkUsageStatsPermission()) {
            val todayUsage = getTodayUsageMinutes()
            val weekUsage = getWeekUsageMinutes()
            
            val dailyProgressPercent = ((todayUsage / 60.0) / dailyGoalHours * 100).toInt()
            val weeklyProgressPercent = ((weekUsage / 60.0) / weeklyGoalHours * 100).toInt()
            
            if (isShowingWeeklyStats) {
                screenTimeGoalLimitText.text = String.format(Locale.getDefault(), "limit: %dh [%d%%]", weeklyGoalHours, weeklyProgressPercent)
                
                // Color and status based on limit completion
                when {
                    weeklyProgressPercent >= 100 -> {
                        screenTimeGoalLimitText.setTextColor(ContextCompat.getColor(this, R.color.terminal_error))
                        goalStatus.text = "weekly limit exceeded"
                        goalStatus.setTextColor(ContextCompat.getColor(this, R.color.terminal_error))
                    }
                    weeklyProgressPercent >= 80 -> {
                        screenTimeGoalLimitText.setTextColor(ContextCompat.getColor(this, R.color.terminal_string))
                        goalStatus.text = "approaching weekly limit"
                        goalStatus.setTextColor(ContextCompat.getColor(this, R.color.terminal_string))
                    }
                    else -> {
                        screenTimeGoalLimitText.setTextColor(ContextCompat.getColor(this, R.color.terminal_string))
                        goalStatus.text = "on track for week"
                        goalStatus.setTextColor(ContextCompat.getColor(this, R.color.terminal_comment))
                    }
                }
            } else {
                screenTimeGoalLimitText.text = String.format(Locale.getDefault(), "limit: %dh [%d%%]", dailyGoalHours, dailyProgressPercent)
                
                // Color and status based on limit completion
                when {
                    dailyProgressPercent >= 100 -> {
                        screenTimeGoalLimitText.setTextColor(ContextCompat.getColor(this, R.color.terminal_error))
                        goalStatus.text = "daily limit exceeded"
                        goalStatus.setTextColor(ContextCompat.getColor(this, R.color.terminal_error))
                    }
                    dailyProgressPercent >= 80 -> {
                        screenTimeGoalLimitText.setTextColor(ContextCompat.getColor(this, R.color.terminal_string))
                        goalStatus.text = "approaching daily limit"
                        goalStatus.setTextColor(ContextCompat.getColor(this, R.color.terminal_string))
                    }
                    else -> {
                        screenTimeGoalLimitText.setTextColor(ContextCompat.getColor(this, R.color.terminal_string))
                        goalStatus.text = "on track for today"
                        goalStatus.setTextColor(ContextCompat.getColor(this, R.color.terminal_comment))
                    }
                }
            }
        } else {
            screenTimeGoalLimitText.text = "limit: --h [--%]"
            goalStatus.text = "grant permission to track"
            goalStatus.setTextColor(ContextCompat.getColor(this, R.color.terminal_comment))
        }
    }
    
    private fun getTodayUsageMinutes(): Long {
        return getUsageMinutesForPeriod(getMidnightTime(), System.currentTimeMillis())
    }
    
    private fun getWeekUsageMinutes(): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return getUsageMinutesForPeriod(calendar.timeInMillis, System.currentTimeMillis())
    }
    
    private fun getUsageMinutesForPeriod(startTime: Long, endTime: Long): Long {
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usageStatsManager.queryEvents(startTime, endTime)
            
            val appForegroundTimes = HashMap<String, Long>()
            val appLastForegroundTime = HashMap<String, Long>()
            
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName
                
                // Exclude our launcher
                if (pkg == packageName) continue
                
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    appLastForegroundTime[pkg] = event.timeStamp
                } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                    val start = appLastForegroundTime[pkg]
                    if (start != null) {
                        val duration = event.timeStamp - start
                        val total = appForegroundTimes[pkg] ?: 0L
                        appForegroundTimes[pkg] = total + duration
                        appLastForegroundTime.remove(pkg)
                    }
                }
            }
            
            // Check any apps still in foreground
            for ((pkg, start) in appLastForegroundTime) {
                val duration = endTime - start
                if (duration > 0) {
                    val total = appForegroundTimes[pkg] ?: 0L
                    appForegroundTimes[pkg] = total + duration
                }
            }
            
            var totalTimeMillis = 0L
            for (time in appForegroundTimes.values) {
                totalTimeMillis += time
            }
            
            TimeUnit.MILLISECONDS.toMinutes(totalTimeMillis)
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun getMidnightTime(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
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
            val totalTimeMinutes = getTodayUsageMinutes()
            val hours = totalTimeMinutes / 60
            val minutes = totalTimeMinutes % 60
            
            screenTimeText.text = "${hours}h ${minutes}m today"
            
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
            val totalTimeMinutes = getWeekUsageMinutes()
            val totalHours = totalTimeMinutes / 60
            val totalMinutes = totalTimeMinutes % 60
            val avgHours = totalHours / 7
            val avgMinutes = (totalTimeMinutes / 7) % 60
            
            screenTimeText.text = "${totalHours}h ${totalMinutes}m this week"
            screenTimeDetails.text = "avg: ${avgHours}h ${avgMinutes}m/day"
            
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
    
    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun showDefaultLauncherDialog() {
        AlertDialog.Builder(this, R.style.TerminalDialogTheme)
            .setTitle("$ default_launcher")
            .setMessage("Do you want to set MinimalistLauncher as your default home launcher? Or just inspect the app?")
            .setPositiveButton("set as default") { _, _ ->
                sharedPrefs.edit().putBoolean("has_prompted_default_launcher", true).apply()
                showDefaultLauncherStepsDialog()
            }
            .setNegativeButton("inspect app") { dialog, _ ->
                sharedPrefs.edit().putBoolean("has_prompted_default_launcher", true).apply()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showDefaultLauncherStepsDialog() {
        AlertDialog.Builder(this, R.style.TerminalDialogTheme)
            .setTitle("$ steps --set_default")
            .setMessage("1. Tap 'proceed' below.\n\n2. Choose 'MinimalistLauncherApp' (or 'minos') from the system prompt.\n\n3. Tap 'Always' or set it as default.\n\n(If the system prompt doesn't appear, you can set it via Settings -> Apps -> Default Apps -> Home app)")
            .setPositiveButton("proceed") { _, _ ->
                promptSetAsDefaultLauncher()
            }
            .setNegativeButton("cancel", null)
            .show()
    }
    
    private fun promptSetAsDefaultLauncher() {
        val intents = arrayOf(
            // Direct Default Home App settings screen (Android 6.0+)
            Intent(Settings.ACTION_HOME_SETTINGS),
            // Default Apps settings screen (Android 9.0+)
            Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"),
            // System Home chooser dialog fallback
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
        )
        
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            } catch (e: Exception) {
                // Try next fallback
            }
        }
        
        // Final fallback to general settings
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Please configure default launcher in Settings", Toast.LENGTH_LONG).show()
        }
    }

    private fun switchToTodayTab() {
        isShowingWeeklyStats = false
        todayTab.setTextColor(ContextCompat.getColor(this, R.color.terminal_function))
        weekTab.setTextColor(ContextCompat.getColor(this, R.color.terminal_comment))
        screenTimeDetails.visibility = View.GONE
        updateScreenTime()
        updateGoals()
    }

    private fun switchToWeekTab() {
        isShowingWeeklyStats = true
        todayTab.setTextColor(ContextCompat.getColor(this, R.color.terminal_comment))
        weekTab.setTextColor(ContextCompat.getColor(this, R.color.terminal_function))
        screenTimeDetails.visibility = View.VISIBLE
        updateWeeklyScreenTime()
        updateGoals()
    }
    
    private fun openClockApp() {
        val intents = arrayOf(
            Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS),
            Intent().setComponent(ComponentName("com.android.deskclock", "com.android.deskclock.DeskClock")),
            Intent().setComponent(ComponentName("com.google.android.deskclock", "com.android.deskclock.DeskClock")),
            Intent().setComponent(ComponentName("com.sec.android.app.clockpackage", "com.sec.android.app.clockpackage.ClockPackage")),
            Intent().setComponent(ComponentName("com.huawei.deskclock", "com.huawei.deskclock.AlarmsMainActivity")),
            Intent().setComponent(ComponentName("com.oppo.clock", "com.oppo.clock.Clock")),
            Intent().setComponent(ComponentName("com.coloros.alarmclock", "com.coloros.alarmclock.AlarmClock")),
            Intent().setComponent(ComponentName("com.android.clock", "com.android.clock.Clock"))
        )
        
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            } catch (e: Exception) {
                // Try next fallback
            }
        }
        
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setComponent(ComponentName("com.android.deskclock", "com.android.deskclock.DeskClock"))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open clock app", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openBatterySettings() {
        val intents = arrayOf(
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            } catch (e: Exception) {
                // Try next
            }
        }
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.android.settings")
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "Could not open battery settings", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open battery settings", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openDigitalWellbeing() {
        val intents = arrayOf(
            Intent("android.settings.DIGITAL_WELLBEING_SETTINGS"),
            packageManager.getLaunchIntentForPackage("com.google.android.apps.wellbeing"),
            packageManager.getLaunchIntentForPackage("com.samsung.android.forest"),
            Intent().setComponent(ComponentName("com.oneplus.wellbeing", "com.oneplus.wellbeing.MainActivity")),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            if (intent != null) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    return
                } catch (e: Exception) {
                    // Try next
                }
            }
        }
        Toast.makeText(this, "Could not open Digital Wellbeing", Toast.LENGTH_SHORT).show()
    }
    
    private fun openCameraApp() {
        val intents = arrayOf(
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setComponent(ComponentName("com.android.camera", "com.android.camera.Camera"))
            },
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setComponent(ComponentName("com.google.android.GoogleCamera", "com.android.camera.CameraActivity"))
            },
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setComponent(ComponentName("com.sec.android.app.camera", "com.sec.android.app.camera.Camera"))
            }
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            } catch (e: Exception) {
                // Try next fallback
            }
        }
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage("com.android.camera")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open camera", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateBatteryUI(pct: Int, isCharging: Boolean, temp: Double, health: String) {
        val statusText = if (isCharging) "charging" else "discharging"
        batteryLevelText.text = if (pct >= 0) "$pct% ($statusText)" else "--% ($statusText)"
        
        batteryTempText.text = String.format(Locale.getDefault(), "temp: %.1f°C", temp)
        batteryHealthText.text = "health: $health"
        
        val colorRes = when {
            pct >= 50 -> R.color.terminal_variable
            pct >= 20 -> R.color.terminal_string
            else -> R.color.terminal_error
        }
        batteryLevelText.setTextColor(ContextCompat.getColor(this, colorRes))
    }
    
    override fun onResume() {
        super.onResume()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        hideSystemUI()
        updateDateTime()
        checkUsageStatsPermission()
        updateScreenTime()
        updateGoals()
        loadApps() // Reload apps dynamically to capture new installs/uninstalls
    }
    
    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // ignore
        }
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
