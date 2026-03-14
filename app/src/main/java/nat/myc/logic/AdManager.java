package nat.myc.logic;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import nat.myc.config.AppConfig;
import nat.myc.core.DeviceHelper;
import nat.myc.core.NetworkHelper;
import nat.myc.core.TouchHelper;
import nat.myc.utils.Utils;

public class AdManager {
    private final UiDevice d;
    private final NetworkHelper mNetwork;
    private final TouchHelper mTouch;
    private final DeviceHelper mDevice;

    // Cache tọa độ nút tắt quảng cáo (Inner Class)
    private final HandlePoint handlePoint = new HandlePoint();

    public AdManager(UiDevice device, NetworkHelper network, TouchHelper touch, DeviceHelper deviceHelper) {
        this.d = device;
        this.mNetwork = network;
        this.mTouch = touch;
        this.mDevice = deviceHelper;
    }

    /**
     * Hàm xử lý quảng cáo chính.
     * 
     * @param timeoutMs Thời gian tối đa để thoát quảng cáo.
     * @param session   Đối tượng quản lý phiên (lưu thống kê).
     */
    public void handleAds(boolean myTick, long timeoutMs, GameSession session) throws Exception {
        try {
            session.markStartTime();
            long deadline = SystemClock.uptimeMillis() + timeoutMs;
            Utils.sleep(1000); // Đợi UI ổn định

            // Check nếu bị văng ra ngoài app game
            if (!d.hasObject(By.pkg(AppConfig.PKG_GAME).depth(0))) {
                Log.d(AppConfig.TAG, "Màn hình nhảy sang trang khác (không phải game)");
                d.pressBack();
            }
            if (mDevice.isObjectVisible(AppConfig.CURRENT_GAME_VIEW_SELECTOR)) {
                Log.d(AppConfig.TAG, ">> Đã về GameView. Pass Ads.");
                return;
            }
            if (myTick) {
                Log.d(AppConfig.TAG, "Đợi lần đầu");
                Utils.sleep(Utils.getRandom(20000, 35000));
                Log.d(AppConfig.TAG, "Đợi xong");
            }
            Log.d(AppConfig.TAG, "--- Bắt đầu quy trình xử lý Ads ---");
            while (SystemClock.uptimeMillis() < deadline) {
                // 1. Kiểm tra thành công: Đã thấy GameView
                if (mDevice.isObjectVisible(AppConfig.CURRENT_GAME_VIEW_SELECTOR)) {
                    Log.d(AppConfig.TAG, ">> Đã về GameView. Pass Ads.");
                    return;
                }
                clickXYAndReturnIfNeeded(1352, 142);
                Utils.sleep(1000);
                if (mDevice.isObjectVisible(AppConfig.CURRENT_GAME_VIEW_SELECTOR)) {
                    Log.d(AppConfig.TAG, ">> Đã về GameView. Pass Ads.");
                    return;
                }
                // 2. Thử bấm các điểm lịch sử (Cache)
                Log.d(AppConfig.TAG, "Thử bấm các điểm trong Cache...");
                if (handlePoint.loopRightPoint(d)) {
                    return;
                }
                // 3. Tìm các nút dựa trên hình ảnh (Pattern Class từ Server)
                Log.d(AppConfig.TAG, "Quét tìm nút (Image Analysis)...");
                List<Rect> rects = findAdImageBounds(d);
                List<Rect> potentialButtons = new ArrayList<>();

                // Lọc nút có khả năng là Close Button
                for (Rect r : rects) {
                    if (isLikelyCloseButton(r, d))
                        potentialButtons.add(r);
                }
                Log.d(AppConfig.TAG, "Tìm thấy " + potentialButtons.size() + " nút tiềm năng.");

                // Ưu tiên bấm nút bên phải trước
                potentialButtons.sort((r1, r2) -> Integer.compare(r2.left, r1.left));

                // Thực hiện bấm
                int i = 0;
                for (Rect r : potentialButtons) {
                    int xx = Utils.getRandom(r.centerX() - 5, r.centerX() + 5);
                    int yy = Utils.getRandom(r.centerY() - 5, r.centerY() + 5);
                    Log.d(AppConfig.TAG, "-> Click nút ảnh #" + i++ + " tại: (" + xx + "," + yy + ")");

                    clickXYAndReturnIfNeeded(xx, yy);
                    Utils.sleepRandom(500, 1500);
                    if (mDevice.isObjectVisible(AppConfig.CURRENT_GAME_VIEW_SELECTOR)) {
                        Log.d(AppConfig.TAG, "Thành công! Lưu điểm cache: (" + xx + "," + yy + ")");
                        handlePoint.addPointRight(xx, yy);
                        return;
                    } else {
                        d.pressBack(); // Nếu bấm sai có thể bị dẫn đi link, back lại
                    }
                }

                // 4. Tìm các nút dựa trên Văn bản (Text) từ Server
                Log.d(AppConfig.TAG, "Quét tìm nút theo Text...");
                List<String> texts;
                try {
                    texts = mNetwork.getAdButtonTextsFromServer(AppConfig.PKG_MAIN);
                } catch (Exception e) {
                    Log.e(AppConfig.TAG, "Lỗi lấy text server, dùng fallback.", e);
                    texts = new ArrayList<>(Arrays.asList("See next", "Stay and continue", "Continue", "See Next", "X",
                            "x", "CLOSE", "Close"));
                }

                List<Rect> findText = findByText(d, texts);
                for (Rect r : findText) {
                    Log.d(AppConfig.TAG, "-> Click nút text tại: " + r.centerX() + "," + r.centerY());
                    clickXYAndReturnIfNeeded(Utils.getRandom(r.centerX() - 5, r.centerX() + 5),
                            Utils.getRandom(r.centerY() - 5, r.centerY() + 5));
                    Utils.sleepRandom(300, 500);
                    if (mDevice.isObjectVisible(AppConfig.CURRENT_GAME_VIEW_SELECTOR)) {
                        Log.d(AppConfig.TAG, "Thành công! Bấm nút text.");
                        return;
                    }
                    Log.d(AppConfig.TAG, "Đợi 5s sau khi bấm text...");
                    Utils.sleep(5000);
                }

            }

            // ==========================================
            // XỬ LÝ KHI THẤT BẠI (TIMEOUT)
            // ==========================================
            if (!mDevice.isObjectVisible(AppConfig.CURRENT_GAME_VIEW_SELECTOR)) {
                Log.e(AppConfig.TAG, "!!! Hết giờ xử lý Ads -> FAIL !!!");

                // 1. Chụp ảnh màn hình lỗi
                Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
                File cacheDir = context.getExternalCacheDir();
                if (cacheDir == null)
                    cacheDir = context.getCacheDir();

                File screenshotFile = new File(cacheDir, "screenshot_fail.png");
                boolean success = mDevice.takeSafeScreenshot(screenshotFile);

                if (success) {
                    try {
                        String imgId = mNetwork.uploadScreenshotAndGetId(screenshotFile);
                        session.setImageId(imgId); // Lưu ID ảnh vào session
                        Log.d(AppConfig.TAG, "Đã upload ảnh lỗi, ID = " + imgId);
                    } catch (Exception e) {
                        Log.e(AppConfig.TAG, "Upload screenshot thất bại", e);
                    }
                }

                // 2. Gửi báo cáo lỗi lên server (Giới hạn tối đa 3 lần thử)
                boolean reported = false;
                int retryCount = 0;
                while (!reported && retryCount < 3) {
                    try {
                        session.markEndTime(); // Cập nhật thời gian
                        mNetwork.sendSerialLogToServer(
                                session.getSerial(),
                                session.getRoom_hash(),
                                "error",
                                session.getReportData(),
                                AppConfig.PKG_MAIN);
                        reported = true;
                        Log.d(AppConfig.TAG, "Đã gửi báo cáo lỗi thành công.");
                    } catch (Exception e) {
                        retryCount++;
                        Log.e(AppConfig.TAG, "Gửi log lỗi thất bại lần " + retryCount + ". Thử lại sau 5s...", e);
                        Utils.sleep(5000);
                    }
                }

                // 3. Vòng lặp chờ Admin debug (Giới hạn tối đa 5 phút)
                Log.d(AppConfig.TAG, "Kiểm tra chế độ chờ Admin Debug (Tối đa 5 phút)...");
                long adminWaitStart = System.currentTimeMillis();
                long maxAdminWait = 5 * 60 * 1000; // 5 phút
                int lastAdminStatus = -1; // Dùng để tối ưu log

                while (System.currentTimeMillis() - adminWaitStart < maxAdminWait) {
                    try {
                        int adminStatus = mNetwork.getAdminDebugStatus();
                        if (adminStatus == 0) {
                            Log.d(AppConfig.TAG, "Admin Status = 0 (Đã clear). Tiếp tục chạy.");
                            break;
                        }

                        // Tối ưu log: Chỉ in ra khi status thay đổi để tránh spam log liên tục mỗi 10s
                        if (adminStatus != lastAdminStatus) {
                            Log.d(AppConfig.TAG, "Admin Status = " + adminStatus + ". Đang chờ Admin xử lý...");
                            lastAdminStatus = adminStatus;
                        }

                        Utils.sleep(10000); // Vẫn sleep 10s để check API liên tục
                    } catch (Exception e) {
                        Log.e(AppConfig.TAG, "Lỗi mạng khi check Admin Status: " + e.getMessage());
                        Utils.sleep(10000);
                    }
                }

                // 4. Tự phục hồi: Nếu vượt quá 5 phút mà vẫn chưa thấy GameView, force stop
                // game để reset
                if (!mDevice.isObjectVisible(AppConfig.CURRENT_GAME_VIEW_SELECTOR)) {
                    Log.d(AppConfig.TAG, "Hết thời gian chờ Admin, tiến hành Reset Game...");
                    // Lưu ý: Đảm bảo đối tượng 'd' (UiDevice) đã được khai báo ở scope bên ngoài
                    d.executeShellCommand("am force-stop " + AppConfig.PKG_GAME);
                    Utils.sleep(2000);
                    d.executeShellCommand("monkey -p " + AppConfig.PKG_GAME + " -c android.intent.category.LAUNCHER 1");
                    Utils.sleep(8000); // Chờ game load lại
                }
            }
        } catch (Exception e) {
            Log.e(AppConfig.TAG, "Exception trong handleAds: " + e.getMessage());
            throw e;
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private int clickXYAndReturnIfNeeded(int x, int y) throws Exception {
        mTouch.humanTap(x, y);
        Utils.sleepRandom(1000, 2000);
        // Kiểm tra xem có bị văng khỏi game không
        if (!d.hasObject(By.pkg(AppConfig.PKG_GAME).depth(0))) {
            Utils.sleepRandom(1200, 2000);
            Log.d(AppConfig.TAG, "Bị văng khỏi game -> Press Back (max 3 lần)");
            boolean recovered = false;
            for (int i = 0; i < 3; i++) {
                d.pressBack();
                Utils.sleepRandom(500, 1000);
                if (d.hasObject(By.pkg(AppConfig.PKG_GAME).depth(0))) {
                    recovered = true;
                    break;
                }
            }
            if (!recovered) {
                Log.d(AppConfig.TAG, "Vẫn không thấy game -> Đang mở lại package: " + AppConfig.PKG_GAME);

                // Lệnh khởi chạy App bằng shell
                d.executeShellCommand("monkey -p " + AppConfig.PKG_GAME + " -c android.intent.category.LAUNCHER 1");

                // Đợi game load (khoảng 5-8 giây tùy máy)
                Utils.sleepRandom(5000, 8000);

                if (d.hasObject(By.pkg(AppConfig.PKG_GAME).depth(0))) {
                    return 1; // Đã hồi phục bằng cách mở lại app
                } else {
                    Log.e(AppConfig.TAG, "Không thể mở lại game!");
                    return -1; // Trả về -1 để báo hiệu lỗi nghiêm trọng
                }
            }
            return 1;
        }
        return 0;
    }

    private boolean isLikelyCloseButton(Rect br, UiDevice device) {
        if (br == null)
            return false;
        int w = device.getDisplayWidth();
        int h = device.getDisplayHeight();
        // Nằm trên top band (20%), góc trái/phải, kích thước nhỏ, không sát mép
        boolean inTopBand = br.centerY() < (h * 0.20);
        boolean inLeftCorner = br.centerX() <= (w * 0.20);
        boolean inRightCorner = br.centerX() >= (w * 0.80);
        boolean small = br.width() <= (w * 0.20) && br.height() <= (h * 0.1);
        int edgePad = 0;
        boolean notTooEdge = br.top > edgePad && br.left > edgePad && br.right < (w - edgePad);
        return inTopBand && (inLeftCorner || inRightCorner) && small && notTooEdge;
    }

    private List<Rect> findByText(UiDevice device, List<String> texts) {
        List<Rect> res = new ArrayList<>();
        for (String t : texts) {
            UiObject2 obj = device.findObject(By.text(t));
            if (obj != null) {
                res.add(obj.getVisibleBounds());
            }
        }
        return res;
    }

    private List<Rect> findAdImageBounds(UiDevice device) {
        Pattern classes;
        try {
            classes = mNetwork.getAdClassPatternFromServer(AppConfig.PKG_MAIN);
        } catch (Exception e) {
            classes = Pattern.compile(".*(Image(View)?|ImageButton|Button|View|Image|ImageView|TextView)$");
        }
        device.waitForIdle(2000);
        Set<String> dedup = new LinkedHashSet<>();
        List<Rect> rects = new ArrayList<>();
        long start = System.currentTimeMillis();
        long maxWait = 15_000;
        List<UiObject2> nodes = Collections.emptyList();

        while (System.currentTimeMillis() - start < maxWait) {
            nodes = device.findObjects(By.clazz(classes));
            if (nodes != null && !nodes.isEmpty())
                break;
            Utils.sleep(300);
        }
        if (nodes == null || nodes.isEmpty())
            return rects;
        long start_check = System.currentTimeMillis();
        for (UiObject2 n : nodes) {
            long end_check = System.currentTimeMillis();
            if (end_check - start_check > 1000 * 20) {
                Log.d(AppConfig.TAG, "Hết tìm nổi rồi");
                break;
            }
            try {
                Rect b = n.getVisibleBounds();
                if (b.width() > 0 && b.height() > 0) {
                    String key = b.left + "," + b.top + "," + b.right + "," + b.bottom;
                    if (dedup.add(key)) {
                        rects.add(new Rect(b));
                    }
                }
            } catch (Exception e) {
            }
        }
        return rects;
    }

    // =========================================================================
    // INNER CLASS: Quản lý điểm cache (History)
    // =========================================================================

    private class Point {
        public int x, y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private class HandlePoint {
        private List<Point> pointsResults = new ArrayList<>();

        public boolean loopRightPoint(UiDevice d) throws Exception {
            pointsResults.sort((p1, p2) -> Integer.compare(p2.x, p1.x));
            int i = 0;
            for (Point p : pointsResults) {
                int x = Utils.getRandom(p.x - 5, p.x + 5);
                int y = Utils.getRandom(p.y - 5, p.y + 5);
                Log.d(AppConfig.TAG, "[Cache] Nhấn nút " + i++ + ": " + x + "," + y);
                clickXYAndReturnIfNeeded(x, y);
                Utils.sleep(1000);
                if (mDevice.isObjectVisible(AppConfig.CURRENT_GAME_VIEW_SELECTOR)) {
                    return true;
                }
            }
            return false;
        }

        public boolean addPointRight(int x, int y) {
            long bestD2 = Long.MAX_VALUE;
            Point best = null;
            for (Point p : pointsResults) {
                long dx = (long) x - p.x;
                long dy = (long) y - p.y;
                long d2 = dx * dx + dy * dy;
                // Nếu điểm mới gần điểm cũ (trong vòng 48px) -> Update điểm cũ
                if (d2 < bestD2 && d2 <= 48 * 48) {
                    bestD2 = d2;
                    best = p;
                }
            }
            if (best != null) {
                best.x = (int) Math.round((best.x + x) / 2.0);
                best.y = (int) Math.round((best.y + y) / 2.0);
                return true;
            }
            // Nếu chưa đầy cache -> thêm mới
            if (pointsResults.size() < 5) {
                pointsResults.add(new Point(x, y));
            } else {
                // Đầy cache -> thay thế điểm cuối
                pointsResults.set(pointsResults.size() - 1, new Point(x, y));
            }
            return false;
        }
    }
}