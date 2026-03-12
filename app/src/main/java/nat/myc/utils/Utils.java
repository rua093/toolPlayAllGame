package nat.myc.utils;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Log;

import nat.myc.core.NetworkHelper;
import nat.myc.core.ScriptInterpreter;
import nat.myc.core.TouchHelper;
import nat.myc.core.DeviceHelper;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;


import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import nat.myc.config.AppConfig;

public class Utils {

    public static long nowMs() {
        return SystemClock.uptimeMillis();
    }

    public static int getRandom(int min, int max) {
        Random random = new Random();
        return random.nextInt(max - min + 1) + min;
    }

    public static long randBetween(long a, long b) {
        long span = Math.max(1, b - a + 1);
        return a + ThreadLocalRandom.current().nextLong(span);
    }

    public static float randomFloat(float min, float max) {
        return min + new Random().nextFloat() * (max - min);
    }

    public static void sleep(long ms) {
        SystemClock.sleep(ms);
    }

    public static void sleepRandom(int min, int max) {
        SystemClock.sleep(getRandom(min, max));
    }

    public static void checkSpecialScreen(UiDevice d, TouchHelper touch, DeviceHelper mDevice,
                                          NetworkHelper mNetwork, JSONObject cmd, ScriptInterpreter interpreter) {

        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if (bitmap == null) return;

        try {
            // 1. Lấy danh sách điểm màu và lệnh con từ Server gửi về trong cmd
            JSONArray points = cmd.optJSONArray("points");
            JSONArray subCommands = cmd.optJSONArray("sub_commands");

            if (points == null || points.length() == 0) return;

            boolean isSpecialScreen = true;

            for (int i = 0; i < points.length(); i++) {
                JSONObject p = points.getJSONObject(i);
                int[] coords = getRandomCoordsInPercent(d, p.getDouble("px"), p.getDouble("py"));

                int targetColor = (int) Long.decode(p.getString("color")).longValue();

                if (!isPixelMatchFast(bitmap, coords[0], coords[1], targetColor)) {
                    isSpecialScreen = false;
                    break; // Chỉ cần 1 điểm sai là thoát vòng lặp
                }
            }

            // 3. Nếu khớp toàn bộ điểm màu -> Thực thi lệnh con
            if (isSpecialScreen) {
                Log.d(AppConfig.TAG, "Đã khớp màn hình! Đang chạy sub-commands...");
                if (subCommands != null) {
                    for (int j = 0; j < subCommands.length(); j++) {
                        // Thực thi các lệnh con như click, sleep, swipe...
                        interpreter.executeCommand(subCommands.getJSONObject(j));
                    }
                }
            }

        } catch (Exception e) {
            Log.e(AppConfig.TAG, "Lỗi kiểm tra pixel động: " + e.getMessage());
        } finally {
            bitmap.recycle(); // Giải phóng bộ nhớ ngay lập tức
        }
    }

