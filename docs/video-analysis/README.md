# 영상 분석 테스트 (feat/video-analysis)

기준: `기타/codemove_video_test_guide_v2.md` (로컬 문서). 지금은 **견적 흐름에 연결하지 않은 검증 단계**다.

## 1. 기존 프로젝트 분석 (1단계)

| 항목 | 기존 | 이번에 한 것 |
|---|---|---|
| 영상 업로드 | 견적 1단계 `POST /quote/video` → `TBL_RE_VIDEO` | 테스트 화면 전용 업로드(별도 폴더), 견적 업로드는 그대로 |
| AI 분석 연결 지점 | `request.VideoAnalyzer` 인터페이스 + `DummyVideoAnalyzer`(시안 결과 고정) | 건드리지 않음. 8단계에서 새 구현체로 연결 |
| 표준 품목 | `request.ItemCatalog`: 이름별 중형 CBM × 크기 계수(0.75/1/1.25), 코드에 고정 | 품목·크기별 CBM·무게를 CSV 기준표로 (`videoanalysis.ItemStandardCatalog`) |
| 별칭 | 없음 (AI 결과 이름이 표에 없으면 기타 0.5 CBM) | `ItemMappingService.ALIASES` |
| 요청 품목 저장 | `TBL_REQUEST_ITEM` (CATEGORY · ITEM_NAME · ITEM_SIZE · QUANTITY · VOLUME · CONFIDENCE · NEEDS_REVIEW · SOURCE) | 그대로 재사용 예정. 무게 칸은 없음 |
| 사용자 확인 · 관리자 승인 | 견적 요청 품목 수정 → 관리자 견적 승인 → 견적 확정 | 재사용 예정 |

**DB 변경: 없음.** 기준표와 실행 기록은 파일로 둔다 (검증이 끝나면 테이블로 옮길지 결정).

## 2. 구성

```
web/admin/AdminVideoAnalysisController   테스트 화면 · API (/admin/video-analysis, 관리자 로그인 필요)
videoanalysis/
  VideoAnalysisService     처리 순서: 검증·저장 → AI → JSON 검증 → 매핑 → 계산 → 기록
  GeminiVideoClient        Gemini generateContent (영상 base64 인라인, 재시도, 토큰 사용량)
  AnalysisPrompt           프롬프트 (VERSION) + 응답 스키마
  AiResultParser           AI JSON 검증 (필수 칸 · 수량 1~99 · 크기 등급)
  ItemStandardCatalog      품목 기준표 CSV 읽기 (다시 읽기 가능)
  ItemMappingService       AI 선택 품목명 → 이름 일치 → 별칭 → 복수 후보/실패
  VolumeWeightService      규격 × 수량 (부피 소수 3자리 · 무게 1자리 반올림)
  AnalysisRunStore         실행 기록 JSON · 테스트 영상 (uploads/video-analysis, Git 제외)
```

화면 전용 코드는 컨트롤러 · 템플릿 · `js/admin/video-analysis.js` 뿐이고, 나머지는 견적 흐름에서 그대로 쓸 수 있게 분리했다.

## 3. 실행

1. `application-local.yml` (Git 제외)
   ```yaml
   codemove:
     video-analysis:
       standard-csv: C:/.../캡스톤/기타/품목기준/품목기준표.csv
       gemini:
         api-key: <Google AI Studio 키>
       price:                      # 공식 요금표 값 (없으면 비용 '단가 미설정')
         input-per-million: ...
         output-per-million: ...
         as-of: YYYY-MM-DD
   ```
2. 앱 실행 → 관리자 로그인 → `http://localhost:8080/admin/video-analysis`
3. 키가 없으면 **JSON 직접 입력**으로 매핑 · 계산만 시험할 수 있다.

## 4. 단계별 상태

| 단계 | 상태 |
|---|---|
| 1. 기존 프로젝트 분석 | 이 문서 |
| 2. 테스트 화면 · 업로드 | 완료 (형식 · 300MB · 120초 검증, 14MB 넘으면 Gemini Files API) |
| 3. AI 연동 | 코드 완료, **키 받으면 실제 영상으로 검증 필요** |
| 4. DB 매핑 | 완료 (CSV 기준표) |
| 5. 부피 · 무게 | 완료 (`VideoAnalysisUnitTest`) — 무게는 가전 6종만, 에어컨 · 가구는 공식 무게 없음 |
| 6. 토큰 · 비용 · 시간 | 기록 완료, 단가는 설정 필요 |
| 7. 반복 테스트 · 비교 | 다시 분석 · CSV 내보내기 있음. 정답 비교는 영상 확보 후 |
| 8. 견적 기능 통합 | 아직 (`VideoAnalyzer` 구현체로 연결 예정) |

