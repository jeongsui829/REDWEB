-- Run as the application schema owner, after backup, in a maintenance window.
-- Oracle DDL commits implicitly. Never run the fresh-install schema against existing data.
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET SERVEROUTPUT ON
DECLARE
    PROCEDURE ensure_index(p_name VARCHAR2, p_table VARCHAR2, p_columns VARCHAR2) IS
        found NUMBER;
    BEGIN
        SELECT COUNT(*) INTO found FROM (
            SELECT INDEX_NAME, LISTAGG(COLUMN_NAME, ', ') WITHIN GROUP (ORDER BY COLUMN_POSITION) cols
            FROM USER_IND_COLUMNS WHERE TABLE_NAME = p_table GROUP BY INDEX_NAME
        ) WHERE cols = p_columns;
        IF found = 0 THEN
            EXECUTE IMMEDIATE 'CREATE INDEX ' || p_name || ' ON ' || p_table || ' (' || p_columns || ')';
            DBMS_OUTPUT.PUT_LINE('Created index ' || p_name);
        END IF;
    END;
BEGIN
    ensure_index('IX_RW_POST_BOARD_FEED', 'RW_POST', 'BOARD_ID, STATUS, PINNED_YN, CREATED_AT');
    ensure_index('IX_RW_POST_USER_DATE', 'RW_POST', 'USER_ID, CREATED_AT');
    ensure_index('IX_RW_COMMENT_POST_STATUS', 'RW_COMMENT', 'POST_ID, STATUS, CREATED_AT');
    ensure_index('IX_RW_COMMENT_PARENT', 'RW_COMMENT', 'PARENT_COMMENT_ID');
    ensure_index('IX_RW_ATTACHMENT_SAVED', 'RW_ATTACHMENT', 'SAVED_NAME');
    ensure_index('IX_RW_ATTACHMENT_POST', 'RW_ATTACHMENT', 'POST_ID');
    ensure_index('IX_RW_ATTACHMENT_UPLOADER', 'RW_ATTACHMENT', 'UPLOADER_ID');
    ensure_index('IX_RW_MESSAGE_UNREAD', 'RW_MESSAGE', 'RECEIVER_USER_ID, READ_YN, CREATED_AT');
    ensure_index('IX_RW_NOTIFICATION_UNREAD', 'RW_NOTIFICATION', 'USER_ID, READ_YN, CREATED_AT');
    ensure_index('IX_RW_GUESTBOOK_OWNER_DATE', 'RW_GUESTBOOK', 'OWNER_USER_ID, CREATED_AT');
    ensure_index('IX_RW_REPORT_STATUS_DATE', 'RW_REPORT', 'STATUS, CREATED_AT');
END;
/