    private static boolean verifyClick(int x, int y, int targetColorHex) {
        // 1. Chụp màn hình trực tiếp vào RAM (Bitmap)
        Bitmap newBitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();

        if (newBitmap == null) {
            Log.e(AppConfig.TAG, "Lỗi: UiAutomation không chụp được màn hình (null).");
            return false; // Coi như chưa bấm được để thử lại
        }

        try {
            // 2. Kiểm tra màu tại tọa độ cũ (x, y) trên ảnh MỚI
            // Lưu ý: Cần đảm bảo tọa độ x, y nằm trong kích thước ảnh mới (đề phòng xoay màn hình)
            if (x >= newBitmap.getWidth() || y >= newBitmap.getHeight()) {
                Log.e(AppConfig.TAG, "Lỗi: Tọa độ kiểm tra nằm ngoài ảnh chụp.");
                return false;
            }

            // Lấy màu tại điểm đó
            int pixelColor = newBitmap.getPixel(x, y);

            // Chuyển sang Hex để so sánh (bỏ Alpha channel để so sánh chính xác hơn)
            String hexColor = String.format("#%06X", (0xFFFFFF & pixelColor));
            String targetHex = String.format("#%06X", (0xFFFFFF & targetColorHex));

            if (hexColor.equals(targetHex)) {
                Log.d(AppConfig.TAG, "Verify: Màu tại (" + x + "," + y + ") vẫn là " + hexColor + " (Chưa đổi).");
                return false; // Chưa ăn
            } else {
                Log.d(AppConfig.TAG, "Verify: Màu tại (" + x + "," + y + ") đã đổi sang " + hexColor + " (Khác " + targetHex + "). OK!");
                return true; // Đã ăn
            }

        } catch (Exception e) {
            Log.e(AppConfig.TAG, "Lỗi khi verify pixel: " + e.getMessage());
            return false;
        } finally {
            // 3. QUAN TRỌNG: Giải phóng bộ nhớ Bitmap ngay lập tức
            // Vì hàm này chạy trong vòng lặp, nếu không recycle sẽ gây tràn bộ nhớ (OOM) rất nhanh.
            if (!newBitmap.isRecycled()) {
                newBitmap.recycle();
            }
        }
    }
    private static boolean isPixelMatchFast(Bitmap bitmap, int x, int y, int targetColor) {
        if (x < 0 || x >= bitmap.getWidth() || y < 0 || y >= bitmap.getHeight()) return false;

        int pixelColor = bitmap.getPixel(x, y);

        // Tách kênh màu R, G, B
        int r1 = (pixelColor >> 16) & 0xFF;
        int g1 = (pixelColor >> 8) & 0xFF;
        int b1 = pixelColor & 0xFF;

        int r2 = (targetColor >> 16) & 0xFF;
        int g2 = (targetColor >> 8) & 0xFF;
        int b2 = targetColor & 0xFF;

        return Math.abs(r1 - r2) < 15 &&
                Math.abs(g1 - g2) < 15 &&
                Math.abs(b1 - b2) < 15;
    }

    public static void findAndClickNoButton(TouchHelper touch, Bitmap bitmap, int targetColorHex) {
        int startY = 246;
        int endY = 2254;
        int posX = 415; // Tọa độ X cố định để quét

        // Cấu hình bước nhảy
        int currentScanStep = 100; // Bước nhảy ban đầu (quét thô)
        int minStep = 5;           // Bước nhảy nhỏ nhất (quét tinh)

        Log.d(AppConfig.TAG, ">>> Bắt đầu thuật toán tìm nút NO (Chế độ: Multi-Click & Scan)...");

        // VÒNG LẶP 1: Giảm dần bước nhảy nếu chưa tìm thấy
        while (currentScanStep >= minStep) {

            boolean isTrackingButton = false; // Cờ đánh dấu: Đang trong vùng nút
            int clickCount = 0; // Đếm số lần đã bấm

            Log.d(AppConfig.TAG, ">>> Đang quét dải Y với step: " + currentScanStep);

            // VÒNG LẶP 2: Quét dọc trục Y
            for (int currentY = startY; currentY <= endY; currentY += currentScanStep) {

                // Kiểm tra màu tại điểm chính và điểm phụ
                boolean matchMain = isPixelMatchFast(bitmap, posX, currentY, targetColorHex);
                boolean matchSide = isPixelMatchFast(bitmap, posX + 10, currentY, targetColorHex);

                if (matchMain || matchSide) {
                    // TRƯỜNG HỢP 1: TRÚNG MÀU
                    isTrackingButton = true; // Đánh dấu là đã bắt được nút
                    clickCount++;

                    Log.d(AppConfig.TAG, ">>> [HIT] Trúng màu tại Y=" + currentY + ". Bấm lần " + clickCount);

                    // Thực hiện bấm ngay
                    touch.humanTap(posX, currentY);
                    Log.d(AppConfig.TAG, ">>> Đang đợi 3s để verify...");
                    Utils.sleep(3000);
                    boolean isClickSuccessful = verifyClick(posX, currentY, targetColorHex);
                    if (isClickSuccessful) {
                        Log.d(AppConfig.TAG, ">>> [SUCCESS] Verify thành công. Thoát.");
                        return;
                    } else {
                        Log.d(AppConfig.TAG, ">>> [FAIL] Verify thất bại. Thử lại...");
                    }

                    // Delay cực ngắn để tránh spam quá nhanh gây lag máy (tùy chỉnh)
                    Utils.sleep(50);
                } else {
                    // TRƯỜNG HỢP 2: KHÔNG TRÚNG MÀU
                    if (isTrackingButton) {
                        // Nếu trước đó đang tracking (đang bấm) mà giờ hết màu
                        // => Nghĩa là đã đi qua hết cái nút rồi (xuống phần rìa dưới)
                        Log.d(AppConfig.TAG, ">>> [FINISH] Đã đi qua hết vùng nút (Hết màu). Tổng click: " + clickCount + ". Thoát hàm.");
                        return; // <--- THOÁT HOÀN TOÀN
                    }

                    // Nếu chưa tìm thấy gì (isTrackingButton == false) thì cứ quét tiếp...
                }
            }

            // Xử lý trường hợp đặc biệt: Quét hết màn hình mà đang bấm dở (nút nằm sát đáy màn hình)
            if (isTrackingButton) {
                Log.d(AppConfig.TAG, ">>> [FINISH] Đã quét đến cuối màn hình. Tổng click: " + clickCount);
                return;
            }

            // Nếu chạy hết vòng for mà isTrackingButton vẫn là false => Chưa tìm thấy nút nào
            Log.d(AppConfig.TAG, ">>> Không tìm thấy với step " + currentScanStep + ". Giảm step/2 và quét lại...");
            currentScanStep = currentScanStep / 2;
        }

        Log.e(AppConfig.TAG, ">>> QUÉT THẤT BẠI: Không tìm thấy nút NO sau khi đã quét kỹ nhất.");
    }

