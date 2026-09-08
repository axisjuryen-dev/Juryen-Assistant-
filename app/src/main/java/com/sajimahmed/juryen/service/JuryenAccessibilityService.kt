package com.sajimahmed.juryen.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JuryenAccessibilityService : AccessibilityService() {

    companion object {
        // Lets CommandProcessor reach the running instance.
        var instance: JuryenAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used for now - actions are triggered on-demand by CommandProcessor.
    }

    fun tapByDescription(description: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return findNodeByAnyText(root, listOf(description))?.let { tapNode(it); true } ?: false
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findEditableNode(root) ?: return false
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findEditableNode(child)?.let { return it }
        }
        return null
    }

    fun swipeVertical(up: Boolean): Boolean {
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val startY = if (up) dm.heightPixels * 0.75f else dm.heightPixels * 0.25f
        val endY = if (up) dm.heightPixels * 0.25f else dm.heightPixels * 0.75f
        val path = Path().apply { moveTo(x, startY); lineTo(x, endY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 400))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun selectFirstImage(): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findFirstClickableImage(root) ?: return false
        tapNode(node)
        return true
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun uploadFirstGalleryImage() {
        val root = rootInActiveWindow ?: return

        val firstImageNode = findFirstClickableImage(root)
        firstImageNode?.let { tapNode(it) }

        val confirmNode = findNodeByAnyText(root, listOf("Done", "Next", "Upload", "Post", "Share"))
        confirmNode?.let { tapNode(it) }
    }

    private fun findFirstClickableImage(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.contains("ImageView") == true && node.isClickable) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFirstClickableImage(child)
            if (result != null) return result
        }
        return null
    }

    private fun findNodeByAnyText(
        node: AccessibilityNodeInfo,
        options: List<String>
    ): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString() ?: node.contentDescription?.toString()
        if (nodeText != null && options.any { nodeText.equals(it, ignoreCase = true) }) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByAnyText(child, options)
            if (result != null) return result
        }
        return null
    }

    private fun tapNode(node: AccessibilityNodeInfo) {
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val path = Path().apply { moveTo(rect.centerX().toFloat(), rect.centerY().toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun tapByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return findNodeByAnyText(root, listOf(text))?.let { tapNode(it); true } ?: false
    }
}
