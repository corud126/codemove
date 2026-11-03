# 관리자 화면 ↔ ERD 컬럼 대조표

관리자 화면(SCR-A-xxx)이 어떤 테이블·컬럼을 보여 주고 입력받는지 정리한 표입니다. 기준: `ddl.sql`, `code-data.sql`.

- **표시**: 화면에 보여 준다 · **입력**: 화면에서 입력/수정한다 · **검색/필터**: 목록 조건으로 쓴다
- 내부 키(`*_ID`)·파일 경로·소셜번호처럼 업무상 의미가 없는 값은 화면에 보이지 않는다.
- **기록용 컬럼(수정·등록 일시 `UPDATED_AT`·`CREATED_AT`, 수정·등록 관리자 `ADMIN_ID`)은 DB 에만 남기고 화면에 보이지 않는다** (11/03 결정). 단 업무 날짜(견적일·신청일·가입일·발송 시각 등)는 보인다.
- 코드 컬럼은 `TBL_CODE.CODE_NAME`(관리자 이름)으로 표시한다.
- **목록은 한 줄 그대로 두고, 나머지 컬럼은 행을 눌러 여는 상세(화면·팝업)에 보여 준다.**

## SCR-A-023 회원 관리 — `TBL_MEMBER`, `TBL_SOCIAL`

| 컬럼 | 목록 | 상세 | 비고 |
| --- | --- | --- | --- |
| MEMBER_NAME | 표시·검색 | 표시 | |
| PHONE | 표시·검색 | 표시 | 아이디 대신 |
| TBL_SOCIAL.LOGIN_TYPE (01 카카오 · 02 네이버 · 03 애플) + 휴대전화 | — (상세에만) | 연결된 방식 전부 + CONNECTED_AT | 소셜 연결이 없으면 '휴대전화'. 여러 개 연결 가능 |
| IS_VERIFIED | — | 표시 (본인인증 완료/미인증) | |
| IS_ACTIVE | 맨 앞 칩(활성·비활성) · 필터(활성·비활성, 기본 활성) | 표시 | 관리자 화면에 삭제 기능 없음 (11/03 결정) |
| JOINED_AT | 표시·검색(날짜) | 표시 | |
| TERMS_AGREED_AT | — | 표시 | |
| WITHDRAWN_AT | — | 표시(탈퇴 회원만) | |

## SCR-A-001 견적 관리 — `TBL_QUOTE`

| 컬럼 | 목록 | 비고 |
| --- | --- | --- |
| STATUS (QUOTE_STATUS 01~04) | 칩·필터 | 적용 완료 |
| CONTACT_NAME(또는 회원 이름) | 표시·검색 | |
| ESTIMATE_DATE | 표시·검색 | |
| QUOTE_NO · SOURCE_TYPE(견적 요청·사진 등록·PDF 등록) · APPROVED_AT · EXPIRES_AT | — | 목록에는 넣지 않고, 행을 누르면 열리는 견적 수정(A-002) 상단 요약에 표시 |

## SCR-A-002 견적 수정 — `TBL_QUOTE`, `TBL_QUOTE_ITEM`, `TBL_QUOTE_OPTION`, `TBL_MOVE_REQUEST`, `TBL_REQUEST_ITEM`, `TBL_RE_VIDEO`

- 요청 정보(표시): HOUSE_TYPE, PACK_TYPE, MOVE_DATE·PREFER_TIME(희망일·희망 시간대), FROM/TO_ADDRESS + DETAIL, REQUESTED_AT, 영상(TBL_RE_VIDEO.FILE_NAME·UPLOADED_AT)
- AI 분석(표시): AI_CBM, AI_EST_AMOUNT·MIN·MAX, AI_ANALYSIS_SEC, AI_CONFIDENCE, 요청 품목(CATEGORY, ITEM_NAME, ITEM_SIZE 소·중·대, QUANTITY, VOLUME, CONFIDENCE, NEEDS_REVIEW, SOURCE AI 인식/사용자 추가)
- 견적서(입력): QUOTE_NO(표시), COMPANY_NAME, CONTACT_NAME, CONTACT_PHONE, ESTIMATE_DATE, FROM/TO_ADDRESS, WORK_DATE(없으면 승인 불가)·WORK_TIME(고정 시간대 08:00·10:00·13:00·15:00·17:00), TRANSPORT_DATE, WORK_TYPE(포장 방식: 일반·반포장·보관·포장), 출발/도착 각각 LADDER·ELEVATOR·STAIRS 사용, VOLUME_TON, NOTE(500), MOVING_COST, FROM/TO_LADDER_FLOOR·COST, TOTAL_AMOUNT, DEPOSIT_AMOUNT, BALANCE_AMOUNT
- 품목(입력): CATEGORY 4그룹(가구·전자기기·생활용품·기타 고정), ITEM_NAME, QUANTITY, UNIT
- 옵션(입력): QUOTE_OPTION 8종 × CHOICE1_YN·CHOICE2_YN·OPTION_COST
- 상태(표시): STATUS, APPROVED_AT, EXPIRES_AT. 견적 확정 후 [수정] 숨김

