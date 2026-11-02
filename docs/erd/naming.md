# DB 명명 규칙

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| 테이블 | `TBL_` + 대문자 스네이크 | `TBL_MOVE_REQUEST` |
| 시퀀스 | 테이블명의 `TBL_` → `SEQ_` | `SEQ_MOVE_REQUEST` |
| PK 채번 | `SEQ_xxx.NEXTVAL` (Oracle 11g는 IDENTITY 미지원) | `SEQ_MEMBER.NEXTVAL` |

시퀀스 생성 스크립트: [`sequence.sql`](sequence.sql)

| 문서 | 내용 |
| --- | --- |
| [`revision-user.md`](revision-user.md) | ERD 수정안 확정본 (공통코드·테이블별 변경 내역·화면 수정 목록) |
| [`ddl.sql`](ddl.sql) | 확정안 기준 DDL (테이블·FK·인덱스·주석) |
| [`code-data.sql`](code-data.sql) | 공통코드 초기 데이터 |

실행 순서: `sequence.sql` → `ddl.sql` → `code-data.sql`

## ERD 오타 수정 (2026-11-02 확정)

ERD.pdf의 아래 이름은 오타이며, DDL·코드에서는 수정된 이름을 사용합니다.

| 테이블 | ERD 표기 | 수정 |
| --- | --- | --- |
| TBL_SCHEDULE | `SCHDULE_ID` | `SCHEDULE_ID` |
| TBL_SCHEDULE | `SCHDULE_DATE` | `SCHEDULE_DATE` |
| TBL_SCHEDULE | `SCHDULE_TIME` | `SCHEDULE_TIME` |
| TBL_RESERVATION | `SCHDULE_ID` (FK) | `SCHEDULE_ID` (수정안에서 컬럼 삭제) |
| TBL_MOVE_REQUEST | `REQUSTED_AT` | `REQUESTED_AT` |
