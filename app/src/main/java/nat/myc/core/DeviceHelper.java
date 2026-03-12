package nat.myc.core;

import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.json.JSONObject;

import java.io.File;
import nat.myc.config.AppConfig;
import nat.myc.utils.Utils;

public class DeviceHelper {
    private final UiDevice device;
    public DeviceHelper(UiDevice device) {
        this.device = device;
    }

    /** Chờ package foreground (heuristic đủ dùng cho Unity). */
    public boolean waitForPackage(String pkg, long timeoutMs) {
        long end = SystemClock.uptimeMillis() + timeoutMs;
        int poll = 0;
        while (SystemClock.uptimeMillis() < end) {
            boolean onPkg = device.hasObject(By.pkg(pkg).depth(0));
            Log.d(AppConfig.TAG, "Đợi package '" + pkg + "' foreground... poll=" + (poll++) + " -> " + onPkg);
            if (onPkg) return true;
            Utils.sleep(350);
        }
        return false;
    }

    /** Kiểm tra GameView có hiển thị không (Wait ngắn) */
    public boolean isObjectVisible(JSONObject selectors) {
        try {
            BySelector selector = null;

            // Các thuộc tính phổ biến có thể có trong cây UI
            if (selectors.has("pkg")) selector = (selector == null) ? By.pkg(selectors.getString("pkg")) : selector.pkg(selectors.getString("pkg"));
            if (selectors.has("clazz")) selector = (selector == null) ? By.clazz(selectors.getString("clazz")) : selector.clazz(selectors.getString("clazz"));
            if (selectors.has("text")) selector = (selector == null) ? By.text(selectors.getString("text")) : selector.text(selectors.getString("text"));
            if (selectors.has("desc")) selector = (selector == null) ? By.desc(selectors.getString("desc")) : selector.desc(selectors.getString("desc"));
            if (selectors.has("res")) selector = (selector == null) ? By.res(selectors.getString("res")) : selector.res(selectors.getString("res"));

            // Match tương đối (contains)
            if (selectors.has("text_contains")) selector = (selector == null) ? By.textContains(selectors.getString("text_contains")) : selector.textContains(selectors.getString("text_contains"));

            if (selector == null) return false;

            return device.wait(Until.hasObject(selector), 2000);
        } catch (Exception e) {
            return false;
        }
    }

    public void openApp(String pkg, String act) {
        try {
            // Sử dụng shell command để mở chính xác Activity
            device.executeShellCommand("am start -n " + pkg + "/" + act);
        } catch (Exception e) {
            Log.e("DEVICE", "Lỗi mở app: " + e.getMessage());
        }
    }

    /** Chờ GameView xuất hiện (Wait dài tùy chỉnh) */
    public boolean waitForGameView(long timeoutMs) {
        return device.wait(Until.hasObject(By.clazz("android.webkit.WebView")
                .text("Cash Water: Drink Reminder Android")), timeoutMs);
    }

    /** Chụp ảnh màn hình an toàn */
    public boolean takeSafeScreenshot(@NonNull File destFile) {
        // 1. Lấy thư mục cha của file đích
        File parentDir = destFile.getParentFile();
        // 2. Kiểm tra và tạo thư mục nếu chưa tồn tại
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                Log.e(AppConfig.TAG, "Không tạo được thư mục: " + parentDir.getAbsolutePath());
                return false;
            }
        }
        Log.d(AppConfig.TAG, "Đang lưu screenshot vào: " + destFile.getAbsolutePath());
        // 3. Thực hiện chụp màn hình
        boolean success = device.takeScreenshot(destFile);

        if (!success) {
            Log.e(AppConfig.TAG, "Chụp màn hình thất bại (Có thể do FLAG_SECURE của quảng cáo)");
        } else {
            Log.d(AppConfig.TAG, "Chụp màn hình thành công");
        }

        return success;
    }
}