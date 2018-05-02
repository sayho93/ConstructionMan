package databases.mybatis.mapper;

import org.apache.ibatis.annotations.Param;
import server.comm.DataMap;

import java.util.List;

public interface CommMapper {

    List<DataMap> getSidoList();

    List<DataMap> getGugunList(@Param("sidoID") int sidoID);

    List<DataMap> getWorkInfo(@Param("list") int[] list);

    List<DataMap> getGearOption1(@Param("name") String name);

    List<DataMap> getGearOption2(@Param("name") String name, @Param("detail") String detail);

}
