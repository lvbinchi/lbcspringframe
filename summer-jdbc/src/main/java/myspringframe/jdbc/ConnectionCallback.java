package myspringframe.jdbc;


import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface ConnectionCallback<T> {
    T doInConnection(Connection con) throws SQLException;
}
