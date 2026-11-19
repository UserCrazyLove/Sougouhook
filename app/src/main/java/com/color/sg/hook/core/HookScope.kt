package com.color.sg.hook.core

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable

/**
 * 注入目标进程后的运行时上下文，交给各个 [HookFeature] 使用。
 *
 * 所有对框架的调用都收敛在这里：功能实现只关心"定位目标 + 声明 Hooker"。
 * 注意类加载：目标类必须用目标包的 [classLoader] 加载，不能用模块自己的。
 */
class HookScope(
    val packageName: String,
    val processName: String,
    val classLoader: ClassLoader,
    private val module: XposedInterface,
) {

    private val handles = mutableListOf<XposedInterface.HookHandle>()

    /** 当前作用域已登记的 Hook 数量。 */
    val hookCount: Int
        get() = synchronized(handles) { handles.size }

    fun log(message: String) = HookLog.i(message)

    fun logDebug(message: String) = HookLog.d(message)

    fun logError(message: String, throwable: Throwable? = null) = HookLog.e(message, throwable)

    /**
     * 按类名定位目标进程内的类。
     *
     * 找不到时返回 null 并记录日志，调用方可以按版本号做兼容分支：
     * `val cls = scope.findClass("...") ?: return`
     */
    fun findClass(className: String): Class<*>? =
        runCatching { Class.forName(className, false, classLoader) }
            .onFailure { logError("找不到类 $className", it) }
            .getOrNull()

    /**
     * 注册 Hook 的统一入口。
     *
     * 统一优先级与异常模式（保护模式：Hooker 抛错只记日志，不会把输入法带崩），
     * 同时把句柄登记下来，方便整体卸载。
     */
    fun hook(
        target: Executable,
        priority: Int = XposedInterface.PRIORITY_DEFAULT,
        hooker: XposedInterface.Hooker,
    ): XposedInterface.HookHandle? =
        runCatching {
            module.hook(target)
                .setPriority(priority)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(hooker)
        }.onSuccess { handle ->
            synchronized(handles) { handles.add(handle) }
            logDebug("已挂钩 ${target.declaringClass.name}#${target.name}")
        }.onFailure { logError("挂钩失败 $target", it) }
            .getOrNull()

    /** 卸载本作用域登记的全部 Hook。 */
    fun unhookAll() {
        synchronized(handles) {
            handles.forEach { handle -> runCatching { handle.unhook() } }
            handles.clear()
        }
    }
}