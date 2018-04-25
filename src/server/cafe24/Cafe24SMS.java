package server.cafe24;

import utils.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class Cafe24SMS {

    private String userId; // "huneps71"
    private String secure; // "e6ac61f053b7abf60ee934857d2955c7"

    private String sphone1 = "";
    private String sphone2 = "";
    private String sphone3 = "";

    private static Cafe24SMS instance;

    public static Cafe24SMS getInstance(String pureUser, String secureKey, String sender01, String sender02, String sender03) throws IllegalStateException{
        if(instance == null) instance = new Cafe24SMS(pureUser, secureKey, sender01, sender02, sender03);
        return instance;
    }

    public static String toValidNumber(String phone){
        final String normalized = phone
                .replaceAll(" ", "")
                .replaceAll("-", "")
                .replaceAll("[가-힣A-Za-z]*", "");
        if(normalized.length() == 11){
            return String.format("%s-%s-%s", normalized.substring(0, 3), normalized.substring(3, 7), normalized.substring(7, 11));
        }if(normalized.length() == 10){
            return String.format("%s-%s-%s", normalized.substring(0, 3), normalized.substring(3, 6), normalized.substring(6, 10));
        }else{
            return normalized;
        }
    }

    public static void main(String... args){

//        Cafe24SMS inst = Cafe24SMS.getInstance(
//                "huneps71",
//                "e6ac61f053b7abf60ee934857d2955c7",
//                "02",
//                "555",
//                "5555");
//
//        Cafe24SMSManager.initialize(inst);
//        Cafe24SMSManager.getInstanceIfExisting().start(100);
//
//        Cafe24SMSManager.getInstanceIfExisting().send("010-2948-4648", "test");
    }

    private Cafe24SMS(String pureUser, String secureKey, String sender01, String sender02, String sender03) throws IllegalStateException {
        if(pureUser == null
                || pureUser.equals("")
                || secureKey == null
                || secureKey.equals("")
                || sender01 == null
                || sender01.equals("")
                || sender02 == null
                || sender02.equals("")
                || sender03 == null
                || sender03.equals("")
                ){
            throw new IllegalStateException("invalid parameter.");
        }

        this.sphone1 = base64Encode(sender01);
        this.sphone2 = base64Encode(sender02);
        this.sphone3 = base64Encode(sender03);
        this.userId = base64Encode(pureUser);
        this.secure = base64Encode(secureKey);
    }

    /**
     * BASE64 Encoder
     * @param str
     * @return
     */
    private static String base64Encode(String str){
        sun.misc.BASE64Encoder encoder = new sun.misc.BASE64Encoder();
        byte[] strByte = str.getBytes();
        String result = encoder.encode(strByte);
        return result ;
    }

    /**
     * BASE64 Decoder
     * @param str
     * @return
     */
    private static String base64Decode(String str) throws java.io.IOException{
        sun.misc.BASE64Decoder decoder = new sun.misc.BASE64Decoder();
        byte[] strByte = decoder.decodeBuffer(str);
        String result = new String(strByte);
        return result ;
    }


    private final static String apiUrl = "https://sslsms.cafe24.com/sms_sender.php";
    private final static String userAgent = "Mozilla/5.0";
    private final static String charset = "UTF-8";
    private final static boolean isTest = false;

    private final String mode = base64Encode("1");
    private final String sms = "S";

    public void sendSMS(String rphone, String msg){
        try{
            URL obj = new URL(apiUrl);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestProperty("Conten-Type", "application/x-www-form-urlencoded");
            con.setRequestProperty("Accept-Charset", charset);
            con.setRequestMethod("POST");
            con.setRequestProperty("User-Agent", userAgent);

            final String recvPhone = base64Encode(rphone);
            final String encodedMsg = base64Encode(msg);

            String postParams = "user_id=" + userId + "&secure=" + secure + "&msg=" + encodedMsg +
                    "&rphone=" + recvPhone + "&sphone1=" + sphone1 + "&sphone2=" + sphone2 +
                    "&sphone3=" + sphone3 + "&mode=" + mode + "&smsType=" + sms;

            if(isTest){
                postParams += "&testflag=" + base64Encode("Y");
            }

            con.setDoOutput(true);
            OutputStream os = con.getOutputStream();
            os.write(postParams.getBytes());
            os.flush();
            os.close();

            int responseCode = con.getResponseCode();
            Log.i("POST Response Code", responseCode);

            if(responseCode == HttpURLConnection.HTTP_OK){
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer buffer = new StringBuffer();

                while((inputLine = in.readLine()) != null){
                    buffer.append(inputLine);
                }
                in.close();

                Log.i("SMS Content", buffer.toString());
            }
            else{
                Log.e("POST request not worked");
            }

        } catch (IOException e){
            Log.e("SMS ioException", e.getMessage());
        }
    }
}
