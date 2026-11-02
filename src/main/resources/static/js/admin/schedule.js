/*
 * 일정 캘린더(SCR-A-020) · 일정 등록(SCR-A-021) · 일정 관리(SCR-A-022)
 * 화면 개발 단계: 예약·회원·공휴일·손 없는 날은 더미 데이터. DB/API 연동 시 교체.
 * 일정 관리에서 저장한 날짜 상태(업체 휴무일·예약 불가)는 sessionStorage 로 일정 캘린더에 공유한다.
 */
(function () {
  const STATUS_KEY = 'codemove.admin.scheduleStatuses';
  const ICONS = (document.querySelector('use')?.getAttribute('href') || '/images/admin/icons.svg').split('#')[0];
  const icon = (name, size, cls = '') =>
    `<svg class="icon ${cls}" width="${size}" height="${size}"><use href="${ICONS}#i-${name}"/></svg>`;
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const pad = (n) => String(n).padStart(2, '0');
  const keyOf = (y, m, d) => `${String(y).slice(-2)}.${pad(m)}.${pad(d)}`;
  const daysIn = (y, m) => new Date(y, m, 0).getDate();

  /* ---------- 더미 데이터 (프로토타입 예약 30건과 동일한 규칙) ---------- */
  const customers = [
    ['홍길동', 'luxury', '010-1234-5678'], ['김명품', 'luxury2', '010-2244-8811'], ['박철수', 'park789', '010-7788-2200'],
    ['최지우', 'choi00', '010-3377-9021'], ['이서준', 'seojun82', '010-5892-1144'], ['정하윤', 'hayoon5', '010-9834-1420'],
    ['강민준', 'minjun77', '010-4471-3302'], ['윤서아', 'seoa11', '010-8842-3199'], ['송지호', 'jiho90', '010-1166-4201'],
    ['한유진', 'yujin26', '010-6673-9920'], ['오도현', 'dohyun3', '010-2207-6134'], ['문지안', 'jian8', '010-7318-4490'],
    ['임지수', 'jisoo19', '010-9901-7722'], ['김도윤', 'doyun24', '010-3142-6678'], ['이지민', 'jimin31', '010-5521-9034'],
    ['박서연', 'seoyeon6', '010-7713-2088'], ['최현우', 'hyunwoo88', '010-4055-7291'], ['정수빈', 'subin12', '010-6320-1847'],
    ['강준호', 'junho75', '010-9081-3362'], ['윤채원', 'chaewon4', '010-2466-8103'], ['송민재', 'minjae21', '010-8642-5179'],
    ['한예린', 'yerin09', '010-1937-6405'], ['오승민', 'seungmin5', '010-7254-3916'],
  ];
  const statuses = ['반려', '승인 대기', '이사 완료', '예약 확정', '견적 협의', '견적 확정'];
  const addresses = [
    '서울 강남구 테헤란로 521', '서울 중구 세종대로 110', '서울 송파구 올림픽로 300', '서울 용산구 한강대로 405',
    '서울 마포구 월드컵로 212', '서울 서초구 남부순환로 2584', '경기 성남시 분당구 판교역로 166', '경기 수원시 팔달구 효원로 241',
    '경기 고양시 덕양구 고양시청로 10', '인천 남동구 정각로 29', '대전 서구 둔산로 100', '광주 서구 내방로 111', '부산 연제구 중앙대로 1001',
  ];
  const weeklyDays = [27, 26, 26, 25, 24, 27, 23, 22, 22, 21];
  const records = Array.from({ length: 30 }, (_, i) => {
    const c = customers[i % customers.length];
    const month = i < 20 ? '09' : '08';
    const day = i < 10 ? weeklyDays[i] : i < 20 ? 20 - (i - 10) : 28 - (i - 20);
    return {
      id: i + 1, status: statuses[i % 6], name: c[0], userId: c[1], phone: c[2],
      appliedDate: `26.${month}.${pad(day)}`,
      service: i % 4 === 0 ? '기업이사' : i % 3 === 0 ? '원룸이사' : '가정이사',
      origin: addresses[i % addresses.length],
      destination: addresses[(i + 5 + (i % 3)) % addresses.length],
      movingDate: i < 10 ? `26.09.${pad(weeklyDays[i])}` : `26.10.${pad((i % 20) + 1)}`,
      movingTime: `${pad(7 + (i % 9))}:00`,
    };
  });
  const ACTIVE = ['승인 대기', '예약 확정', '이사 완료'];
  const activeRecords = records.filter((r) => ACTIVE.includes(r.status));
  const tone = { '승인 대기': '#4f80ac', '예약 확정': '#4f8b55', '이사 완료': '#59635b' };

  // 공휴일: 양력 고정 + 2026 음력·대체공휴일 (더미, API 연동 시 교체)
  const FIXED_HOLIDAYS = ['01-01', '03-01', '05-05', '06-06', '08-15', '10-03', '10-09', '12-25'];
  const YEAR_HOLIDAYS = {
    2026: ['02-16', '02-17', '02-18', '03-02', '05-24', '05-25', '06-03', '08-17', '09-24', '09-25', '09-26', '10-05'],
  };
  const holidaysOf = (y, m) => [...FIXED_HOLIDAYS, ...(YEAR_HOLIDAYS[y] || [])]
    .filter((md) => Number(md.slice(0, 2)) === m).map((md) => Number(md.slice(3)));
  // 손 없는 날: 더미(프로토타입 규칙). 연동 시 음력 9·10·19·20·29·30일로 계산
  const noMoveDaysOf = () => [7, 8, 17, 18, 27, 28];

  function loadStatuses() {
    try {
      const raw = sessionStorage.getItem(STATUS_KEY);
      if (raw) return JSON.parse(raw);
    } catch (e) { /* 저장소 사용 불가 시 기본값 */ }
    return { '26.09.20': 'closed' };
  }
  function saveStatuses(value) {
    try { sessionStorage.setItem(STATUS_KEY, JSON.stringify(value)); } catch (e) { /* ignore */ }
  }
  const statusChip = (s) =>
    `<span class="mt-1 block w-full truncate rounded px-1 py-0.5 text-[7px] font-bold text-white ${s === 'closed' ? 'bg-[#343a35]' : 'bg-[#F57C00]'}">${s === 'closed' ? '정기 휴무' : '예약 불가'}</span>`;

  /* ---------- 년·월 선택 팝업 (두 화면 공통) ---------- */
  function monthPicker(root, getCurrent, apply) {
    const layer = root.querySelector('#monthPicker');
    const yearSel = layer.querySelector('[data-temp-year]');
    const monthSel = layer.querySelector('[data-temp-month]');
    const daySel = layer.querySelector('[data-temp-day]');
    const label = layer.querySelector('[data-temp-label]');
    const selectBox = layer.querySelector('[data-picker-select]');
    const grid = layer.querySelector('[data-picker-grid]');
    let t = {};

    const render = () => {
      label.textContent = `${t.year}년 ${t.month}월`;
      if (yearSel) yearSel.value = t.year;
      if (monthSel) monthSel.value = t.month;
      // 일정 관리(A-022): 년·월 드롭다운 버튼 (프로토타입 방식)
      layer.querySelectorAll('[data-dd]').forEach((dd) => {
        const key = dd.dataset.dd;
        const values = key === 'year' ? Array.from({ length: 21 }, (_, i) => 2015 + i) : Array.from({ length: 12 }, (_, i) => i + 1);
        const unit = key === 'year' ? '년' : '월';
        dd.querySelector('[data-dd-label]').textContent = `${t[key]}${unit}`;
        dd.querySelector('[data-dd-list]').innerHTML = values.map((v) =>
          `<button type="button" data-dd-pick="${v}" class="h-9 w-full rounded-lg text-[11px] font-bold ${t[key] === v ? 'bg-[#edf3ed] text-[#346538]' : 'text-[#59625b]'}">${v}${unit}</button>`).join('');
      });
      if (daySel) {
        const n = daysIn(t.year, t.month);
        t.day = Math.min(t.day, n);
        daySel.innerHTML = Array.from({ length: n }, (_, i) => `<option value="${i + 1}">${i + 1}</option>`).join('');
        daySel.value = t.day;
      }
      grid.innerHTML = Array.from({ length: 12 }, (_, i) => i + 1).map((m) =>
        `<button type="button" data-grid-month="${m}" class="h-11 rounded-xl text-[11px] font-bold ${t.month === m ? 'bg-[#346538] text-white' : 'bg-[#f3f5f3]'}">${m}월</button>`).join('');
    };
    const open = () => {
      t = { ...getCurrent() };
      selectBox.hidden = false;
      grid.hidden = true;
      closeDropdowns();
      render();
      layer.hidden = false;
    };
    root.querySelector('[data-open-month]').addEventListener('click', open);
    layer.querySelector('[data-grid-toggle]').addEventListener('click', () => {
      selectBox.hidden = !selectBox.hidden;
      grid.hidden = !grid.hidden;
      closeDropdowns();
    });
    const closeDropdowns = (except) => layer.querySelectorAll('[data-dd]').forEach((dd) => {
      if (dd !== except) { dd.querySelector('[data-dd-list]').hidden = true; dd.querySelector('[data-dd-toggle]').setAttribute('aria-expanded', 'false'); }
    });
    layer.querySelectorAll('[data-dd]').forEach((dd) => {
      const list = dd.querySelector('[data-dd-list]');
      const toggle = dd.querySelector('[data-dd-toggle]');
      toggle.addEventListener('click', () => {
        closeDropdowns(dd);
        list.hidden = !list.hidden;
        toggle.setAttribute('aria-expanded', String(!list.hidden));
        if (!list.hidden) list.querySelector('.bg-\\[\\#edf3ed\\]')?.scrollIntoView({ block: 'nearest' });
      });
      list.addEventListener('click', (e) => {
        const b = e.target.closest('[data-dd-pick]');
        if (!b) return;
        t[dd.dataset.dd] = Number(b.dataset.ddPick);
        list.hidden = true;
        toggle.setAttribute('aria-expanded', 'false');
        render();
      });
    });
    yearSel?.addEventListener('change', () => { t.year = Number(yearSel.value); render(); });
    monthSel?.addEventListener('change', () => { t.month = Number(monthSel.value); render(); });
    daySel?.addEventListener('change', () => { t.day = Number(daySel.value); });
    grid.addEventListener('click', (e) => {
      const b = e.target.closest('[data-grid-month]');
      if (b) { t.month = Number(b.dataset.gridMonth); render(); }
    });
    layer.querySelector('[data-month-apply]').addEventListener('click', () => {
      apply(t);
      layer.hidden = true;
    });
    return { open };
  }

  /* ================= SCR-A-020 일정 캘린더 / SCR-A-021 일정 등록 ================= */
  function initCalendar(root) {
    const s = { year: 2026, month: 9, day: 27, expanded: false };
    const managed = loadStatuses();
    const manual = [];
    let deletingId = null;

    const grid = root.querySelector('[data-cal-grid]');
    const list = root.querySelector('[data-day-list]');
    const calBox = root.querySelector('[data-cal-box]');
    const dayBox = root.querySelector('[data-day-box]');

    function render() {
      const { year, month } = s;
      root.querySelector('[data-month-label]').textContent = `${year}년 ${month}월`;
      const first = new Date(year, month - 1, 1).getDay();
      const n = daysIn(year, month);
      const holidays = holidaysOf(year, month);
      const noMove = noMoveDaysOf(year, month);
      let html = '';
      for (let i = 0; i < 42; i++) {
        const d = i - first + 1;
        if (d < 1 || d > n) {
          html += '<span class="h-[58px]"></span>';
          continue;
        }
        const key = keyOf(year, month, d);
        const ev = activeRecords.find((r) => r.movingDate === key);
        const mi = manual.find((m) => m.date === key);
        const st = managed[key];
        const isHoliday = holidays.includes(d);
        html += `<button type="button" data-day="${d}" class="relative flex h-[58px] flex-col items-start border-t border-[#f0f2f0] p-1 text-left ${s.day === d ? 'bg-[#edf3ed]' : ''}">
          <span class="relative flex h-5 w-4 shrink-0 items-end justify-center">
            ${noMove.includes(d) ? '<span class="absolute left-1/2 top-0 h-1.5 w-1.5 -translate-x-1/2 rounded-full bg-[#4f80ac]"></span>' : ''}
            ${isHoliday ? '<span class="absolute left-1/2 top-0 h-1.5 w-1.5 -translate-x-1/2 rounded-full bg-[#c85f57]"></span>' : ''}
            <span class="text-[10px] font-bold ${isHoliday || i % 7 === 0 ? 'text-[#c85f57]' : 'text-[#555e57]'}">${d}</span>
          </span>
          ${ev ? `<span class="mt-1 block max-w-full truncate rounded px-1 py-0.5 text-[7px] font-bold text-white" style="background:${tone[ev.status]}">${esc(ev.name)}</span>` : ''}
          ${mi ? `<span class="mt-1 block max-w-full truncate rounded px-1 py-0.5 text-[7px] font-bold text-white ${manualTone(mi.category, 'bg')}">${esc(mi.title)}</span>` : ''}
          ${st ? statusChip(st).replace('w-full', 'max-w-full') : ''}
        </button>`;
      }
      grid.innerHTML = html;
      renderDay();
    }

    function manualTone(category, kind) {
      const c = category === '업체 휴무일' ? '#343a35' : category === '견적 상담' ? '#7a6db0' : '#7a837c';
      return kind === 'bg' ? `bg-[${c}]` : `border-[${c}]`;
    }

    function renderDay() {
      const { year, month, day } = s;
      const key = keyOf(year, month, day);
      root.querySelector('[data-day-month]').textContent = `${year}년 ${month}월`;
      root.querySelector('[data-day-title]').textContent = `${month}월 ${day}일`;
      root.querySelector('[data-day-badges]').innerHTML =
        (noMoveDaysOf(year, month).includes(day) ? '<span class="text-[9px] font-bold text-[#4f80ac]">손 없는 날</span>' : '') +
        (holidaysOf(year, month).includes(day) ? '<span class="text-[9px] font-bold text-[#c85f57]">공휴일</span>' : '');

      const daySchedules = activeRecords.filter((r) => r.movingDate === key).sort((a, b) => a.movingTime.localeCompare(b.movingTime));
      const dayManual = manual.filter((m) => m.date === key);
      let html = daySchedules.map((r) => `
        <button type="button" data-record="${r.id}" class="flex w-full gap-3 text-left">
          <span class="w-11 pt-3 text-[10px] font-bold text-[#687069]">${r.movingTime}</span>
          <span class="flex-1 rounded-xl border-l-4 bg-[#f5f8f5] p-3" style="border-color:${tone[r.status]}"><b class="block text-[11px]">${esc(r.name)}</b><small class="mt-1 block text-[9px] text-[#727a74]">${r.status} · ${r.service}</small></span>
        </button>`).join('');
      html += dayManual.map((m) => `
        <div class="relative">
          <button type="button" data-manual="${m.id}" class="flex w-full gap-3 text-left">
            <span class="w-11 pt-3 text-[10px] font-bold">${esc(m.time)}</span>
            <span class="flex-1 rounded-xl border-l-4 bg-[#f3f4f3] p-3 pr-10 text-[11px] font-bold ${manualTone(m.category, 'border')}">${esc(m.title)}<small class="mt-1 block font-normal text-[#7a827c]">${esc(m.category)}${m.place ? ` · ${esc(m.place)}` : ''}</small></span>
          </button>
          <button type="button" aria-label="${esc(m.title)} 일정 삭제" data-manual-trash="${m.id}" class="absolute right-2 top-2 grid h-8 w-8 place-items-center rounded-lg text-[#c7635a] hover:bg-[#fbefed]">${icon('trash', 15)}</button>
        </div>`).join('');
      if (managed[key]) {
        html += `<div class="flex gap-3"><span class="w-11 pt-3 text-[10px] font-bold">종일</span>
          <span class="flex-1 rounded-xl border-l-4 bg-[#f3f4f3] p-3 text-[11px] font-bold ${managed[key] === 'closed' ? 'border-[#343a35]' : 'border-[#F57C00]'}">${managed[key] === 'closed' ? '업체 휴무일' : '이사 예약 불가능'}</span></div>`;
      }
      list.innerHTML = html || '<p class="py-8 text-center text-[10px] text-[#929994]">등록된 일정이 없습니다.</p>';
    }

    function setExpanded(v) {
      s.expanded = v;
      calBox.classList.toggle('max-h-[250px]', v);
      calBox.classList.toggle('overflow-hidden', v);
      calBox.classList.toggle('opacity-90', v);
      dayBox.classList.toggle('min-h-[470px]', v);
      root.querySelector('[data-expand]').textContent = v ? '달력 펼치기' : '일정 펼치기';
    }

    function openRecord(id) {
      const r = records.find((x) => x.id === id);
      const layer = root.querySelector('#reservationDetail');
      const values = { ...r, estimateNo: `EST-${pad(r.id).padStart(4, '0')}`, reservationNo: `RSV-${pad(r.id).padStart(4, '0')}` };
      layer.querySelectorAll('[data-r]').forEach((el) => { el.textContent = values[el.dataset.r] || '-'; });
      const link = layer.querySelector('[data-r-link]');
      link.href = `${link.getAttribute('href').split('?')[0]}?id=${r.id}`; // 예약 관리(SCR-A-011)에서 해당 예약 강조용
      layer.hidden = false;
    }

    function openManual(id) {
      const m = manual.find((x) => x.id === id);
      const member = m.memberId ? customers.find((c) => c[1] === m.memberId) : null;
      const r = m.reservationId ? records.find((x) => x.id === m.reservationId) : null;
      const row = (k, v) => `<p class="flex justify-between gap-3"><b>${k}</b><span class="text-right">${esc(v)}</span></p>`;
      let html = `<div class="space-y-3 rounded-xl bg-[#f7f8f7] p-4 text-[10px]">
        ${row('제목', m.title)}${row('분류', m.category)}${row('요일·날짜', m.date)}${row('시간', m.time)}${row('장소', m.place || '미등록')}</div>`;
      if (member) {
        html += `<div class="mt-3 rounded-xl border border-[#e1e5e1] p-4"><p class="text-[10px] font-extrabold text-[#346538]">연결 회원</p>
          <p class="mt-2 text-[10px] font-bold">${esc(member[0])}</p><p class="mt-1 text-[9px] text-[#7c847e]">${member[2]}</p></div>`;
      }
      if (r) {
        html += `<div class="mt-3 rounded-xl border border-[#e1e5e1] p-4"><div class="flex items-center justify-between">
          <p class="text-[10px] font-extrabold text-[#346538]">연결 예약</p><span class="rounded-full bg-[#edf4ed] px-2 py-1 text-[8px] font-bold text-[#346538]">${r.status}</span></div>
          <p class="mt-2 truncate text-[9px] text-[#626a64]">${esc(r.origin)} → ${esc(r.destination)}</p></div>
          <button type="button" data-record="${r.id}" class="mt-5 h-11 w-full rounded-xl bg-[#346538] text-[11px] font-bold text-white">상세 보기</button>`;
      }
      root.querySelector('[data-manual-body]').innerHTML = html;
      root.querySelector('#manualDetail').hidden = false;
    }

    root.addEventListener('click', (e) => {
      const day = e.target.closest('[data-day]');
      if (day) { s.day = Number(day.dataset.day); render(); return; }
      const rec = e.target.closest('[data-record]');
      if (rec) { root.querySelector('#manualDetail').hidden = true; openRecord(Number(rec.dataset.record)); return; }
      const trash = e.target.closest('[data-manual-trash]');
      if (trash) { deletingId = Number(trash.dataset.manualTrash); root.querySelector('#manualDelete').hidden = false; return; }
      const mi = e.target.closest('[data-manual]');
      if (mi) { openManual(Number(mi.dataset.manual)); return; }
      const step = e.target.closest('[data-month-step]');
      if (step) {
        const next = new Date(s.year, s.month - 1 + Number(step.dataset.monthStep), 1);
        s.year = next.getFullYear(); s.month = next.getMonth() + 1; s.day = 1;
        render();
      }
    });
    root.querySelector('[data-expand]').addEventListener('click', () => setExpanded(!s.expanded));
    root.querySelector('[data-manual-delete]').addEventListener('click', () => {
      const idx = manual.findIndex((m) => m.id === deletingId);
      if (idx > -1) manual.splice(idx, 1);
      root.querySelector('#manualDelete').hidden = true;
      render();
    });
    // 일정 목록 위/아래로 밀어 펼치기·접기
    let touchY = 0;
    root.addEventListener('touchstart', (e) => { touchY = e.touches[0].clientY; }, { passive: true });
    root.addEventListener('touchend', (e) => {
      if (!e.target.closest('[data-day-box]')) return;
      const delta = touchY - e.changedTouches[0].clientY;
      if (Math.abs(delta) > 45) setExpanded(delta > 0);
    });

    const picker = monthPicker(root, () => ({ year: s.year, month: s.month, day: s.day }), (t) => {
      s.year = t.year; s.month = t.month; s.day = Math.min(t.day, daysIn(t.year, t.month));
      render();
    });

    /* ----- SCR-A-021 일정 등록 ----- */
    const reg = root.querySelector('#scheduleRegister');
    const form = reg.querySelector('[data-register-form]');
    const f = form.elements;
    const catLabel = reg.querySelector('[data-category-label]');
    const catMenu = reg.querySelector('[data-category-menu]');
    const catCustom = reg.querySelector('[data-category-custom]');
    const catChevron = reg.querySelector('[data-category-chevron]');
    const memberInput = reg.querySelector('[data-member-query]');
    const memberBox = reg.querySelector('[data-member-results]');
    const resInput = reg.querySelector('[data-reservation-query]');
    const resBox = reg.querySelector('[data-reservation-results]');
    const submit = reg.querySelector('[data-register-submit]');
    let selMember = '';
    let selReservation = null;

    const digits = (v) => v.replace(/\D/g, '');
    const matches = (text, q) => {
      const ql = q.trim().toLowerCase();
      if (!ql) return true;
      return text.toLowerCase().includes(ql) || (digits(ql) && digits(text).includes(digits(ql)));
    };

    function validate() {
      submit.disabled = !(f.title.value.trim() && f.category.value.trim() && f.date.value && f.time.value);
    }
    function setCategory(value, custom) {
      catMenu.hidden = true;
      catChevron.classList.remove('rotate-180');
      catCustom.hidden = !custom;
      f.category.value = custom ? catCustom.value : value;
      catLabel.textContent = custom || !value ? '분류를 선택하세요' : value;
      catLabel.className = custom || !value ? 'text-[#939a95]' : 'font-semibold text-[#424a44]';
      if (custom) catCustom.focus();
      validate();
    }
    function renderMembers() {
      const q = memberInput.value;
      memberBox.hidden = !q || !!selMember;
      if (memberBox.hidden) return;
      const rows = customers.filter((c) => matches(`${c[0]} ${c[2]}`, q)).slice(0, 5);
      memberBox.innerHTML = rows.map((c) =>
        `<button type="button" data-pick-member="${c[1]}" class="flex w-full justify-between border-b border-[#edf0ed] px-3 py-2 text-left text-[9px] last:border-none"><b>${esc(c[0])}</b><span class="text-[#858d87]">${c[2]}</span></button>`).join('')
        || '<p class="p-3 text-center text-[9px] text-[#939a95]">일치하는 회원이 없습니다.</p>';
    }
    function renderReservations() {
      const q = resInput.value;
      resBox.hidden = !(selMember || q);
      if (resBox.hidden) return;
      const rows = records.filter((r) => !selMember || r.userId === selMember)
        .filter((r) => selReservation === r.id || matches(`${r.name} ${r.phone} ${r.origin} ${r.destination}`, q)).slice(0, 6);
      resBox.innerHTML = rows.map((r) =>
        `<button type="button" data-pick-reservation="${r.id}" class="w-full border-b border-[#edf0ed] px-3 py-2 text-left text-[9px] last:border-none ${selReservation === r.id ? 'bg-[#edf3ed]' : ''}"><b class="block">${esc(r.name)} · ${r.phone}</b><span class="mt-1 block truncate text-[#7d857f]">${esc(r.origin)} → ${esc(r.destination)}</span></button>`).join('')
        || '<p class="p-3 text-center text-[9px] text-[#939a95]">일치하는 예약이 없습니다.</p>';
    }
    function openRegister() {
      form.reset();
      f.date.value = `${s.year}-${pad(s.month)}-${pad(s.day)}`;
      catCustom.value = '';
      selMember = ''; selReservation = null;
      setCategory('견적 상담', false);
      memberBox.hidden = true; resBox.hidden = true;
      reg.hidden = false;
      validate();
    }

    root.querySelector('[data-open-register]').addEventListener('click', openRegister);
    reg.querySelector('[data-category-toggle]').addEventListener('click', () => {
      catMenu.hidden = !catMenu.hidden;
      catChevron.classList.toggle('rotate-180', !catMenu.hidden);
    });
    catMenu.addEventListener('click', (e) => {
      const b = e.target.closest('[data-category]');
      if (b) setCategory(b.dataset.category, b.dataset.category === '직접 입력');
    });
    catCustom.addEventListener('input', () => { f.category.value = catCustom.value; validate(); });
    form.addEventListener('input', validate);
    memberInput.addEventListener('input', () => { selMember = ''; selReservation = null; renderMembers(); renderReservations(); });
    resInput.addEventListener('input', () => { selReservation = null; renderReservations(); });
    memberBox.addEventListener('click', (e) => {
      const b = e.target.closest('[data-pick-member]');
      if (!b) return;
      const c = customers.find((x) => x[1] === b.dataset.pickMember);
      selMember = c[1];
      memberInput.value = `${c[0]}(${c[2]})`;
      resInput.value = '';
      selReservation = null;
      renderMembers(); renderReservations();
    });
    resBox.addEventListener('click', (e) => {
      const b = e.target.closest('[data-pick-reservation]');
      if (!b) return;
      const r = records.find((x) => x.id === Number(b.dataset.pickReservation));
      selReservation = r.id;
      resInput.value = `${r.name} · ${r.origin}`;
      if (!selMember) { selMember = r.userId; memberInput.value = `${r.name}(${r.phone})`; renderMembers(); }
      renderReservations();
    });
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      validate();
      if (submit.disabled) return;
      const [y, m, d] = f.date.value.split('-').map(Number);
      manual.push({
        id: Date.now(), date: keyOf(y, m, d), title: f.title.value.trim(), category: f.category.value.trim(),
        time: f.time.value, place: f.place.value.trim(), memberId: selMember || null, reservationId: selReservation,
      });
      s.year = y; s.month = m; s.day = d;
      reg.hidden = true;
      render();
    });

    render();

    // 스토리보드 상태
    const state = root.dataset.state;
    if (state === 'day' || state === 'detail') {
      s.day = 26; render();
      if (state === 'day') requestAnimationFrame(() => dayBox.scrollIntoView({ block: 'end' }));
      if (state === 'detail') openRecord(2);
    } else if (state === 'month') {
      picker.open();
    } else if (state === 'register' || state === 'member' || state === 'custom') {
      openRegister();
      if (state === 'member') { memberInput.value = '홍'; renderMembers(); memberInput.focus(); }
      if (state === 'custom') setCategory('', true);
    }
  }

  /* ================= SCR-A-022 일정 관리 ================= */
  function initManage(root) {
    const s = { year: 2026, month: 9, editing: false, selected: '' };
    let saved = loadStatuses();
    let draft = { ...saved };
    const grid = root.querySelector('[data-cal-grid]');
    const dateLayer = root.querySelector('#dateStatus');
    const confirmLayer = root.querySelector('#saveConfirm');
    const toast = root.querySelector('[data-toast]');
    let toastTimer;

    function render() {
      const { year, month } = s;
      root.querySelector('[data-month-label]').textContent = `${year}년 ${month}월`;
      root.querySelector('[data-edit-start]').hidden = s.editing;
      root.querySelector('[data-edit-done]').hidden = !s.editing;
      root.querySelector('[data-edit-pill]').hidden = !s.editing;
      const current = s.editing ? draft : saved;
      const first = new Date(year, month - 1, 1).getDay();
      const n = daysIn(year, month);
      let html = '';
      for (let i = 0; i < 42; i++) {
        const d = i - first + 1;
        if (d < 1 || d > n) {
          html += '<span class="h-[62px]"></span>';
          continue;
        }
        const key = keyOf(year, month, d);
        const st = current[key];
        const ev = activeRecords.find((r) => r.movingDate === key);
        html += `<button type="button" data-key="${key}" class="flex h-[62px] flex-col items-start border-t border-[#f0f2f0] p-1 text-left ${s.editing ? 'hover:bg-[#f4f7f4]' : 'cursor-default'}">
          <span class="h-5 text-[10px] font-bold ${i % 7 === 0 ? 'text-[#c85f57]' : 'text-[#555e57]'}">${d}</span>
          ${st ? statusChip(st).replace('mt-1 ', '') : ''}
          ${!st && ev ? `<span class="block w-full truncate rounded bg-[#7e8a80] px-1 py-0.5 text-[7px] font-bold text-white">${esc(ev.name)}</span>` : ''}
        </button>`;
      }
      grid.innerHTML = html;
    }

    function beginEdit() { draft = { ...saved }; s.editing = true; render(); }
    function openDate(key) {
      s.selected = key;
      dateLayer.querySelector('[data-date-key]').textContent = key;
      const value = draft[key] || 'unavailable';
      dateLayer.querySelectorAll('input[name="dateStatus"]').forEach((r) => { r.checked = r.value === value; });
      const has = !!draft[key];
      dateLayer.querySelector('[data-date-clear]').hidden = !has;
      dateLayer.querySelector('[data-date-actions]').classList.toggle('grid-cols-2', has);
      dateLayer.querySelector('[data-date-actions]').classList.toggle('grid-cols-1', !has);
      dateLayer.hidden = false;
    }

    root.addEventListener('click', (e) => {
      const cell = e.target.closest('[data-key]');
      if (cell) { if (s.editing) openDate(cell.dataset.key); return; }
      const step = e.target.closest('[data-month-step]');
      if (step) {
        const next = new Date(s.year, s.month - 1 + Number(step.dataset.monthStep), 1);
        s.year = next.getFullYear(); s.month = next.getMonth() + 1;
        render();
      }
    });
    root.querySelector('[data-edit-start]').addEventListener('click', beginEdit);
    root.querySelector('[data-edit-done]').addEventListener('click', () => { confirmLayer.hidden = false; });
    dateLayer.querySelector('[data-date-save]').addEventListener('click', () => {
      draft[s.selected] = dateLayer.querySelector('input[name="dateStatus"]:checked').value;
      dateLayer.hidden = true;
      render();
    });
    dateLayer.querySelector('[data-date-clear]').addEventListener('click', () => {
      delete draft[s.selected];
      dateLayer.hidden = true;
      render();
    });
    confirmLayer.querySelector('[data-edit-cancel]').addEventListener('click', () => {
      draft = { ...saved }; s.editing = false; confirmLayer.hidden = true; render();
    });
    confirmLayer.querySelector('[data-edit-save]').addEventListener('click', () => {
      saved = { ...draft }; saveStatuses(saved);
      s.editing = false; confirmLayer.hidden = true; render();
      // 자동 알림 실행(시뮬레이션)
      toast.hidden = false;
      clearTimeout(toastTimer);
      toastTimer = setTimeout(() => { toast.hidden = true; }, 2500);
    });

    const picker = monthPicker(root, () => ({ year: s.year, month: s.month }), (t) => { s.year = t.year; s.month = t.month; render(); });

    render();
    const state = root.dataset.state;
    if (state === 'edit' || state === 'date' || state === 'confirm') beginEdit();
    if (state === 'date') openDate('26.09.23');
    if (state === 'confirm') confirmLayer.hidden = false;
    if (state === 'month' || state === 'month-year') picker.open();
    if (state === 'month-year') root.querySelector('[data-dd="year"] [data-dd-toggle]').click();
  }

  const cal = document.getElementById('scheduleCalendar');
  if (cal) initCalendar(cal);
  const manage = document.getElementById('scheduleManage');
  if (manage) initManage(manage);
})();
