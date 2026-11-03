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

- 요청 정보(표시): HOUSE_TYPE, PACK_TYPE, MOVE_DATE·MOVE_TIME(희망), FROM/TO_ADDRESS + DETAIL, REQUESTED_AT, 영상(TBL_RE_VIDEO.FILE_NAME·UPLOADED_AT)
- AI 분석(표시): AI_CBM, AI_EST_AMOUNT·MIN·MAX, AI_ANALYSIS_SEC, AI_CONFIDENCE, 요청 품목(CATEGORY, ITEM_NAME, ITEM_SIZE 소·중·대, QUANTITY, VOLUME, CONFIDENCE, NEEDS_REVIEW, SOURCE AI 인식/사용자 추가)
- 견적서(입력): QUOTE_NO(표시), COMPANY_NAME, CONTACT_NAME, CONTACT_PHONE, ESTIMATE_DATE, FROM/TO_ADDRESS, WORK_DATE(없으면 승인 불가), TRANSPORT_DATE, WORK_TYPE(포장 방식: 일반·반포장·보관·포장), 출발/도착 각각 LADDER·ELEVATOR·STAIRS 사용, VOLUME_TON, NOTE(500), MOVING_COST, FROM/TO_LADDER_FLOOR·COST, TOTAL_AMOUNT, DEPOSIT_AMOUNT, BALANCE_AMOUNT
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
| MOVE_TIME | — | 표시 |
| 출발·도착 주소, 이사 유형·포장 방식 | — | 표시 |
| TOTAL·DEPOSIT·BALANCE_AMOUNT | — | 표시 |
| MOVE_MEMO | — | 표시 |
| APPLIED_AT, DEPOSIT_PAID_AT | — | 표시 |
| CANCELLED_AT, CANCELLED_BY (회원·관리자) | — | 취소 건만 표시 |

## SCR-A-012 리뷰 관리 — `TBL_REVIEW`, `TBL_REVIEW_PHOTO`

- 표시: 작성자(회원 이름), RATING(필터), CONTENT(1000), CREATED_AT, 사진(TBL_REVIEW_PHOTO, SORT_ORDER 순), 연결 예약(예약 번호·이사일·이사 유형)
- 답변: REPLY_CONTENT(입력, **1000자**), REPLIED_AT·답변 관리자(표시), 답변 여부(필터 후보)

## SCR-A-005~007 채팅 — `TBL_CHAT_ROOM`, `TBL_CHAT_MESSAGE`

- 방(목록/상세 헤더): 회원 이름·휴대폰, STATUS(챗봇 응대·상담 대기·상담 중·종료) 칩·필터, STARTED_AT, WAITING_AT(대기 시작), ENDED_AT, 담당 관리자(ADMIN_ID), 안 읽은 수(IS_READ)
- 메시지: SENDER_TYPE(고객·AI·관리자 — 관리자는 상담사 이름), MESSAGE_TYPE 02 파일(ATTACHMENT_NAME 표시·다운로드), AI 답변 주제(TOPIC_ID → 제목), SENT_AT, 읽음(IS_READ)
- 동작: 상담 종료(STATUS 04, ENDED_AT)

## SCR-A-008 채팅 운영 설정 — `TBL_CHAT_SETTING`

- 입력: BOT_ENABLED, HOURS_ENABLED(화면 이름 '상담 운영' — 끄면 운영 시간이어도 상담원 연결을 받지 않음, 휴무 등), START_TIME·END_TIME 은 늘 적용, DELAY_MESSAGE(500)

## SCR-A-009 채팅 시작 화면 — `TBL_CHAT_SETTING.GREETING`, `TBL_QUICK_MENU`

- GREETING(500), 퀵메뉴 최대 5개: MENU_NAME(50), 연결 주제(TOPIC_ID), SORT_ORDER(순서 변경), IS_ACTIVE(노출 on/off)

## SCR-A-010 챗봇 답변 관리 — `TBL_CHATBOT_TOPIC`, `TBL_LOREBOOK`, `TBL_CHATBOT_ACTION`

- 주제: TITLE(50), INTRO(200, **선택**), TOPIC_TYPE(안내·예약 조회·견적 조회), IS_ACTIVE(사용 on/off)
- 로어북(안내 주제만, 여러 개): KEYWORDS(200), CONTENT(1000), SORT_ORDER
- 답변 버튼(여러 개): LABEL(20), ACTION_TYPE(화면 이동·이어서 질문·상담원 연결), ACTION_VALUE(200), SORT_ORDER

## SCR-A-020 예약 일정 · SCR-A-022 일정 관리 — `TBL_RESERVATION`, `TBL_DAY_STATUS` (+ 예약 설정 신규, `TBL_SCHEDULE` 삭제 예정)

- 캘린더 표시: 예약(예약 완료·이사 완료, MOVE_DATE·MOVE_TIME), 일정(SCHEDULE_TIME·TITLE·CATEGORY), 날짜 상태
- 일정 등록(TBL_SCHEDULE, SCR-A-021): **화면에서 뺌** (개인 일정 관리 안 함 — 아래 ERD 반영 대기)
- 일정 관리: 휴무일(TBL_DAY_STATUS) — 종류 구분 없이 하루 1건 (예전 01 이사 예약 불가능 · 02 업체 휴무일을 하나로 합침, 아래 ERD 반영 대기)

## SCR-A-013~015 알림 관리 · A-019 알림 패널 — `TBL_NOTIFICATION`, `TBL_NOTIFICATION_SETTING`, `TBL_ADMIN`

