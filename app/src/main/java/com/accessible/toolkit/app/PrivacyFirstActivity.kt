package com.accessible.toolkit.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class PrivacyFirstActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var dotsLayout: LinearLayout
    private lateinit var btnNext: Button
    private lateinit var tvSkip: TextView
    private lateinit var prefs: AppPreferences
    private lateinit var permissionManager: PermissionManager

    private val pages = listOf(
        PrivacyPage(
            icon = "\uD83D\uDD12",
            title = "你的声音，只在你的手机里",
            desc1 = "所有语音识别完全在你的手机本地运行。",
            desc2 = "不需要联网，不会上传到任何服务器。"
        ),
        PrivacyPage(
            icon = "\uD83D\uDDD1\uFE0F",
            title = "没人在说话，我们就不留数据",
            desc1 = "当检测到周围安静时，音频数据会被立即丢弃，不会保存或缓存。",
            desc2 = "转写文字默认不在你的手机上保存。如需查看历史记录，你可以在设置中手动开启。"
        ),
        PrivacyPage(
            icon = "\u2699\uFE0F",
            title = "你随时掌控",
            desc1 = "你可以随时在通知栏一键暂停监听。",
            desc2 = "所有权限都可以在系统设置中撤回。"
        )
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        finishAndLaunchMain()
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        finishAndLaunchMain()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_first)

        prefs = AppPreferences(this)
        permissionManager = PermissionManager(this)
        permissionManager.registerLaunchers(requestPermissionLauncher, overlayPermissionLauncher)

        viewPager = findViewById(R.id.viewpager)
        dotsLayout = findViewById(R.id.dots_layout)
        btnNext = findViewById(R.id.btn_next)
        tvSkip = findViewById(R.id.tv_skip)

        viewPager.adapter = PageAdapter()
        setupDots()
        updateButtonForPage(0)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                updateButtonForPage(position)
            }
        })

        btnNext.setOnClickListener {
            val currentItem = viewPager.currentItem
            if (currentItem < pages.size - 1) {
                viewPager.currentItem = currentItem + 1
            } else {
                onStartClick()
            }
        }

        tvSkip.setOnClickListener {
            onStartClick()
        }
    }

    private fun onStartClick() {
        prefs.hasSeenPrivacyIntro = true
        prefs.privacyIntroVersion = getAppVersionCode()

        permissionManager.setCallback(object : PermissionManager.PermissionCallback {
            override fun onAllPermissionsGranted() {
                finishAndLaunchMain()
            }

            override fun onPermissionDenied(permission: String) {
                finishAndLaunchMain()
            }

            override fun onOverlayPermissionDenied() {
                finishAndLaunchMain()
            }
        })
        permissionManager.checkAndRequestAllPermissions()
    }

    private fun finishAndLaunchMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun updateButtonForPage(position: Int) {
        if (position == pages.size - 1) {
            btnNext.text = "开始使用"
        } else {
            btnNext.text = "下一页"
        }
    }

    private fun setupDots() {
        dotsLayout.removeAllViews()
        val dotSize = 12.dpToPx()
        val margin = 6.dpToPx()

        for (i in pages.indices) {
            val dot = View(this).apply {
                val params = LinearLayout.LayoutParams(dotSize, dotSize)
                params.setMargins(margin, 0, margin, 0)
                layoutParams = params
                background = ContextCompat.getDrawable(
                    this@PrivacyFirstActivity,
                    android.R.drawable.radiobutton_off_background
                )
            }
            dotsLayout.addView(dot)
        }
        updateDots(0)
    }

    private fun updateDots(position: Int) {
        for (i in 0 until dotsLayout.childCount) {
            val dot = dotsLayout.childAt(i)
            dot.background = ContextCompat.getDrawable(
                this,
                if (i == position) android.R.drawable.radiobutton_on_background
                else android.R.drawable.radiobutton_off_background
            )
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun getAppVersionCode(): Int {
        return try {
            val info = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        } catch (_: Exception) {
            1
        }
    }

    data class PrivacyPage(
        val icon: String,
        val title: String,
        val desc1: String,
        val desc2: String
    )

    private inner class PageAdapter : RecyclerView.Adapter<PageHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_privacy_page, parent, false)
            return PageHolder(view)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.bind(pages[position])
        }

        override fun getItemCount(): Int = pages.size
    }

    private inner class PageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: TextView = itemView.findViewById(R.id.tv_icon)
        private val title: TextView = itemView.findViewById(R.id.tv_title)
        private val desc1: TextView = itemView.findViewById(R.id.tv_desc1)
        private val desc2: TextView = itemView.findViewById(R.id.tv_desc2)

        fun bind(page: PrivacyPage) {
            icon.text = page.icon
            title.text = page.title
            desc1.text = page.desc1
            if (page.desc2.isNotEmpty()) {
                desc2.text = page.desc2
                desc2.visibility = View.VISIBLE
            } else {
                desc2.visibility = View.GONE
            }
        }
    }
}
