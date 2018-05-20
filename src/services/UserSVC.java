package services;

import com.sun.org.apache.xpath.internal.operations.Bool;
import databases.mybatis.mapper.CommMapper;
import databases.mybatis.mapper.UserMapper;
import databases.paginator.ListBox;
import databases.paginator.PageInfo;
import delayed.managers.PushManager;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.jdbc.SQL;
import org.apache.ibatis.session.SqlSession;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.eclipse.jetty.server.Authentication;
import server.cafe24.Cafe24SMSManager;
import server.comm.DataMap;
import server.comm.models.GearInfo;
import server.response.Response;
import server.response.ResponseConst;
import server.rest.DataMapUtil;
import server.rest.RestUtil;
import server.rest.ValidationUtil;
import server.temporaries.SMSAuth;
import utils.Log;
import utils.MailSender;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class UserSVC extends BaseService {

    public DataMap turnOnPush(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.turnOnPush(id);
            sqlSession.commit();

            final DataMap map = getUserInfo(id);
            DataMapUtil.mask(map, "password");
            return map;
        }
    }

    public DataMap turnOffPush(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.turnOffPush(id);
            sqlSession.commit();

            final DataMap map = getUserInfo(id);
            DataMapUtil.mask(map, "password");
            return map;
        }
    }

    public void userSMSAuth(String phone){
        final String code = SMSAuth.getInstance().addAuthAndGetCode(phone, 6);
        String message = "인증번호는 [" + code + "]입니다. 본인 외 제3자에게 누설금지! -휴넵스/건설인-";
        Log.i("SMS Code Generated", phone + " : " + code);
        Cafe24SMSManager.getInstanceIfExisting().send(phone, message);
    }

    public DataMap checkAccount(String account){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            Log.i("accoun", account);
            final DataMap accountInfo = userMapper.getUserByAccount(account);
            if(accountInfo != null)
                Log.i("info", accountInfo.toString());

            return accountInfo;
        }
    }

    public DataMap checkPhone(String phone){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            final DataMap accountInfo = userMapper.getUserByPhone(phone);

            return accountInfo;
        }
    }

    public DataMap joinUser(DataMap map){
        final String password = RestUtil.getMessageDigest(map.getString("password"));
        final String phone = map.getString("phone").replaceAll("-", "");
        final String type = map.getString("type");
        int lastId = 0;

        map.put("password", password);

        if(ValidationUtil.validate(phone, ValidationUtil.ValidationType.Phone)){
            try(SqlSession sqlSession = super.getSession()){
                UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

                userMapper.registerUserBasic(map);
                sqlSession.commit();
                lastId = map.getInt("id");
            }
            if(type.equals("M")){
                final int[] region = map.getStringToIntArr("region", ",");
                final int[] work = map.getStringToIntArr("work", ",");
                final int[] career = map.getStringToIntArr("career", ",");
                final String welderType = map.getString("welderType");

                joinMan(lastId, region, work, career, welderType);
            }
            else if(type.equals("G")){
                final int[] region = map.getStringToIntArr("region", ",");

                ObjectMapper mapper = new ObjectMapper();

                final String jsonInput = map.getString("gearInfo");
                try {
                    List<GearInfo> myObjects = mapper.readValue(jsonInput, new TypeReference<List<GearInfo>>() {});
                    Log.e("GearInfo Test", myObjects.toString());
                    joinGear(lastId, region, myObjects);
                }catch (IOException e){
                    e.printStackTrace();
                    return null;
                }
            }

            return getUserInfo(lastId);
        }
        return null;
    }


    private void joinMan(int userId, int[] region, int[] work, int[] career, String welderType){
        try(SqlSession sqlSession = super.getSession()){
            Log.i("joinMain :::::::::::::");
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            for(int i=0; i<region.length; i++)
                userMapper.setUserRegion(userId, region[i]);

            for(int i=0; i<work.length; i++){
                userMapper.setUserWork(userId, work[i], career[i], "");
//                if(work[i] == 16)
//                    userMapper.setUserWork(userId, work[i], career[i], welderType);
//                else
//                    userMapper.setUserWork(userId, work[i], career[i], "");
            }
            sqlSession.commit();
        }
    }

    private void joinGear(int userId, int[] region, List<GearInfo> gearInfoList){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            for(int i=0; i<region.length; i++)
                userMapper.setUserRegion(userId, region[i]);

            for(GearInfo gearInfo : gearInfoList){
                userMapper.setUserGear(userId, gearInfo.getId(), gearInfo.getAttach());
            }

            sqlSession.commit();
        }
    }

    public int registerSearch(int userId, DataMap map){
        final String type = map.getString("type");
        int lastId = 0;
        final int gugunId = map.getInt("gugunId");

        map.put("userId", userId);

        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            DataMapUtil.isEmptyValueThenPut(map,
                    new String[]{"lodging", "price", "discussLater"},
                    new Object[]{0, 0, 0}
                    );
            userMapper.registerSearchBasic(map);
            sqlSession.commit();
            lastId = map.getInt("id");
        }

        if(type.equals("M")){
            final int[] work = map.getStringToIntArr("work", ",");
            final int[] career = map.getStringToIntArr("career", ",");
            final String welderType = map.getString("welderType", "");

            int allType = 0;
            final int lodging = map.getInt("lodging");
            try{
                final Date startDate = map.getDate("startDate");
                final Date endDate = map.getDate("endDate");

                final long diffTime = endDate.getTime() - startDate.getTime();
                final long diffDays = diffTime / (1000 * 60 * 60 * 24);

                if(diffDays >= 60 || lodging == 1) allType = 1;
            }
            catch(ParseException e){
                e.printStackTrace();
            }

            searchMan(lastId, work, career, welderType, allType, gugunId);
        }
        else if(type.equals("G")){
            final int gearId = map.getInt("gearId");
            final String attachment = map.getString("attachment");
            final String[] attachmentArr = StringUtils.split(attachment, ",");

            searchGear(lastId, gearId, attachment, gugunId, attachmentArr);
        }

        return ResponseConst.CODE_SUCCESS;
    }

    private void searchMan(int searchId, int[] work, int[] career, String welderType, int allType, int gugunId){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            for(int i=0; i<work.length; i++){
                userMapper.setSearchWork(searchId, work[i], career[i], welderType);
//                if(work[i] == 16)   //용접공 선택시 welderType 사용
//                    userMapper.setSearchWork(searchId, work[i], career[i], welderType);
//                else
//                    userMapper.setSearchWork(searchId, work[i], career[i], welderType);
            }
            sqlSession.commit();

            List<DataMap> userList = userMapper.findManMatch(searchId, allType, gugunId);

            final String title = "인력";
            String message = "";

            DataMap searchBasicInfo = userMapper.getSearchBasicInfo(searchId);

            message = "위치 " + searchBasicInfo.getString("sidoText") + " " + searchBasicInfo.getString("gugunText") + "/ " + searchBasicInfo.getString("name") + "현장/ ";

            List<DataMap> workList = userMapper.getSearchManInfo(searchId);

            for(DataMap map : workList){
                message += map.getString("name") + "/ 숙련도";
                final int tmpCareer = map.getInt("career");
                switch(tmpCareer){
                    case 1: message += "(하)/ ";
                        break;
                    case 2: message += "(중)/ ";
                        break;
                    case 3: message += "(상)/ ";
                        break;
                }
            }

            if(searchBasicInfo.getInt("lodging") == 1)
                message += "숙식제공/ ";

            message += "공사기간 " + searchBasicInfo.getString("startDate") + "~" + searchBasicInfo.getString("endDate") + "/ ";
            message += "단가 " + searchBasicInfo.getInt("price");
            if(searchBasicInfo.getInt("discussLater") == 1){
                message += "(추후협의)";
            }

            userMapper.saveComment(searchId, message);
            sqlSession.commit();

            List<String> pushKeyList = new ArrayList<String>();

            for(DataMap map : userList){
                final int userId = map.getInt("userId");
                pushKeyList.add(map.getString("pushKey"));
                Log.i("userList :: pushKey", map.getInt("userId") + "::" + map.getString("pushKey"));
            }

            //TODO sendPush
            final DataMap dataMap = new DataMap();
            dataMap.put("title", "구인 정보 알림");
            dataMap.put("body", "아래로 당겨 자세히 보기");
            dataMap.put("notiClass", title);
            dataMap.put("notiBox", message);
            dataMap.put("notiGuide", "위 현장에 지원하시겠습니까?");
            dataMap.put("articleNumber", Integer.toString(searchId)); // 구인글 번호
            dataMap.put("isRedirect", Boolean.toString(false)); // 알림글일 경우 true
            PushManager.getInstance().sendOnlyData(pushKeyList, dataMap);

        }
    }

    private void searchGear(int searchId, int gearId, String attachment, int gugunId, String[] attachmentArr){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            userMapper.setSearchGear(searchId, gearId, attachment);
            sqlSession.commit();

            List<DataMap> userList = userMapper.findGearMatch(gearId, attachmentArr, gugunId);
            final String title = "장비";
            String message = "";

            DataMap searchBasicInfo = userMapper.getSearchBasicInfo(searchId);
            message = "위치 " + searchBasicInfo.getString("sidoText") + " " + searchBasicInfo.getString("gugunText") + "/ ";

            DataMap gearInfo = userMapper.getSearchGearInfo(searchId);
            message += gearInfo.getString("name") + "/ ";
            message += "작업기간 " + searchBasicInfo.getString("startDate") + " ~ " + searchBasicInfo.getString("endDate");


            userMapper.saveComment(searchId, message);
            sqlSession.commit();


            final Iterator<DataMap> iterator = userList.iterator();
            List<String> pushKeyList = new ArrayList<>();

            while(iterator.hasNext()){
                final DataMap map = iterator.next();
                final int userId = map.getInt("userId");
                pushKeyList.add(map.getString("pushKey"));
                Log.i("userId :: pushKey", userId + " :: " + map.getString("pushKey"));
            }

            //sendPush
            final DataMap dataMap = new DataMap();
            dataMap.put("title", "구인 정보 알림");
            dataMap.put("body", "두 손가락으로 펼쳐서 알림 상세보기");
            dataMap.put("notiClass", title);
            dataMap.put("notiBox", message);
            dataMap.put("notiGuide", "위 현장에 지원하시겠습니까?");
            dataMap.put("articleNumber", Integer.toString(searchId)); // 구인글 번호
            dataMap.put("isRedirect", Boolean.toString(false)); // 알림글일 경우 true
            PushManager.getInstance().sendOnlyData(pushKeyList, dataMap);
        }
    }

    public DataMap userLogin(DataMap map){
        final String account = map.getString("account");
        final String password = RestUtil.getMessageDigest(map.getString("password"));

        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            DataMap userInfo = userMapper.getUserByAccountPass(account, password);
            DataMapUtil.mask(userInfo, "password");
            return userInfo;
        }
    }

    public DataMap getUserBasic(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            DataMap userInfo = userMapper.getUserById(id);
            DataMapUtil.mask(userInfo, "password");
            return userInfo;
        }
    }

    public DataMap getUserInfo(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            DataMap userBasic = userMapper.getUserById(id);
            List<DataMap> userRegion = userMapper.getUserRegion(id);
            userBasic.put("regionInfo", userRegion);

            final String type = userBasic.getString("type");

            if(type.equals("M")){
                List<DataMap> workInfo = userMapper.getUserWork(id);
                userBasic.put("workInfo", workInfo);
            }
            else if(type.equals("G")){
                List<DataMap> gearInfo = userMapper.getUserGear(id);
                userBasic.put("gearInfo", gearInfo);
            }

            userBasic.put("userRegion", userRegion);
            return userBasic;
        }
    }

    public DataMap updatePushKey(int id, String pushKey){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            userMapper.updatePushKey(id, pushKey);
            sqlSession.commit();

            DataMap userInfo = userMapper.getUserById(id);;
            DataMapUtil.mask(userInfo, "password");

            return userInfo;
        }
    }

    public DataMap updateUserInfo(int id, DataMap map){
        final String type = map.getString("type");

        if(type.equals("M")){
            final int[] region = map.getStringToIntArr("region", ",");
            final int[] work = map.getStringToIntArr("work", ",");
            final int[] career = map.getStringToIntArr("career", ",");
            final String welderType = map.getString("welderType", "");

            try(SqlSession sqlSession = super.getSession()){
                UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

                userMapper.deleteUserRegion(id);
                Log.i("deleteUserRegion executed :::::::::::::::::::::::::");
                userMapper.deleteUserWork(id);
                sqlSession.commit();
            }
            joinMan(id, region, work, career, welderType);
        }
        else if(type.equals("G")){
            final int[] region = map.getStringToIntArr("region", ",");

            try(SqlSession sqlSession = super.getSession()){
                UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

                userMapper.deleteUserRegion(id);
                userMapper.deleteUserGear(id);
                sqlSession.commit();
            }

            ObjectMapper mapper = new ObjectMapper();

            final String jsonInput = map.getString("gearInfo");
            try {
                List<GearInfo> myObjects = mapper.readValue(jsonInput, new TypeReference<List<GearInfo>>() {});
                Log.e("GearInfo Test", myObjects.toString());
                joinGear(id, region, myObjects);
            }catch (IOException e){
                e.printStackTrace();
            }
        }

        return getUserInfo(id);
    }

    public DataMap updateUserName(int userId, String name){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.updateUserName(userId, name);
            sqlSession.commit();
        }
        return getUserInfo(userId);
    }

    public DataMap withdrawUser(int userId){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.withdrawUser(userId);
            sqlSession.commit();
        }
        return getUserInfo(userId);
    }

    public void applySearch(int userId, int searchId){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            userMapper.applySearch(userId, searchId);
            sqlSession.commit();
            //TODO send push

            int applyCnt = userMapper.getAppCountBySearchId(searchId);
            if(applyCnt % 10 == 0){
                DataMap searchInfo = userMapper.getSearchBasicInfo(searchId);
                SimpleDateFormat fmtOrigin = new SimpleDateFormat("yyyy-mm-dd hh:MM:ss");
                SimpleDateFormat fmt = new SimpleDateFormat("yyyy년 mm월 dd일");
                String rawDate = searchInfo.getString("regDate");
                try {
                    Date regDt = fmtOrigin.parse(rawDate);
                    rawDate = fmt.format(regDt);
                }catch (ParseException e){
                    e.printStackTrace();
                }

                final int id = searchInfo.getInt("userInfo");
                final DataMap userInfo = userMapper.getUserById(id);


                List<String> pushKeyList = new ArrayList<>();
                pushKeyList.add(userInfo.getString("pushKey"));

                String message = "귀하가 " + rawDate + "에 요청하신 인력/장비에 대하여 " + applyCnt + "명이 지원하였습니다.";

                final DataMap dataMap = new DataMap();
                dataMap.put("title", "구인 정보 알림");
                dataMap.put("body", "아래로 당겨 자세히 보기");
                dataMap.put("notiClass", "");
                dataMap.put("notiBox", "");
                dataMap.put("notiGuide", String.format("%s\n\n%s", message, "포인트로 결제 후 지원명단을 확인하시겠습니까?"));
                dataMap.put("articleNumber", Integer.toString(searchId)); // 구인글 번호
                dataMap.put("isRedirect", Boolean.toString(true)); // 알림글일 경우 true
                PushManager.getInstance().sendOnlyData(pushKeyList, dataMap);

            }
        }
    }

    public DataMap getUserByNamePhone(DataMap map){
        final String name = map.getString("name");
        final String phone = map.getString("phone");

        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            DataMap userInfo = userMapper.getUserByNamePhone(name, phone);
            DataMapUtil.mask(userInfo, "password");

            return userInfo;
        }
    }

    public DataMap getUSerByAccountPhone(DataMap map){
        final String name = map.getString("name");
        final String phone = map.getString("phone");
        final String account = map.getString("account");

        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            DataMap userInfo = userMapper.getUserByAccountPhone(name, phone, account);
            DataMapUtil.mask(userInfo, "password");

            return userInfo;
        }
    }

    public DataMap updateUserImg(int id, String imgPath){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            userMapper.updateUserImg(id, imgPath);
            sqlSession.commit();
            DataMap userInfo = userMapper.getUserById(id);
            return userInfo;
        }
    }

    public void changePassword(int id, String newPassword){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.changePassword(id, RestUtil.getMessageDigest(newPassword));
            sqlSession.commit();
        }
    }

    /**
     * @apiNote PayType [0:Manual / 1:PG]
     * @param id memberId
     * @param inc difference
     * @param payType payType
     * @param paymentId Unique ID for PG Type
     * @param comment additional comment
     */
    public int changeUserPoint(int id, int inc, int payType, int paymentId, String comment){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.addPointHistory(id, inc, payType, paymentId, comment);
            sqlSession.commit();
        }
        return getUserPoint(id);
    }

    public int getUserPoint(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            Integer point = userMapper.getUserPoint(id);
            if(point == null) return 0;
            return point;
        }
    }

    public List<DataMap> getPointList(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            List<DataMap> list = userMapper.getPointList(id);
            return list;
        }
    }

    public List<DataMap> getApplyList(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            List<DataMap> list = userMapper.getApplyList(id);

            for(DataMap item : list){
                final int searchUserId = item.getInt("searchUserId");
                final int searchId = item.getInt("searchId");
                DataMap limits = userMapper.getAppLimit(searchUserId);
                if(limits == null) limits = new DataMap();
                final int start = limits.getInt("start", 0);
                final int end = limits.getInt("end", 0);
                Log.i("start :: end", start + "::::" + end);

                final Integer isPaid = userMapper.getPaymentStatus(searchUserId, searchId, start, end);
                item.put("isPaid", isPaid == null ? 0 : isPaid);
            }
            return list;
        }
    }

    public void hideApplyHistory(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.hideApplyHistory(id);
            sqlSession.commit();
        }
    }

    public void hidePointHistory(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.hidePointHistory(id);
            sqlSession.commit();
        }
    }

    public List<DataMap> getApps(int id){
        return provide(s -> {
            UserMapper userMapper = s.getMapper(UserMapper.class);

            final int count = userMapper.getAppCount(id);
//            if(count < 10) return null;

            final DataMap limits = userMapper.getAppLimit(id);
            int start = -1;
            int end = -1;

            if(limits != null){
                start = limits.getInt("start");
                end = limits.getInt("end");
            }

//            final Integer start = limits.getInt("start");
//            final Integer end = limits.getInt("end");
            Log.i("start :: end", start + "::::" + end);
            List<DataMap> apps = userMapper.getApps(id, start, end);

            for(int i=0; i<apps.size(); i++){
                final DataMap map = apps.get(i);
                final int userId= map.getInt("id");
                final String type = map.getString("type");
                //TODO limit
                if(i<start || i>end){
                    DataMapUtil.maskWithLength(map, "name");
                    DataMapUtil.maskWithLength(map, "account");
                    DataMapUtil.maskWithLength(map, "password");
                    DataMapUtil.maskWithLength(map, "phone");
                    DataMapUtil.maskWithLength(map, "age");
                }

                List<DataMap> regionInfo = userMapper.getUserRegion(userId);
                map.put("regionInfo", regionInfo);

                if(type.equals("M")){
                    List<DataMap> workInfo = userMapper.getUserWork(userId);
                    map.put("workInfo", workInfo);
                }
                else if(type.equals("G")){
                    List<DataMap> gearInfo = userMapper.getUserGear(userId);
                    map.put("gearInfo", gearInfo);
                }
            }

            Collections.reverse(apps);

//            for(DataMap map : apps){
//                final int userId= map.getInt("id");
//                final String type = map.getString("type");
//
//                List<DataMap> regionInfo = userMapper.getUserRegion(userId);
//                map.put("regionInfo", regionInfo);
//
//                if(type.equals("M")){
//                    List<DataMap> workInfo = userMapper.getUserWork(userId);
//                    map.put("workInfo", workInfo);
//                }
//                else if(type.equals("G")){
//                    List<DataMap> gearInfo = userMapper.getUserGear(userId);
//                    map.put("gearInfo", gearInfo);
//                }
//            }

            return apps;
        });
    }

    public int usePoint(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            final int cnt = userMapper.getAppCount(id);

            int start = 0;
            int end = 0;

            DataMap map = userMapper.getAppLimit(id);
            if(map != null){
                start = map.getInt("start");
                end = map.getInt("end");
            }

            Log.i("cnt", cnt);

            if(cnt < 10 || cnt - (end-start) < 10){
                return -1;
            }
            userMapper.addPointHistory(id, -1000, -1, -1, "포인트 사용");

            final int amount = 10;
            userMapper.addExposure(id, amount);
            sqlSession.commit();
            return 1;
        }
    }

}
