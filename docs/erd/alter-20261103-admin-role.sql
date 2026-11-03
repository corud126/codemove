-- ============================================================
-- 변경 2026-11-03: 관리자 권한(ADMIN_ROLE) 추가 — revision-user.md Q12
-- 이미 ddl.sql · code-data.sql 을 실행한 DB 에만 실행한다 (새로 만드는 DB 는 ddl.sql · code-data.sql 에 반영돼 있음)
-- ============================================================

ALTER TABLE TBL_ADMIN ADD (ADMIN_ROLE CHAR(2) DEFAULT '02' NOT NULL);
COMMENT ON COLUMN TBL_ADMIN.ADMIN_ROLE IS '권한(ADMIN_ROLE: 01 마스터, 02 운영자)';

INSERT INTO TBL_CODE_GROUP (GROUP_CODE, GROUP_NAME) VALUES ('ADMIN_ROLE', '관리자 권한');
INSERT INTO TBL_CODE (GROUP_CODE, CODE, CODE_NAME, USER_NAME, SORT_ORDER) VALUES ('ADMIN_ROLE', '01', '마스터', NULL, 1);
INSERT INTO TBL_CODE (GROUP_CODE, CODE, CODE_NAME, USER_NAME, SORT_ORDER) VALUES ('ADMIN_ROLE', '02', '운영자', NULL, 2);

COMMIT;
