package nat.myc.utils;

import android.app.Instrumentation;
import android.graphics.Rect;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class DumpScreen {

    private static final String TAG = "DUMP_UI";

    // ====== TIME WINDOW ======
    private static final long TIME_WINDOW_MS = 2_000; // 2 giây

    // ====== PATTERNS ======
    private static final Pattern CLASS_PATTERN =
            Pattern.compile(".*(Image(View)?|ImageButton|Button|View|Image|ImageView|TextView)$");

    private static final Pattern AD_RES_PATTERN =
            Pattern.compile(".*(close|skip|dismiss|interstitial|ad_.*|btn_.*|inmobi|applovin).*",
                    Pattern.CASE_INSENSITIVE);

    private static final List<String> CTA_TEXTS = Arrays.asList(
            "see next",
            "stay and continue",
            "continue"
    );

    // ====== DATA ======
    private static class Hit {
        String reason;
        int depth;
        Rect bounds;
        long foundAtMs;
        String info;
    }

    private final List<Hit> hits = new ArrayList<>();
    private final List<UiObject2> rootsCache = new ArrayList<>();

    private long startTime;

    // ====== TEST ======
    @Test
    public void dumpCurrentScreen() {

        startTime = System.currentTimeMillis();

        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        UiDevice device = UiDevice.getInstance(inst);

        List<UiObject2> roots = device.findObjects(By.depth(0));

        if (roots == null || roots.isEmpty()) {
            Log.e(TAG, "❌ Không tìm thấy UI object nào");
            return;
        }

        rootsCache.addAll(roots);

        Log.d(TAG, "==== SCAN HIT IN " + TIME_WINDOW_MS + " ms ====");

        // 1️⃣ QUÉT + THU HIT TRONG 2 GIÂY
        for (UiObject2 root : roots) {
            traverseForHit(root, 0);
        }

        long scanTime = System.currentTimeMillis() - startTime;

        // 2️⃣ IN KẾT QUẢ HIT
        Log.d(TAG, "==== HIT RESULT (TIME-LIMITED) ====");
        Log.d(TAG, "Total hits = " + hits.size());
        Log.d(TAG, "Scan time = " + scanTime + " ms");

        for (Hit h : hits) {
            Log.d(TAG,
                    "Hit[" + h.reason + "] " +
                            "depth=" + h.depth +
                            ", bounds=" + h.bounds +
                            ", foundAt=" + h.foundAtMs + "ms" +
                            ", info=" + h.info
            );
        }

        // 3️⃣ LOG CÂY UI SAU CÙNG
        Log.d(TAG, "==== UI TREE (POST SCAN) ====");
        for (UiObject2 root : rootsCache) {
            dumpTree(root, 0);
        }
        Log.d(TAG, "==== UI TREE END ====");
    }

    // ====== HIT SCAN (TIME-LIMITED) ======
    private void traverseForHit(UiObject2 node, int depth) {
        if (node == null) return;

        long elapsed = System.currentTimeMillis() - startTime;

        // quá 2 giây → không detect nữa
        if (elapsed > TIME_WINDOW_MS) return;

        String className = node.getClassName();
        String resId = node.getResourceName();
        String text = node.getText();

        if (className != null && CLASS_PATTERN.matcher(className).matches()) {
            record("CLASS", node, depth, elapsed, className);
        }

        if (resId != null && AD_RES_PATTERN.matcher(resId).matches()) {
            record("RES_ID", node, depth, elapsed, resId);
        }

        if (text != null) {
            String lower = text.toLowerCase();
            for (String cta : CTA_TEXTS) {
                if (lower.contains(cta)) {
                    record("TEXT", node, depth, elapsed, text);
                    break;
                }
            }
        }

        for (UiObject2 child : node.getChildren()) {
            traverseForHit(child, depth + 1);
        }
    }

    // ====== UI TREE LOGGER ======
    private void dumpTree(UiObject2 node, int depth) {
        if (node == null) return;

        String indent = "  ".repeat(depth);

        Log.d(TAG, indent +
                "Node[" + depth + "] " +
                "Class=" + node.getClassName() +
                ", ResId=" + node.getResourceName() +
                ", Text=" + node.getText() +
                ", Clickable=" + node.isClickable() +
                ", Bounds=" + node.getVisibleBounds()
        );

        for (UiObject2 child : node.getChildren()) {
            dumpTree(child, depth + 1);
        }
    }

    private void record(String reason, UiObject2 node, int depth, long timeMs, String info) {
        Hit h = new Hit();
        h.reason = reason;
        h.depth = depth;
        h.bounds = node.getVisibleBounds();
        h.foundAtMs = timeMs;
        h.info = info;
        hits.add(h);
    }
}
