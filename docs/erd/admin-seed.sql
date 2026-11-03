-- ============================================================
-- CodeMove 관리자 초기 계정 (Oracle 11g XE)
-- 관리자 계정은 가입 화면이 없으므로 DB 에 직접 등록한다. 실행 순서: ddl.sql → code-data.sql → admin-seed.sql
-- PASSWORD 는 BCrypt 해시. PW_CHANGED_AT 을 비워 두면 첫 로그인 시 비밀번호 변경 화면으로 간다.
-- ADMIN_ROLE: 01 마스터 · 02 운영자. 운영자 관리 화면이 생기기 전까지 운영자도 같은 형식으로 INSERT 한다.
--
-- 비밀번호는 저장소에 남기지 않는다 (평문 · 해시 모두).
-- 실행 전에 등록하는 사람이 초기 비밀번호를 정하고, 그 BCrypt 해시를 <BCRYPT_HASH> 자리에 넣어 실행한 뒤 파일은 되돌린다.
--   해시 만들기 예: jshell --class-path <spring-security-crypto · spring-core · commons-logging jar>
--                  new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("초기비밀번호")
-- 초기 비밀번호는 팀에 따로 전달하고, 첫 로그인에서 바로 바꾼다.
-- ============================================================

-- 마스터 계정: 아이디 master
INSERT INTO TBL_ADMIN (ADMIN_ID, LOGIN_ID, PASSWORD, ADMIN_NAME, PHONE, ADMIN_ROLE)
SELECT SEQ_ADMIN.NEXTVAL, 'master', '<BCRYPT_HASH>', '마스터', '010-0000-0000', '01'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TBL_ADMIN WHERE LOGIN_ID = 'master');

COMMIT;
