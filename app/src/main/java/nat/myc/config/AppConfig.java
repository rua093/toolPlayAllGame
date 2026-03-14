package nat.myc.config;


import org.json.JSONObject;

public class AppConfig {

    // === Cấu hình App & Game ===
    public static final String TAG = "KTESTK_K";

    // Tên gói ứng dụng chính (App kiếm tiền)
    public static String PKG_MAIN ;
    public static String ACT_MAIN ;

    public static JSONObject CURRENT_GAME_VIEW_SELECTOR = null;
    // Tên gói Game mini
    public static String PKG_GAME;

//    public static final String ACT_GAME_MAIN = "com.unity3d.player.UnityPlayerActivity";

    // === Cấu hình Server API ===
    public static final String SERVER_BASE_URL = "http://160.25.81.154:9000";
    public static final String SERVER_BASE_URL_ROBOT = "https://robot.b6-team.site";
    public static final String URL_REPORT      = SERVER_BASE_URL + "/api/v1/report";
    public static final String URL_UPLOAD      = SERVER_BASE_URL + "/api/v1/images/";

    public static final String URL_UPLOAD2      = SERVER_BASE_URL_ROBOT + "/api/upload";

    public static final String CREATE_TOOLS      = SERVER_BASE_URL_ROBOT + "/api/creating-tools/data?game_tools_name=";
    public static final String URL_DEBUG       = SERVER_BASE_URL + "/api/v1/debug-status";

    public static final String URL_GET_RESULT  = SERVER_BASE_URL_ROBOT + "/api/get";

    // Helper để tạo URL động
    public static String getAdConfigUrl(String pkg) {
        return SERVER_BASE_URL + "/api/v1/game-button-configs/by-package/" + pkg;
    }
    public static String getAdTextUrl(String pkg) {
        return SERVER_BASE_URL + "/api/v1/game-close-texts/by-package/" + pkg;
    }

    // === Cấu hình Thời gian (Timeouts & Delays) ===
    public static final long LAUNCH_TIMEOUT = 25_000; // 25s
    public static final long FIND_TIMEOUT   = 1500; // 20s

    // Thời gian hoạt động và nghỉ ngơi
    public static final long ACTIVE_MIN_MS = 15 * 60_000;  // 35 phút
    public static final long ACTIVE_MAX_MS = 30 * 60_000;  // 50 phút
    public static final long BREAK_MIN_MS  =  3 * 60_000;  // 8 phút
    public static final long BREAK_MAX_MS  =  5 * 60_000; // 13 phút
    public static final long NO_AD_RESTART_MS = 5 * 60 * 1000L;
}