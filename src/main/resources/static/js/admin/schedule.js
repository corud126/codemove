/*
 * 예약 일정(SCR-A-020) · 일정 관리(SCR-A-022 설정 · 연동) — 더미 데이터 (templates/admin/schedule/calendar.html · manage.html)
 * 역할 분리
 *  - 예약 일정: 예약을 확인하는 뷰어 (보기 전용). 월 달력(접기 가능) + 고른 날 예약 — 박스 목록 하나, 오늘이면 지난 이사 흐리게 · 다음 이사에 남은 시간 꼬리표. 예약을 누르면 간단한 예약 정보 시트 (결제 상태는 시트에만)
 *    TBL_RESERVATION 예약 완료(01)·이사 완료(02)만, 휴무일(TBL_DAY_STATUS)은 표시만
 *  - 일정 관리: 탭 [휴무일 | 공휴일] — 휴무일(TBL_DAY_STATUS) 목록 + 추가·수정·삭제(예약 있는 날이면 경고) / 공휴일·손 없는 날(외부 API) 확인·다시 불러오기
 *    (예약 있는 날을 휴무로 지정하면 경고, 자동 알림 없음 Q23)
 *  - 오늘 = 26.09.27 가정 → 이사일이 지난 예약은 이사 완료 (Q7) · 더미 단계에서는 휴무일·설정을 sessionStorage 로 두 화면이 공유
 *  - 번호: 견적 QT-YYYYMMDD-NNNN(견적일) · 예약 ME-YYYYMMDD-NNNN(이사일)
 */
