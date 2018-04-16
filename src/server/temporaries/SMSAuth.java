package server.temporaries;

import org.apache.commons.lang3.RandomStringUtils;
import utils.Log;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class SMSAuth {

    public class AuthUnit{
        private String phone;
        private String code;
        private long expiry;

        public String getPhone() {
            return phone;
        }

        public String getCode() {
            return code;
        }

        public long getExpiry() {
            return expiry;
        }

        public AuthUnit(String phone, String code, long expiry) {
            this.phone = phone;
            this.code = code;
            this.expiry = expiry;
        }

        public boolean isExpired(int expireDiffMin){
            final long expiry = this.expiry;
            final long now = System.currentTimeMillis();
            if(now - expiry >= expireDiffMin * 1000 * 60){
                return true;
            }else{
                return false;
            }
        }
    }

    private volatile static ConcurrentHashMap<String, AuthUnit> map;

    private static SMSAuth ourInstance = new SMSAuth();

    public static SMSAuth getInstance() {
        return ourInstance;
    }

    public void consume(int poolSize, int diffMin) throws InterruptedException{
        for(int e = 0; e < poolSize; e++){
            new Thread(() -> {
                try{
                    final Iterator<String> iterator = map.keySet().iterator();
                    while(iterator.hasNext()){
                        final String key = iterator.next();
                        if(map.get(key).isExpired(diffMin)) map.remove(key);
                    }
                    Thread.sleep(5000);
                }catch (Exception ie){
                    ie.printStackTrace();
                }
            }).start();
        }
    }

    private SMSAuth() {
        map = new ConcurrentHashMap<>();
    }

    public static String normalizeNumber(String raw){
        return raw.replaceAll(" ", "").replaceAll("-", "").trim();
    }

    public boolean isValid(String phone, String code, int diffMin){
        final String key = normalizeNumber(phone);
        return (map.containsKey(key) && map.get(key).getCode().equals(code) && !map.get(key).isExpired(diffMin));
    }

    public void removeAuth(String phone){
        final String key = normalizeNumber(phone);
        if(map.containsKey(key)) map.remove(key);
    }

    public void addAuthInfo(String phone, String code){
        if(phone == null || phone.trim().equals("")) {
            Log.e(this.getClass().getSimpleName(), "AuthInfo not inserted. : 01");
            return;
        }
        final String key = normalizeNumber(phone);
        if(key.equals("")) {
            Log.e(this.getClass().getSimpleName(), "AuthInfo not inserted. : 02");
            return;
        }
        map.put(key, new AuthUnit(key, code, System.currentTimeMillis()));
    }

    public String getRandomString(int size){
        return RandomStringUtils.random(size, "0123456789");
    }

    public String addAuthAndGetCode(String phone, int len){
        final String code = getRandomString(len);
        addAuthInfo(phone, code);
        return code;
    }

}
