package com.color.sg.hook.core

import android.util.Log
import com.color.sg.hook.HookConfig
import io.github.libxposed.api.XposedInterface

/**
 * 统一日志出口。
 *
 * - 被框架注入时：额外写入 Xposed / LSPosed 的日志；
 * - 在模块自己的界面进程里没有框架，退化成纯 logcat。
 */
object HookLog {

    @Volatile
    private var framework: XposedInterface? = null

    /** 由模块入口在 onModuleLoaded 里调用。 */
    fun attach(iface: XposedInterface) {
        framework = iface
    }

    fun d(message: String) = write(Log.DEBUG, message, null)

    fun i(message: String) = write(Log.INFO, message, null)

    fun w(message: String, throwable: Throwable? = null) = write(Log.WARN, message, throwable)

    fun e(message: String, throwable: Throwable? = null) = write(Log.ERROR, message, throwable)

    private fun write(priority: Int, message: String, throwable: Throwable?) {
        val text = HookConfig.LOG_PREFIX + message
        Log.println(
            priority,
            HookConfig.LOG_TAG,
            if (throwable == null) text else "$text\n${Log.getStackTraceString(throwable)}"
        )
        val iface = framework ?: return
        // 框架日志写失败不能影响目标进程
        runCatching { iface.log(priority, HookConfig.LOG_TAG, text, throwable) }
    }
}