    // --- Giữ nguyên phần ZoneSelector bên dưới ---
    public static class ZoneSelector {
        private int[] weights;
        private int[] stepWeights; // Mảng lưu step riêng cho từng zone
        private final Random random = new Random();
        private int baseWeight;

        public ZoneSelector(int n, int baseWeight, int[] stepWeights) {
            this.baseWeight = baseWeight;
            this.stepWeights = stepWeights;
            this.weights = new int[n];
            // Khởi tạo ban đầu tất cả bằng baseWeight
            for (int i = 0; i < n; i++) weights[i] = baseWeight;
        }

        public int selectZone() {
            int totalWeight = 0;
            for (int w : weights) totalWeight += w;

            if (totalWeight <= 0) {
                for (int i = 0; i < weights.length; i++) weights[i] = baseWeight;
                totalWeight = weights.length * baseWeight;
            }

            int r = random.nextInt(totalWeight) + 1;
            int cumulative = 0;
            int selectedIndex = -1;

            for (int i = 0; i < weights.length; i++) {
                cumulative += weights[i];
                if (r <= cumulative) {
                    selectedIndex = i;
                    break;
                }
            }

            // Cập nhật trọng số sau khi chọn
            for (int i = 0; i < weights.length; i++) {
                if (i == selectedIndex) {
                    weights[i] = baseWeight; // Reset vùng vừa chọn
                } else {
                    // Tăng trọng số dựa trên step riêng của từng vùng
                    weights[i] += stepWeights[i];
                }
            }
            return selectedIndex;
        }
    }

