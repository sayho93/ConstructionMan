package services;

import databases.mybatis.mapper.AdminMapper;
import databases.paginator.ListBox;
import databases.paginator.PageInfo;
import org.apache.ibatis.session.SqlSession;
import server.comm.DataMap;
import server.rest.DataMapUtil;
import server.rest.RestUtil;

import java.util.List;

public class AdminSVC extends BaseService {
    public DataMap adminLogin(DataMap map){
        final String account = map.getString("account");
        final String password = RestUtil.getMessageDigest(map.getString("password"));

        try(SqlSession sqlSession = super.getSession()){
            AdminMapper adminMapper = sqlSession.getMapper(AdminMapper.class);

            DataMap adminInfo = adminMapper.getAdminByAccount(account, password);
            DataMapUtil.mask(adminInfo, "password");
            return adminInfo;
        }
    }

    public ListBox getUserList(int page, int limit, String account, String phone){
        int realPage = (page - 1) * limit;
        int total;
        List<DataMap> list;
        try(SqlSession sqlSession = super.getSession()){
            AdminMapper adminMapper = sqlSession.getMapper(AdminMapper.class);
            list = adminMapper.getUserList(realPage, limit, account, phone);
            total = adminMapper.getUserCount(account, phone);
        }
        PageInfo pageInfo = new PageInfo(limit, page);
        pageInfo.commit(total);

        ListBox listBox = new ListBox(pageInfo, list);
        return listBox;
    }
}