## SCR-A-003/004 견적서 등록 — `TBL_QUOTE`

- SOURCE_TYPE 02 사진 / 03 PDF 선택, 파일(DOCUMENT_PATH), 회원 연결(MEMBER_ID, 선택 — 없으면 CONTACT_NAME·CONTACT_PHONE 입력), ESTIMATE_DATE, STATUS, 금액 칸(선택)

## SCR-A-011 예약 관리 — `TBL_RESERVATION` (+ 견적·회원)

| 컬럼 | 목록 | 상세 |
| --- | --- | --- |
| RESERVATION_NO (ME-YYYYMMDD-NNNN) | 검색 | 표시 |
| 연결 견적 QUOTE_NO | — | 표시 |
| 회원 이름·휴대폰 | 표시·검색 | 표시 |
| STATUS (예약 완료·이사 완료·예약 취소) | 칩·필터는 예약 완료·예약 취소 둘만 (이사 완료는 예약 완료로 묶음) | 표시 (이사 완료 포함) |
| PAY_STATUS (결제 대기·완료·취소) | — | 표시 + [결제 완료] |
| MOVE_DATE (= 예약일) | 표시·검색 | 표시 |
| MOVE_TIME | — | 표시 · 일정 변경은 고정 시간대에서 선택 |
| 출발·도착 주소, 이사 유형·포장 방식 | — | 표시 |
| TOTAL·DEPOSIT·BALANCE_AMOUNT | — | 표시 |
| MOVE_MEMO | — | 표시 |
| APPLIED_AT, DEPOSIT_PAID_AT | — | 표시 |
| CANCELLED_AT, CANCELLED_BY (회원·관리자) | — | 취소 건만 표시 |

## SCR-A-012 리뷰 관리 — `TBL_REVIEW`, `TBL_REVIEW_PHOTO`

- 표시: 작성자(회원 이름), RATING(필터), CONTENT(1000), CREATED_AT, 사진(TBL_REVIEW_PHOTO, SORT_ORDER 순), 연결 예약(예약 번호·이사일·이사 유형)
- 답변: REPLY_CONTENT(입력, **500자**), REPLIED_AT·답변 관리자(표시), 답변 여부(필터 후보). 답변 등록 시 고객 알림 없음 — 사용자 후기 목록에 '사장님 답변'으로 표시

## SCR-A-005~007 채팅 — `TBL_CHAT_ROOM`, `TBL_CHAT_MESSAGE`

- 방(목록/상세 헤더): 회원 이름·휴대폰, STATUS(챗봇 응대·대기 중·상담 중·종료) 칩·필터, STARTED_AT, WAITING_AT(대기 시작), ENDED_AT, 담당 관리자(ADMIN_ID), 안 읽은 수(IS_READ)
- 메시지: SENDER_TYPE(고객·AI·관리자 — 관리자는 상담사 이름), MESSAGE_TYPE 02 파일(ATTACHMENT_NAME 표시·다운로드), AI 답변 주제(TOPIC_ID → 제목), SENT_AT, 읽음(IS_READ)
- 동작: 상담 종료(STATUS 04, ENDED_AT)

## SCR-A-008 채팅 운영 설정 — `TBL_CHAT_SETTING`

- 입력: BOT_ENABLED, CONSULT_ENABLED(화면 이름 '상담 운영' — 끄면 운영 시간이어도 상담원 연결을 받지 않음, 휴무 등), START_TIME·END_TIME 은 늘 적용, DELAY_MESSAGE(500, 지연 안내), AWAY_MESSAGE(500, 부재 안내 — 운영 시간 밖·상담 운영 꺼짐)
- 자주 쓰는 답변: `TBL_SAVED_REPLY` TITLE(30), CONTENT(500) — 순서 기능 없음, 등록순

## SCR-A-009 채팅 시작 화면 — `TBL_CHAT_SETTING.GREETING`, `TBL_QUICK_MENU`

- GREETING(500), 퀵메뉴 최대 5개: MENU_NAME(50), 연결 주제(TOPIC_ID), SORT_ORDER(순서 변경), IS_ACTIVE(노출 on/off)

## SCR-A-010 챗봇 답변 관리 — `TBL_CHATBOT_TOPIC`, `TBL_LOREBOOK`, `TBL_CHATBOT_ACTION`

- 주제: TITLE(50), INTRO(200, **선택**), TOPIC_TYPE(안내·예약 조회·견적 조회), IS_ACTIVE(사용 on/off)
- 로어북(안내 주제만, 여러 개): KEYWORDS(200), CONTENT(1000), SORT_ORDER
- 답변 버튼(여러 개): LABEL(20), ACTION_TYPE(화면 이동·이어서 질문·상담원 연결), ACTION_VALUE(200), SORT_ORDER

## SCR-A-020 예약 일정 · SCR-A-022 일정 관리 — `TBL_RESERVATION`, `TBL_RESERVATION_SETTING`, `TBL_CLOSED_DAY`

