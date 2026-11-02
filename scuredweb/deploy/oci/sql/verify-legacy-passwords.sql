-- COUNT only: never SELECT or log a password/hash value.
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET SERVEROUTPUT ON
SET VERIFY OFF
DECLARE
    legacy_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO legacy_count FROM RW_USER
    WHERE PASSWORD_HASH IS NULL OR PASSWORD_HASH NOT LIKE 'pbkdf2$%';
    DBMS_OUTPUT.PUT_LINE('LEGACY_PASSWORD_COUNT=' || legacy_count);
    IF legacy_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20001, 'Legacy passwords remain; reset or migrate before public deployment');
    END IF;
END;
/
