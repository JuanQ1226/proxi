package com.jq.proxi

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusPill: TextView
    private lateinit var subtitleText: TextView
    private lateinit var heroTitleText: TextView
    private lateinit var heroSubtitleText: TextView
    private lateinit var primaryActionButton: Button

    private lateinit var activeConnectionsValue: TextView
    private lateinit var totalConnectionsValue: TextView
    private lateinit var uploadValue: TextView
    private lateinit var downloadValue: TextView

    private val healthRows = mutableListOf<HealthRow>()
    private val handler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateDashboard()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureSystemBars()

        requestNotificationPermissionIfNeeded()
        setContentView(makeRootLayout())
        updateDashboard()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshRunnable)
        super.onDestroy()
    }

    private fun makeRootLayout(): LinearLayout {
        val horizontalPadding = dp(18)
        val contentTopPadding = dp(24)
        val contentBottomPadding = dp(28)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_CANVAS)
        }

        val statusBarScrim = View(this).apply {
            setBackgroundColor(COLOR_CARBON)
        }

        root.addView(
            statusBarScrim,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
        )

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(COLOR_CANVAS)
            isFillViewport = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(horizontalPadding, contentTopPadding, horizontalPadding, contentBottomPadding)
        }

        scrollView.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        content.addFullWidth(makeHeader())
        content.addFullWidth(makeHeroCard(), topMarginDp = 22)
        content.addFullWidth(makeSectionTitle("Proxy ports"), topMarginDp = 24)
        content.addFullWidth(
            makeTwoColumnRow(
                makePortCard("SOCKS5", "1080", "TCP tunnel"),
                makePortCard("HTTP", "8080", "CONNECT ready")
            ),
            topMarginDp = 10
        )
        content.addFullWidth(makeSectionTitle("Live telemetry"), topMarginDp = 24)
        content.addFullWidth(makeStatsSection(), topMarginDp = 10)
        content.addFullWidth(makeSectionTitle("Connection health"), topMarginDp = 24)
        content.addFullWidth(makeHealthCard(), topMarginDp = 10)
        content.addFullWidth(makeSectionTitle("Quick actions"), topMarginDp = 24)
        content.addFullWidth(makeQuickActions(), topMarginDp = 10)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { _, insets ->
                val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
                val statusLayoutParams = statusBarScrim.layoutParams
                statusLayoutParams.height = systemBars.top
                statusBarScrim.layoutParams = statusLayoutParams

                content.setPadding(
                    horizontalPadding,
                    contentTopPadding,
                    horizontalPadding,
                    contentBottomPadding + systemBars.bottom
                )

                insets
            }
        }

        return root
    }

    private fun configureSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = COLOR_CARBON
            window.navigationBarColor = COLOR_CANVAS
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var flags = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }

            window.decorView.systemUiVisibility = flags
        }
    }

    private fun makeHeader(): LinearLayout {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val mark = TextView(this).apply {
            text = "PX"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(COLOR_LIME)
            includeFontPadding = false
            background = makeRoundedBackground(COLOR_CARBON, radiusDp = 16)
        }

        header.addView(mark, LinearLayout.LayoutParams(dp(46), dp(46)))

        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, dp(10), 0)
        }

        val titleText = TextView(this).apply {
            text = "Proxi"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_INK)
            includeFontPadding = false
        }

        subtitleText = TextView(this).apply {
            textSize = 13f
            setTextColor(COLOR_MUTED)
            setPadding(0, dp(5), 0, 0)
            includeFontPadding = false
        }

        titleColumn.addView(titleText)
        titleColumn.addView(subtitleText)

        header.addView(
            titleColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        statusPill = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(13), dp(8), dp(13), dp(8))
            includeFontPadding = false
        }
        header.addView(statusPill)

        return header
    }

    private fun makeHeroCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(18))
            background = makeGradientBackground(COLOR_CARBON, COLOR_CARBON_SOFT, radiusDp = 30)
            elevate(8f)
        }

        val eyebrow = TextView(this).apply {
            text = "USB PROXY CONTROL"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.16f
            setTextColor(COLOR_LIME)
            includeFontPadding = false
        }

        heroTitleText = TextView(this).apply {
            textSize = 31f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(16), 0, 0)
            includeFontPadding = false
        }

        heroSubtitleText = TextView(this).apply {
            textSize = 15f
            setTextColor(COLOR_CARBON_TEXT)
            setPadding(0, dp(9), 0, 0)
        }

        val miniMetrics = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(18), 0, 0)
        }

        miniMetrics.addView(
            makeMiniMetric("SOCKS5", "1080"),
            LinearLayout.LayoutParams(0, dp(72), 1f).apply { rightMargin = dp(6) }
        )
        miniMetrics.addView(
            makeMiniMetric("HTTP", "8080"),
            LinearLayout.LayoutParams(0, dp(72), 1f).apply { leftMargin = dp(6) }
        )

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(18), 0, 0)
        }

        primaryActionButton = makeButton("Start Proxy", COLOR_LIME, COLOR_INK).apply {
            setOnClickListener {
                if (ProxyStats.isRunning.get()) {
                    stopProxy()
                } else {
                    startProxy()
                }
            }
        }

        val resetButton = makeButton("Reset", COLOR_CARBON_RAISED, Color.WHITE, COLOR_CARBON_STROKE).apply {
            setOnClickListener {
                ProxyStats.reset()
                Toast.makeText(this@MainActivity, "Stats reset", Toast.LENGTH_SHORT).show()
                updateDashboard()
            }
        }

        buttonRow.addView(
            primaryActionButton,
            LinearLayout.LayoutParams(0, dp(50), 1.55f).apply { rightMargin = dp(7) }
        )
        buttonRow.addView(
            resetButton,
            LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(7) }
        )

        card.addView(eyebrow)
        card.addView(heroTitleText)
        card.addView(heroSubtitleText)
        card.addView(miniMetrics)
        card.addView(buttonRow)

        return card
    }

    private fun makeMiniMetric(label: String, value: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = makeRoundedBackground(COLOR_CARBON_RAISED, radiusDp = 20, strokeColor = COLOR_CARBON_STROKE)
        }

        card.addView(
            TextView(this).apply {
                text = label
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                setTextColor(COLOR_CARBON_TEXT)
                includeFontPadding = false
            }
        )
        card.addView(
            TextView(this).apply {
                text = value
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setPadding(0, dp(6), 0, 0)
                includeFontPadding = false
            }
        )

        return card
    }

    private fun makeSectionTitle(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_INK)
            includeFontPadding = false
        }
    }

    private fun makePortCard(label: String, value: String, caption: String): LinearLayout {
        val card = makeBaseCard()

        val labelText = TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setTextColor(COLOR_MUTED)
            includeFontPadding = false
        }

        val valueText = TextView(this).apply {
            text = value
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_INK)
            setPadding(0, dp(10), 0, 0)
            includeFontPadding = false
        }

        val captionText = TextView(this).apply {
            text = caption
            textSize = 12f
            setTextColor(COLOR_MUTED)
            setPadding(0, dp(7), 0, 0)
        }

        card.addView(labelText)
        card.addView(valueText)
        card.addView(captionText)

        return card
    }

    private fun makeStatsSection(): LinearLayout {
        val section = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val activeCard = makeStatCard("Active", COLOR_LIME_DARK)
        activeConnectionsValue = activeCard.second

        val totalCard = makeStatCard("Total", COLOR_BLUE)
        totalConnectionsValue = totalCard.second

        val uploadCard = makeStatCard("Upload", COLOR_PURPLE)
        uploadValue = uploadCard.second

        val downloadCard = makeStatCard("Download", COLOR_GREEN_DARK)
        downloadValue = downloadCard.second

        section.addView(makeTwoColumnRow(activeCard.first, totalCard.first))
        section.addView(
            makeTwoColumnRow(uploadCard.first, downloadCard.first),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        )

        return section
    }

    private fun makeStatCard(label: String, accentColor: Int): Pair<LinearLayout, TextView> {
        val card = makeBaseCard()

        val labelText = TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setTextColor(COLOR_MUTED)
            includeFontPadding = false
        }

        val valueText = TextView(this).apply {
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accentColor)
            setPadding(0, dp(10), 0, 0)
            includeFontPadding = false
        }

        card.addView(labelText)
        card.addView(valueText)

        return card to valueText
    }

    private fun makeHealthCard(): LinearLayout {
        val card = makeBaseCard()
        card.addView(makeHealthRow("Proxy service running"))
        card.addView(makeDivider())
        card.addView(makeHealthRow("SOCKS5 ready on 1080"))
        card.addView(makeDivider())
        card.addView(makeHealthRow("HTTP ready on 8080"))
        return card
    }

    private fun makeHealthRow(label: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(5))
        }

        val badge = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
        }

        val labelText = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(COLOR_INK)
            setPadding(dp(12), 0, 0, 0)
        }

        row.addView(badge, LinearLayout.LayoutParams(dp(44), dp(28)))
        row.addView(labelText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        healthRows.add(HealthRow(badge, labelText))

        return row
    }

    private fun makeQuickActions(): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val logsButton = makeButton("Logs", COLOR_SURFACE, COLOR_INK, COLOR_BORDER).apply {
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Logs coming soon", Toast.LENGTH_SHORT).show()
            }
        }

        val setupButton = makeButton("Setup", COLOR_CARBON, COLOR_LIME).apply {
            setOnClickListener { showSetupDialog() }
        }

        val notificationButton = makeButton("Notify", COLOR_SURFACE, COLOR_INK, COLOR_BORDER).apply {
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "Notification controls coming soon",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        row.addView(logsButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(5) })
        row.addView(
            setupButton,
            LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                leftMargin = dp(5)
                rightMargin = dp(5)
            }
        )
        row.addView(notificationButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(5) })

        return row
    }

    private fun makeBaseCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = makeRoundedBackground(COLOR_SURFACE, radiusDp = 24, strokeColor = COLOR_BORDER)
            elevate(3f)
        }
    }

    private fun makeDivider(): View {
        return View(this).apply {
            setBackgroundColor(COLOR_BORDER)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(10)
            }
        }
    }

    private fun makeTwoColumnRow(left: View, right: View): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                left,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = dp(6)
                }
            )
            addView(
                right,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dp(6)
                }
            )
        }
    }

    private fun makeButton(
        label: String,
        fillColor: Int,
        textColor: Int,
        strokeColor: Int? = null
    ): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            minWidth = 0
            minHeight = 0
            minimumWidth = 0
            minimumHeight = 0
            stateListAnimator = null
            setPadding(dp(10), 0, dp(10), 0)
            background = makeRoundedBackground(fillColor, radiusDp = 18, strokeColor = strokeColor)
        }
    }

    private fun updateDashboard() {
        val running = ProxyStats.isRunning.get()

        statusPill.text = if (running) "Running" else "Stopped"
        statusPill.setTextColor(if (running) COLOR_INK else COLOR_MUTED)
        statusPill.background = makeRoundedBackground(
            fillColor = if (running) COLOR_LIME else COLOR_SURFACE,
            radiusDp = 100,
            strokeColor = if (running) COLOR_LIME_DARK else COLOR_BORDER
        )

        subtitleText.text = if (running) {
            "USB connected - foreground service active"
        } else {
            "Ready to start USB proxy"
        }

        heroTitleText.text = if (running) "Proxy online" else "Proxy standby"
        heroSubtitleText.text = if (running) {
            "Traffic is being routed through this device."
        } else {
            "Start the service to accept Linux client connections."
        }

        primaryActionButton.text = if (running) "Stop Proxy" else "Start Proxy"
        primaryActionButton.setTextColor(if (running) Color.WHITE else COLOR_INK)
        primaryActionButton.background = makeRoundedBackground(
            fillColor = if (running) COLOR_STOP else COLOR_LIME,
            radiusDp = 18
        )

        activeConnectionsValue.text = ProxyStats.activeConnections.get().coerceAtLeast(0).toString()
        totalConnectionsValue.text = ProxyStats.totalConnections.get().coerceAtLeast(0).toString()
        uploadValue.text = ProxyStats.formatBytes(ProxyStats.bytesFromClient.get())
        downloadValue.text = ProxyStats.formatBytes(ProxyStats.bytesToClient.get())

        healthRows.forEach { row ->
            row.badge.text = if (running) "OK" else "OFF"
            row.badge.setTextColor(if (running) COLOR_INK else COLOR_MUTED)
            row.badge.background = makeRoundedBackground(
                fillColor = if (running) COLOR_LIME else COLOR_CANVAS,
                radiusDp = 100,
                strokeColor = if (running) COLOR_LIME_DARK else COLOR_BORDER
            )
            row.label.setTextColor(if (running) COLOR_INK else COLOR_MUTED)
        }
    }

    private fun startProxy() {
        val intent = Intent(this, ProxyService::class.java).apply {
            action = ProxyService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        Toast.makeText(this, "Starting proxy...", Toast.LENGTH_SHORT).show()
        updateDashboard()
    }

    private fun stopProxy() {
        val intent = Intent(this, ProxyService::class.java).apply {
            action = ProxyService.ACTION_STOP
        }

        startService(intent)

        Toast.makeText(this, "Stopping proxy...", Toast.LENGTH_SHORT).show()
        updateDashboard()
    }

    private fun showSetupDialog() {
        AlertDialog.Builder(this)
            .setTitle("Linux Setup")
            .setMessage(
                """
                adb forward tcp:1080 tcp:1080
                adb forward tcp:8080 tcp:8080
                proxi transparent-start
                """.trimIndent()
            )
            .setPositiveButton("Done", null)
            .show()
    }

    private fun makeRoundedBackground(
        fillColor: Int,
        radiusDp: Int,
        strokeColor: Int? = null,
        strokeWidthDp: Int = 1
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) {
                setStroke(dp(strokeWidthDp), strokeColor)
            }
        }
    }

    private fun makeGradientBackground(
        startColor: Int,
        endColor: Int,
        radiusDp: Int
    ): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(startColor, endColor)
        ).apply {
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun View.elevate(value: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            elevation = dp(value.toInt()).toFloat()
        }
    }

    private fun LinearLayout.addFullWidth(view: View, topMarginDp: Int = 0) {
        addView(
            view,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(topMarginDp) }
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private data class HealthRow(
        val badge: TextView,
        val label: TextView
    )

    companion object {
        private val COLOR_CANVAS = Color.parseColor("#F1F3EA")
        private val COLOR_SURFACE = Color.parseColor("#FBFCF5")
        private val COLOR_BORDER = Color.parseColor("#D9DDD0")
        private val COLOR_INK = Color.parseColor("#101313")
        private val COLOR_MUTED = Color.parseColor("#6D746E")

        private val COLOR_CARBON = Color.parseColor("#101112")
        private val COLOR_CARBON_SOFT = Color.parseColor("#252A28")
        private val COLOR_CARBON_RAISED = Color.parseColor("#202423")
        private val COLOR_CARBON_STROKE = Color.parseColor("#343A37")
        private val COLOR_CARBON_TEXT = Color.parseColor("#C8D0C1")

        private val COLOR_LIME = Color.parseColor("#D8FF2F")
        private val COLOR_LIME_DARK = Color.parseColor("#56670B")
        private val COLOR_BLUE = Color.parseColor("#4868FF")
        private val COLOR_PURPLE = Color.parseColor("#7665E8")
        private val COLOR_GREEN_DARK = Color.parseColor("#167452")
        private val COLOR_STOP = Color.parseColor("#C54747")
    }
}
