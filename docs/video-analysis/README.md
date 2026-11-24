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