## 5. 알려진 한계

- 기준표 CBM 중 10개 품목(가전 7 + 침대 · 소파 · 옷장)만 제조사 치수 조사값이고 나머지는 가정값 (`근거` 열).
- 14MB 이하는 인라인, 넘으면 Files API 로 올려 처리 후 분석하고 바로 지운다 (지우기 실패 시 48시간 뒤 자동 삭제).
- 기본 모델 gemini-3.5-flash: 2.5-flash 는 새 사용자에게 막혀 있음. 생각(thinking) 토큰이 출력보다 훨씬 많아 비용에 크게 반영된다.
- 업로드 시간은 브라우저 송신 시작 ~ 서버 수신 완료(같은 PC 시계 기준 근사값).
- AI 신뢰도 숫자는 받지 않는다 (`needs_review` 만 사용).

## 6. 분석이 어려운 영상 처리 (`AnalysisOutcomePolicy`)

프롬프트 v4 는 AI 에게 영상 상태도 받는다: `quality_issues`(어두움 · 흔들림 · 흐림 · 너무 빠름 · 가려짐), `coverage`(전체 · 일부 · 거의없음), `unseen_areas`(안 보인 공간 · 닫힌 수납장).

| 판정 | 언제 | 고객 안내 (제목) | 버튼 | 관리자 표시 |
|---|---|---|---|---|
| 다시 찍기 | 01 형식 · 02 용량/길이(업로드 단계, AI 호출 없음) | 동영상 파일만 / 길이나 용량이 맞지 않아요 | 다시 찍어 올리기 · 품목 직접 입력 | 업로드 형식 · 길이 오류 |
| 다시 찍기 | 03 품질 불량 | 영상이 어두워서 / 흔들려서 / 흐려서 … 확인하기 어려워요 + 촬영 요령 | 〃 | 영상 품질 불량 (문제) |
| 다시 찍기 | 04 집 안 아님 · 짐 안 보임 | 집 안 짐이 잘 보이지 않아요 | 〃 | 집 안 영상 아님 · 짐 없음 |
| 잠시 후 다시 | 05 · 06 · 07 · 08 · 09 · 10 (AI 쪽 문제, 영상은 저장됨) | 지금 분석이 몰려 있어요 / 분석을 마치지 못했어요 | 다시 분석하기 · 품목 직접 입력 | AI 분석 실패 (이유) |
| 일부만 보임 | 분석됐지만 P1 coverage 일부·거의없음 · P2 품목 3줄 미만 · P3 공간 1곳 · P4 확인 필요 절반 이상 | 일부 공간만 보였어요 (안 보인 공간) / 확인이 필요한 품목이 많아요 | 영상 추가하기 · 빠진 품목 추가 · 이대로 진행 | AI 일부 분석 (이유) |
| 정상 | 그 밖 | 짐을 확인해 주세요 | 확인했어요 · 빠진 품목 추가 | AI 분석 완료 · 확인 필요 n개 |

- 다시 찍기 · 재분석은 **2회까지**(처음 1회 + 2회). 다 쓰면 버튼은 **품목 직접 입력 · 방문 견적 요청**만 남는다. 어떤 경우든 고객이 막히지 않고 견적 요청까지 갈 수 있다.
- 예비 모델로 분석된 결과는 고객 안내는 그대로 두고 관리자 표시에 "예비 모델"을 붙인다 (개수를 적게 세는 경향).
- 기준값(재시도 2회 · 품목 3줄 · 확인 필요 0.5)은 `codemove.video-analysis.outcome.*` 로 바꿀 수 있다.
- 테스트 화면: 결과 맨 위 "고객에게 보일 안내" 미리보기, 시도 횟수(1~3회째)를 바꿔 버튼 변화를 볼 수 있다. 실제 고객 화면은 시안이 정해진 뒤 붙인다.
