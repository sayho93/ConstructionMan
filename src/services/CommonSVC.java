package services;

import databases.mybatis.mapper.CommMapper;
import databases.mybatis.mapper.DiligenceMapper;
import databases.mybatis.mapper.UserMapper;
import databases.paginator.ListBox;
import databases.paginator.PageInfo;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.ibatis.session.SqlSession;
import server.comm.DataMap;
import server.rest.DataMapUtil;

import java.util.*;

public class CommonSVC extends BaseService {

    public List<DataMap> getSidoList(){
        try(SqlSession sqlSession = super.getSession()){
            CommMapper commMapper = sqlSession.getMapper(CommMapper.class);
            return commMapper.getSidoList();
        }
    }

    public List<DataMap> getGugunList(int sidoID){
        try(SqlSession sqlSession = super.getSession()){
            CommMapper commMapper = sqlSession.getMapper(CommMapper.class);
            return commMapper.getGugunList(sidoID);
        }
    }

    public List<DataMap> getWorkInfo(int[] work){
        try(SqlSession sqlSession = super.getSession()){
            CommMapper commMapper = sqlSession.getMapper(CommMapper.class);

            List<DataMap> list = commMapper.getWorkInfo(work);

            final Map<Integer, Integer> orderMap = new HashMap<>();

            for(int e = 0; e < work.length; e++) orderMap.put(work[e], e);

            Comparator<DataMap> comparator = (o1, o2) -> {
                int id1 = o1.getInt("id");
                int id2 = o2.getInt("id");
                int orderOfId1 = orderMap.get(id1);
                int orderOfId2 = orderMap.get(id2);
                if(orderOfId1 > orderOfId2) return 1;
                else if(orderOfId1 < orderOfId2) return -1;
                else return 0;
            };

            Collections.sort(list, comparator);

            return list;
        }
    }

    public List<DataMap> getGearOption1(String name){
        try(SqlSession sqlSession = super.getSession()){
            CommMapper commMapper = sqlSession.getMapper(CommMapper.class);

            List<DataMap> list = commMapper.getGearOption1(name);
            return list;
        }
    }

    public List<DataMap> getGearOption2(String name, String detail){
        try(SqlSession sqlSession = super.getSession()){
            CommMapper commMapper = sqlSession.getMapper(CommMapper.class);

            List<DataMap> list = commMapper.getGearOption2(name, detail);
            return list;
        }
    }
}
