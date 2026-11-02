-- Additive, rerunnable repair for the confirmed board list/detail failure.
-- This script does not drop, truncate, delete, or recreate existing objects.

SET SERVEROUTPUT ON

DECLARE
    v_table_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_table_count
      FROM USER_TABLES
     WHERE TABLE_NAME = 'RW_POST_REACTION';

    IF v_table_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE RW_POST_REACTION (
                POST_ID NUMBER NOT NULL,
                USER_ID NUMBER NOT NULL,
                REACTION_TYPE VARCHAR2(10) NOT NULL,
                CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
                CONSTRAINT PK_RW_POST_REACTION PRIMARY KEY (POST_ID, USER_ID),
                CONSTRAINT FK_RW_POST_REACTION_POST FOREIGN KEY (POST_ID) REFERENCES RW_POST (POST_ID),
                CONSTRAINT FK_RW_POST_REACTION_USER FOREIGN KEY (USER_ID) REFERENCES RW_USER (USER_ID),
                CONSTRAINT CK_RW_POST_REACTION_TYPE CHECK (REACTION_TYPE IN (''LIKE'', ''DISLIKE''))
            )';
        DBMS_OUTPUT.PUT_LINE('Created RW_POST_REACTION.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('RW_POST_REACTION already exists; no table change.');
    END IF;
END;
/

DECLARE
    v_sequence_count NUMBER;
    v_current_value NUMBER;
    v_target_value NUMBER;
    v_increment NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_sequence_count
      FROM USER_SEQUENCES
     WHERE SEQUENCE_NAME = 'SEQ_RW_POST';

    IF v_sequence_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20001, 'SEQ_RW_POST is missing; create it after manual review.');
    END IF;

    SELECT NVL(MAX(POST_ID), 0) + 1
      INTO v_target_value
      FROM RW_POST;

    SELECT SEQ_RW_POST.NEXTVAL
      INTO v_current_value
      FROM DUAL;

    IF v_current_value < v_target_value THEN
        v_increment := v_target_value - v_current_value;
        EXECUTE IMMEDIATE 'ALTER SEQUENCE SEQ_RW_POST INCREMENT BY ' || TO_CHAR(v_increment);
        SELECT SEQ_RW_POST.NEXTVAL INTO v_current_value FROM DUAL;
        EXECUTE IMMEDIATE 'ALTER SEQUENCE SEQ_RW_POST INCREMENT BY 1';
        DBMS_OUTPUT.PUT_LINE('Advanced SEQ_RW_POST to ' || TO_CHAR(v_current_value) || '.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('SEQ_RW_POST is already safe at ' || TO_CHAR(v_current_value) || '.');
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        BEGIN
            EXECUTE IMMEDIATE 'ALTER SEQUENCE SEQ_RW_POST INCREMENT BY 1';
        EXCEPTION
            WHEN OTHERS THEN NULL;
        END;
        RAISE;
END;
/

SELECT SEQUENCE_NAME, LAST_NUMBER
FROM USER_SEQUENCES
WHERE SEQUENCE_NAME = 'SEQ_RW_POST';

SELECT MAX(POST_ID) AS MAX_POST_ID
FROM RW_POST;

