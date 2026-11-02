import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class OracleSchemaProbe {
    private OracleSchemaProbe() {
    }

    public static void main(String[] args) throws Exception {
        try (Connection connection = VerificationDatabase.connect()) {
            printUserForeignKeys(connection);
            printSequenceState(connection);
            printVerificationResidue(connection);
        }
    }

    private static void printUserForeignKeys(Connection connection) throws Exception {
        String sql = """
                SELECT C.CONSTRAINT_NAME, C.TABLE_NAME, CC.COLUMN_NAME, C.DELETE_RULE
                  FROM USER_CONSTRAINTS C
                  JOIN USER_CONS_COLUMNS CC ON CC.CONSTRAINT_NAME = C.CONSTRAINT_NAME
                  JOIN USER_CONSTRAINTS P ON P.CONSTRAINT_NAME = C.R_CONSTRAINT_NAME
                 WHERE C.CONSTRAINT_TYPE = 'R'
                   AND P.TABLE_NAME = 'RW_USER'
                 ORDER BY C.TABLE_NAME, CC.POSITION
                """;
        System.out.println("USER_FK_BEGIN");
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                System.out.printf("%s|%s|%s|%s%n",
                        resultSet.getString(1),
                        resultSet.getString(2),
                        resultSet.getString(3),
                        resultSet.getString(4));
            }
        }
        System.out.println("USER_FK_END");
    }

    private static void printSequenceState(Connection connection) throws Exception {
        String sql = """
                SELECT 'SEQ_RW_USER', LAST_NUMBER, (SELECT NVL(MAX(USER_ID), 0) FROM RW_USER)
                  FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_RW_USER'
                UNION ALL
                SELECT 'SEQ_RW_BOARD', LAST_NUMBER, (SELECT NVL(MAX(BOARD_ID), 0) FROM RW_BOARD)
                  FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_RW_BOARD'
                UNION ALL
                SELECT 'SEQ_RW_CATEGORY', LAST_NUMBER, (SELECT NVL(MAX(CATEGORY_ID), 0) FROM RW_CATEGORY)
                  FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_RW_CATEGORY'
                UNION ALL
                SELECT 'SEQ_RW_POST', LAST_NUMBER, (SELECT NVL(MAX(POST_ID), 0) FROM RW_POST)
                  FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_RW_POST'
                UNION ALL
                SELECT 'SEQ_RW_ATTACHMENT', LAST_NUMBER, (SELECT NVL(MAX(ATTACH_ID), 0) FROM RW_ATTACHMENT)
                  FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_RW_ATTACHMENT'
                UNION ALL
                SELECT 'SEQ_RW_TAG', LAST_NUMBER, (SELECT NVL(MAX(TAG_ID), 0) FROM RW_TAG)
                  FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_RW_TAG'
                UNION ALL
                SELECT 'SEQ_RW_USER_AUTH_LOG', LAST_NUMBER, (SELECT NVL(MAX(AUTH_LOG_ID), 0) FROM RW_USER_AUTH_LOG)
                  FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_RW_USER_AUTH_LOG'
                ORDER BY 1
                """;
        System.out.println("SEQUENCE_STATE_BEGIN");
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                System.out.printf("%s|LAST_NUMBER=%d|MAX_ID=%d%n",
                        resultSet.getString(1), resultSet.getLong(2), resultSet.getLong(3));
            }
        }
        System.out.println("SEQUENCE_STATE_END");
    }

    private static void printVerificationResidue(Connection connection) throws Exception {
        String sql = "SELECT USER_ID, LOGIN_ID FROM RW_USER "
                + "WHERE LOGIN_ID LIKE 'verify\\_%' ESCAPE '\\' ORDER BY USER_ID";
        System.out.println("VERIFICATION_RESIDUE_BEGIN");
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                System.out.printf("USER_ID=%d|LOGIN_ID=%s%n", resultSet.getLong(1), resultSet.getString(2));
            }
        }
        System.out.println("VERIFICATION_RESIDUE_END");
    }
}
