package com.drake.brv.sample.mod

import com.drake.brv.annotaion.DragType
import com.drake.brv.annotaion.SwipeType
import com.drake.brv.item.ItemTouchable

data class SwipeDragModel(override var itemSwipe: Int, override var itemDrag: Int) : ItemTouchable {

    val txt: String
        get() {

            var temp = ""

            if (itemDrag == DragType.NONE) {
                temp += "不支持拖拽"
            }

            if (itemDrag == DragType.NONE && itemSwipe == SwipeType.NONE)
                temp += " | "

            if (itemSwipe == SwipeType.NONE)
                temp += "不支持侧滑"

            if (temp.isNotEmpty()) {
                temp = "当前条目$temp"
            }

            return temp
        }
}