/*
 * Copyright (C) 2018, Umbrella CompanyLimited All rights reserved.
 * Project：BRV
 * Author：Drake
 * Date：5/5/20 9:12 PM
 */

package com.drake.brv

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.IdRes
import androidx.recyclerview.widget.RecyclerView
import com.drake.brv.listener.OnMultiStateListener
import com.drake.statelayout.StateConfig
import com.drake.statelayout.StateConfig.errorLayout
import com.drake.statelayout.StateLayout
import com.scwang.smart.refresh.layout.SmartRefreshLayout
import com.scwang.smart.refresh.layout.api.RefreshComponent
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.constant.RefreshState
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener


/**
 * 扩展SmartRefreshLayout
 *
 * 功能:
 * - 下拉刷新
 * - 上拉加载
 * - 分页加载
 * - 添加数据
 * - 缺省状态页
 */
@Suppress("UNUSED_PARAMETER")
open class PageRefreshLayout : SmartRefreshLayout, OnRefreshLoadMoreListener {


    var emptyLayout = View.NO_ID
        set(value) {
            field = value
            state?.emptyLayout = value
        }
    var errorLayout = View.NO_ID
        set(value) {
            field = value
            state?.errorLayout = value
        }
    var loadingLayout = View.NO_ID
        set(value) {
            field = value
            state?.loadingLayout = value
        }

    var index = startIndex // 分页索引
    var loaded = false // 已加载, 已加载后将无法显示错误页面

    companion object {
        var startIndex = 1
    }

    var stateEnabled = true // 启用缺省页
        set(value) {

            if (!value && !mEnableLoadMore) {
                setEnableLoadMore(value)
            }

            if (finishInflate) {
                if (value && state == null) {
                    replaceStateLayout()
                } else if (!value) {
                    state?.showContent()
                }
            }

            field = value
        }


    private var stateChanged = false
    private var finishInflate = false
    private var trigger = false
    private var hasMore = true
    private var adapter: BindingAdapter? = null
    private var autoEnabledLoadMoreState = false
    private var contentView: View? = null
    private var state: StateLayout? = null
    private var onRefresh: (PageRefreshLayout.() -> Unit)? = null
    private var onLoadMore: (PageRefreshLayout.() -> Unit)? = null

