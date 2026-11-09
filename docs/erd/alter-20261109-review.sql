-- ============================================================
-- 변경 2026-11-09: 후기 사진 순서 제약 — revision-user.md TBL_REVIEW_PHOTO
-- 이미 ddl.sql 을 실행한 DB 에만 실행한다 (새로 만드는 DB 는 ddl.sql 에 반영돼 있음)
-- 후기당 최대 5장 · SORT_ORDER 1~5 를 DB 에서도 보장한다. 앱(ReviewService)은 이 제약 없이도 같은 규칙으로 동작한다
-- 실행 전 확인: SELECT REVIEW_ID, SORT_ORDER, COUNT(*) FROM TBL_REVIEW_PHOTO GROUP BY REVIEW_ID, SORT_ORDER HAVING COUNT(*) > 1;  (0건이어야 함)
-- ============================================================

ALTER TABLE TBL_REVIEW_PHOTO ADD CONSTRAINT CK_REVIEW_PHOTO_SORT CHECK (SORT_ORDER BETWEEN 1 AND 5);
-- (REVIEW_ID, SORT_ORDER) 유니크 인덱스가 후기별 사진 조회 인덱스도 겸한다
ALTER TABLE TBL_REVIEW_PHOTO ADD CONSTRAINT UK_REVIEW_PHOTO_SORT UNIQUE (REVIEW_ID, SORT_ORDER);
