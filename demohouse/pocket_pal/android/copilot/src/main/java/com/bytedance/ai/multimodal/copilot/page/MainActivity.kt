package com.bytedance.ai.multimodal.copilot.page

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bytedance.ai.multimodal.copilot.R
import com.bytedance.ai.multimodal.copilot.core.utils.dp2px
import com.bytedance.ai.multimodal.copilot.view.floating.AudioShot
import com.bytedance.ai.multimodal.copilot.view.floating.FloatingVisualAssistant

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_ASSISTANT_ENABLED = "assistant_enabled"
        private const val TAG = "MainActivity"
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var switchAssistant: SwitchCompat

    private var audioShot: AudioShot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        setupViewPager()
        setupSwitch()
        setupBaseFunctions()
    }

    private fun setupBaseFunctions() {
        audioShot = AudioShot.getSingleton(applicationContext)
    }

    private fun initViews() {
        viewPager = findViewById(R.id.viewPager)
        switchAssistant = findViewById(R.id.switch_assistant)
        val root = findViewById<View>(R.id.main_root)
        val colors = intArrayOf(
            Color.parseColor("#DCE0FF"),
            Color.parseColor("#EEF0FB"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#FFFFFF")
        )

        val bg = GradientDrawable().apply {
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            this.colors = colors
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
        root.background = bg
    }

    private fun setupViewPager() {
        val pagerAdapter = GuidePageAdapter()
        viewPager.adapter = pagerAdapter
        // 设置页面间距
        viewPager.apply {
            // 设置预览
            val recyclerView = getChildAt(0) as RecyclerView
            recyclerView.apply {
                setPadding(16.dp2px(), 0, 48.dp2px(), 0)
                clipToPadding = false
            }
        }
    }

    private fun setupSwitch() {
        //每次打开需要手动开启
        switchAssistant.isChecked = FloatingVisualAssistant.isFloatingBallShow
        switchAssistant.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // 开启状态的处理逻辑
                FloatingVisualAssistant.showFloatingBall(this)
            } else {
                // 关闭状态的处理逻辑
                FloatingVisualAssistant.removeFloatingBall(this)
            }
        }
    }


    @SuppressLint("CommitPrefEdits")
    private fun saveAssistantState(isChecked: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(KEY_ASSISTANT_ENABLED, isChecked).apply()
    }

}

class GuidePageAdapter : RecyclerView.Adapter<GuidePageViewHolder>() {
    private val pages = listOf(
        GuidePageData(R.drawable.guide_friends),
        GuidePageData(R.drawable.guide_image),
        GuidePageData(R.drawable.guide_report),
        GuidePageData(R.drawable.guide_meeting)
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GuidePageViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.page_guide_item, parent, false)
        return GuidePageViewHolder(view)
    }

    override fun onBindViewHolder(holder: GuidePageViewHolder, position: Int) {
        val data = pages[position]
        holder.bind(data)
    }

    override fun getItemCount(): Int = pages.size
}

data class GuidePageData(
    @DrawableRes val imageRes: Int
)

class GuidePageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val guideImage: ImageView = view.findViewById(R.id.iv_guide)

    fun bind(data: GuidePageData) {
        guideImage.setImageResource(data.imageRes)
    }
}