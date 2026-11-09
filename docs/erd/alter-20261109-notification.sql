-- ============================================================
-- 변경 2026-11-09: 알림 조회 인덱스 — admin-screen-mapping.md SCR-A-013~015 · A-019, revision-user.md (알림 단계)
-- 이미 ddl.sql 을 실행한 DB 에만 실행한다 (새로 만드는 DB 는 ddl.sql 에 반영돼 있음). 컬럼·코드 변경 없음
--  - IX_NTF_RECEIVER_CREATED: 관리자 알림 패널(관리자 수신 최근 30일) · 발송 내역(고객 수신 최근 30일) · 오늘 발송 · 보류 수
--  - IX_NTF_MEMBER: 사용자 알림 화면 · 헤더 종 안 읽은 수 · 발송 내역 이름 검색 · D-3 중복 확인
-- ============================================================

CREATE INDEX IX_NTF_RECEIVER_CREATED ON TBL_NOTIFICATION (RECEIVER_TYPE, CREATED_AT);
CREATE INDEX IX_NTF_MEMBER ON TBL_NOTIFICATION (MEMBER_ID);
