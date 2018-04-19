package services;

import com.sun.org.apache.xpath.internal.operations.Bool;
import databases.mybatis.mapper.CommMapper;
import databases.mybatis.mapper.UserMapper;
import databases.paginator.ListBox;
import databases.paginator.PageInfo;
import delayed.managers.PushManager;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.jdbc.SQL;
import org.apache.ibatis.session.SqlSession;
import server.cafe24.Cafe24SMSManager;
import server.comm.DataMap;
import server.response.Response;
import server.response.ResponseConst;
import server.rest.DataMapUtil;
import server.rest.RestUtil;
import server.rest.ValidationUtil;
import server.temporaries.SMSAuth;
import utils.Log;
import utils.MailSender;

import javax.xml.crypto.Data;
import java.text.ParseException;
import java.util.*;

public class UserSVC extends BaseService {

    public DataMap turnOnPush(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.turnOnPush(id);
            sqlSession.commit();

            final DataMap map = getUserByKey(id);
            DataMapUtil.mask(map, "password");
            return map;
        }
    }

    public DataMap turnOffPush(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.turnOffPush(id);
            sqlSession.commit();

            final DataMap map = getUserByKey(id);
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

    public int joinUser(DataMap map){
        final String password = RestUtil.getMessageDigest(map.getString("password"));
        final String phone = map.getString("phone").replaceAll("-", "");
        final String type = map.getString("type");
        int lastId = 0;

        map.put("password", password);

        if(ValidationUtil.validate(phone, ValidationUtil.ValidationType.Phone)){
            try(SqlSession sqlSession = super.getSession()){
                UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
                final DataMap preProcessUser = userMapper.getUserByPhone(phone);
                if(preProcessUser != null) return ResponseConst.CODE_ALREADY_EXIST;
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
                final int gearId = map.getInt("gearId");
                final String attachment = map.getString("attachment");

                joinGear(lastId, region, gearId, attachment);
            }


            return ResponseConst.CODE_SUCCESS;
        }
        return ResponseConst.CODE_FAILURE;
    }


    private void joinMan(int userId, int[] region, int[] work, int[] career, String welderType){
        try(SqlSession sqlSession = super.getSession()){
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

    private void joinGear(int userId, int[] region, int gearId, String attachment){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            for(int i=0; i<region.length; i++)
                userMapper.setUserRegion(userId, region[i]);

            userMapper.setUserGear(userId, gearId, attachment);
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

            searchGear(lastId, gearId, attachment);
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
            final Iterator<DataMap> iterator = workList.iterator();
            while(iterator.hasNext()){
                final DataMap map = iterator.next();
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

    private void searchGear(int searchId, int gearId, String attachment){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            userMapper.setSearchGear(searchId, gearId, attachment);
            sqlSession.commit();

            List<DataMap> userList = userMapper.findGearMatch(gearId, attachment);
            final String title = "장비";
            String message = "";

            DataMap searchBasicInfo = userMapper.getSearchBasicInfo(searchId);
            message = "위치 " + searchBasicInfo.getString("gugunText") + "/ ";

            DataMap gearInfo = userMapper.getSearchGearInfo(searchId);
            message += gearInfo.getString("name") + "/ ";
            message += "작업기간 " + searchBasicInfo.getString("startDate") + " ~ " + searchBasicInfo.getString("endDate");

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
        final String pushKey = map.getString("pushKey");

        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            int id = userMapper.getUserIdByAccount(account, password);
            userMapper.updatePushKey(id, pushKey);

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

            final String type = userBasic.getString("type");

            if(type.equals("M")){
                List<DataMap> workInfo = userMapper.getUserWork(id);
                userBasic.put("workInfo", workInfo);
            }
            else if(type.equals("G")){
                DataMap gearInfo = userMapper.getUserGear(id);
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


    public static void main(String ... args){
        List<String> regKeys = new ArrayList<String>();
        regKeys.add("fmgs31uYE_Q:APA91bH-g2Pv7zgKhnjtKHkE9KEjdu2C0IzgH5HhoTnmUF-TA1Tdz-iqttohxkOLIoeB08zdh5qvmReACFzsS9Q3BHKVyT9w_6aje0sRZ8gTAxn277d7PAC6NAiXChrF3brFnnVo2-9u");
        PushManager.start("AAAALAuy9Ms:APA91bHvU-eINQYL59NviY_imyPrhNc76o_Kgb1J9GFv6LhYBl545-yfpHK6iShVUCsOrXNNcZdPznFzR4p5NBrFOnubcWD93DzxzyNG0yv3j5jNGg_X1fjT_jNYmTq8Bcr_IVv6fp3A");
        PushManager.getInstance().send(regKeys, "test", "testtesttest", null);
    }





































    public DataMap checkPassword(String id, String pw){
        pw = RestUtil.getMessageDigest(pw);
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            return userMapper.getUser(id, pw);
        }
    }

    public DataMap getUserAppendableData(int id){
        final DataMap toAppend = new DataMap();

        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            final List<DataMap> affiliation = userMapper.getWorkplaceList(id);
            int primaryCompany = -1;
            for(DataMap map : affiliation) if(map.getInt("isPrimary") == 1) primaryCompany = map.getInt("isPrimary");
            toAppend.put("affiliation", affiliation);
            toAppend.put("primary", getPrimaryWorkSpace(id));
            toAppend.put("current", primaryCompany != -1 ? userMapper.getUserCurrentDiligence(id, primaryCompany) : null);

        }

        return toAppend;
    }

    public DataMap loginWithApprovalToken(String id, String token){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            DataMap user = userMapper.getUserByApprovalCodeStateless(token);
            if(user != null) user = getUserByKey(user.getInt("id"));
            if(user != null) userMapper.updateLastLoginDate(user.getInt("id"));
            return user;
        }
    }

    public DataMap loginWeb(String id, String pw){
        pw = RestUtil.getMessageDigest(pw);
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            DataMap user = userMapper.getUser(id, pw);
            if(user != null) user = getUserByKey(user.getInt("id"));
            if(user != null) userMapper.updateLastLoginDate(user.getInt("id"));
            return user;
        }
    }

    public DataMap findEmail(String name, String phone){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            DataMap map = userMapper.findEmail(name, phone);
            if(map != null) map = getUserByKey(map.getInt("id"));
            DataMapUtil.mask(map, "password");
            return map;
        }
    }

    public void changePassword(int id, String newPassword){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.changePassword(id, RestUtil.getMessageDigest(newPassword));
            sqlSession.commit();
        }
    }

    public DataMap addWorkplace(int memberId, int companyId, int permission){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            int count = userMapper.getAffiliationCount(memberId, companyId);
            if(count > 0) return null;
            else{
                final DataMap param = new DataMap();
                param.put("memberId", memberId);
                param.put("companyId", companyId);
                param.put("permission", permission);
                userMapper.addWorkplace(param);
                sqlSession.commit();
                return getUserByKey(memberId);
            }
        }
    }

    public Boolean confirmWorkplaceToken(int memberId, int companyId, String token){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            String key = userMapper.getApprovalCode(companyId);

            if(key.equals(token)){
                userMapper.approveWorkplace(memberId, companyId);
                return true;
            }
            else return false;
        }
    }

    public int countDoorGesture(int memberId){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            int enabledGusture = userMapper.countDoorGesture(memberId);
            return enabledGusture;
        }
    }

    public boolean gestureDoor(int memberId, int gateId){
        if(countDoorGesture(memberId) > 0) return false;

        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.gestureDoor(memberId, gateId);
            sqlSession.commit();
        }
        return true;
    }

    public boolean undoGestureDoor(int memberId, int gateId){
        if(countDoorGesture(memberId) == 0) return false;

        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.undoGestureDoor(memberId, gateId);
            sqlSession.commit();
        }
        return true;
    }

    public int countDoorLikes(int memberId) {
        try (SqlSession sqlSession = super.getSession()) {
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            int likes = userMapper.countDoorLikes(memberId);
            return likes;
        }
    }

    public boolean likeDoor(int memberId, int gateId){
        if(countDoorLikes(memberId) >= 10) return false;

        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.likeDoor(memberId, gateId);
            sqlSession.commit();
        }
        return true;
    }

    public boolean unlikeDoor(int memberId, int gateId){
        if(countDoorLikes(memberId) == 0) return false;

        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.unlikeDoor(memberId, gateId);
            sqlSession.commit();
        }
        return true;
    }

    public DataMap changeName(int id, String newName){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.changeName(id, newName);
            sqlSession.commit();

            final DataMap map = getUserByKey(id);
            DataMapUtil.mask(map, "password");
            return map;
        }
    }

    public DataMap changePhone(int id, String newPhone){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.changePhone(id, newPhone.replaceAll("-", ""));
            sqlSession.commit();

            final DataMap map = getUserByKey(id);
            DataMapUtil.mask(map, "password");
            return map;
        }
    }

    public  DataMap turnOnAlarm(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.turnOnAlarm(id);
            sqlSession.commit();

            final DataMap map = getUserByKey(id);
            DataMapUtil.mask(map, "password");
            return map;
        }
    }

    public DataMap turnOffAlarm(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.turnOffAlarm(id);
            sqlSession.commit();

            final DataMap map = getUserByKey(id);
            DataMapUtil.mask(map, "password");
            return map;
        }
    }

    public boolean findPassword(DataMap params){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            DataMap map = userMapper.findPassword(params);
            if(map == null) return false;
            final String newPassword = RandomStringUtils.random(7, RestUtil.RANDOM_STRING_SET);
            userMapper.changePassword(map.getInt("id"), RestUtil.getMessageDigest(newPassword));
            MailSender.getInstance().sendAnEmail(map.getString("email"), "OTION 비밀번호 재발급 메일입니다.", "재발급 비밀번호 : " + newPassword);
            sqlSession.commit();
            return true;
        }
    }

    public List<DataMap> getWorkplaces(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            return userMapper.getWorkplaceList(id);
        }
    }

    public DataMap getWorkplace(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            return userMapper.getWorkplaceDetail(id);
        }
    }

    public DataMap getWorkplaceAdmin(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            return userMapper.getWorkplaceAdmin(id);
        }
    }

    public DataMap getUserByKey(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            DataMap map = userMapper.getUserByKey(id);
            map.put("summatives",  getUserAppendableData(id));
            return map;
        }
    }

    public DataMap getPrimaryWorkSpace(int id){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            DataMap map = userMapper.getPrimaryWorkPlace(id);
            return map;
        }
    }

    public boolean authEmailApprovalCode(String code){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            DataMap user = userMapper.getUserByApprovalCode(code);
            if(user == null) return false;
            userMapper.changeUserStatus(user.getInt("id"), 1);
            sqlSession.commit();
        }
        return true;
    }

    public void initUser(DataMap map){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.initUser(map);
            sqlSession.commit();
        }
    }

    public List<DataMap> getGateList(int companyId, int memberId){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            final List<DataMap> likedGates = userMapper.getLikedDoorList(memberId);
            final Set<Integer> likedNumbers = new HashSet<>();
            for(DataMap map : likedGates) likedNumbers.add(map.getInt("gateId"));
            List<DataMap> gates = userMapper.getDoorListOfCompany(companyId);
            for(DataMap map : gates) {
                map.put("liked", likedNumbers.contains(map.getInt("id")));
                map.put("gesture", likedNumbers.contains(map.getInt("id")));
            }
            return gates;
        }
    }

    public List<DataMap> getFavoredGateList(int memberId){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            final List<DataMap> likedGates = userMapper.getLikedDoorList(memberId);
            return likedGates;
        }
    }

    public DataMap getGateDetail(int gateId, int memberId){
        try(SqlSession sqlSession = super.getSession()) {
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

            final DataMap gateInfo = userMapper.getDoorDetail(gateId, memberId);
            final List<DataMap> entranceRange = userMapper.getEntranceRange(gateId);

            final DataMap retVal = new DataMap();
            retVal.put("gateInfo", gateInfo);
            retVal.put("entranceRange", entranceRange);

            return retVal;
        }
    }

    public boolean deleteWorkplace(int user, int company){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.deleteWorkplace(user, company);
            sqlSession.commit();
        }catch (Exception e){
            return false;
        }
        return true;
    }

    public DataMap getLatestDiligenceCompany(int user, int company){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            final DataMap retVal = userMapper.getLatestDiligenceCompany(user, company);
            return retVal;
        }
    }

    public DataMap getLatestDiligenceUser(int user){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            final List<DataMap> retVal = userMapper.getLatestDiligenceUser(user);
            DataMap latest = retVal.get(0);
            int prevType = -1;

            if(retVal.size() > 1) prevType = retVal.get(1).getInt("type");
            latest.put("prevType", prevType);
            return latest;
        }
    }

    public boolean manipulateDiligence(int memberId, int gateId, int classifier, int type){
        try(SqlSession sqlSession = super.getSession()){
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            userMapper.insertDiligence(memberId, gateId, classifier, type);
            sqlSession.commit();
        }
        return true;
    }
}
