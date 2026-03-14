package nat.myc;

import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import nat.myc.config.AppConfig;
import nat.myc.core.DeviceHelper;
import nat.myc.core.NetworkHelper;
import nat.myc.core.TouchHelper;
import nat.myc.core.ScriptInterpreter;
import nat.myc.logic.AdManager;
import nat.myc.logic.GameSession;
import nat.myc.utils.Utils;

@RunWith(AndroidJUnit4.class)
public class ToolPlayGame {
    private UiDevice d;
    private ScriptInterpreter interpreter;
    private NetworkHelper mNetwork;
    private GameSession mSession;


    @Before
    public void setup() {
        Instrumentation ins = InstrumentationRegistry.getInstrumentation();
        d = UiDevice.getInstance(ins);

        mNetwork = new NetworkHelper();
        mSession = new GameSession();

        // Khởi tạo các Helper
        TouchHelper mTouch = new TouchHelper(ins, d);
        DeviceHelper mDevice = new DeviceHelper(d);
        AdManager mAdManager = new AdManager(d, mNetwork, mTouch, mDevice);
        // Khởi tạo Bộ thông dịch
        interpreter = new ScriptInterpreter(mAdManager, mTouch, mDevice, mSession,mNetwork);
    }

    @Test
    public void runPlayGame() throws Exception {
        // 1. Lấy Serial từ tham số ADB
        Bundle args = InstrumentationRegistry.getArguments();
        String serialArg = args.getString("serial", "UNKNOWN");
        String roomHashArg = args.getString("room_hash", "UNKNOWN");
        String scriptName = args.getString("script", "UNKNOWN");
        mSession.setSerial(serialArg); // Lưu vào session
        mSession.setRoom_hash(roomHashArg);
        mSession.setScript_name(scriptName);


        Log.d(AppConfig.TAG, "Tool Start. Serial: " + mSession.getSerial());

        while (true){
            try {
                Log.d(AppConfig.TAG, "--- Đang tải Script từ Server ---");

                String jsonResponse = mNetwork.getScriptFromServer(scriptName);

                if (jsonResponse == null || jsonResponse.isEmpty()) {
                    Log.e(AppConfig.TAG, "Không lấy được kịch bản. Thử lại sau 10s...");
                    Utils.sleep(10000);
                    continue;
                }

                // 1. Parse JSON ngoài cùng
                JSONObject rootResponse = new JSONObject(jsonResponse);

                // 2. (Tuỳ chọn) Kiểm tra cờ success xem server có trả về lỗi logic không
                if (!rootResponse.optBoolean("success", false)) {
                    Log.e(AppConfig.TAG, "API báo lỗi: " + rootResponse.optString("message", "Unknown error"));
                    Utils.sleep(10000);
                    continue;
                }

                JSONObject script = rootResponse.getJSONObject("data");
                AppConfig.PKG_MAIN = script.optString("pkg_main", AppConfig.PKG_MAIN);
                AppConfig.ACT_MAIN = script.optString("act", AppConfig.ACT_MAIN);
                AppConfig.PKG_GAME = script.optString("pkg_game", AppConfig.PKG_GAME);
                JSONArray commands = script.getJSONArray("commands");
                while (true) {
                    try {
                        for (int i = 0; i < commands.length(); i++) {
                            interpreter.executeCommand(commands.getJSONObject(i));
                        }
                    } catch (Exception e) {
                        Log.e(AppConfig.TAG, "Kịch bản bị ngắt quãng. Lý do: " + e.getMessage());
                        Utils.sleep(2000);
                    }
                    Utils.sleep(500);
                }
            } catch (Exception e) {
                Log.e(AppConfig.TAG, "Lỗi thực thi: " + e.getMessage());
                Utils.sleep(5000); // Đợi một chút trước khi reset vòng lặp
            }
            Utils.sleepRandom(2000, 4000);
        }
    }
    private String getMockScript() {
        return "{" +
                "  \"settings\": {" +
                "    \"pkg_main\": \"com.water.dink.time.reminder.cash.money\"," +
                "    \"act_main\": \"com.universal.player.MainActivity\"" +
                "  }," +
                "  \"commands\": [" +
                "    { \"type\": \"open_game\" }," +
                "    { \"type\": \"sleep\", \"time_sleep\": 15000 }," +
                "    {" +
                "      \"type\": \"while_loop\"," +
                "      \"condition\": { \"value\": true }," +
                "      \"subCommands_while\": [" +
                "        { \"type\": \"check_app\", \"pkg\": \"com.water.dink.time.reminder.cash.money\" }," +
                "        {" +
                "          \"type\": \"check_gameview\"," +
                "          \"selectors\": {" +
                "            \"clazz\": \"android.webkit.WebView\"," +
                "            \"text\": \"Cash Water: Drink Reminder Android\"" +
                "          }" +
                "        }," +
                "        {" +
                "          \"type\": \"check_special_screen\"," +
                "          \"points\": [" +
                "            { \"px\": 23.12, \"py\": 74.10, \"color\": \"0xFF228EED\" }" +
                "          ]," +
                "          \"sub_commands\": [" +
                "            { \"type\": \"click\", \"px\": 23.12, \"py\": 74.10 }," +
                "            { \"type\": \"sleep\", \"time_sleep\": 2000 }" +
                "          ]" +
                "        }," +
                "        {" +
                "          \"type\": \"random_zones\"," +
                "          \"config\": { \"base\": 1 }," +
                "          \"zones\": [" +
                "            { \"type\": \"click\", \"px1\": 45.90, \"py1\": 61.84, \"px2\": 54.31, \"py2\": 67.34, \"step\": 10 }" +
                "          ]" +
                "        }," +
                "        { \"type\": \"sleep_random\", \"min\": 800, \"max\": 1000 }" +
                "      ]" +
                "    }" +
                "  ]" +
                "}";
    }

}