    public static long handleBalanceCheck(UiDevice device, NetworkHelper network, long lastCheckTime) {
        long INTERVAL = 2 * 60 * 1000; // 90 phút
        // Để test nhanh, bạn có thể giảm xuống ví dụ: 5 * 60 * 1000 (5 phút)

        if (System.currentTimeMillis() - lastCheckTime < INTERVAL) {
            return lastCheckTime; // Chưa đến giờ, trả về thời gian cũ, không làm gì cả
        }

        Log.d(AppConfig.TAG, ">>> ĐÃ ĐẾN GIỜ KIỂM TRA TIỀN (90p) <<<");

        try {
            // 1. Chuyển sang App Main
            openApp(device, AppConfig.PKG_MAIN, AppConfig.ACT_MAIN);
            sleep(15000); // Đợi load UI App Main

            int currentDiamonds = checkDiamondAmount(device);
            Log.d(AppConfig.TAG, "Số kim cương hiện tại: " + currentDiamonds);

            // 3. Xử lý theo số tiền
//            if (currentDiamonds < 1500) {
//                Log.w(AppConfig.TAG, "Tiền < 1k5 (" + currentDiamonds + ") -> YÊU CẦU RESET.");
//                return SIGNAL_RESET; // Trả về tín hiệu yêu cầu Reset
//            }

            if (currentDiamonds > 3000) {
                Log.d(AppConfig.TAG, "Tiền > 3k (" + currentDiamonds + ") -> RÚT TIỀN.");
                performWithdraw(device);
            } else {
                Log.d(AppConfig.TAG, "Tiền ổn định (" + currentDiamonds + ") -> CHƠI TIẾP.");
            }
            return System.currentTimeMillis();

        } catch (Exception e) {
            Log.e(AppConfig.TAG, "Lỗi trong quá trình check tiền: " + e.getMessage());
            // Nếu lỗi, tạm thời coi như vừa check để tránh loop lỗi liên tục
            return System.currentTimeMillis();
        }
    }
    public static void openApp(UiDevice device, String pkg, String act) {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Intent intent = new Intent();
        intent.setClassName(pkg, act);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 10000);
    }

    public static int checkDiamondAmount(UiDevice device) {
        // Log.d("Utils", ">>> Bắt đầu đọc số kim cương (RAM Mode)...");

        // 1. CHỤP MÀN HÌNH TRỰC TIẾP VÀO BITMAP (RAM)
        Bitmap fullScreen = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();

        if (fullScreen == null) {
            Log.e(AppConfig.TAG, "Lỗi: Không chụp được màn hình vào RAM!");
            return 0;
        }

        int width = fullScreen.getWidth();
        int height = fullScreen.getHeight();

        // 2. Tọa độ cắt ảnh (Đã thay theo toạ độ 352 434 | 797 566)
        int x1 = 352;
        int y1 = 434;
        int x2 = 797;
        int y2 = 566;

        int cropX = x1;                 // 352
        int cropY = y1;                 // 434
        int cropW = x2 - x1;            // 445
        int cropH = y2 - y1;            // 132

        // Safety check: Kiểm tra xem toạ độ có bị lòi ra ngoài màn hình không (tránh crash)
        if (cropX >= width || cropY >= height) {
            Log.e(AppConfig.TAG, "Lỗi: Toạ độ cắt ảnh nằm ngoài màn hình!");
            fullScreen.recycle();
            return 0;
        }
        if (cropX + cropW > width) cropW = width - cropX;
        if (cropY + cropH > height) cropH = height - cropY;

        // 3. Cắt ảnh từ Bitmap gốc
        Bitmap croppedBitmap = Bitmap.createBitmap(fullScreen, cropX, cropY, cropW, cropH);

        // (Tùy chọn) Lưu ảnh CẮT NHỎ ra để debug xem đúng vùng chưa
        // saveBitmap(croppedBitmap, "/sdcard/debug_crop_ram.png");

        // 4. Đọc số
        int amount = readTextFromBitmap(croppedBitmap);

        // 5. Giải phóng bộ nhớ
        fullScreen.recycle();
        // croppedBitmap.recycle(); // ML Kit tự quản lý hoặc GC sẽ dọn dẹp sau

        return amount;
    }

    /**
     * Hàm thực hiện OCR dùng ML Kit (Giữ nguyên logic cũ)
     */
    private static int readTextFromBitmap(Bitmap bitmap) {
        final AtomicInteger resultAmount = new AtomicInteger(0);
        final CountDownLatch latch = new CountDownLatch(1);

        try {
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            InputImage image = InputImage.fromBitmap(bitmap, 0);

            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String text = visionText.getText();
                        // Chỉ giữ lại số
                        String cleanText = text.replaceAll("[^0-9]", "");
                        Log.d(AppConfig.TAG, "OCR Raw: " + text + " | Clean: " + cleanText);

                        if (!cleanText.isEmpty()) {
                            try {
                                resultAmount.set(Integer.parseInt(cleanText));
                            } catch (NumberFormatException e) {
                                resultAmount.set(0);
                            }
                        }
                        latch.countDown();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(AppConfig.TAG, "OCR Lỗi: " + e.getMessage());
                        latch.countDown();
                    });

            latch.await(5, TimeUnit.SECONDS);
            recognizer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultAmount.get();
    }


    // Hàm vỏ: Rút tiền
    public static void performWithdraw(UiDevice device) {
        Log.d(AppConfig.TAG, ">>> BẮT ĐẦU QUY TRÌNH RÚT TIỀN <<<");

        String email = "kanthakmadara9884@outlook.com";
        if (email == null || email.isEmpty()) {
            Log.e(AppConfig.TAG, "Lỗi: Không có Email để rút tiền!");
            return;
        }

        try {
            // 2. Các bước Click menu (Giữ nguyên vì là nút trong Game Unity)
            Log.d(AppConfig.TAG, "Click tab rút tiền (871, 2393)");
            device.click(871, 2393);
            sleep(7000);

            Log.d(AppConfig.TAG, "Click paypal (332, 1489)");
            device.click(332, 1489);
            sleep(2000);

            Log.d(AppConfig.TAG, "Click ô input (526, 1600)");
            device.click(526, 1600);
            sleep(2000);

            // ================= NHẬP EMAIL LẦN 1 =================
            Log.d(AppConfig.TAG, "--- Nhập Email lần 1 ---");

            // Click vào vị trí ô nhập để kích hoạt EditText
            device.click(624, 1216);
            sleep(2000); // Đợi popup hiện lên

            // Gọi hàm xử lý nhập liệu thông minh
            handleInputText(device, email);


            // ================= NHẬP EMAIL LẦN 2 (Xác thực) =================
            Log.d(AppConfig.TAG, "--- Nhập Email lần 2 ---");

            // Click vào vị trí ô nhập thứ 2
            device.click(560, 1519);
            sleep(2000); // Đợi popup hiện lên

            // Gọi hàm xử lý nhập liệu thông minh
            handleInputText(device, email);


            // ================= XÁC NHẬN CUỐI CÙNG =================
            Log.d(AppConfig.TAG, "Click Confirm (733, 1930)");
            device.click(733, 1930);

            Log.d(AppConfig.TAG, ">>> HOÀN TẤT RÚT TIỀN <<<");
            sleep(5000); // Đợi UI phản hồi

        } catch (Exception e) {
            Log.e(AppConfig.TAG, "Lỗi process rút tiền: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private static void handleInputText(UiDevice device, String text) {
        try {
            // Tìm đối tượng EditText trên màn hình
            // (Thường game Unity khi nhập liệu sẽ đè 1 lớp native Android lên trên)
            UiObject inputField = device.findObject(new UiSelector().className("android.widget.EditText"));

            // Đợi tối đa 5 giây cho EditText xuất hiện
            if (inputField.waitForExists(5000)) {
                Log.d(AppConfig.TAG, ">>> Tìm thấy EditText, đang nhập: " + text);

                // 1. Xóa trắng ô nhập cũ
                inputField.clearTextField();

                // 2. Điền text mới
                inputField.setText(text);

                // 3. Đợi chút cho chắc ăn
                sleep(1000);

                // 4. Bấm Enter trên bàn phím để xác nhận (và đóng bàn phím)
                device.pressEnter();
                Log.d(AppConfig.TAG, "Đã nhấn Enter, đợi bàn phím hạ...");

                // 5. Đợi bàn phím thụt xuống để không che nút Confirm
                sleep(3000);
            } else {
                Log.e(AppConfig.TAG, "Lỗi: Không tìm thấy ô nhập liệu (EditText)!");
            }
        } catch (Exception e) {
            Log.e(AppConfig.TAG, "Lỗi thao tác nhập liệu: " + e.getMessage());
        }
    }

    // Hàm lấy tọa độ ngẫu nhiên trong một Box %
    public static int[] getRandomCoordsInPercent(UiDevice d, double... percents) {
        int w = d.getDisplayWidth();
        int h = d.getDisplayHeight();

        // TRƯỜNG HỢP 1: Truyền vào 4 tham số -> Xử lý theo Box (Vùng)
        if (percents.length == 4) {
            int realX1 = (int) (w * (percents[0] / 100.0));
            int realY1 = (int) (h * (percents[1] / 100.0));
            int realX2 = (int) (w * (percents[2] / 100.0));
            int realY2 = (int) (h * (percents[3] / 100.0));

            int finalX = Utils.getRandom(realX1,realX2);
            int finalY = Utils.getRandom(realY1,realY2);

            return new int[]{finalX, finalY};
        }
        // TRƯỜNG HỢP 2: Truyền vào 2 tham số -> Xử lý theo Point (Điểm)
        else if (percents.length == 2) {
            int realX = (int) (w * (percents[0] / 100.0));
            int realY = (int) (h * (percents[1] / 100.0));


            return new int[]{realX, realY};
        }
        // TRƯỜNG HỢP LỖI: Truyền thiếu hoặc thừa số
        else {
            throw new IllegalArgumentException("Hàm yêu cầu truyền vào 2 (Point) hoặc 4 (Box) tham số phần trăm!");
        }
    }

}