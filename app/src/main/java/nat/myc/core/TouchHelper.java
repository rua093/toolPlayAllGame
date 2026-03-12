package nat.myc.core;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.res.Resources;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.uiautomator.UiDevice;
import java.util.concurrent.ThreadLocalRandom;

import nat.myc.config.AppConfig;

public class TouchHelper {
    private static final String TAG = "TouchHelper";
    private final Instrumentation instrumentation;
    private final UiDevice device;
    private final UiAutomation uiAutomation;

    public TouchHelper(Instrumentation instrumentation, UiDevice device) {
        this.instrumentation = instrumentation;
        this.device = device;
        this.uiAutomation = instrumentation.getUiAutomation();
    }

    public boolean humanTap(float x, float y) {
        try {
            // ---- 1) Random hoá vị trí trong bán kính 1–3dp
            float jitterDp = randomFloat(1f, 3.1f);
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            float dx = (float) (Math.cos(angle) * dpToPx(jitterDp));
            float dy = (float) (Math.sin(angle) * dpToPx(jitterDp));
            float x0 = x + dx;
            float y0 = y + dy;

            // ---- 2) Các tham số “giống người”
            int holdMs = ThreadLocalRandom.current().nextInt(60, 161);   // 60–160ms
            int preMs = ThreadLocalRandom.current().nextInt(6, 15);      // DOWN→MOVE
            int postMs = ThreadLocalRandom.current().nextInt(150, 351);  // sau UP chờ UI
            float size = randomFloat(0.05f, 0.12f);

            float slopPx = ViewConfiguration.get(instrumentation.getTargetContext()).getScaledTouchSlop();
            float microMove = Math.min(dpToPx(0.5f) + ThreadLocalRandom.current().nextFloat() * dpToPx(0.5f),
                    Math.max(1f, slopPx * 0.3f)); // < touch slop

            long downTime = SystemClock.uptimeMillis();

            // ---- 3) ACTION_DOWN (áp lực tăng nhẹ)
            MotionEvent evDown = obtainTouch(
                    MotionEvent.ACTION_DOWN, downTime, SystemClock.uptimeMillis(),
                    x0, y0, /*pressure=*/0.6f, size
            );
            uiAutomation.injectInputEvent(evDown, true);
            evDown.recycle();

            // ---- 4) (tuỳ chọn) 1 MOVE rất nhỏ sau preMs
            SystemClock.sleep(preMs);
            float x1 = x0 + (ThreadLocalRandom.current().nextBoolean() ? microMove : -microMove);
            float y1 = y0 + (ThreadLocalRandom.current().nextBoolean() ? microMove : -microMove);
            MotionEvent evMove = obtainTouch(
                    MotionEvent.ACTION_MOVE, downTime, SystemClock.uptimeMillis(),
                    x1, y1, /*pressure=*/0.7f, size
            );
            uiAutomation.injectInputEvent(evMove, true);
            evMove.recycle();

            // ---- 5) Giữ một khoảng tự nhiên
            SystemClock.sleep(holdMs);

            // ---- 6) ACTION_UP (áp lực về 0)
            MotionEvent evUp = obtainTouch(
                    MotionEvent.ACTION_UP, downTime, SystemClock.uptimeMillis(),
                    x1, y1, /*pressure=*/0f, size
            );
            uiAutomation.injectInputEvent(evUp, true);
            evUp.recycle();

            // ---- 7) Đợi UI phản hồi tự nhiên
            SystemClock.sleep(postMs);
            return true;
        } catch (Exception e) {
            // Log nếu cần
            return false;
        }
    }

    /** Nhấn-giữ-thả như người. */
    public void pressAndRelease(float x, float y, float size, int holdMs) throws Exception {
        long t0 = pressDown(x, y, size);
        SystemClock.sleep(Math.max(holdMs, 25)); // giữ 1 chút
        releaseUp(x, y, size, t0);
    }

