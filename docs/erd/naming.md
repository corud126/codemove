# DB 명명 규칙

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| 테이블 | `TBL_` + 대문자 스네이크 | `TBL_MOVE_REQUEST` |
| 시퀀스 | 테이블명의 `TBL_` → `SEQ_` | `SEQ_MOVE_REQUEST` |
| PK 채번 | `SEQ_xxx.NEXTVAL` (Oracle 11g는 IDENTITY 미지원) | `SEQ_MEMBER.NEXTVAL` |

시퀀스 생성 스크립트: [`sequence.sql`](sequence.sql)

## ERD 오타 수정 (2026-11-02 확정)

ERD.pdf의 아래 이름은 오타이며, DDL·코드에서는 수정된 이름을 사용합니다.

| 테이블 | ERD 표기 | 수정 |
| --- | --- | --- |
| TBL_SCHEDULE | `SCHDULE_ID` | `SCHEDULE_ID` |
| TBL_SCHEDULE | `SCHDULE_DATE` | `SCHEDULE_DATE` |
| TBL_SCHEDULE | `SCHDULE_TIME` | `SCHEDULE_TIME` |
| TBL_RESERVATION | `SCHDULE_ID` (FK) | `SCHEDULE_ID` |
| TBL_MOVE_REQUEST | `REQUSTED_AT` | `REQUESTED_AT` |
