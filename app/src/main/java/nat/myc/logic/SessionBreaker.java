package nat.myc.logic;

import android.util.Log;
import androidx.test.uiautomator.UiDevice;
import java.util.Random;
import nat.myc.config.AppConfig;
import nat.myc.utils.Utils;

public class SessionBreaker {
    private long nextCutoffAt;
    private final Random random = new Random();

    // Danh sách các gói ứng dụng để lướt (Bạn cần đảm bảo máy đã cài các app này)
    // Ví dụ: Chrome, TikTok, Instagram, YouTube, Settings
    private static final String[] ENTERTAINMENT_APPS = {
            "com.google.android.googlequicksearchbox",           // Google Chrome
            "com.ss.android.ugc.trill",     // TikTok (Asia/Global tùy bản)
            "com.instagram.android",        // Instagram
//            "com.google.android.youtube",   // YouTube
            "com.amazon.mShop.android.shopping" // Amazon
    };

    public SessionBreaker() {
        scheduleNextBreak();
    }

    private void scheduleNextBreak() {
        this.nextCutoffAt = Utils.nowMs() + Utils.randBetween(AppConfig.ACTIVE_MIN_MS, AppConfig.ACTIVE_MAX_MS);
    }

    public void maybeTakeBreak(UiDevice d) throws Exception {
        long now = Utils.nowMs();
        if (now < nextCutoffAt) return;

        long breakMs = Utils.randBetween(AppConfig.BREAK_MIN_MS, AppConfig.BREAK_MAX_MS);
        Log.i(AppConfig.TAG, "⏸ Bắt đầu nghỉ " + (breakMs / 1000) + "s...");

        // Random chọn hành động nghỉ:
        // 0-1: Về Home ngủ yên (20%)
        // 2-3: Khóa màn hình (20%)
        // 4-9: Mở app khác lướt (60%)
        int action = random.nextInt(10);

        try {
            d.pressHome();
            Utils.sleep(2000); // Đợi về home ổn định

            if (action <= 1) {
                // Cách 1: Chỉ nằm im ở Home
                Log.i(AppConfig.TAG, " -> Mode: Idle at Home");
                Utils.sleep(breakMs);
            }
            else if (action <= 3) {
                // Cách 2: Khóa màn hình (Giả lập tắt máy đi làm việc khác)
                Log.i(AppConfig.TAG, " -> Mode: Lock Screen");
                performLockScreen(d, breakMs);
            }
            else {
                // Cách 3: Mở App khác lướt
                Log.i(AppConfig.TAG, " -> Mode: Surfing other App");
                performAppSurf(d, breakMs);
            }

        } catch (Throwable t) {
            Log.w(AppConfig.TAG, "Lỗi trong lúc nghỉ: " + t.getMessage());
            // Nếu lỗi thì ngủ nốt thời gian còn lại cho an toàn
            Utils.sleep(5000);
        }

        // Lên lịch ca tiếp theo
        scheduleNextBreak();

        // Ném Exception để reset quy trình
        throw new Exception("Nghỉ ngơi xong");
    }

    // --- LOGIC LƯỚT APP ---
    private void performAppSurf(UiDevice d, long durationMs) {
        long endTime = Utils.nowMs() + durationMs;

        // Chọn random 1 app
        String pkg = ENTERTAINMENT_APPS[random.nextInt(ENTERTAINMENT_APPS.length)];
        Log.i(AppConfig.TAG, " -> Mở app: " + pkg);

        try {
            // Dùng lệnh shell monkey để mở app (cách này hoạt động tốt trên UiAutomator)
            d.executeShellCommand("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1");
            Utils.sleep(5000); // Đợi app load

            int width = d.getDisplayWidth();
            int height = d.getDisplayHeight();

            // Lặp lại việc lướt cho đến khi hết giờ
            while (Utils.nowMs() < endTime) {
                // Random lướt lên hoặc xuống
                boolean swipeUp = random.nextBoolean();

                int startX = width / 2;
                int startY = swipeUp ? (height * 3 / 4) : (height / 4);
                int endY   = swipeUp ? (height / 4) : (height * 3 / 4);

                // Vuốt
                d.swipe(startX, startY, startX, endY, 30); // 20 steps là tốc độ vuốt vừa phải

                // Đọc báo/xem video trong khoảng 2-10 giây ngẫu nhiên
                Utils.sleep(Utils.randBetween(2000, 10000));
            }

            // Hết giờ thì về Home
            d.pressHome();

        } catch (Exception e) {
            Log.e(AppConfig.TAG, "Không mở được app: " + pkg);
        }
    }

    // --- LOGIC KHÓA MÀN HÌNH ---
    private void performLockScreen(UiDevice d, long durationMs) {
        try {
            d.sleep(); // Tắt màn hình
            Utils.sleep(durationMs);
            d.wakeUp(); // Bật màn hình
            Utils.sleep(1000);

            // Nếu có màn hình khóa (Swipe to unlock), cần vuốt lên để mở
            // (Lưu ý: Chỉ hoạt động nếu không có mật khẩu/PIN)
            int w = d.getDisplayWidth();
            int h = d.getDisplayHeight();
            d.swipe(w / 2, h - 100, w / 2, 100, 15);

        } catch (Exception e) {
            Log.e(AppConfig.TAG, "Lỗi lock screen: " + e);
        }
    }
}