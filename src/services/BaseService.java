package services;

import databases.mybatis.DBSessionWrapper;
import org.apache.ibatis.session.SqlSession;

public class BaseService extends DBSessionWrapper {

    public interface ToDo<T>{
        T fire(SqlSession sqlSession);
    }

    public <T> T provide(ToDo<T> toDo){
        try(SqlSession sqlSession = super.getSession()) {
            T retVal = toDo.fire(sqlSession);
            sqlSession.commit();
            return retVal;
        }
    }

}
