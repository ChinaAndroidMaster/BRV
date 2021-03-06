package com.drake.brv.sample.mod

import com.drake.brv.item.ItemExpand
import com.drake.brv.item.ItemHover

class GroupModel : ItemExpand, ItemHover {

    override var itemGroupPosition: Int = 0
    override var itemExpand: Boolean = false
    override var itemSublist: List<Any?>? = listOf(Model(), Model(), Model(), Model())
    override var itemHover: Boolean = true
    val title get() = "分组 [ $itemGroupPosition ]"

}