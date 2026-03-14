package nat.myc.logic;

import org.json.JSONObject;

/**
 * Class quản lý toàn bộ dữ liệu của một phiên chạy game.
 * (Thời gian, Số lượng quảng cáo, Serial máy, Dữ liệu báo cáo)
 */
public class GameSession {
    private String serial = "UNKNOWN";
    private String room_hash = "UNKNOW";

    private String script_name = "UNKNOW";
    private long startTime;
    private long endTime;
    private String imageId = null;
    private long lastAdHandledAt;
    private final JSONObject extraData;

    public GameSession() {
        this.extraData = new JSONObject();
        this.lastAdHandledAt = System.currentTimeMillis();
        syncData(); // Khởi tạo dữ liệu ban đầu
    }

    // --- Getter & Setter ---

    public void setSerial(String serial) {
        if (serial != null) {
            this.serial = serial;
        }
    }

    public void setRoom_hash(String room_hash) {
        if (room_hash != null) {
            this.room_hash = room_hash;
        }
    }

    public void setScript_name(String serial) {
        if (serial != null) {
            this.script_name = script_name;
        }
    }

    public String getSerial() {
        return serial;
    }

    public String getRoom_hash() {
        return room_hash;
    }

    public void setImageId(String imageId) {
        this.imageId = imageId;
        syncData();
    }

    public void markEndTime() {
        this.endTime = System.currentTimeMillis();
        syncData();
    }

    public void markStartTime() {
        this.startTime = System.currentTimeMillis();
        syncData();
    }

    public void markAdHandled() {
        this.lastAdHandledAt = System.currentTimeMillis();
        syncData();
    }

    public long getLastAdHandledAt() {
        return lastAdHandledAt;
    }

    public JSONObject getReportData() {
        syncData();
        return extraData;
    }

    private void syncData() {
        try {
            extraData.put("start_time", startTime);
            if (endTime > 0)
                extraData.put("end_time", endTime);
            if (imageId != null)
                extraData.put("image_id", imageId);
            extraData.put("last_ad_handled_at", lastAdHandledAt);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}