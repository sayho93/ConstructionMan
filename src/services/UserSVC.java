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
        Log.i("SMS Code Generated", phone + " : " + code);
        Cafe24SMSManager.getInstanceIfExisting().send(phone, code);
    }

    public DataMap checkAccount(String account){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            final DataMap accountInfo = userMapper.getUserByAccount(account);

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

    public int joinUser(DataMap map){
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
                    return ResponseConst.CODE_FAILURE;
                }
            }

            return ResponseConst.CODE_SUCCESS;
        }
        return ResponseConst.CODE_FAILURE;
    }


    private void joinMan(int userId, int[] region, int[] work, int[] career, String welderType){
        try(SqlSession sqlSession = super.getSession()){
            Log.i("joinMain :::::::::::::");
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            for(int i=0; i<region.length; i++)
                userMapper.setUserRegion(userId, region[i]);

            for(int i=0; i<work.length; i++){
                if(work[i] == 16)
                    userMapper.setUserWork(userId, work[i], career[i], welderType);
                else
                    userMapper.setUserWork(userId, work[i], career[i], null);
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
            final String welderType = map.getString("welderType");

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
                if(work[i] == 16)   //용접공 선택시 welderType 사용
                    userMapper.setSearchWork(searchId, work[i], career[i], welderType);
                else
                    userMapper.setSearchWork(searchId, work[i], career[i], null);
            }
            sqlSession.commit();

            List<DataMap> userList = userMapper.findManMatch(searchId, allType, gugunId);

            final String title = "인력";
            String message = "";

            DataMap searchBasicInfo = userMapper.getSearchBasicInfo(searchId);

            message = "위치 " + searchBasicInfo.getString("gugunText") + "/ " + searchBasicInfo.getString("name") + "현장/ ";

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

            userMapper.saveComment(searchId, message);
            sqlSession.commit();


            final Iterator<DataMap> iter = userList.iterator();
            List<String> pushKeyList = new ArrayList<String>();

            while(iter.hasNext()){
                final DataMap map = iter.next();
                final int userId = map.getInt("userId");
                pushKeyList.add(map.getString("pushKey"));
                Log.i("userList :: pushKey", map.getInt("userId") + "::" + map.getString("pushKey"));
            }
            //TODO sendPush
//            PushManager.getInstance().send(pushKeyList, title, message);
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
            message = "위치 " + searchBasicInfo.getString("gugunText") + "/ ";

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
            //TODO sendPush


//            PushManager.getInstance().send(pushKeyList, title, message);
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

    public DataMap getUserInfo(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            DataMap userBasic = userMapper.getUserById(id);
            List<DataMap> userRegion = userMapper.getUserRegion(id);

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
            final String welderType = map.getString("welderType");

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

    public List<DataMap> getApps(int id){
        return provide(s -> {
            UserMapper userMapper = s.getMapper(UserMapper.class);

            final int count = userMapper.getAppCount(id);
            if(count < 10) return null;

            final DataMap limits = userMapper.getAppLimit(id);
            final int start = limits.getInt("start");
            final int end = limits.getInt("end");
            List<DataMap> apps = userMapper.getApps(id, start, end);

            return apps;
        });
    }

    public static void main(String ... args){
        List<String> regKeys = new ArrayList<String>();
        regKeys.add("fmgs31uYE_Q:APA91bH-g2Pv7zgKhnjtKHkE9KEjdu2C0IzgH5HhoTnmUF-TA1Tdz-iqttohxkOLIoeB08zdh5qvmReACFzsS9Q3BHKVyT9w_6aje0sRZ8gTAxn277d7PAC6NAiXChrF3brFnnVo2-9u");
        PushManager.start("AAAALAuy9Ms:APA91bHvU-eINQYL59NviY_imyPrhNc76o_Kgb1J9GFv6LhYBl545-yfpHK6iShVUCsOrXNNcZdPznFzR4p5NBrFOnubcWD93DzxzyNG0yv3j5jNGg_X1fjT_jNYmTq8Bcr_IVv6fp3A");
        PushManager.getInstance().send(regKeys, "test", "testtesttest", null);
    }

}
