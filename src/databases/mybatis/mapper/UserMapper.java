package databases.mybatis.mapper;

import org.apache.ibatis.annotations.Param;
import server.comm.DataMap;
import sun.util.resources.cldr.en.CalendarData_en_MP;

import java.util.List;

public interface UserMapper {

    void turnOnPush(@Param("id") int id);

    void turnOffPush(@Param("id") int id);

    DataMap getUserByPhone(@Param("phone") String phone);

    void registerUserBasic(DataMap map);

    DataMap getUserByAccount(@Param("account") String account);

    void setUserRegion(@Param("userId") int userId, @Param("gugunId") int gugunId);

    void setUserWork(@Param("userId") int userId, @Param("workId") int workId, @Param("career") int career, @Param("welderType") String welderType);

    void setUserGear(@Param("userId") int userId, @Param("gearId") int gearId, @Param("attachment") String attachment);

    void registerSearchBasic(DataMap map);

    void setSearchWork(@Param("searchId") int searchId, @Param("workId") int workId, @Param("career") int career, @Param("welderType") String welderType);

    void setSearchGear(@Param("searchId") int searchId, @Param("gearId") int gearId, @Param("attachment") String attachment);

    List<DataMap> findManMatch(@Param("searchId") int searchId, @Param("allType") int allType, @Param("gugunId") int gugunId);

    List<DataMap> findGearMatch(@Param("gearId") int gearId, @Param("attachmentArr") String[] attachmentArr, @Param("gugunId") int gugunId);

    int getUserIdByAccount(@Param("account") String account, @Param("password") String password);

    DataMap getUserByAccountPass(@Param("account") String account, @Param("password") String password);

    void updatePushKey(@Param("id") int id, @Param("pushKey") String pushKey);

    DataMap getUserById(@Param("id") int id);

    List<DataMap> getUserRegion(@Param("userId") int userId);

    List<DataMap> getUserGear(@Param("userId") int userId);

    List<DataMap> getUserWork(@Param("userId") int userId);

    DataMap getSearchBasicInfo(@Param("searchId") int searchId);

    List<DataMap> getSearchManInfo(@Param("searchId") int searchId);

    DataMap getSearchGearInfo(@Param("searchId") int searchId);

    void deleteUserRegion(@Param("id") int id);

    void deleteUserWork(@Param("id") int id);

    void deleteUserGear(@Param("id") int id);

    void applySearch(@Param("userId") int userId, @Param("searchId") int searchId);

    void updateUserName(@Param("userId") int userId, @Param("name") String name);

    void withdrawUser(@Param("userId") int userId);

    DataMap getUserByNamePhone(@Param("name") String name, @Param("phone") String phone);

    DataMap getUserByAccountPhone(@Param("name") String name, @Param("phone") String phone, @Param("account") String account);

    void changePassword(@Param("id") int id, @Param("pw") String pw);

    void updateUserImg(@Param("id") int id, @Param("imgPath") String imgPath);

    Integer getUserPoint(@Param("id") int id);

    void addPointHistory(@Param("id") int id,
                         @Param("inc") int inc,
                         @Param("payType") int payType,
                         @Param("paymentId") int paymentId,
                         @Param("comment") String comment);

    List<DataMap> getPointList(@Param("id") int id);

    List<DataMap> getApplyList(@Param("id") int id);

    Integer getPaymentStatus(@Param("searchUserId") int searchUserId, @Param("searchId") int searchId, @Param("start") int start, @Param("end") int end);

    void saveComment(@Param("searchId") int searchId, @Param("comment") String comment);

    DataMap getAppLimit(@Param("userId") int userId);

    Integer getAppCount(@Param("userId") int userId);

    List<DataMap> getApps(@Param("userId") int userId, @Param("start") int start, @Param("end") int end);

    void hidePointHistory(@Param("id") int id);

    void hideApplyHistory(@Param("id") int id);

    int getAppCountBySearchId(@Param("searchId") int searchId);

    void addExposure(@Param("id") int id, @Param("amount") int amount);
}
