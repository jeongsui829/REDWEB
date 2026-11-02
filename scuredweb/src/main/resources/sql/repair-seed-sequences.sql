-- Advance sequences used by fixed-ID seed rows without changing existing data.
-- Stop application writes while this script runs.
SET SERVEROUTPUT ON SIZE UNLIMITED
SET VERIFY OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

DECLARE
    PROCEDURE repair_sequence(
        p_sequence_name IN VARCHAR2,
        p_table_name    IN VARCHAR2,
        p_id_column     IN VARCHAR2
    ) IS
        v_before_last    NUMBER;
        v_after_last     NUMBER;
        v_max_id         NUMBER;
        v_target_next    NUMBER;
        v_current_value  NUMBER;
        v_desired_value  NUMBER;
        v_increment      NUMBER;
    BEGIN
        SELECT LAST_NUMBER
          INTO v_before_last
          FROM USER_SEQUENCES
         WHERE SEQUENCE_NAME = UPPER(p_sequence_name);

        EXECUTE IMMEDIATE
            'SELECT NVL(MAX(' || p_id_column || '), 0) FROM ' || p_table_name
            INTO v_max_id;

        v_target_next := v_max_id + 1;
        DBMS_OUTPUT.PUT_LINE(
            'BEFORE ' || p_sequence_name
            || ': LAST_NUMBER=' || TO_CHAR(v_before_last)
            || ', MAX_ID=' || TO_CHAR(v_max_id)
            || ', TARGET_NEXT=' || TO_CHAR(v_target_next)
        );

        IF v_before_last < v_target_next THEN
            EXECUTE IMMEDIATE
                'SELECT ' || p_sequence_name || '.NEXTVAL FROM DUAL'
                INTO v_current_value;

            v_desired_value := v_target_next - 1;
            IF v_current_value < v_desired_value THEN
                v_increment := v_desired_value - v_current_value;
                EXECUTE IMMEDIATE
                    'ALTER SEQUENCE ' || p_sequence_name
                    || ' INCREMENT BY ' || TO_CHAR(v_increment);
                EXECUTE IMMEDIATE
                    'SELECT ' || p_sequence_name || '.NEXTVAL FROM DUAL'
                    INTO v_current_value;
            END IF;

            EXECUTE IMMEDIATE
                'ALTER SEQUENCE ' || p_sequence_name || ' INCREMENT BY 1';
            DBMS_OUTPUT.PUT_LINE(
                'ADVANCED ' || p_sequence_name
                || ': CURRENT_VALUE=' || TO_CHAR(v_current_value)
            );
        ELSE
            DBMS_OUTPUT.PUT_LINE('UNCHANGED ' || p_sequence_name || ': already safe.');
        END IF;

        SELECT LAST_NUMBER
          INTO v_after_last
          FROM USER_SEQUENCES
         WHERE SEQUENCE_NAME = UPPER(p_sequence_name);

        DBMS_OUTPUT.PUT_LINE(
            'AFTER  ' || p_sequence_name
            || ': LAST_NUMBER=' || TO_CHAR(v_after_last)
            || ', MAX_ID=' || TO_CHAR(v_max_id)
        );

        IF v_after_last < v_target_next THEN
            RAISE_APPLICATION_ERROR(
                -20002,
                p_sequence_name || ' remains below MAX(ID)+1.'
            );
        END IF;
    EXCEPTION
        WHEN OTHERS THEN
            BEGIN
                EXECUTE IMMEDIATE
                    'ALTER SEQUENCE ' || p_sequence_name || ' INCREMENT BY 1';
            EXCEPTION
                WHEN OTHERS THEN
                    NULL;
            END;
            DBMS_OUTPUT.PUT_LINE(
                'FAILED  ' || p_sequence_name || ': ' || SQLERRM
            );
            RAISE;
    END repair_sequence;
BEGIN
    repair_sequence('SEQ_RW_USER', 'RW_USER', 'USER_ID');
    repair_sequence('SEQ_RW_BOARD', 'RW_BOARD', 'BOARD_ID');
    repair_sequence('SEQ_RW_CATEGORY', 'RW_CATEGORY', 'CATEGORY_ID');
    repair_sequence('SEQ_RW_POST', 'RW_POST', 'POST_ID');
    repair_sequence('SEQ_RW_TAG', 'RW_TAG', 'TAG_ID');
    repair_sequence('SEQ_RW_USER_AUTH_LOG', 'RW_USER_AUTH_LOG', 'AUTH_LOG_ID');
END;
/

PROMPT Seed-related sequence repair completed.
