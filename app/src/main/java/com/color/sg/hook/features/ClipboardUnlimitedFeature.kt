package com.color.sg.hook.features

import com.color.sg.hook.core.HookFeature
import com.color.sg.hook.core.HookScope

/**
 * 解除搜狗输入法剪贴板的 300 条上限，并把标题栏的「（x/300）」改成「（x）」。
 *
 * 目标版本（sg.apk）里的两处硬编码：
 * ```
 * // 1) 真正的裁剪：插入后按时间升序查全表，超过 300 条就删最旧那条
 * com.sohu.inputmethod.clipboard.y#z(String)        // static synthetic，由 m.call() 调起
 *     ... insertOrReplace(item)
 *     list = queryBuilder().orderAsc(Properties.b).list()
 *     if (list.size() > 0x12c) dao.delete(list.get(0))
 *
 * // 2) 显示文案：「（x/300）」，string/lk = "（%d/%d）"，第二个参数写死 0x12c
 * com.sohu.inputmethod.clipboard.ClipboardCandidateView#setClipboardCount(II)
 *     mCount = arg0; mCountText = String.format(getString(0x7f1101d4), mCount, 300)
 * ```
 *
 * 做法：
 * - 只拦 `AbstractDao.delete` 里「由 y#z 触发的、且 DAO 是剪贴板 DAO」的那一次调用，
 *   所以用户手动删除、清空剪贴板、其它 DAO 的删除都不受影响；
 * - `setClipboardCount` 走原逻辑后再把 `mCountText` 改写成「（x）」，并 invalidate 刷新。
 *
 * 注意 `ClipboardKeyboard#v(int)` 里的 300 是「单条文本转快捷短语的字数上限」（文案"文本过长,无法移入快捷短语"），
 * 与条数上限无关，这里不动它。
 */
class ClipboardUnlimitedFeature : HookFeature {

    override val id: String = "clipboard-unlimited"

    override val description: String = "剪贴板解除 300 条上限，计数文案改为「（x/∞）」"

    override fun install(scope: HookScope) {
        hookAutoTrim(scope)
        hookCountText(scope)
    }

    /** 1. 拦掉「超过 300 条删最旧」这一步。 */
    private fun hookAutoTrim(scope: HookScope) {
        val daoBase = scope.findClass(DAO_BASE_CLASS) ?: return
        val delete = runCatching { daoBase.getDeclaredMethod("delete", Any::class.java) }.getOrNull()
        if (delete == null) {
            scope.logError("$DAO_BASE_CLASS#delete(Object) 不存在，可能是版本差异")
            return
        }
        scope.hook(delete) { chain ->
            val isClipboardDao = chain.thisObject?.javaClass?.name == CLIPBOARD_DAO_CLASS
            if (isClipboardDao && isAutoTrimCall()) {
                scope.logDebug("已拦截一次自动裁剪（保留全部剪贴板条目）")
                null
            } else {
                chain.proceed()
            }
        }
        scope.log("剪贴板：已接管自动裁剪 $DAO_BASE_CLASS#delete(Object)")
    }

    /** 2. 把「（x/300）」改写成「（x）」。 */
    private fun hookCountText(scope: HookScope) {
        val viewCls = scope.findClass(CANDIDATE_VIEW_CLASS) ?: return
        val setCount = runCatching {
            viewCls.getDeclaredMethod(
                "setClipboardCount",
                java.lang.Integer.TYPE,
                java.lang.Integer.TYPE,
            )
        }.getOrNull()
        if (setCount == null) {
            scope.logError("$CANDIDATE_VIEW_CLASS#setClipboardCount(II) 不存在，可能是版本差异")
            return
        }
        val countField = runCatching { viewCls.getDeclaredField("mCount").apply { isAccessible = true } }.getOrNull()
        val textField = runCatching { viewCls.getDeclaredField("mCountText").apply { isAccessible = true } }.getOrNull()
        if (countField == null || textField == null) {
            scope.logError("$CANDIDATE_VIEW_CLASS 的 mCount / mCountText 字段不存在")
            return
        }

        scope.hook(setCount) { chain ->
            val result = chain.proceed()
            runCatching {
                val self = chain.thisObject
                val count = countField.getInt(self)
                textField.set(self, COUNT_TEXT_FORMAT.format(count))
                (self as? android.view.View)?.invalidate()
            }.onFailure { scope.logError("改写剪贴板计数文案失败", it) }
            result
        }
        scope.log("剪贴板：已接管计数文案 $CANDIDATE_VIEW_CLASS#setClipboardCount(II)")
    }

    /** 判断这次 delete 是不是 `clipboard.y#z` 里的自动裁剪。 */
    private fun isAutoTrimCall(): Boolean = Thread.currentThread().stackTrace.any {
        it.className == TRIM_CLASS && it.methodName == TRIM_METHOD
    }

    private companion object {
        const val DAO_BASE_CLASS = "org.greenrobot.greendao.AbstractDao"
        const val CLIPBOARD_DAO_CLASS = "com.sohu.inputmethod.clipboard.db.ClipboardItemDao"
        const val TRIM_CLASS = "com.sohu.inputmethod.clipboard.y"
        const val TRIM_METHOD = "z"
        const val CANDIDATE_VIEW_CLASS = "com.sohu.inputmethod.clipboard.ClipboardCandidateView"
        const val COUNT_TEXT_FORMAT = "（%d/∞）"
    }
}