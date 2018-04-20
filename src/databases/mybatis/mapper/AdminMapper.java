package databases.mybatis.mapper;

import org.apache.ibatis.annotations.Param;
import server.comm.DataMap;
import sun.util.resources.cldr.en.CalendarData_en_MP;

import java.util.List;

public interface AdminMapper {
    DataMap getAdminByAccount(@Param("account") String account, @Param("password") String password);

    List<DataMap> getUserList(@Param("page") int page, @Param("limit") int limit, @Param("account") String account, @Param("phone") String phone);

    Integer getUserCount(@Param("account") String account, @Param("phone") String phone);
}