11/03 결정: 알림 관리는 화면 하나에 탭 2개(발송 내역 · 알림 설정). 관리자가 받는 알림은 알림 패널에서만 본다.

- 발송 내역 (고객에게 보낸 알림만, RECEIVER_TYPE 01): 대상 회원, NTF_DETAIL, NTF_TITLE, NTF_CONTENT, NTF_CHANNEL(앱·알림톡·문자), CREATED_AT, SENT_AT(발송 시각 / 보류 중), 결과(발송됨·보류 중·문자 대체) — 필터: 채널·결과, 검색: 이름·날짜
- 많아질 때: 기본 **최근 30일**, 20건씩 이어서 불러오기(무한 스크롤), 그 이전은 날짜 검색으로만. 검색·필터는 서버 쿼리로 처리. 인덱스 (RECEIVER_TYPE, CREATED_AT) · (MEMBER_ID). 보관 기간은 보류(11/03) — 정해지면 ERD 운영 규칙으로 추가
- 알림 설정
  - 내 알림: `TBL_ADMIN.PUSH_ENABLED`(알림 받기, 계정별) — **ERD 추가 컬럼** `CHAR(1) DEFAULT 'Y' NOT NULL`. 끄면 휴대폰 푸시만 끊기고 알림 패널에는 그대로 쌓임
  - 고객 발송: IS_ACTIVE, QUIET_ENABLED, QUIET_START·END, SMS_FALLBACK (알림톡·문자에만 적용, Q22)
- 알림 패널: RECEIVER_TYPE 02(관리자 수신)만, NTF_DETAIL, NTF_CONTENT, IS_IMPORTANT, IS_READ(안 읽음 표시·읽음 처리·모두 읽음), CREATED_AT, LINK_URL(이동) — 화면 전체 페이지, 탭 전체·안 읽음, 묶음 오늘·어제·이전

## ERD 반영 대기 (관리자 화면 정리 후 사용자·관리자 대조 때 한 번에 결정)

| 항목 | 내용 | 근거 화면 |
| --- | --- | --- |
| `TBL_ADMIN.PUSH_ENABLED` | `CHAR(1) DEFAULT 'Y' NOT NULL` — 관리자 계정별 알림 받기. 실제 관리자 휴대폰 푸시 발송은 별도 범위 (Q22 는 앱 안 패널만) | 알림 설정 > 내 알림 |
| `TBL_SAVED_REPLY` + `SEQ_SAVED_REPLY` | 자주 쓰는 답변: REPLY_ID, TITLE(30), CONTENT(500), ADMIN_ID, UPDATED_AT (순서 기능 없음 · 등록순) | 채팅 설정 > 자주 쓰는 답변, 채팅방 입력창 |
| 알림 보관 기간 | 보류 (11/03) | 발송 내역 |
| 부재 안내 메시지 (후보) | `TBL_CHAT_SETTING` 에 상담원 연결을 못 받을 때(운영 시간 밖 · 상담 운영 꺼짐) 고객에게 보낼 문구 컬럼 추가 여부. 지금 `DELAY_MESSAGE` 는 대기가 길어질 때만 | 채팅 설정 > 상담 |
| `HOURS_ENABLED` 이름 (후보) | 뜻이 '운영 시간 사용' → '상담 운영(상담원 연결 받기)'으로 바뀜. `CONSULT_ENABLED` 등으로 이름 변경 여부 | 채팅 설정 > 상담 |
| `TBL_DAY_STATUS.STATUS` 통일 | '이사 예약 불가능 · 업체 휴무일' 두 값을 '휴무일' 하나로 합침 (고객 앱 동작이 같음). STATUS 컬럼·공통코드 DAY_STATUS 삭제 또는 값 하나로 | 일정 관리 > 휴무일, 예약 일정 |
| 공휴일 · 손 없는 날 저장 (후보) | 외부 API(공공데이터 특일 정보 · 음력)로 불러온 값을 테이블에 저장할지 (예전 Q17 '저장하지 않음') | 일정 관리 > 공휴일, 예약 일정 |
| `TBL_SCHEDULE` + `SEQ_SCHEDULE` 삭제 | 사장님 개인 일정 등록 기능을 뺌 (11/03). 관련 FK · 공통코드(SCHEDULE_CATEGORY) 함께 정리 | 일정 관리 |
| 예약 설정 (신규) | 하루 예약 가능 건수(`DAILY_CAPACITY`, 기본 3 — 그날 예약이 이 수만큼 차면 남은 시간대도 마감. Q18 의 '시간대당 건수 `application.yml` 설정값'을 하루 기준으로 바꾸고 사장님이 바꾸도록 DB 로) · 정기 휴무 요일(`CLOSED_WEEKDAYS` 예: '0,1') · 공휴일에 쉬기(`HOLIDAY_CLOSED` CHAR(1)) — 설정 테이블(1행) 신설. `TBL_DAY_STATUS` 는 임시 휴무만 | 일정 관리 > 예약 설정 |
| 사용자 앱 날짜 선택 (화면 수정) | 고객이 이사 날짜를 고를 때 휴무일(정기 · 공휴일 · 임시)과 마감일(그날 예약이 하루 건수만큼 참)을 선택 불가로 표시. 관리자 예약 일정 달력에는 '마감'을 따로 표시하지 않음(사장님은 설정만 알면 됨) | 사용자 견적·예약 날짜 선택 |
