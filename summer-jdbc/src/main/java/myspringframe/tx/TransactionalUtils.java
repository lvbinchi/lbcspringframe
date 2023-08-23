package myspringframe.tx;

import java.sql.Connection;

public class TransactionalUtils {
    public static Connection getCurrentConnection() {
        TransactionStatus ts = DataSourceTransactionManager.transactionStatus.get();
        return ts == null ? null : ts.connection;
    }
}
