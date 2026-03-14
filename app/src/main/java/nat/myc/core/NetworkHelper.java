package nat.myc.core;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import nat.myc.config.AppConfig;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class NetworkHelper {

    public NetworkHelper() {
        // Constructor trống
    }

    public Pattern getAdClassPatternFromServer(String pkgName) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                .protocols(Arrays.asList(Protocol.HTTP_1_1))
                .build();
        // Tạo URL với package
        String url = AppConfig.getAdConfigUrl(pkgName);
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Connection", "close")
                .build();
        int maxRetries = 3;
        for (int retry = 0; retry < maxRetries; retry++) {
            try (Response response = client.newCall(request).execute()) {

                if (!response.isSuccessful()) {
                    throw new Exception("HTTP " + response.code());
                }
                String json = response.body().string();
                JSONObject obj = new JSONObject(json);
                String patternStr = obj.getString("selector_pattern");
                Log.d(AppConfig.TAG, "Nhận class pattern từ server: " + patternStr);
                return Pattern.compile(patternStr);

            } catch (IOException e) {
                if (retry == maxRetries - 1) {
                    throw new Exception(
                            "Không lấy được class pattern sau " + maxRetries + " lần thử", e);
                }
                Thread.sleep(500);
            }
        }
        // fallback an toàn
        return Pattern.compile(".*(Image(View)?|ImageButton|Button|View|Image|ImageView|TextView)$");
    }

    public List<String> getAdButtonTextsFromServer(String pkgName) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                .protocols(Arrays.asList(Protocol.HTTP_1_1))
                .build();
        // URL gửi kèm package
        String url = AppConfig.getAdTextUrl(pkgName);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Connection", "close")
                .build();
        int maxRetries = 3;
        for (int retry = 0; retry < maxRetries; retry++) {
            try (Response response = client.newCall(request).execute()) {

                if (!response.isSuccessful()) {
                    throw new Exception("HTTP " + response.code());
                }
                String json = response.body().string();
                JSONObject obj = new JSONObject(json);

                // Giả sử server trả về JSON dạng:
                // { "texts": ["See next","Stay and continue","Continue","See Next"] }
                JSONArray textsJson = obj.getJSONArray("texts");
                List<String> texts = new ArrayList<>();
                for (int i = 0; i < textsJson.length(); i++) {
                    texts.add(textsJson.getString(i));
                }

                Log.d(AppConfig.TAG, "Nhận texts từ server: " + texts);
                return texts;

            } catch (IOException e) {
                if (retry == maxRetries - 1) {
                    throw new Exception(
                            "Không lấy được texts từ server sau " + maxRetries + " lần thử", e);
                }
                Thread.sleep(500); // delay trước khi retry
            }
        }

        // fallback an toàn (hardcode)
        return new ArrayList<>(Arrays.asList("See next", "Stay and continue", "Continue", "See Next", "X", "x"));
    }

    public String uploadScreenshotAndGetId(File screenshotFile) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                .protocols(Arrays.asList(Protocol.HTTP_1_1))
                .build();

        // Multipart body để upload file
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                        "files",
                        screenshotFile.getName(),
                        RequestBody.create(screenshotFile, MediaType.parse("image/png"))
                )
                .build();

        Request request = new Request.Builder()
                .url(AppConfig.URL_UPLOAD)
                .post(body)
                .addHeader("Connection", "close")
                .build();

        int maxRetries = 3;

        for (int retry = 0; retry < maxRetries; retry++) {
            try (Response response = client.newCall(request).execute()) {

                if (!response.isSuccessful()) {
                    throw new Exception("HTTP " + response.code());
                }

                String resp = response.body().string();
                JSONObject json = new JSONObject(resp);

                // Lấy id từ response: data[0].id
                JSONArray dataArr = json.getJSONArray("data");
                if (dataArr.length() > 0) {
                    JSONObject first = dataArr.getJSONObject(0);
                    return first.getString("id"); // <<< Đây là imageId
                } else {
                    throw new Exception("Response API không có data");
                }

            } catch (IOException e) {
                if (retry == maxRetries - 1) {
                    throw new Exception("Không upload được ảnh sau " + maxRetries + " lần", e);
                }
                Thread.sleep(500); // delay trước khi thử lại
            }
        }

        return null; // Không bao giờ tới đây, nhưng để compiler yên tâm
    }

    public String uploadScreenshotAndGetHashCode(File screenshotFile) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                .protocols(Arrays.asList(Protocol.HTTP_1_1))
                .build();

        // Multipart body để upload file
        // LƯU Ý: Kiểm tra lại key là "files" hay "file" theo đúng document của server thực tế
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                        "file",
                        screenshotFile.getName(),
                        RequestBody.create(screenshotFile, MediaType.parse("image/png"))
                )
                .build();

        Request request = new Request.Builder()
                .url(AppConfig.URL_UPLOAD2)
                .post(body)
                .addHeader("Connection", "close")
                .build();

        int maxRetries = 3;

        for (int retry = 0; retry < maxRetries; retry++) {
            try (Response response = client.newCall(request).execute()) {

                if (!response.isSuccessful()) {
                    // Nếu server trả về lỗi 500 hoặc 400
                    String errorBody = response.body() != null ? response.body().string() : "null";
                    Log.e(AppConfig.TAG, "Upload failed code: " + response.code() + " body: " + errorBody);
                    throw new Exception("HTTP " + response.code());
                }

                String resp = response.body().string();
                JSONObject json = new JSONObject(resp);

                // --- SỬA LOGIC PARSE JSON THEO SCHEMA ---
                // Schema: { "data": { "hash": "string" } }
                if (json.has("data") && !json.isNull("data")) {
                    JSONObject dataObj = json.getJSONObject("data"); // data là Object, không phải Array

                    if (dataObj.has("hash") && !dataObj.isNull("hash")) {
                        return dataObj.getString("hash"); // Lấy key "hash"
                    }
                }

                throw new Exception("Response API không có key 'hash' trong data");

            } catch (IOException | JSONException e) {
                if (retry == maxRetries - 1) {
                    throw new Exception("Không upload được ảnh sau " + maxRetries + " lần: " + e.getMessage(), e);
                }
                Thread.sleep(500); // delay trước khi thử lại
            }
        }

        return null;
    }

    public void sendSerialLogToServer(
            String serial,
            String room_hash,
            String status,
            JSONObject extraData,
            String gamePackage
    ) throws Exception {

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                .protocols(Arrays.asList(Protocol.HTTP_1_1))
                .build();

        // ========= Build JSON body =========
        JSONObject jsonBody = new JSONObject();
        jsonBody.put("serial", serial);
        jsonBody.put("room_hash", room_hash);
        jsonBody.put("status", status);
        jsonBody.put("game_package", gamePackage);
        jsonBody.put("extra_data", extraData);

        MediaType JSON
                = MediaType.parse("application/json; charset=utf-8");

        RequestBody body = RequestBody.create(
                jsonBody.toString(),
                JSON
        );

        Request request = new Request.Builder()
                .url(AppConfig.URL_REPORT)
                .post(body)
                .addHeader("Connection", "close")
                .build();

        int maxRetries = 3;

        for (int retry = 0; retry < maxRetries; retry++) {
            try (Response response = client.newCall(request).execute()) {

                if (!response.isSuccessful()) {
                    throw new IOException("HTTP " + response.code()
                            + " - " + response.message());
                }

                Log.d(AppConfig.TAG, "Gửi log thành công | serial=" + serial
                        + " | status=" + status);
                Log.d(AppConfig.TAG, "Payload: " + jsonBody.toString());

                return; // thành công → thoát hàm

            } catch (IOException e) {

                Log.e(AppConfig.TAG, "Lần thử " + (retry + 1)
                        + " gửi log thất bại", e);

                if (retry == maxRetries - 1) {
                    throw new Exception(
                            "Không gửi được SERIAL + LOG sau "
                                    + maxRetries + " lần thử", e);
                }

                Thread.sleep(500); // delay trước khi retry
            }
        }
    }

    public int getAdminDebugStatus() throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                .protocols(Arrays.asList(Protocol.HTTP_1_1))
                .build();

        Request request = new Request.Builder()
                .url(AppConfig.URL_DEBUG)
                .get()
                .addHeader("Connection", "close")
                .build();

        int maxRetries = 3;

        for (int retry = 0; retry < maxRetries; retry++) {
            try (Response response = client.newCall(request).execute()) {

                if (!response.isSuccessful()) {
                    throw new Exception("HTTP " + response.code());
                }

                String json = response.body().string();
                JSONObject obj = new JSONObject(json);

                // expected: { "debug_ads": 0 | 1 }
                return obj.getInt("debug_ads");

            } catch (IOException e) {
                if (retry == maxRetries - 1) {
                    throw new Exception(
                            "Không lấy được trạng thái debug từ admin sau "
                                    + maxRetries + " lần thử", e);
                }
                Thread.sleep(500);
            }
        }
        return 1; // mặc định GIỮ debug
    }
    public JSONObject getCaptchaResult(String hash) {
        // Xây dựng URL: /get/{mã hash}
        // Đảm bảo không bị thừa dấu / khi nối chuỗi
        String baseUrl = AppConfig.URL_GET_RESULT;
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String url = baseUrl + "/" + hash;

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Connection", "close")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String respStr = response.body().string();

                try {
                    JSONObject json = new JSONObject(respStr);

                    // --- SỬA LOGIC PARSE JSON THEO SCHEMA ---
                    // Schema: { "data": { "result": "string" } }

                    // 1. Kiểm tra success (tuỳ chọn, nhưng an toàn hơn)
                    if (json.has("success") && !json.getBoolean("success")) {
                        return null; // Server báo thất bại
                    }

                    // 2. Vào object data
                    if (json.has("data") && !json.isNull("data")) {
                        JSONObject dataObj = json.getJSONObject("data");

                        // 3. Lấy key "result"
                        if (dataObj.has("result") && !dataObj.isNull("result")) {
                            String result = dataObj.getString("result");
                            // Nếu result rỗng, trả về null để tiếp tục vòng lặp polling
                            return result.isEmpty() ? null : dataObj;
                        }
                    }

                    return null; // Chưa có kết quả hoặc cấu trúc không khớp

                } catch (JSONException e) {
                    Log.e(AppConfig.TAG, "Lỗi parse JSON get result: " + e.getMessage());
                    return null;
                }
            } else {
                // Xử lý trường hợp 404 (Hash not found) hoặc 500
                // Nếu 404 nghĩa là hash sai hoặc đã hết hạn -> trả về null để vòng lặp bên ngoài xử lý (hoặc break)
                if (response.code() == 404) {
                    Log.e(AppConfig.TAG, "Hash not found (404)");
                    JSONObject errorObj = new JSONObject();
                    errorObj.put("STATUS_ERROR_404", true);
                    return errorObj;
                }
            }
        } catch (Exception e) {
            Log.e(AppConfig.TAG, "Lỗi khi gọi API get captcha result: " + e.getMessage());
        }

        return null; // Trả về null để vòng lặp bên ngoài tiếp tục thử lại
    }

    public String getScriptFromServer(String scriptName) throws Exception {
        // 1. Khởi tạo Client (Giữ nguyên cấu hình cũ của bạn)
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                .protocols(Arrays.asList(Protocol.HTTP_1_1))
                .build();

        String url = AppConfig.CREATE_TOOLS + scriptName;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Connection", "close")
                .build();

        // 2. Vòng lặp vô hạn cho đến khi lấy được Script
        while (true) {
            try (Response response = client.newCall(request).execute()) {

                // Kiểm tra nếu HTTP OK (200)
                if (response.isSuccessful()) {
                    String jsonResponse = response.body().string();

                    if (jsonResponse != null && !jsonResponse.isEmpty()) {
                        Log.d(AppConfig.TAG, "===> [Network] Tải kịch bản thành công!");
                        return jsonResponse; // Thoát vòng lặp và trả về dữ liệu
                    } else {
                        Log.w(AppConfig.TAG, "[Network] Server trả về rỗng. Thử lại sau 10s...");
                    }
                } else {
                    Log.e(AppConfig.TAG, "[Network] Lỗi HTTP: " + response.code() + ". Thử lại sau 10s...");
                }

            } catch (IOException e) {
                // Lỗi kết nối, timeout, DNS, v.v.
                Log.e(AppConfig.TAG, "[Network] Không thể kết nối Server: " + e.getMessage() + ". Đang đợi 10s để thử lại...");
            }

            // 3. Nghỉ 10 giây trước khi bắt đầu vòng lặp tiếp theo
            try {
                Thread.sleep(10000); // 10s = 10.000ms
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new Exception("Quá trình đợi bị ngắt quãng", ie);
            }
        }
    }
}