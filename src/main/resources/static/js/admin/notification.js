/* SCR-A-013~015 알림 관리 — 탭(발송 내역 · 알림 설정)
   내역: 현황 칸(보류 · 문자 대체) 결과 필터 · 채널 글자 드롭다운 · 검색(이름·날짜) · 날짜 묶음 · 상세 시트
   설정: 토글(알림 받기 · 자동 발송 · 야간 발송 보류 · 문자 대체) · 보류 시각 시트 (더미, 화면 안에서만) */
(function () {
  const root = document.getElementById('notiManage');
  if (!root) return;
  const $ = (s, el = root) => el.querySelector(s);
  const $$ = (s, el = root) => [...el.querySelectorAll(s)];
  const state = root.dataset.state;

  /* ---------- 탭 ---------- */
  const tabs = $$('[data-tab-btn]');
  const showTab = (name) => {
    tabs.forEach((b) => { b.classList.toggle('is-active', b.dataset.tabBtn === name); b.setAttribute('aria-selected', String(b.dataset.tabBtn === name)); });
    $$('[data-panel]').forEach((p) => { p.hidden = p.dataset.panel !== name; });
  };
  tabs.forEach((b) => b.addEventListener('click', () => showTab(b.dataset.tabBtn)));
  showTab(root.dataset.tab);

  /* ---------- 채널 글자 드롭다운 (리뷰 관리와 같은 동작) ---------- */
  $$('[data-dropdown]').forEach((box) => {
    const select = $('select', box);
    const trigger = $('[data-dd-trigger]', box);
    const menu = $('[data-dd-menu]', box);
    const label = $('[data-dd-label]', box);
    const close = () => { menu.hidden = true; trigger.setAttribute('aria-expanded', 'false'); };
    const render = () => {
      label.textContent = select.selectedOptions[0].textContent;
      menu.innerHTML = '';
      [...select.options].forEach((o) => {
        const sel = o.selected;
        const li = document.createElement('li');
        li.innerHTML = `<button type="button" role="option" aria-selected="${sel}" class="flex h-10 w-full items-center justify-between gap-3 rounded-[10px] px-3 text-[15px] ${sel ? 'font-semibold text-[#346538]' : 'text-[#292929] hover:bg-[#f2f3f1]'}">
          ${o.textContent}${sel ? '<svg class="icon" width="16" height="16"><use href="/images/admin/icons-v2.svg#i-check"/></svg>' : ''}</button>`;
        li.firstElementChild.addEventListener('click', () => {
          select.value = o.value;
          select.dispatchEvent(new Event('change'));
          render();
          close();
        });
        menu.appendChild(li);
      });
    };
    trigger.addEventListener('click', () => {
      const open = menu.hidden;
      $$('[data-dd-menu]').forEach((m) => { m.hidden = true; });
      menu.hidden = !open;
      trigger.setAttribute('aria-expanded', String(open));
    });
    document.addEventListener('click', (e) => { if (!box.contains(e.target)) close(); });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });
    render();
  });

  /* ---------- 내역 검색 · 필터 ---------- */
  // 날짜 검색: 견적 관리(estimate.js)와 같은 규칙 — 26.3 · 3.25 · 3월 · 260325 등
  function dateMatcher(q) {
    const text = q.replace(/년|월/g, '.').replace(/일/g, '').trim();
    if (!/^[\d.\s/-]+$/.test(text)) return null;
    if (!/[.\s/-]/.test(text)) {
      return (date) => {
        const d = date.replace(/\D/g, '');
        return d.startsWith(text) || `20${d}`.startsWith(text) || d.slice(2).startsWith(text) || d.slice(2, 4) === `0${text}`;
      };
    }
    const parts = text.split(/[.\s/-]+/).filter(Boolean);
    const typing = !/[.\s/-]$/.test(text);
    const fieldMatch = (field, value, p, last) => {
      if (field === 'y' && p.length > 2) return p.length === 4 ? value === p.slice(2) : `20${value}`.startsWith(p);
      if (last && typing && p.length === 1) return value === `0${p}` || value.startsWith(p);
      return value === p.padStart(2, '0');
    };
    const forms = /년/.test(q) ? [['y', 'm', 'd']] : /월/.test(q) ? [['m', 'd']] : [['y', 'm', 'd'], ['m', 'd']];
    return (date) => {
      const [y, m, d] = date.split('.');
      const value = { y, m, d };
      return forms.some((form) => parts.length <= form.length
        && parts.every((p, i) => fieldMatch(form[i], value[form[i]], p, i === parts.length - 1)));
    };
  }

  const panel = $('[data-panel="history"]');
  const rows = () => $$('[data-row]');
  const groups = $$('[data-group]');
  const input = $('#notiQuery');
  const channel = $('#channelFilter');
  const stats = $$('[data-stat]');
  let result = '';
  // 머리줄 건수 = 최근 30일 전체 중 조건에 맞는 수 (아직 안 불러온 행 포함, 실제: 서버가 전체 개수를 함께 돌려줌)
  let notLoadedCount = null;

  const apply = () => {
    const q = input.value.trim();
    const byDate = q ? dateMatcher(q) : null;
    let shown = 0;
    rows().forEach((r) => {
      // 30일 지난 기록(data-archive)은 날짜로 검색할 때만 (실제: 날짜 조건이면 서버가 30일 범위를 넘어 조회)
      if (r.dataset.archive && !(byDate && byDate(r.dataset.date))) { r.hidden = true; return; }
      const ok = (!q || (byDate ? byDate(r.dataset.date) : r.dataset.name.includes(q)))
        && (!channel.value || r.dataset.channel === channel.value)
        && (!result || r.dataset.result === result);
      r.hidden = !ok;
      if (ok) shown += 1;
    });
    groups.forEach((g) => { g.hidden = !g.querySelector('[data-row]:not([hidden])'); });
    $('#notiEmpty').hidden = shown > 0;
    $('#notiCount').textContent = shown + (notLoadedCount ? notLoadedCount(q, byDate) : 0);
  };
  // 현황 칸: 누르면 그 결과만, 선택된 칸을 다시 누르면 해제
  const setResult = (value) => {
    result = value;
    stats.forEach((s) => s.setAttribute('aria-pressed', String(s.dataset.stat === value)));
    $('#resultClear').hidden = !value;
    $('[data-result-label]').textContent = value === 'held' ? '보류' : '문자 대체';
    apply();
  };
  stats.forEach((s) => s.addEventListener('click', () => setResult(result === s.dataset.stat ? '' : s.dataset.stat)));
  $('#resultClear').addEventListener('click', () => setResult(''));

  // 지우기 버튼은 검색어가 있을 때만
  const syncClear = () => { $('#notiClear').hidden = !input.value; };
  $('#notiClear').addEventListener('click', () => { input.value = ''; syncClear(); apply(); input.focus(); });
  input.addEventListener('input', syncClear);
  input.addEventListener('input', apply);
  channel.addEventListener('change', apply);
  if (panel.dataset.initialQuery) input.value = panel.dataset.initialQuery;
  syncClear();
  if (state === 'filter-held') setResult('held'); else apply();

  /* ---------- 무한 스크롤: 20건씩, 최근 30일(26.10.04)까지 ---------- */
  // 화면 개발 단계: 더 오래된 더미 행을 만들어 '이전' 묶음 끝(30일 지난 기록 앞)에 붙인다. 실제: 서버가 다음 20건을 돌려줌
  const PAGE = 20;
  const loadingRow = $('#notiLoading');
  const endRow = $('#notiEnd');
  const olderBox = groups[groups.length - 1].querySelector('div');
  const POOL = [
    ['정현우', '2286', '견적 도착', '견적이 도착했어요', '요청하신 견적이 도착했습니다. 확인해 주세요.'],
    ['한유진', '1575', '견적 접수', '견적 요청 접수', '견적 요청이 정상적으로 접수되었습니다.'],
    ['박철수', '7712', '예약 확정', '예약이 확정되었어요', '견적 확정으로 이사 예약이 완료되었습니다.'],
    ['윤아름', '8803', '계약금 결제', '계약금 결제 완료', '계약금 결제가 확인되었습니다.'],
    ['송지호', '4419', '이사 D-3', '이사 3일 전', '이사 예정일이 3일 남았습니다. 준비 사항을 확인해 주세요.'],
    ['강민주', '1234', '이사 완료', '이사 완료', '이사 완료 처리가 되었습니다. 서비스 이용은 어떠셨나요?'],
    ['최지우', '9048', '일정 변경', '이사 일정 변경', '이사 일정이 변경되었습니다. 확인해 주세요.'],
    ['이서준', '3307', '견적 변경', '견적이 변경되었어요', '승인된 견적 내용이 수정되었습니다. 다시 확인해 주세요.'],
  ];
  const BOUNDARY = new Date(2026, 9, 4);   // 26.11.02 기준 최근 30일 시작
  let cursor = new Date(2026, 9, 23);     // 서버가 처음 준 14건 다음(더 오래된) 날짜부터
  let seq = 0;
  let done = false;
  let fetching = false;
  const fmt = (d) => `${String(d.getFullYear()).slice(2)}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
  const makeRow = (date) => {
    const p = POOL[seq % POOL.length];
    const res = seq % 9 === 4 ? 'sms' : 'sent';
    const ch = res === 'sms' ? '문자' : (seq % 7 === 3 ? '앱' : '알림톡');
    const time = `${String(9 + (seq * 5) % 10).padStart(2, '0')}:${String((seq * 17) % 60).padStart(2, '0')}`;
    seq += 1;
    const el = document.createElement('div');
    Object.assign(el.dataset, { row: '', name: p[0], phone: p[1], date, channel: ch, result: res, detail: p[2], title: p[3], content: p[4], sent: `${date} ${time}` });
    el.setAttribute('role', 'button');
    el.tabIndex = 0;
    el.className = 'cursor-pointer border-b border-[#eef0ee] px-4 py-3 transition last:border-none hover:bg-[#f7f8f6]';
    el.innerHTML = `<div class="flex items-center justify-between gap-2">
        <strong class="truncate text-[15px] font-semibold text-[#292929]">${p[0]}</strong>
        <span class="shrink-0 text-[13px] tabular-nums text-[#888]">${date}</span></div>
      <div class="mt-0.5 flex items-center justify-between gap-2">
        <span class="truncate text-[13px] text-[#888]">${p[2]} · ${ch}</span>
        <span class="chip shrink-0 ${res === 'sms' ? 'chip-progress' : 'chip-done'}">${res === 'sms' ? '문자 대체' : '발송'}</span></div>`;
    return el;
  };
  // 다음 n건: 하루 3건씩 과거로, 30일 경계에서 멈춤
  const appendRows = (n) => {
    const firstArchive = olderBox.querySelector('[data-archive]');
    for (let i = 0; i < n; i += 1) {
      if (cursor < BOUNDARY) { done = true; break; }
      olderBox.insertBefore(makeRow(fmt(cursor)), firstArchive);
      if (seq % 3 === 0) cursor = new Date(cursor.getFullYear(), cursor.getMonth(), cursor.getDate() - 1);
    }
    if (cursor < BOUNDARY) done = true;
    endRow.hidden = !done;
    apply();
  };
  const loadMore = (instant) => {
    if (done || fetching) return;
    fetching = true;
    loadingRow.hidden = false;
    const finish = () => {
      appendRows(PAGE);
      fetching = false;
      loadingRow.hidden = true;
      // 필터로 보이는 행이 적어 감시 지점이 여전히 보이면 이어서 불러옴
      io.unobserve(sentinel); if (!done) io.observe(sentinel);
    };
    if (instant) finish(); else setTimeout(finish, 700);
  };
  const sentinel = $('#notiSentinel');
  const io = new IntersectionObserver((entries) => { if (entries[0].isIntersecting && !panel.hidden) loadMore(false); }, { root: panel, rootMargin: '0px 0px 80px 0px' });
  $('#notiEndSearch').addEventListener('click', () => { panel.scrollTo({ top: 0, behavior: 'smooth' }); input.focus(); });

  // 아직 안 불러온 행(화면 개발 단계: 같은 규칙으로 미리 계산)도 건수에 포함
  const FUTURE = (() => {
    const list = [];
    let s = 0;
    let c = new Date(cursor);
    while (c >= BOUNDARY) {
      const p = POOL[s % POOL.length];
      const res = s % 9 === 4 ? 'sms' : 'sent';
      list.push({ name: p[0], date: fmt(c), channel: res === 'sms' ? '문자' : (s % 7 === 3 ? '앱' : '알림톡'), result: res });
      s += 1;
      if (s % 3 === 0) c = new Date(c.getFullYear(), c.getMonth(), c.getDate() - 1);
    }
    return list;
  })();
  notLoadedCount = (q, byDate) => FUTURE.slice(seq).filter((d) => (!q || (byDate ? byDate(d.date) : d.name.includes(q)))
    && (!channel.value || d.channel === channel.value) && (!result || d.result === result)).length;

  appendRows(PAGE - 14);   // 첫 화면 20건 = 서버가 준 14건 + 6건
  if (state === 'end') {
    while (!done) appendRows(PAGE);
    panel.scrollTop = panel.scrollHeight;
  } else if (state === 'loading') {
    appendRows(PAGE);
    loadingRow.hidden = false;   // 불러오는 중 줄을 멈춘 상태로 보여 줌
    fetching = true;
    panel.scrollTop = panel.scrollHeight;
  } else {
    io.observe(sentinel);
  }

  /* ---------- 시간 표시 ---------- */
  const toDisplay = (hhmm) => {
    const [h, m] = hhmm.split(':').map(Number);
    return `${h >= 12 ? '오후' : '오전'} ${String(h % 12 || 12).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
  };
  const quietRows = $$('[data-quiet-time]');   // 보류 시작 · 보류 종료 행

  /* ---------- 상세 시트 ---------- */
  const sheet = document.getElementById('notiDetail');
  const set = (k, v) => { sheet.querySelector(`[data-d="${k}"]`).textContent = v; };
  const openDetail = (row) => {
    const d = row.dataset;
    set('title', d.title);
    set('content', d.content);
    set('to', `${d.name} · 010-****-${d.phone}`);
    set('channel', d.result === 'sms' ? '문자 (알림톡 실패)' : d.channel);
    set('sent', d.sent || `보류 중 · ${toDisplay(quietRows[1].dataset.value)} 발송 예정`);
    set('detail', d.detail);
    sheet.hidden = false;
  };
  // 행 클릭은 위임 (무한 스크롤로 붙는 행 포함)
  panel.addEventListener('click', (e) => { const row = e.target.closest('[data-row]'); if (row) openDetail(row); });
  panel.addEventListener('keydown', (e) => {
    const row = e.target.closest('[data-row]');
    if (row && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); openDetail(row); }
  });
  if (state === 'detail') openDetail(rows()[0]);

  /* ---------- 알림 설정 ---------- */
  const sw = (name) => $(`[data-switch="${name}"]`);
  const on = (name) => sw(name).getAttribute('aria-pressed') === 'true';
  const footer = $('#sendFooter');
  const FOOTER = footer.textContent;
  // 자동 발송이 꺼지면 아래 행 흐리게 + 섹션 아래 문구를 주황 경고로, 야간 발송 보류가 꺼지면 보류 시작·종료 행 비활성
  const sync = () => {
    const enabled = on('enabled');
    const quietOk = enabled && on('quiet');
    $$('[data-needs="enabled"]').forEach((el) => el.classList.toggle('opacity-40', !enabled));
    ['quiet', 'sms'].forEach((n) => { sw(n).disabled = !enabled; });
    quietRows.forEach((r) => { r.disabled = !quietOk; r.classList.toggle('opacity-40', !quietOk); });
    footer.textContent = enabled ? FOOTER : '고객에게 알림톡·문자가 발송되지 않습니다.';
    footer.classList.toggle('text-[#888]', enabled);
    footer.classList.toggle('font-semibold', !enabled);
    footer.classList.toggle('text-[#d9701a]', !enabled);
  };
  $$('[data-switch]').forEach((b) => b.addEventListener('click', () => {
    if (b.disabled) return;
    b.setAttribute('aria-pressed', String(!on(b.dataset.switch)));
    sync();
  }));

  /* 보류 시각 시트: 누른 행(보류 시작 / 보류 종료)의 시각만 휠(오전/오후 · 시 · 분)로 — 채팅 설정 시간 시트와 같은 휠 */
  const picker = document.getElementById('timePicker');
  const ROW = 44;
  const wheels = [...picker.querySelectorAll('[data-wheel]')];
  const wheel = (name) => picker.querySelector(`[data-wheel="${name}"]`);
  const opts = (w) => [...w.querySelectorAll('[role=option]')];
  let target = null;
  const mark = (w) => {
    const i = Math.round(w.scrollTop / ROW);
    opts(w).forEach((o, k) => o.setAttribute('aria-selected', String(k === i)));
  };
  const setWheel = (name, value) => {
    const w = wheel(name);
    w.scrollTop = Math.max(0, opts(w).findIndex((o) => o.dataset.value === value)) * ROW;
    mark(w);
  };
  const val = (name) => { const w = wheel(name); const o = opts(w); return o[Math.min(o.length - 1, Math.round(w.scrollTop / ROW))].dataset.value; };
  wheels.forEach((w) => {
    w.addEventListener('scroll', () => mark(w));
    w.addEventListener('click', (e) => { const o = e.target.closest('[role=option]'); if (o) w.scrollTo({ top: opts(w).indexOf(o) * ROW, behavior: 'smooth' }); });
  });
  const openPicker = (row) => {
    target = row;
    const [h, m] = row.dataset.value.split(':').map(Number);
    document.getElementById('timePickerTitle').textContent = row.dataset.quietTime === 'start' ? '보류 시작' : '보류 종료';
    picker.hidden = false;
    setWheel('period', h >= 12 ? '오후' : '오전');
    setWheel('hour', String(h % 12 || 12).padStart(2, '0'));
    setWheel('minute', String(m - (m % 10)).padStart(2, '0'));
  };
  quietRows.forEach((row) => row.addEventListener('click', () => { if (!row.disabled) openPicker(row); }));
  document.getElementById('pickerDone').addEventListener('click', () => {
    const h12 = Number(val('hour'));
    const h24 = val('period') === '오후' ? (h12 % 12) + 12 : (h12 === 12 ? 0 : h12);
    target.dataset.value = `${String(h24).padStart(2, '0')}:${val('minute')}`;
    target.querySelector('[data-quiet-text]').textContent = toDisplay(target.dataset.value);
    picker.hidden = true;
  });

  sync();
  if (state === 'picker') openPicker(quietRows[0]);
  if (state === 'picker-end') openPicker(quietRows[1]);
})();
