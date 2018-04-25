package delayed.managers;

import delayed.QueuedProcessorImpl;
import org.apache.commons.collections4.ListUtils;
import org.codehaus.jackson.map.ObjectMapper;
import server.comm.DataMap;
import utils.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Vector;

public class PushManager extends QueuedProcessorImpl{

    private String senderId;
    private static final int DEFAULT_POOL_SIZE = 20;
    private static final int MULTICAST_LIMIT_SIZE = 1000;

    public final static String API_URL = "https://fcm.googleapis.com/fcm/send";

    private static PushManager instance;

    public static PushManager getInstance(){
        if(instance == null) instance = new PushManager();
        return instance;
    }

    public static void start(String senderId){
        PushManager.getInstance().senderId = senderId;
        PushManager.getInstance().start(DEFAULT_POOL_SIZE);
    }

    private void _sendOnlyData(List<String> registrationKeys, DataMap extras){
        if(registrationKeys.size() == 0) return;
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "key=" + senderId);
            conn.setRequestProperty("Content-Type", "application/json");

            final ObjectMapper objectMapper = new ObjectMapper();

            final DataMap json = new DataMap();
            final DataMap info = new DataMap();

            json.put("notification", info);
            json.put("registration_ids", registrationKeys);

            json.put("data", extras);

            final String jsonString = objectMapper.writeValueAsString(json);
            Log.e(jsonString);

            Log.i(this.getClass().getSimpleName(), "Sending FCM to " + registrationKeys.size() + " user(s).");

            try (OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream(), "UTF-8")) {
                wr.write(jsonString);
                wr.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }

            final BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

            String output;
            while ((output = br.readLine()) != null) Log.i(this.getClass().getSimpleName(), output);

            conn.disconnect();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void _send(List<String> registrationKeys, String title, String message, DataMap extras){
        if(registrationKeys.size() == 0) return;
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "key=" + senderId);
            conn.setRequestProperty("Content-Type", "application/json");

            final ObjectMapper objectMapper = new ObjectMapper();

            final DataMap json = new DataMap();
            final DataMap info = new DataMap();

            info.put("title", title);
            info.put("body", message);

            json.put("notification", info);
            json.put("registration_ids", registrationKeys);

            json.put("data", extras);

            final String jsonString = objectMapper.writeValueAsString(json);
            Log.e(jsonString);

            Log.i(this.getClass().getSimpleName(), "Sending FCM to " + registrationKeys.size() + " user(s).");

            try (OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream(), "UTF-8")) {
                wr.write(jsonString);
                wr.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }

            final BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

            String output;
            while ((output = br.readLine()) != null) Log.i(this.getClass().getSimpleName(), output);

            conn.disconnect();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void send(List<String> registrationKeys, String title, String message, DataMap extras){
        for(final List<String> multicastUnit : ListUtils.partition(registrationKeys, MULTICAST_LIMIT_SIZE)) {
            super.offer(() -> _send(multicastUnit, title, message, extras));
        }
    }

    public void sendOnlyData(List<String> registrationKeys, DataMap extras){
        for(final List<String> multicastUnit : ListUtils.partition(registrationKeys, MULTICAST_LIMIT_SIZE)) {
            super.offer(() -> _sendOnlyData(multicastUnit, extras));
        }
    }

    public static void main(String... args){
        PushManager.start("AAAALAuy9Ms:APA91bHvU-eINQYL59NviY_imyPrhNc76o_Kgb1J9GFv6LhYBl545-yfpHK6iShVUCsOrXNNcZdPznFzR4p5NBrFOnubcWD93DzxzyNG0yv3j5jNGg_X1fjT_jNYmTq8Bcr_IVv6fp3A");
        final List<String> regKeys = new Vector<>();
        regKeys.add("fmgs31uYE_Q:APA91bH-g2Pv7zgKhnjtKHkE9KEjdu2C0IzgH5HhoTnmUF-TA1Tdz-iqttohxkOLIoeB08zdh5qvmReACFzsS9Q3BHKVyT9w_6aje0sRZ8gTAxn277d7PAC6NAiXChrF3brFnnVo2-9u");

        final DataMap dataMap = new DataMap();
        dataMap.put("title", "Test Title");
        dataMap.put("body", "Test Body");
        PushManager.getInstance().sendOnlyData(regKeys, dataMap);
    }

}