(function () {
  const STATUS_KEY = 'codemove.admin.dayStatus';
  const TODAY = '26.09.27';
  const ICONS = (document.querySelector('use[href*="icons-v2"]')?.getAttribute('href') || '/images/admin/icons-v2.svg').split('#')[0];
  const icon = (name, size, cls = '') => `<svg class="icon ${cls}" width="${size}" height="${size}"><use href="${ICONS}#i-${name}"/></svg>`;
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const pad = (n) => String(n).padStart(2, '0');
  const keyOf = (y, m, d) => `${String(y).slice(-2)}.${pad(m)}.${pad(d)}`;
  const keyOfDate = (dt) => keyOf(dt.getFullYear(), dt.getMonth() + 1, dt.getDate());
  const dateOf = (key) => { const [y, m, d] = key.split('.').map(Number); return new Date(2000 + y, m - 1, d); };
  const daysIn = (y, m) => new Date(y, m, 0).getDate();
  const WD = ['일', '월', '화', '수', '목', '금', '토'];
  const dateLabel = (key) => { const dt = dateOf(key); return `${dt.getMonth() + 1}월 ${dt.getDate()}일 (${WD[dt.getDay()]})`; };
  const shortAddr = (a) => a.split(' ').slice(0, 2).join(' ');

  /* ---------- 더미 데이터 ---------- */
  const customers = [
    ['홍길동', '010-1234-5678'], ['김명품', '010-2244-8811'], ['박철수', '010-7788-2200'], ['최지우', '010-3377-9021'],
    ['이서준', '010-5892-1144'], ['정하윤', '010-9834-1420'], ['강민준', '010-4471-3302'], ['윤서아', '010-8842-3199'],
    ['송지호', '010-1166-4201'], ['한유진', '010-6673-9920'], ['오도현', '010-2207-6134'], ['문지안', '010-7318-4490'],
    ['홍서윤', '010-4410-2387'], ['임지수', '010-9901-7722'], ['김도윤', '010-3142-6678'],
  ];
  const phoneOf = (name) => (customers.find((c) => c[0] === name) || [, ''])[1];
  // [id, 이름, 이사일, 시간, 이사 유형, 출발지, 도착지]
  const reservations = [
    [9, '한유진', '26.09.21', '16:00', '원룸이사', '인천 남동구 정각로 29', '서울 중구 세종대로 110'],
    [7, '윤서아', '26.09.22', '14:00', '가정이사', '경기 수원시 팔달구 효원로 241', '서울 강남구 테헤란로 521'],
    [8, '송지호', '26.09.22', '15:00', '가정이사', '경기 고양시 덕양구 고양시청로 10', '서울 송파구 올림픽로 300'],
    [3, '최지우', '26.09.25', '10:00', '원룸이사', '서울 용산구 한강대로 405', '경기 고양시 덕양구 고양시청로 10'],
    [2, '김명품', '26.09.26', '08:00', '가정이사', '서울 중구 세종대로 110', '경기 수원시 팔달구 효원로 241'],
    [4, '박철수', '26.09.26', '09:00', '가정이사', '서울 송파구 올림픽로 300', '인천 남동구 정각로 29'],
    [15, '문지안', '26.09.27', '08:00', '가정이사', '서울 서초구 남부순환로 2584', '경기 성남시 분당구 판교역로 166'],
    [16, '홍서윤', '26.09.27', '13:00', '원룸이사', '서울 마포구 월드컵로 212', '서울 용산구 한강대로 405'],
    [18, '김도윤', '26.09.27', '16:30', '가정이사', '경기 고양시 덕양구 고양시청로 10', '서울 송파구 올림픽로 300'],
    [11, '강민준', '26.09.28', '09:00', '가정이사', '서울 마포구 월드컵로 212', '서울 서초구 남부순환로 2584'],
    [12, '정하윤', '26.09.29', '08:00', '기업이사', '경기 성남시 분당구 판교역로 166', '서울 강남구 테헤란로 521'],
    [13, '오도현', '26.09.29', '13:00', '원룸이사', '서울 용산구 한강대로 405', '서울 마포구 월드컵로 212'],
    [14, '이서준', '26.09.30', '10:00', '가정이사', '대전 서구 둔산로 100', '경기 수원시 팔달구 효원로 241'],
    [17, '임지수', '26.10.02', '09:00', '가정이사', '서울 강남구 테헤란로 521', '서울 송파구 올림픽로 300'],
  ].map(([id, name, date, time, service, origin, destination]) => ({
    id, name, phone: phoneOf(name), date, time, service, origin, destination,
    status: date < TODAY ? '이사 완료' : '예약 완료',
    payStatus: date < TODAY || id % 3 === 0 ? '결제 완료' : '결제 대기', // PAY_STATUS 01 결제 대기 · 02 결제 완료
    reservationNo: `ME-20${date.replace(/\./g, '')}-${pad(id).padStart(4, '0')}`,
  }));
  const resOn = (key) => reservations.filter((r) => r.date === key).sort((a, b) => a.time.localeCompare(b.time));

  // 공휴일(외부 데이터)·손 없는 날(음력 계산) — 저장하지 않음 (Q17)
  const FIXED_HOLIDAYS = ['01-01', '03-01', '05-05', '06-06', '08-15', '10-03', '10-09', '12-25'];
  const YEAR_HOLIDAYS = { 2026: ['02-16', '02-17', '02-18', '03-02', '05-24', '05-25', '06-03', '08-17', '09-24', '09-25', '09-26', '10-05'] };
  const holidaysOf = (y, m) => [...FIXED_HOLIDAYS, ...(YEAR_HOLIDAYS[y] || [])].filter((md) => Number(md.slice(0, 2)) === m).map((md) => Number(md.slice(3)));
  const isRed = (key) => { const dt = dateOf(key); return dt.getDay() === 0 || holidaysOf(dt.getFullYear(), dt.getMonth() + 1).includes(dt.getDate()); };
  const noMoveOf = (key) => [7, 8, 17, 18, 27, 28].includes(dateOf(key).getDate()); // 손 없는 날 (더미: 음력 계산 대신 고정값)
  // 공휴일 이름 · 손 없는 날 — 외부 API(공공데이터 특일 정보 · 음력)에서 불러오는 값 (더미)
  const HOLIDAY_NAMES = { '01-01': '신정', '03-01': '삼일절', '05-05': '어린이날', '06-06': '현충일', '08-15': '광복절', '10-03': '개천절', '10-09': '한글날', '12-25': '성탄절',
    '09-24': '추석 연휴', '09-25': '추석', '09-26': '추석 연휴', '10-05': '대체공휴일', '08-17': '대체공휴일', '02-16': '설날 연휴', '02-17': '설날', '02-18': '설날 연휴', '03-02': '대체공휴일', '05-24': '부처님오신날', '05-25': '대체공휴일', '06-03': '지방선거' };

  // TBL_DAY_STATUS: 날짜 → { status (01 이사 예약 불가능 · 02 업체 휴무일) } — UPDATED_AT·ADMIN_ID 는 감사용, 화면에 표시하지 않음
  const DEFAULT_STATUS = { '26.09.11': { status: 'closed' }, '26.09.20': { status: 'closed' }, '26.10.09': { status: 'closed' }, '26.10.15': { status: 'closed' } };
  function loadStatuses() {
    try { const raw = sessionStorage.getItem(STATUS_KEY); if (raw) return JSON.parse(raw); } catch (e) { /* 기본값 */ }
    return { ...DEFAULT_STATUS };
  }
  function saveStatuses(v) { try { sessionStorage.setItem(STATUS_KEY, JSON.stringify(v)); } catch (e) { /* ignore */ } }
  // 예약 설정 (ERD 반영 대기: 하루 최대 예약 · 정기 휴무 요일 · 공휴일 휴무) — 더미 단계에서는 sessionStorage 로 두 화면이 공유
  const CONFIG_KEY = 'codemove.admin.closeConfig';
  function loadConfig() {
    try { const raw = sessionStorage.getItem(CONFIG_KEY); if (raw) return JSON.parse(raw); } catch (e) { /* 기본값 */ }
    return { weekdays: [], holiday: false, daily: 3 };
  }
  function saveConfig(v) { try { sessionStorage.setItem(CONFIG_KEY, JSON.stringify(v)); } catch (e) { /* ignore */ } }
  // 그날 쉬는지: 임시 휴무(TBL_DAY_STATUS) · 정기 휴무 요일 · 공휴일 휴무
  const isClosed = (key, statuses, cfg) => {
    const dt = dateOf(key);
    return Boolean(statuses[key]) || cfg.weekdays.includes(dt.getDay()) || (cfg.holiday && holidaysOf(dt.getFullYear(), dt.getMonth() + 1).includes(dt.getDate()));
  };
  // 휴무일 하나로 통일 (예전 '이사 예약 불가능 · 업체 휴무일' 구분은 고객 앱 동작이 같아 합침 — ERD 반영 대기)
  const STATUS_NAME = { closed: '휴무일' };
  const serviceChip = (sv) => `<span class="shrink-0 text-[13px] text-[#9a9f9b]">${sv}</span>`; // 색 칩은 다음 이사 강조를 묻어서 회색 글자로

  /* ================= SCR-A-020 예약 일정 — 오늘 카드(오늘 이사가 있을 때만) + 월 달력 + 고른 날 목록, 보기 전용 ================= */
  function initReservations(root) {
    const statuses = loadStatuses(), cfg = loadConfig();
    const s = { selected: TODAY, cursor: dateOf(TODAY), collapsed: false };
    const body = root.querySelector('[data-view-body]');
    const NOW = '11:00'; // 더미: 오늘 현재 시각 가정 → 지난 예약은 흐리게, 다음 이사에 남은 시간
    const done = (r) => r.status === '이사 완료' || (r.date === TODAY && r.time < NOW);

    // 월 달력 — 날짜 + 예약 수만큼 점(최대 3) · 오늘 초록 테두리 · 고른 날 초록 원 · 휴무일 줄 긋기, 예약 불가 주황 점
    // 칸을 낮게 두어 아래 예약 목록이 한 화면에 보이게 · [달력 접기] = 고른 날이 있는 주만 · 다른 달·날을 보고 있으면 [오늘]
    function calendar() {
      const y = s.cursor.getFullYear(), m = s.cursor.getMonth() + 1;
      const first = new Date(y, m - 1, 1).getDay();
      const total = Math.ceil((first + daysIn(y, m)) / 7) * 7;
      const selIdx = (() => { const d = dateOf(s.selected); return d.getMonth() + 1 === m && d.getFullYear() === y ? first + d.getDate() - 1 : -1; })();
      let cells = '';
      for (let i = 0; i < total; i++) {
        if (s.collapsed && selIdx > -1 && Math.floor(i / 7) !== Math.floor(selIdx / 7)) continue;
        const dt = new Date(y, m - 1, 1 - first + i), key = keyOfDate(dt), inMonth = dt.getMonth() + 1 === m;
        const sel = key === s.selected, today = key === TODAY, st = isClosed(key, statuses, cfg), list = resOn(key);
        const num = sel ? 'bg-[#346538] font-bold text-white shadow-[0_3px_8px_rgba(52,101,56,.3)]'
          : today ? 'font-bold text-[#346538] ring-[1.5px] ring-inset ring-[#346538]'
          : st ? 'font-medium text-[#b0b4b1]'
          : `font-medium ${i % 7 === 0 || holidaysOf(y, m).includes(dt.getDate()) && inMonth ? 'text-[#d8663d]' : 'text-[#292929]'}`;
        // 날짜 아래: 휴무는 회색 작은 글자(빨강은 공휴일 전용), 예약은 건수만큼 크림색 점(최대 3) — '마감'은 고객 앱 날짜 선택에서 보여 줌
        const marks = st ? `<span class="text-[12px] font-bold leading-3 text-[#888]">휴무</span>` : list.slice(0, 3).map(() => `<i class="h-[6px] w-[6px] rounded-full ${sel ? 'bg-[#346538]' : 'bg-[#cdbf86]'}"></i>`).join(''); 
        cells += `<button type="button" data-pick="${key}" class="flex flex-col items-center gap-[2px] py-0.5 ${inMonth ? '' : 'opacity-25'}">
          <span class="grid h-[34px] w-[34px] place-items-center rounded-full text-[15px] tabular-nums ${num}">${dt.getDate()}</span>
          <span class="flex h-4 items-center gap-[3px]">${marks}</span></button>`;
      }
      const showToday = s.selected !== TODAY;
      return `<section class="mt-4 rounded-[24px] bg-white px-3 pb-1.5 pt-3.5 shadow-[0_2px_14px_rgba(26,26,26,.06)]">
        <div class="flex items-center gap-1 pl-2">
          <b class="text-[18px] font-extrabold tracking-[-0.02em] text-[#1a1a1a]">${y}년 ${m}월</b>
          ${showToday ? '<button type="button" data-go-today class="ml-auto h-8 rounded-full bg-[#eef4f0] px-3 text-[13px] font-bold text-[#346538]">오늘</button>' : ''}
          <button type="button" data-step="-1" aria-label="이전 달" class="${showToday ? '' : 'ml-auto'} grid h-9 w-9 place-items-center rounded-full text-[#555] hover:bg-[#f2f3f1]">${icon('chevronLeft', 19)}</button>
          <button type="button" data-step="1" aria-label="다음 달" class="-mr-1 grid h-9 w-9 place-items-center rounded-full text-[#555] hover:bg-[#f2f3f1]">${icon('chevronRight', 19)}</button>
        </div>
        <div class="mt-2.5 grid grid-cols-7 text-center text-[12px] font-semibold text-[#a3a8a4]"><span class="text-[#e3a58e]">일</span><span>월</span><span>화</span><span>수</span><span>목</span><span>금</span><span>토</span></div>
        <div class="mt-1 grid grid-cols-7 gap-y-0.5">${cells}</div>
        <button type="button" data-collapse class="mx-auto mt-1 flex h-7 items-center gap-1 px-3 text-[12px] font-semibold text-[#9a9f9b]">${s.collapsed ? '달력 펼치기' : '달력 접기'}${icon('chevron', 14, s.collapsed ? '' : 'rotate-180')}</button>
      </section>`;
    }

    // 고른 날 예약 — 박스 목록 하나 (남은 시간 표시용 함수)
    const mins = (t) => { const [h, mi] = t.split(':').map(Number); return h * 60 + mi; };
    const until = (t) => { const g = mins(t) - mins(NOW); return g <= 0 ? '진행 중' : g < 60 ? `${g}분 후` : `${Math.floor(g / 60)}시간${g % 60 ? ` ${g % 60}분` : ''} 후`; };
    function dayList(key) {
      const ds = isClosed(key, statuses, cfg);
      const list = resOn(key);
      // 머리: 날짜 · 건수(또는 휴무) / 오른쪽에 공휴일 이름 · 손 없는 날 (이삿날 고를 때 사장님이 보는 정보)
      const dt = dateOf(key);
      const holiday = holidaysOf(dt.getFullYear(), dt.getMonth() + 1).includes(dt.getDate()) ? HOLIDAY_NAMES[`${pad(dt.getMonth() + 1)}-${pad(dt.getDate())}`] || '공휴일' : '';
      const tags = [holiday && `<span class="text-[13px] font-semibold text-[#d8663d]">${holiday}</span>`, noMoveOf(key) && '<span class="text-[13px] font-semibold text-[#346538]">손 없는 날</span>'].filter(Boolean).join('<span class="text-[#dcdfdc]">·</span>');
      const head = `<div class="mt-6 flex items-baseline gap-2 px-1"><h3 class="text-[17px] font-bold text-[#1a1a1a]">${key === TODAY ? '오늘 · ' : ''}${dateLabel(key)}</h3>
        <span class="text-[14px] ${ds ? 'text-[#888]' : 'text-[#9a9f9b]'}">${ds ? '휴무일' : list.length ? `${list.length}건` : ''}</span>
        ${tags ? `<span class="ml-auto flex items-baseline gap-1.5">${tags}</span>` : ''}</div>`;
      if (!list.length) return head + `<p class="py-10 text-center text-[14px] text-[#9a9f9b]">${ds ? `휴무일로 지정된 날이에요` : '예약이 없습니다'}</p>`;
      // 모든 날 같은 박스 목록 (시간 · 이름 / 출발 → 도착) — 오늘만: 지난 이사는 흐리게, 다음 이사 이름 옆에 남은 시간
      const next = key === TODAY ? list.find((r) => !done(r)) : null;
      return head + `<div class="mt-3 overflow-hidden rounded-[20px] bg-white shadow-[0_2px_12px_rgba(26,26,26,.05)]">${list.map((r) => {
        const past = done(r), isNext = r === next; // 다음 이사 = 초록 시간 + 오른쪽 '2시간 후' (바탕·꼬리표 없이, 유형 칩과 겹치지 않게)
        return `<button type="button" data-record="${r.id}" class="flex w-full items-center gap-4 border-b border-[#eef0ee] px-5 py-4 text-left last:border-none hover:bg-[#f7f8f6] ${past ? 'opacity-45' : ''}">
          <b class="w-12 shrink-0 text-[17px] font-bold tabular-nums ${isNext ? 'text-[#346538]' : 'text-[#1a1a1a]'}">${r.time}</b>
          <span class="min-w-0 flex-1">
            <span class="flex items-center gap-2"><b class="truncate text-[16px] font-semibold text-[#292929]">${esc(r.name)}</b>${serviceChip(r.service)}
              </span>
            <span class="mt-0.5 block truncate text-[14px] text-[#888]">${esc(shortAddr(r.origin))} → ${esc(shortAddr(r.destination))}</span>
          </span>${isNext ? `<span class="shrink-0 rounded-full bg-[#346538] px-2.5 py-1 text-[13px] font-bold text-white">${until(r.time)}</span>` : icon('chevronRight', 18, 'shrink-0 text-[#c4c8c5]')}</button>`;
      }).join('')}</div>`;
    }

    function render() { body.innerHTML = calendar() + dayList(s.selected); }

    // 간단한 예약 정보 시트
    const sheet = root.querySelector('#reservationSheet');
    function openRecord(id) {
      const r = reservations.find((x) => x.id === id);
      const set = (k, v) => { sheet.querySelector(`[data-r-${k}]`).textContent = v; };
      set('date', dateLabel(r.date)); sheet.querySelector('[data-r-today]').hidden = r.date !== TODAY;
      set('time', r.time); set('name', r.name);
      const sv = sheet.querySelector('[data-r-service]'); sv.outerHTML = serviceChip(r.service).replace('<span ', '<span data-r-service ');
      set('origin', r.origin); set('destination', r.destination);
      sheet.querySelector('[data-r-tel]').href = `tel:${r.phone.replace(/\D/g, '')}`;
      const link = sheet.querySelector('[data-r-link]');
      link.href = `${link.getAttribute('href').split('?')[0]}?id=${r.id}`;
      sheet.hidden = false;
    }

    root.addEventListener('click', (e) => {
      const rec = e.target.closest('[data-record]');
      if (rec) { openRecord(Number(rec.dataset.record)); return; }
      const p = e.target.closest('[data-pick]');
      if (p) { s.selected = p.dataset.pick; s.cursor = new Date(dateOf(s.selected).getFullYear(), dateOf(s.selected).getMonth(), 1); render(); return; }
      if (e.target.closest('[data-go-today]')) { s.selected = TODAY; s.cursor = new Date(dateOf(TODAY).getFullYear(), dateOf(TODAY).getMonth(), 1); render(); return; }
      if (e.target.closest('[data-collapse]')) { s.collapsed = !s.collapsed; render(); return; }
      const step = e.target.closest('[data-step]');
      if (step) {
        s.cursor = new Date(s.cursor.getFullYear(), s.cursor.getMonth() + Number(step.dataset.step), 1);
        s.selected = keyOfDate(s.cursor.getMonth() === dateOf(TODAY).getMonth() && s.cursor.getFullYear() === dateOf(TODAY).getFullYear() ? dateOf(TODAY) : s.cursor);
        render();
      }
    });
    render();

    const state = root.dataset.state;
    if (state === 'day') { s.selected = '26.09.29'; render(); }
    if (state === 'closed') { s.selected = '26.09.20'; render(); }
    if (state === 'next-month') { s.cursor = new Date(2026, 9, 1); s.selected = '26.10.02'; render(); }
    if (state === 'detail') openRecord(16); // 홍서윤 (오늘)
    if (state === 'collapsed') { s.collapsed = true; render(); }
    if (state === 'weekly') { cfg.weekdays = [1]; cfg.holiday = true; render(); }
  }

  /* ================= SCR-A-022 일정 관리 — 글자 탭 [설정 | 연동] (개인 일정 등록 SCR-A-021 은 뺌) ================= */
  function initManage(root) {
    let statuses = loadStatuses(), cfg = loadConfig(); // 예약 일정과 공유
    const s = { tab: 'days', showPast: false, hy: 2026, hm: 9 };
    const toKey = (v) => { const [y, m, d] = v.split('-').map(Number); return keyOf(y, m, d); };
    const listBox = root.querySelector('[data-list]');
    const toast = root.querySelector('[data-toast]');
    let toastTimer;
    const showToast = (msg) => { toast.querySelector('[data-toast-text]').textContent = msg; toast.hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => { toast.hidden = true; }, 2000); };
    const card = (rows) => `<div class="overflow-hidden rounded-[20px] bg-white shadow-[0_2px_12px_rgba(26,26,26,.05)]">${rows}</div>`;
    const empty = (text) => `<p class="py-16 text-center text-[15px] text-[#888]">${text}</p>`;
    const pastToggle = (label, n) => `<button type="button" data-toggle-past class="mx-auto mt-6 flex h-10 items-center gap-1 rounded-full px-4 text-[14px] font-semibold text-[#888] hover:bg-[#eef0ee]">${label} ${s.showPast ? '접기' : `${n}개 보기`}${icon('chevron', 16, s.showPast ? 'rotate-180' : '')}</button>`;

    // 설정 탭 — 채팅 설정과 같은 설정 앱 모양: 회색 묶음 제목 · 흰 카드 · 한 줄씩(이름 · 값 ›)
    //   예약 받기: 하루 최대 예약(− N건 +) / 쉬는 날: 정기 휴무(매주 월요일 › → 요일 시트) · 공휴일에 쉬기(토글) / 임시 휴무 N(+ 추가): 날짜 목록
    const group = (title, right = '') => `<div class="mb-2 mt-7 flex h-6 items-center justify-between px-1 first:mt-0"><h3 class="text-[13px] font-semibold text-[#888]">${title}</h3>${right}</div>`;
    const rowCls = 'flex min-h-14 w-full items-center justify-between gap-3 border-b border-[#eef0ee] px-5 py-3 text-left last:border-none';
    function renderDays() {
      const keys = Object.keys(statuses).sort();
      const upcoming = keys.filter((k) => k >= TODAY);
      const past = keys.filter((k) => k < TODAY).reverse();
      const per = cfg.daily || 3;
      const wds = cfg.weekdays.slice().sort();
      const regular = !wds.length ? '없음' : wds.length === 1 ? `매주 ${WD[wds[0]]}요일` : `매주 ${wds.map((i) => WD[i]).join(' · ')}`;
      const dateRows = (list, dim) => card(list.map((k) => {
        const booked = resOn(k).filter((r) => r.status === '예약 완료').length;
        return `<button type="button" data-daykey="${k}" class="${rowCls} hover:bg-[#f7f8f6] ${dim ? 'opacity-50' : ''}">
          <span class="text-[16px] font-medium text-[#292929]">${dateLabel(k)}</span>
          <span class="flex items-center gap-1">${booked && !dim ? `<span class="text-[13px] font-semibold text-[#d9701a]">예약 ${booked}건</span>` : ''}${icon('chevronRight', 18, 'text-[#c4c8c5]')}</span></button>`;
      }).join(''));
      let html = group('예약 받기') + card(`<button type="button" data-open-daily class="${rowCls} hover:bg-[#f7f8f6]">
          <span class="text-[16px] font-medium text-[#292929]">하루 최대 예약</span>
          <span class="flex items-center gap-0.5 text-[15px] font-medium tabular-nums text-[#666]">${per}건${icon('chevronRight', 18, 'text-[#c4c8c5]')}</span></button>`);
      html += group('쉬는 날') + card(`<button type="button" data-open-weekdays class="${rowCls} hover:bg-[#f7f8f6]">
          <span class="text-[16px] font-medium text-[#292929]">정기 휴무</span>
          <span class="flex items-center gap-0.5 text-[15px] font-medium text-[#666]">${regular}${icon('chevronRight', 18, 'text-[#c4c8c5]')}</span></button>
        <div class="${rowCls}">
          <span class="text-[16px] font-medium text-[#292929]">공휴일에 쉬기</span>
          <button type="button" role="switch" data-holiday-toggle aria-checked="${cfg.holiday}" aria-label="공휴일에 쉬기" class="group relative h-[30px] w-[50px] shrink-0 rounded-full bg-[#d5d8d5] transition-colors aria-checked:bg-[#346538]">
            <span class="absolute left-[3px] top-[3px] h-6 w-6 rounded-full bg-white shadow-[0_1px_3px_rgba(26,26,26,.2)] transition-transform group-aria-checked:translate-x-5"></span></button></div>`);
      html += group(`임시 휴무 <span class="tabular-nums">${upcoming.length}</span>`, `<button type="button" data-add-day class="-mr-1 flex h-7 items-center gap-0.5 rounded-full px-2 text-[13px] font-semibold text-[#346538] hover:bg-[#eef4f0]">${icon('plus', 15)}추가</button>`);
      html += upcoming.length ? dateRows(upcoming) : card('<p class="py-6 text-center text-[14px] text-[#9a9f9b]">지정한 날짜가 없습니다</p>');
      if (past.length) html += pastToggle('지난 날짜', past.length) + (s.showPast ? `<div class="mt-2">${dateRows(past, true)}</div>` : '');
      listBox.innerHTML = html;
    }
    // 하루 최대 예약 휠 시트 (1~10건) — 3줄 휠, 가운데가 선택
    const dSheet = root.querySelector('#dailySheet');
    const dWheel = dSheet.querySelector('[data-count-wheel]');
    const ROW = 44;
    const dOpts = () => Array.from(dWheel.querySelectorAll('[role=option]'));
    const dMark = () => { const i = Math.round(dWheel.scrollTop / ROW); dOpts().forEach((o, k) => o.setAttribute('aria-selected', String(k === i))); };
    dWheel.addEventListener('scroll', dMark);
    dWheel.addEventListener('click', (e) => { const o = e.target.closest('[role=option]'); if (o) dWheel.scrollTo({ top: dOpts().indexOf(o) * ROW, behavior: 'smooth' }); });
    function openDaily() { dSheet.hidden = false; dWheel.scrollTop = ((cfg.daily || 3) - 1) * ROW; dMark(); }
    dSheet.querySelector('[data-daily-save]').addEventListener('click', () => {
      cfg = { ...cfg, daily: Number(dOpts()[Math.round(dWheel.scrollTop / ROW)].dataset.value) };
      saveConfig(cfg); dSheet.hidden = true; render(); showToast('저장되었습니다');
    });
    // 정기 휴무 시트 — 요일 7개를 누르면 켜짐/꺼짐, [완료]로 저장
    const wdSheet = root.querySelector('#weekdaySheet');
    let wdDraft = [];
    function renderWeekdays() {
      wdSheet.querySelector('[data-wd-list]').innerHTML = WD.map((w, i) => {
        const on = wdDraft.includes(i);
        return `<button type="button" data-weekday="${i}" aria-pressed="${on}" class="grid h-11 w-11 place-items-center rounded-full text-[16px] font-semibold ${on ? 'bg-[#346538] text-white' : `ring-1 ring-inset ring-[#dcdfdc] ${i === 0 ? 'text-[#d8663d]' : 'text-[#555]'}`}">${w}</button>`;
      }).join('');
    }
    function openWeekdays() { wdDraft = cfg.weekdays.slice(); renderWeekdays(); wdSheet.hidden = false; }
    wdSheet.addEventListener('click', (e) => {
      const b = e.target.closest('[data-weekday]');
      if (b) { const i = Number(b.dataset.weekday); wdDraft = wdDraft.includes(i) ? wdDraft.filter((x) => x !== i) : [...wdDraft, i]; renderWeekdays(); return; }
      if (e.target.closest('[data-wd-save]')) { cfg = { ...cfg, weekdays: wdDraft }; saveConfig(cfg); wdSheet.hidden = true; render(); showToast('저장되었습니다'); }
    });
    // 공휴일 · 손 없는 날 — 외부 API 값, 확인 · 다시 불러오기만
    function renderHolidays() {
      const { hy: y, hm: m } = s;
      const items = [
        ...holidaysOf(y, m).map((d) => ({ d, kind: 'holiday', name: HOLIDAY_NAMES[`${pad(m)}-${pad(d)}`] || '공휴일' })),
        ...[7, 8, 17, 18, 27, 28].filter((d) => d <= daysIn(y, m)).map((d) => ({ d, kind: 'nomove', name: '손 없는 날' })),
      ].sort((a, b) => a.d - b.d || (a.kind === 'holiday' ? -1 : 1));
      const rows = items.map((it) => {
        const key = keyOf(y, m, it.d);
        return `<div class="flex items-center gap-4 border-b border-[#eef0ee] px-5 py-4 last:border-none ${key < TODAY ? 'opacity-50' : ''}">
          <b class="w-[104px] shrink-0 text-[16px] font-semibold tabular-nums text-[#292929]">${dateLabel(key)}</b>
          <span class="min-w-0 flex-1 truncate text-[15px] font-medium ${it.kind === 'holiday' ? 'text-[#d8663d]' : 'text-[#346538]'}">${it.name}</span></div>`;
      }).join('');
      listBox.innerHTML = `<div class="flex items-center justify-between px-1">
          <div class="flex items-center">
            <button type="button" data-hstep="-1" aria-label="이전 달" class="-ml-2 grid h-9 w-9 place-items-center rounded-full text-[#555] hover:bg-[#eef0ee]">${icon('chevronLeft', 19)}</button>
            <b class="min-w-[92px] text-center text-[15px] font-bold text-[#292929]">${y}년 ${m}월</b>
            <button type="button" data-hstep="1" aria-label="다음 달" class="grid h-9 w-9 place-items-center rounded-full text-[#555] hover:bg-[#eef0ee]">${icon('chevronRight', 19)}</button>
          </div>
          <button type="button" data-refresh class="-mr-1 flex h-9 items-center gap-1 rounded-full px-2 text-[14px] font-semibold text-[#346538] hover:bg-[#eef4f0]">${icon('refresh', 16)}다시 불러오기</button></div>
        <div class="mt-3">${card(rows || '<p class="py-10 text-center text-[14px] text-[#888]">공휴일 · 손 없는 날이 없습니다</p>')}</div>`;
    }
    function render() {
      root.querySelectorAll('[data-tab]').forEach((b) => b.setAttribute('aria-checked', String(b.dataset.tab === s.tab)));

      if (s.tab === 'days') renderDays(); else renderHolidays();
    }
    root.querySelectorAll('[data-tab]').forEach((b) => b.addEventListener('click', () => { s.tab = b.dataset.tab; s.showPast = false; render(); }));
    listBox.addEventListener('click', (e) => {
      if (e.target.closest('[data-toggle-past]')) { s.showPast = !s.showPast; render(); return; }
      const st = e.target.closest('[data-hstep]');
      if (st) { const d = new Date(s.hy, s.hm - 1 + Number(st.dataset.hstep), 1); s.hy = d.getFullYear(); s.hm = d.getMonth() + 1; render(); return; }
      if (e.target.closest('[data-refresh]')) { showToast('최신 정보로 불러왔습니다'); return; }
      const dk = e.target.closest('[data-daykey]');
      if (e.target.closest('[data-add-day]')) { openDay(); return; }
      if (e.target.closest('[data-open-daily]')) { openDaily(); return; }
      if (e.target.closest('[data-open-weekdays]')) { openWeekdays(); return; }
      if (e.target.closest('[data-holiday-toggle]')) { cfg = { ...cfg, holiday: !cfg.holiday }; saveConfig(cfg); render(); showToast(cfg.holiday ? '공휴일에는 예약을 받지 않아요' : '공휴일에도 예약을 받아요'); return; }
      if (dk) openDay(dk.dataset.daykey);
    });


    /* ----- 휴무일 추가·수정 시트 (TBL_DAY_STATUS 하루 1건) — 작은 달력에서 날짜를 누름 · 예약 있는 날이면 경고, 자동 알림 없음 (Q23) ----- */
    const day = root.querySelector('#dayStatus');
    const miniCal = day.querySelector('[data-mini-cal]');
    let editingKey = null, pick = '', miniCursor = dateOf(TODAY);
    function renderMini() {
      const y = miniCursor.getFullYear(), m = miniCursor.getMonth() + 1;
      const first = new Date(y, m - 1, 1).getDay();
      let cells = '';
      for (let i = 0; i < Math.ceil((first + daysIn(y, m)) / 7) * 7; i++) {
        const dt = new Date(y, m - 1, 1 - first + i), key = keyOfDate(dt), inMonth = dt.getMonth() + 1 === m;
        // 지난 날 · 이미 쉬는 날(정기·공휴일·다른 임시 휴무)은 고를 수 없음 — 고친 날짜 자신은 고를 수 있음
        const closed = key !== editingKey && isClosed(key, statuses, cfg);
        const off = !inMonth || key < TODAY || closed, sel = key === pick, has = resOn(key).some((r) => r.status === '예약 완료');
        cells += `<button type="button" data-mini="${key}" ${off ? 'disabled' : ''} class="flex flex-col items-center gap-[2px] py-0.5 ${inMonth ? '' : 'invisible'}">
          <span class="grid h-9 w-9 place-items-center rounded-full text-[15px] tabular-nums ${sel ? 'bg-[#346538] font-bold text-white' : off ? 'text-[#c4c8c5]' : `font-medium ${isRed(key) ? 'text-[#d8663d]' : 'text-[#292929]'}`}">${dt.getDate()}</span>
          <span class="flex h-3 items-center">${closed && inMonth ? '<span class="text-[12px] font-semibold leading-3 text-[#b0b4b1]">휴무</span>' : has ? `<i class="h-[5px] w-[5px] rounded-full ${sel ? 'bg-[#346538]' : 'bg-[#cdbf86]'}"></i>` : ''}</span></button>`;
      }
      miniCal.innerHTML = `<div class="flex items-center justify-between">
          <button type="button" data-mini-step="-1" aria-label="이전 달" class="-ml-2 grid h-9 w-9 place-items-center rounded-full text-[#555] hover:bg-[#f2f3f1]">${icon('chevronLeft', 19)}</button>
          <b class="text-[16px] font-bold text-[#1a1a1a]">${y}년 ${m}월</b>
          <button type="button" data-mini-step="1" aria-label="다음 달" class="-mr-2 grid h-9 w-9 place-items-center rounded-full text-[#555] hover:bg-[#f2f3f1]">${icon('chevronRight', 19)}</button></div>
        <div class="mt-2 grid grid-cols-7 text-center text-[12px] font-semibold text-[#a3a8a4]"><span class="text-[#e3a58e]">일</span><span>월</span><span>화</span><span>수</span><span>목</span><span>금</span><span>토</span></div>
        <div class="mt-1 grid grid-cols-7">${cells}</div>`;
      const booked = pick ? resOn(pick).filter((r) => r.status === '예약 완료').length : 0;
      day.querySelector('[data-day-picked]').textContent = pick ? dateLabel(pick) : '날짜를 눌러 주세요';
      day.querySelector('[data-day-picked]').className = `text-[15px] font-semibold ${pick ? 'text-[#346538]' : 'text-[#9a9f9b]'}`;
      day.querySelector('[data-date-warning]').hidden = !booked;
      day.querySelector('[data-warning-count]').textContent = booked;
      day.querySelector('[data-day-save]').disabled = !pick || pick === editingKey;
    }
    function openDay(key) {
      editingKey = key || null; pick = key || '';
      miniCursor = key ? dateOf(key) : dateOf(TODAY);
      day.querySelector('[data-day-title]').textContent = key ? '휴무일 수정' : '휴무일 추가';
      day.querySelector('[data-day-delete]').hidden = !key;
      day.querySelector('[data-day-save]').textContent = key ? '저장' : '등록';
      renderMini();
      day.hidden = false;
    }
    miniCal.addEventListener('click', (e) => {
      const st = e.target.closest('[data-mini-step]');
      if (st) { miniCursor = new Date(miniCursor.getFullYear(), miniCursor.getMonth() + Number(st.dataset.miniStep), 1); renderMini(); return; }
      const b = e.target.closest('[data-mini]');
      if (b && !b.disabled) { pick = b.dataset.mini; renderMini(); }
    });
    day.querySelector('[data-day-save]').addEventListener('click', () => {
      const key = pick;
      statuses = { ...statuses };
      if (editingKey && editingKey !== key) delete statuses[editingKey];
      statuses[key] = { status: 'closed' };
      saveStatuses(statuses);
      day.hidden = true; render(); showToast(editingKey ? '저장되었습니다' : '휴무일이 등록되었습니다');
    });
    day.querySelector('[data-day-delete]').addEventListener('click', () => {
      statuses = { ...statuses }; delete statuses[editingKey]; saveStatuses(statuses);
      day.hidden = true; render(); showToast('삭제되었습니다');
    });

    render();
    const state = root.dataset.state;
    if (state === 'past') { s.showPast = true; render(); }
    if (state === 'weekly') { cfg = { weekdays: [1], holiday: true, daily: 3 }; render(); }
    if (state === 'weekdays') openWeekdays();
    if (state === 'daily') openDaily(); // 검토용: 매주 월요일 + 공휴일 휴무
    if (state === 'holidays') { s.tab = 'holidays'; render(); }
    if (state === 'day-add') openDay();
    if (state === 'day-edit') openDay('26.10.09');
    if (state === 'day-warning') { openDay(); pick = '26.09.29'; renderMini(); }
    if (state === 'saved') { toast.hidden = false; }
  }

  const reservationsRoot = document.getElementById('scheduleCalendar');
  if (reservationsRoot) initReservations(reservationsRoot);
  const manage = document.getElementById('scheduleManage');
  if (manage) initManage(manage);
})();
