package services;

import databases.mybatis.mapper.CommMapper;
import databases.mybatis.mapper.DiligenceMapper;
import databases.mybatis.mapper.UserMapper;
import databases.paginator.ListBox;
import databases.paginator.PageInfo;
import org.apache.ibatis.session.SqlSession;
import server.comm.DataMap;
import server.rest.DataMapUtil;

import java.util.List;
import java.util.Vector;

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
}