- 예약 일정(조회 전용 캘린더): 예약(예약 완료·이사 완료, MOVE_DATE·MOVE_TIME), 휴무, 손 없는 날·공휴일(외부 API, 저장 안 함). 일정 등록(SCR-A-021)은 뺌 — `TBL_SCHEDULE` 삭제
- 일정 관리 > 예약 설정: DAILY_CAPACITY(하루 예약 가능 건수, 1~10), CLOSED_WEEKDAYS(정기 휴무 요일), HOLIDAY_CLOSED(공휴일에 쉬기)
- 일정 관리 > 임시 휴무: `TBL_CLOSED_DAY.CLOSED_DATE` (하루 1행, 해제 = 행 삭제)
- 사용자 앱 날짜 선택과 연결: 휴무(정기·공휴일·임시)는 선택 불가, 그날 예약 확정 건수가 DAILY_CAPACITY 에 닿으면 '마감', 손 없는 날은 '손없음' 표시만. 고객은 희망 시간대(PREFER_TIME)만 고르고 정확한 시각은 견적서 WORK_TIME 으로 정함 (revision-user.md Q18)

## SCR-A-013~015 알림 관리 · A-019 알림 패널 — `TBL_NOTIFICATION`, `TBL_NOTIFICATION_SETTING`, `TBL_ADMIN`

11/03 결정: 알림 관리는 화면 하나에 탭 2개(발송 내역 · 알림 설정). 관리자가 받는 알림은 알림 패널에서만 본다.

- 발송 내역 (고객에게 보낸 알림만, RECEIVER_TYPE 01): 대상 회원, NTF_DETAIL, NTF_TITLE, NTF_CONTENT, NTF_CHANNEL(앱·알림톡·문자), CREATED_AT, SENT_AT(발송 시각 / 보류 중), 결과(발송됨·보류 중·문자 대체) — 필터: 채널·결과, 검색: 이름·날짜
- 많아질 때: 기본 **최근 30일**, 20건씩 이어서 불러오기(무한 스크롤), 그 이전은 날짜 검색으로만. 검색·필터는 서버 쿼리로 처리. 인덱스 (RECEIVER_TYPE, CREATED_AT) · (MEMBER_ID). 보관 기간은 보류(11/03) — 정해지면 ERD 운영 규칙으로 추가
- 알림 설정
  - 내 알림: `TBL_ADMIN.PUSH_ENABLED`(알림 받기, 계정별) `CHAR(1) DEFAULT 'Y' NOT NULL`. 끄면 휴대폰 푸시만 끊기고 알림 패널에는 그대로 쌓임
  - 고객 발송: IS_ACTIVE, QUIET_ENABLED, QUIET_START·END, SMS_FALLBACK (알림톡·문자에만 적용, Q22)
- 알림 패널: RECEIVER_TYPE 02(관리자 수신)만, NTF_DETAIL, NTF_CONTENT, IS_IMPORTANT, IS_READ(안 읽음 표시·읽음 처리·모두 읽음), CREATED_AT, LINK_URL(이동) — 화면 전체 페이지, 탭 전체·안 읽음, 묶음 오늘·어제·이전

## ERD 반영 결과 (2026-11-03, revision-user.md 4단계)

사용자·관리자 화면 대조 후 아래처럼 정해 ddl.sql · sequence.sql · code-data.sql 에 반영했다.

| 항목 | 결정 |
| --- | --- |
| 관리자 알림 받기 | `TBL_ADMIN.PUSH_ENABLED` 추가 |
| 자주 쓰는 답변 | `TBL_SAVED_REPLY` + `SEQ_SAVED_REPLY` 신설 (순서 기능 없음) |
| 상담 운영 | `HOURS_ENABLED` → `CONSULT_ENABLED` |
| 부재 안내 메시지 | `TBL_CHAT_SETTING.AWAY_MESSAGE` 추가 |
| 휴무일 | `TBL_DAY_STATUS` → `TBL_CLOSED_DAY` (임시 휴무만, 상태 컬럼·`DAY_STATUS` 코드 삭제) |
| 예약 설정 | `TBL_RESERVATION_SETTING` + `SEQ_RESERVATION_SETTING` 신설 |
| 개인 일정 | `TBL_SCHEDULE` · `SEQ_SCHEDULE` · `SCHEDULE_CATEGORY` 삭제 |
| 공휴일 · 손 없는 날 | 저장하지 않음 (외부 API, Q17 유지) |
| 이사 시간 | 고객은 희망 시간대 `TBL_MOVE_REQUEST.PREFER_TIME`(오전·오후·상관없음), 정확한 시각은 `TBL_QUOTE.WORK_TIME` (고정 시간대) |
| 후기 · 답변 길이 | `CONTENT` · `REPLY_CONTENT` 500자, 답변 알림 없음 |
| 공지 알림 | `NTF_TYPE` 02, `NTF_DETAIL` 11·12 삭제 |
| 알림 보관 기간 | 보류 — 정해지면 운영 규칙으로 추가 |
