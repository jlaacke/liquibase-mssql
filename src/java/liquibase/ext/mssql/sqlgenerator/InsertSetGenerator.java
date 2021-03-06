package liquibase.ext.mssql.sqlgenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import liquibase.database.Database;
import liquibase.database.core.MSSQLDatabase;
import liquibase.exception.ValidationErrors;
import liquibase.ext.mssql.statement.InsertSetStatementMSSQL;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.statement.core.InsertSetStatement;

public class InsertSetGenerator extends liquibase.sqlgenerator.core.InsertSetGenerator {
    public static final String IF_TABLE_HAS_IDENTITY_STATEMENT = "IF ((select objectproperty(\n"
                    + "            object_id(N'${schemaName}.${tableName}'),\n"
                    + "           'TableHasIdentity')) = 1)\n" + "\t${then}\n";

    @Override
    public int getPriority() {
        return 15;
    }

    public boolean supports(InsertSetStatement statement, Database database) {
        return database instanceof MSSQLDatabase;
    }

    public ValidationErrors validate(InsertSetStatement statement, Database database,
                    SqlGeneratorChain sqlGeneratorChain) {
        return sqlGeneratorChain.validate(statement, database);
    }

    @Override
    public Sql[] generateSql(InsertSetStatement statement, Database database, SqlGeneratorChain sqlGeneratorChain) {
        Boolean identityInsertEnabled = false;
        if (statement instanceof InsertSetStatementMSSQL) {
            identityInsertEnabled = ((InsertSetStatementMSSQL) statement).getIdentityInsertEnabled();
        }
        if (identityInsertEnabled == null || !identityInsertEnabled) {
            return super.generateSql(statement, database, sqlGeneratorChain);
        }
        String tableName = database.escapeTableName(statement.getCatalogName(), statement.getSchemaName(),
                        statement.getTableName());
        String enableIdentityInsert = "SET IDENTITY_INSERT " + tableName + " ON";
        String disableIdentityInsert = "SET IDENTITY_INSERT " + tableName + " OFF";
        String safelyEnableIdentityInsert = ifTableHasIdentityColumn(enableIdentityInsert, statement,
                        database.getDefaultSchemaName());
        String safelyDisableIdentityInsert = ifTableHasIdentityColumn(disableIdentityInsert, statement,
                        database.getDefaultSchemaName());

        List<Sql> sql = new ArrayList<Sql>(Arrays.asList(sqlGeneratorChain.generateSql(statement, database)));
        sql.add(0, new UnparsedSql(safelyEnableIdentityInsert));
        sql.add(new UnparsedSql(safelyDisableIdentityInsert));
        return sql.toArray(new Sql[sql.size()]);
    }

    private String ifTableHasIdentityColumn(String then, InsertSetStatement statement, String defaultSchemaName) {
        String tableName = statement.getTableName();
        String schemaName = statement.getSchemaName();
        if (schemaName == null) {
            if (defaultSchemaName != null && !defaultSchemaName.isEmpty()) {
                schemaName = defaultSchemaName;
            } else {
                schemaName = "dbo";
            }
        }

        Map<String, String> tokens = new HashMap<String, String>();
        tokens.put("${tableName}", tableName);
        tokens.put("${schemaName}", schemaName);
        tokens.put("${then}", then);
        return performTokenReplacement(IF_TABLE_HAS_IDENTITY_STATEMENT, tokens);
    }

    private String performTokenReplacement(String input, Map<String, String> tokens) {
        String result = input;
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}