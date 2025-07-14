package com.bytedance.ai.multimodal.copilot.screenshot

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

// 添加透明Activity类
class CapturePermissionActivity : Activity() {
    companion object {
        const val REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            // 将结果传回ScreenShotHelper
            ScreenshotSession.Companion.permissionResultCallback?.invoke(resultCode, data)
            finish()
        }
    }

    override fun finish() {
        super.finish()
        // 禁用退出动画
        overridePendingTransition(0, 0)
    }
}