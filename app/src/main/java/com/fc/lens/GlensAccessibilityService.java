package com.fc.lens;
import com.fc.lens.overlay.GlensSession;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

public class GlensAccessibilityService extends AccessibilityService {

    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN;
        info.flags = AccessibilityServiceInfo.DEFAULT;
        info.packageNames = new String[]{"com.fc.lens"};
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Cari teks yang bisa di-copy
        AccessibilityNodeInfo nodeInfo = event.getSource();
        if (nodeInfo != null) {
            List<AccessibilityNodeInfo> clickableNodes = findClickableNodes(nodeInfo);
            for (AccessibilityNodeInfo node : clickableNodes) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                String text = node.getText() != null ? node.getText().toString() : "";
                if (!text.isEmpty()) {
                    // Kirim teks + koordinat ke GlensSession
                    GlensSession.sendTextData(text, bounds);
                }
            }
        }
    }

    private List<AccessibilityNodeInfo> findClickableNodes(AccessibilityNodeInfo node) {
        // Implementasi untuk cari node yang bisa diklik
        // (di sini hanya contoh sederhana)
        return node.findAccessibilityNodeInfosByViewId("android:id/text1");
    }

    @Override
    public void onInterrupt() {
        Toast.makeText(this, "Accessibility service dihentikan", Toast.LENGTH_SHORT).show();
    }
}
