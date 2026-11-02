-- Add persistent suspension expiry support to an existing REDWEB schema.
SET SERVEROUTPUT ON SIZE UNLIMITED
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

DECLARE
    v_column_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_column_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'RW_USER'
       AND COLUMN_NAME = 'SUSPENDED_UNTIL';

    IF v_column_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE RW_USER ADD (SUSPENDED_UNTIL TIMESTAMP)';
        DBMS_OUTPUT.PUT_LINE('Added RW_USER.SUSPENDED_UNTIL.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('RW_USER.SUSPENDED_UNTIL already exists; unchanged.');
    END IF;
END;
/

PROMPT Suspension expiry migration completed. DDL changes cannot be rolled back.