    /** Gửi ACTION_DOWN (nhấn). Trả về downTime để dùng cho ACTION_UP. */
    public long pressDown(float x, float y, float size) throws Exception {
        long downTime = SystemClock.uptimeMillis();
        // Logic cũ: buildMotionEvent
        MotionEvent down = buildMotionEvent(MotionEvent.ACTION_DOWN, downTime, x, y, /*pressure=*/randomFloat(0.7f, 0.9f), size);
        // Log.d(TAG, "pressDown tại (" + x + "," + y + "), size=" + size + ", downTime=" + downTime);
        try {
            uiAutomation.injectInputEvent(down, true);
        } finally {
            down.recycle();
        }
        return downTime;
    }

    /** Gửi ACTION_UP (thả), sử dụng cùng downTime với lần nhấn. */
    public void releaseUp(float x, float y, float size, long downTime) throws Exception {
        MotionEvent up = buildMotionEvent(MotionEvent.ACTION_UP, downTime, x, y, /*pressure=*/0.0f, size);
        // Log.d(TAG, "releaseUp tại (" + x + "," + y + "), size=" + size + ", downTime=" + downTime);
        try {
            uiAutomation.injectInputEvent(up, true);
        } finally {
            up.recycle();
        }
    }

    /** Swipe up từ ~80%H -> ~30%H. */
    public void swipeUp(UiDevice d) {
        int w = device.getDisplayWidth();
        int h = device.getDisplayHeight();
        int startX = w / 2;
        int startY = (int) (h * 0.80);
        int endX = w / 2;
        int endY = (int) (h * 0.10);
        Log.d(AppConfig.TAG, "Swipe up: (" + startX + "," + startY + ") -> (" + endX + "," + endY + ")");
        device.swipe(startX, startY, endX, endY, /*steps*/15);
    }


    private MotionEvent obtainTouch(int action, long downTime, long eventTime,
                                    float x, float y, float pressure, float size) {
        MotionEvent.PointerProperties[] pp = new MotionEvent.PointerProperties[1];
        MotionEvent.PointerProperties p0 = new MotionEvent.PointerProperties();
        p0.id = 0;
        p0.toolType = MotionEvent.TOOL_TYPE_FINGER;
        pp[0] = p0;

        MotionEvent.PointerCoords[] pc = new MotionEvent.PointerCoords[1];
        MotionEvent.PointerCoords c0 = new MotionEvent.PointerCoords();
        c0.x = x;
        c0.y = y;
        c0.pressure = pressure;
        c0.size = size;
        pc[0] = c0;

        return MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                1,          // pointerCount
                pp,
                pc,
                0,          // metaState
                0,          // buttonState
                1.0f,       // xPrecision
                1.0f,       // yPrecision
                0,          // deviceId
                0,          // edgeFlags
                InputDevice.SOURCE_TOUCHSCREEN, // source
                0           // flags
        );
    }

    private MotionEvent buildMotionEvent(int action, long downTime,
                                         float x, float y,
                                         float pressure, float size) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[1];
        MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
        pp.id = 0;
        pp.toolType = MotionEvent.TOOL_TYPE_FINGER;
        props[0] = pp;

        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
        MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
        pc.x = x;
        pc.y = y;
        pc.pressure = pressure;
        pc.size = size;
        pc.orientation = 0f;
        coords[0] = pc;

        long eventTime = SystemClock.uptimeMillis();

        return MotionEvent.obtain(
                downTime, eventTime, action,
                /*pointerCount*/1, props, coords,
                /*metaState*/0, /*buttonState*/0,
                /*xPrecision*/1f, /*yPrecision*/1f,
                /*deviceId*/0, /*edgeFlags*/0,
                InputDevice.SOURCE_TOUCHSCREEN, /*flags*/0
        );
    }

    private float dpToPx(float dp) {
        float density = ApplicationProvider
                .getApplicationContext().getResources().getDisplayMetrics().density;
        return dp * density;
    }
    public float randomFloat(float min, float max) {
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }
    @SuppressWarnings("unused")
    private int dp(float dps) {
        DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
        return (int) (dps * dm.density + 0.5f);
    }
}