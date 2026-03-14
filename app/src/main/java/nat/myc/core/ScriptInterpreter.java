package nat.myc.core;

import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import nat.myc.config.AppConfig;
import nat.myc.logic.AdManager;
import nat.myc.logic.GameSession;
import nat.myc.utils.Utils;

public class ScriptInterpreter {
    private AdManager mAdManager;
    private TouchHelper mTouch;
    private DeviceHelper mDevice;
    private GameSession mSession;

    private NetworkHelper mNetwork;

    private UiDevice d;

    public ScriptInterpreter(AdManager ad, TouchHelper th, DeviceHelper dh, GameSession gs, NetworkHelper nh) {
        this.mAdManager = ad;
        this.mTouch = th;
        this.mDevice = dh;
        this.mSession = gs;
        this.mNetwork = nh; // Gán giá trị
        this.d = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }


    public void executeCommand(JSONObject cmd) throws Exception {
        String type = cmd.optString("type", "unknown");
        switch (type) {
            case "click":
                int x, y;
                if (cmd.has("px1") && cmd.has("py1") && cmd.has("px2") && cmd.has("py2")) {
                    int[] coords = Utils.getRandomCoordsInPercent(d,
                            cmd.getDouble("px1"), cmd.getDouble("py1"),
                            cmd.getDouble("px2"), cmd.getDouble("py2"));
                    x = coords[0];
                    y = coords[1];
                }
                else if (cmd.has("px") && cmd.has("py")) {
                    int[] coords = Utils.getRandomCoordsInPercent(d,
                            cmd.getDouble("px"), cmd.getDouble("py"));
                    x = coords[0];
                    y = coords[1];
                }
                else {
                    Log.e(AppConfig.TAG, "Lỗi lệnh click: Không tìm thấy tham số tọa độ hợp lệ!");
                    break; // Thoát lệnh này để bot không bị crash
                }
                mTouch.pressAndRelease(x, y, 0.01f, 80);
                Log.d(AppConfig.TAG, "Đã thực hiện click tại tọa độ thực: x=" + x + ", y=" + y);
                break;

            case "handle_ads":
                Log.d(AppConfig.TAG, "Bắt đầu quét và xử lý quảng cáo...");
                long timeout = cmd.optLong("timeout", 180_000);
                mAdManager.handleAds(true, timeout, mSession);
                Log.d(AppConfig.TAG, "Đã hoàn thành xử lý quảng cáo.");
                break;

            case "wait_game_view":
                int waitTimeout = cmd.optInt("timeout", 10_000);
                Log.d(AppConfig.TAG, "Chờ GameView xuất hiện, timeout: " + waitTimeout + "ms");
                mDevice.waitForGameView(waitTimeout);
                break;

            case "sleep_random":
                int minRand = cmd.getInt("min");
                int maxRand = cmd.getInt("max");
                Log.d(AppConfig.TAG, "Nghỉ ngẫu nhiên từ " + minRand + "ms đến " + maxRand + "ms");
                Utils.sleepRandom(minRand, maxRand);
                break;

            case "sleep":
                int time_sleep = cmd.getInt("time_sleep");
                Log.d(AppConfig.TAG, "Nghỉ cố định: " + time_sleep + "ms");
                Utils.sleep(time_sleep);
                break;

            case "random_zones":
                JSONArray zones = cmd.getJSONArray("zones");
                int n = zones.length();
                Utils.ZoneSelector selector;

                // Lấy baseWeight chung
                int baseW = cmd.optInt("base", 1);

                Object cached = cmd.opt("cached_selector");

                if (cached instanceof Utils.ZoneSelector) {
                    selector = (Utils.ZoneSelector) cached;
                } else {
                    // Lấy danh sách step từ từng zone trong mảng
                    int[] stepWeights = new int[n];
                    for (int i = 0; i < n; i++) {
                        // Mặc định step là 1 nếu server không gửi trong zone đó
                        stepWeights[i] = zones.getJSONObject(i).optInt("step", 1);
                    }

                    Log.d(AppConfig.TAG, "Khởi tạo Selector với step riêng từng zone. Base=" + baseW);
                    selector = new Utils.ZoneSelector(n, baseW, stepWeights);
                    try {
                        cmd.put("cached_selector", selector);
                    } catch (Exception e) {
                        Log.e(AppConfig.TAG, "Lỗi cache selector: " + e.getMessage());
                    }
                }

                int idx = selector.selectZone();
                Log.d(AppConfig.TAG, "Zone " + idx + " được chọn");
                executeCommand(zones.getJSONObject(idx));
                break;

            case "open_game":
                Log.d(AppConfig.TAG, "Đang mở ứng dụng chính");
                mDevice.openApp(AppConfig.PKG_MAIN, AppConfig.ACT_MAIN);
                break;

            case "check_gameview":
//                Log.d(AppConfig.TAG, "Kiểm tra GameView");
                JSONObject selectorRules = cmd.getJSONObject("selectors");

                AppConfig.CURRENT_GAME_VIEW_SELECTOR = selectorRules;
//                Log.d(AppConfig.TAG, "[CheckView] Đã cập nhật Global Selector: " + selectorRules.toString());

                boolean found = mDevice.isObjectVisible(selectorRules);
                if (!found) {
                    Log.w(AppConfig.TAG, "Không thấy GameView! Kích hoạt xử lý quảng cáo...");
                    mAdManager.handleAds(true, 180000, mSession);
                    Utils.sleep(500);
                    if (!mDevice.isObjectVisible(selectorRules)) {
                        Log.e(AppConfig.TAG, "Vẫn không thấy GameView sau khi xử lý Ads. Thoát lệnh.");
                        throw new Exception("Mất GameView, buộc dừng luồng chạy hiện tại.");
                    } else {
                        Log.d(AppConfig.TAG, "Đã thấy GameView sau khi xử lý Ads.");
                    }
                }
                break;

            case "check_app":
                if (!d.hasObject(By.pkg(AppConfig.PKG_MAIN).depth(0))) {
                    Log.e(AppConfig.TAG, "SAI APP! App hiện tại không khớp với: " + AppConfig.PKG_MAIN);
                    mDevice.openApp(AppConfig.PKG_MAIN, AppConfig.ACT_MAIN);
                    Log.d(AppConfig.TAG, "Đang mở lại app");
                }
                break;

            case "for_loop":
                int iterations = cmd.optInt("count", 1);
                JSONArray subCommands_for = cmd.getJSONArray("sub_commands_for");
                Log.d(AppConfig.TAG, "[FOR] Bắt đầu vòng lặp " + iterations + " lần");

                for (int j = 0; j < iterations; j++) {
                    Log.d(AppConfig.TAG, "[FOR] ===> Lần lặp: " + (j + 1) + "/" + iterations);
                    for (int k = 0; k < subCommands_for.length(); k++) {
                        executeCommand(subCommands_for.getJSONObject(k));
                    }
                }
                Log.d(AppConfig.TAG, "[FOR] Đã kết thúc vòng lặp.");
                break;

            case "while_loop":
                JSONObject condition = cmd.getJSONObject("condition");
                JSONArray subCommands_while = cmd.getJSONArray("children");
                boolean val = condition.optBoolean("value");
                Log.d(AppConfig.TAG, "Bắt đầu vòng lặp While");

                int whileCount = 0;
                while (condition.optBoolean("value")) {
                    whileCount++;
//                    Log.d(AppConfig.TAG, "[WHILE] ===> Thực thi vòng lặp thứ: " + whileCount);
                    for (int i = 0; i < subCommands_while.length(); i++) {
                        executeCommand(subCommands_while.getJSONObject(i));
                    }
                    // Tránh vòng lặp quá khít làm log bị trôi hoặc treo CPU
                    Utils.sleep(50);
                }
                Log.d(AppConfig.TAG, "Đã thoát vòng lặp while.");
                break;

            case "swipe":
                int startX, startY, endX, endY;

                // 1. Cách mới - Dùng Phần Trăm: psX, psY (percent start) -> peX, peY (percent end)
                if (cmd.has("px1") && cmd.has("px2") && cmd.has("py1") && cmd.has("py2")) {
                    // Dùng hàm 2 tham số để lấy tọa độ điểm kèm theo sai số nhỏ (giả lập tay người)
                    int[] startPoint = Utils.getRandomCoordsInPercent(d, cmd.getDouble("px1"), cmd.getDouble("py1"));
                    int[] endPoint = Utils.getRandomCoordsInPercent(d, cmd.getDouble("px2"), cmd.getDouble("py2"));

                    startX = startPoint[0];
                    startY = startPoint[1];
                    endX = endPoint[0];
                    endY = endPoint[1];
                }
                else {
                    Log.e(AppConfig.TAG, "Lỗi lệnh swipe: Không tìm thấy tham số tọa độ hợp lệ!");
                    break; // Thoát lệnh
                }

                int steps = cmd.optInt("steps", 20);

                Log.d(AppConfig.TAG, "Bắt đầu vuốt: (" + startX + "," + startY + ") -> (" + endX + "," + endY + ") với " + steps + " steps");

                try {
                    d.swipe(startX, startY, endX, endY, steps);
                } catch (Exception e) {
                    Log.e(AppConfig.TAG, "Lỗi thực thi swipe: " + e.getMessage());
                }
                break;

            case "check_special_screen":
                Utils.checkSpecialScreen(d, mTouch, mDevice, mNetwork, cmd, this);
                break;
            default:
                Log.w(AppConfig.TAG, "Lệnh không xác định: " + type);
        }
    }
}