    // <editor-fold desc="构造函数">

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.PageRefreshLayout)

        try {

            stateEnabled =
                attributes.getBoolean(R.styleable.PageRefreshLayout_stateEnabled, stateEnabled)

            mEnableLoadMoreWhenContentNotFull = false
            mEnableLoadMoreWhenContentNotFull = attributes.getBoolean(
                R.styleable.SmartRefreshLayout_srlEnableLoadMoreWhenContentNotFull,
                mEnableLoadMoreWhenContentNotFull
            )

            emptyLayout =
                attributes.getResourceId(R.styleable.PageRefreshLayout_empty_layout, View.NO_ID)
            errorLayout =
                attributes.getResourceId(R.styleable.PageRefreshLayout_error_layout, View.NO_ID)
            loadingLayout =
                attributes.getResourceId(R.styleable.PageRefreshLayout_loading_layout, View.NO_ID)
        } finally {
            attributes.recycle()
        }
    }


    override fun onFinishInflate() {
        super.onFinishInflate()
        init()
        finishInflate = true
    }


    internal fun init() {

        setOnRefreshLoadMoreListener(this)
        autoEnabledLoadMoreState = mEnableLoadMore

        if (autoEnabledLoadMoreState) {
            setEnableLoadMore(false)
        }

        if (contentView == null) {
            for (i in 0 until childCount) {
                val view = getChildAt(i)
                if (view !is RefreshComponent) {
                    contentView = view
                    break
                }
            }
        } else return

        if (stateEnabled) {
            replaceStateLayout()
        }
    }

    private fun replaceStateLayout() {

        if (StateConfig.errorLayout == View.NO_ID && errorLayout == View.NO_ID) {
            stateEnabled = false
            return
        }

        state = StateLayout(context)

        state?.apply {
            this@PageRefreshLayout.removeView(contentView)
            addView(contentView)
            state!!.setContentView(contentView!!)
            setRefreshContent(this)

            emptyLayout = emptyLayout
            errorLayout = errorLayout
            loadingLayout = loadingLayout

            onRefresh {
                setEnableRefresh(false)
                notifyStateChanged(RefreshState.Refreshing)
                onRefresh(this@PageRefreshLayout)
            }
        }
    }

    // </editor-fold>


    // <editor-fold desc="刷新数据">

    /**
     * 触发刷新 (不包含下拉动画)
     */
    fun refresh() {
        notifyStateChanged(RefreshState.Refreshing)
        onRefresh(this)
    }


    /**
     * 设置[errorLayout]中的视图点击后会执行[StateLayout.showLoading]
     * 并且500ms内防重复点击
     */
    fun setRetryIds(@IdRes vararg ids: Int): PageRefreshLayout {
        state?.setRetryIds(*ids)
        return this
    }

    /**
     * 直接接受数据, 自动判断当前属于下拉刷新还是上拉加载更多
     *
     * @param data 数据集
     * @param bindingAdapter 指定你想要添加数据的[BindingAdapter], 如果RecyclerView属于PageRefreshLayout的直接子View则不需要传入此参数
     * @param hasMore 在函数参数中返回布尔类型来判断是否存在更多页
     */
    fun addData(
        data: List<Any?>?,
        bindingAdapter: BindingAdapter? = null,
        hasMore: BindingAdapter.() -> Boolean = { false }
    ) {

        if (contentView == null) {
            throw UnsupportedOperationException("PageRefreshLayout require least one child view")
        }

        adapter =
            bindingAdapter ?: adapter ?: (contentView as RecyclerView).adapter as? BindingAdapter

        if (adapter == null) {
            throw UnsupportedOperationException("PageRefreshLayout require direct child is RecyclerView or specify BindingAdapter")
        }

        val isRefreshState = getState() == RefreshState.Refreshing

        adapter?.let {
            if (isRefreshState) {
                it.models = data

                if (data.isNullOrEmpty()) {
                    showEmpty()
                    return
                }

            } else {
                it.addModels(data)
            }
        }

        this.hasMore = adapter!!.hasMore()
        index += 1

        if (isRefreshState) showContent() else finish(true)
    }

    // </editor-fold>


    // <editor-fold desc="生命周期">

    fun onError(block: View.(Any?) -> Unit): PageRefreshLayout {
        state?.onError(block)
        return this
    }

    fun onEmpty(block: View.(Any?) -> Unit): PageRefreshLayout {
        state?.onEmpty(block)
        return this
    }

    fun onLoading(block: View.(Any?) -> Unit): PageRefreshLayout {
        state?.onLoading(block)
        return this
    }

    fun onRefresh(block: PageRefreshLayout.() -> Unit): PageRefreshLayout {
        onRefresh = block
        return this
    }

    fun onLoadMore(block: PageRefreshLayout.() -> Unit): PageRefreshLayout {
        onLoadMore = block
        return this
    }


    /**
     * 监听多种状态, 不会拦截已有的刷新(onRefresh)和加载生命周期(onLoadMore)
     * @param onMultiStateListener OnMultiStateListener
     * @return PageRefreshLayout
     */
    fun setOnMultiStateListener(onMultiStateListener: OnMultiStateListener): PageRefreshLayout {
        setOnMultiListener(onMultiStateListener)
        return this
    }

    override fun autoRefresh(): Boolean {
        return super.autoRefresh()
    }

    // </editor-fold>


    /**
     * 关闭下拉加载|上拉刷新
     * @param success Boolean 刷新结果 true: 成功 false: 失败
     */
    fun finish(success: Boolean = true) {

        if (trigger) {
            stateChanged = true
        }

        val currentState = getState()

        if (success) {
            loaded = true
        }

        if (currentState == RefreshState.Refreshing) {
            finishRefresh(success)
            setEnableRefresh(true)
            setNoMoreData(!hasMore)

            if (!mEnableLoadMoreWhenContentNotFull) {
                setEnableLoadMoreWhenContentNotFull(hasMore)
            }
        } else {
            if (hasMore) finishLoadMore(success) else finishLoadMoreWithNoMoreData()
        }


        if (currentState != RefreshState.Loading && autoEnabledLoadMoreState) {
            setEnableLoadMore(success)
        }
    }

    /**
     * 用于网络请求的触发器
     */
    fun trigger(): Boolean {
        trigger = !trigger
        if (!trigger) stateChanged = false
        return trigger
    }


    // <editor-fold desc="缺省页">


    fun showEmpty(tag: Any? = null) {
        if (stateEnabled) state?.showEmpty(tag)
        finish()
    }


    /**
     * 加载成功以后不会再显示错误页面, 除非指定强制显示
     *
     * @param force 强制显示错误页面
     */
    fun showError(tag: Any? = null, force: Boolean = false) {
        if (stateEnabled && (force || !loaded)) {
            loaded = false
            state?.showError(tag)
        }
        finish(false)
    }

    fun showLoading(tag: Any? = null, refresh: Boolean = true) {
        if (stateEnabled) state?.showLoading(tag, refresh)
    }

    fun showContent() {
        if (trigger && stateChanged) return

        if (stateEnabled) state?.showContent()
        finish()
    }

    // </editor-fold>

    override fun onLoadMore(refreshLayout: RefreshLayout) {
        if (onLoadMore == null) {
            onRefresh?.invoke(this)
        } else {
            onLoadMore?.invoke(this)
        }
    }

    override fun onRefresh(refreshLayout: RefreshLayout) {
        index = startIndex

        setNoMoreData(false)
        onRefresh?.invoke(this)
    }

}
