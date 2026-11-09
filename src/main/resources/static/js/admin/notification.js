/* SCR-A-013~015 알림 관리 — 탭(발송 내역 · 알림 설정)
   내역: 현황 칸(보류 · 문자 대체) 결과 필터 · 채널 글자 드롭다운 · 검색(이름·날짜) · 날짜 묶음 · 상세 시트
   설정: 토글(알림 받기 · 자동 발송 · 야간 발송 보류 · 문자 대체) · 보류 시각 시트 — 바꿀 때마다 서버에 저장
   서버 값이 없는 미리보기(/dev)는 예시 행 · 화면 안에서만 동작 */
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
  // live: 서버 값으로 그린 화면 (검색 · 필터 · 이어서 불러오기 = GET /admin/notifications/page). 미리보기(/dev)는 예시 행만 화면에서 거름
  const live = root.dataset.live === 'Y';
  const rows = () => $$('[data-row]');
  const groups = $$('[data-group]');
  const input = $('#notiQuery');
  const channel = $('#channelFilter');
  const stats = $$('[data-stat]');
  const loadingRow = $('#notiLoading');
  const endRow = $('#notiEnd');
  const sentinel = $('#notiSentinel');
  let result = '';
  let cursor = panel.dataset.nextCursor || '';
  let done = !cursor;
  let fetching = false;
  let seq = 0;   // 요청 순서: 늦게 도착한 이전 검색 결과는 버림

  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const CHIP = { held: ['chip-review', '보류'], sms: ['chip-progress', '문자 대체'], sent: ['chip-done', '발송'] };
  // 템플릿 행과 같은 모양
  const makeRow = (r) => {
    const el = document.createElement('div');
    Object.assign(el.dataset, { row: '', name: r.name, phone: r.phone4, date: r.date, channel: r.channel, result: r.result,
      detail: r.detail, title: r.title, content: r.content, sent: r.sent });
    el.setAttribute('role', 'button');
    el.tabIndex = 0;
    el.className = 'cursor-pointer border-b border-[#eef0ee] px-4 py-3 transition last:border-none hover:bg-[#f7f8f6]';
    const chip = CHIP[r.result] || CHIP.sent;
    el.innerHTML = `<div class="flex items-center justify-between gap-2">
        <strong class="truncate text-[15px] font-semibold text-[#292929]">${esc(r.name)}</strong>
        <span class="shrink-0 text-[13px] tabular-nums text-[#888]">${esc(r.group === 'older' ? r.date : r.time)}</span></div>
      <div class="mt-0.5 flex items-center justify-between gap-2">
        <span class="truncate text-[13px] text-[#888]">${esc(r.detail)} · ${esc(r.channel)}</span>
        <span class="chip shrink-0 ${chip[0]}">${chip[1]}</span></div>`;
    return el;
  };
  const syncGroups = () => { groups.forEach((g) => { g.hidden = !g.querySelector('[data-row]:not([hidden])'); }); };
  const syncEnd = () => {
    const any = rows().some((r) => !r.hidden);
    $('#notiEmpty').hidden = any;
    endRow.hidden = !done || !any;
  };

  // 미리보기: 화면에 있는 예시 행만 거름
  const applyLocal = () => {
    const q = input.value.trim();
    const byDate = q ? dateMatcher(q) : null;
    let shown = 0;
    rows().forEach((r) => {
      const ok = (!q || (byDate ? byDate(r.dataset.date) : r.dataset.name.includes(q)))
        && (!channel.value || r.dataset.channel === channel.value)
        && (!result || r.dataset.result === result);
      r.hidden = !ok;
      if (ok) shown += 1;
    });
    syncGroups();
    syncEnd();
    $('#notiCount').textContent = shown;
  };

  // 서버: 조건이 바뀌면 첫 페이지부터(reset), 스크롤 끝이면 다음 페이지
  const fetchPage = async (reset) => {
    const my = ++seq;
    const p = new URLSearchParams();
    if (input.value.trim()) p.set('q', input.value.trim());
    if (channel.value) p.set('channel', channel.value);
    if (result) p.set('result', result);
    if (!reset && cursor) p.set('cursor', cursor);
    fetching = true;
    loadingRow.hidden = reset;
    try {
      const res = await fetch(`/admin/notifications/page?${p}`, { headers: { Accept: 'application/json' } });
      if (!res.ok) throw new Error(res.status);
      const data = await res.json();
      if (my !== seq) return;
      if (reset) rows().forEach((r) => r.remove());
      data.rows.forEach((r) => {
        const box = $(`[data-group="${r.group}"] > div`);
        if (box) box.appendChild(makeRow(r));
      });
      cursor = data.nextCursor ? String(data.nextCursor) : '';
      done = !cursor;
      $('#notiCount').textContent = data.total;
      $('#statToday').textContent = data.todaySent;
      $('#statHeld').textContent = data.held;
      $('#statSms').textContent = data.sms;
      syncGroups();
      syncEnd();
    } catch (e) {
      // 네트워크 오류: 화면은 그대로 두고 다음 스크롤 · 검색 때 다시 시도
    } finally {
      if (my === seq) {
        fetching = false;
        loadingRow.hidden = true;
        // 받은 행이 적어 감시 지점이 여전히 보이면 이어서 불러옴
        io.unobserve(sentinel);
        if (!done) io.observe(sentinel);
      }
    }
  };

  let timer = null;
  const apply = (debounce) => {
    if (!live) { applyLocal(); return; }
    clearTimeout(timer);
    if (debounce) timer = setTimeout(() => fetchPage(true), 250);
    else fetchPage(true);
  };
  // 현황 칸: 누르면 그 결과만, 선택된 칸을 다시 누르면 해제
  const setResult = (value) => {
    result = value;
    stats.forEach((s) => s.setAttribute('aria-pressed', String(s.dataset.stat === value)));
    $('#resultClear').hidden = !value;
    $('[data-result-label]').textContent = value === 'held' ? '보류' : '문자 대체';
    apply(false);
  };
  stats.forEach((s) => s.addEventListener('click', () => setResult(result === s.dataset.stat ? '' : s.dataset.stat)));
  $('#resultClear').addEventListener('click', () => setResult(''));

  // 지우기 버튼은 검색어가 있을 때만
  const syncClear = () => { $('#notiClear').hidden = !input.value; };
  $('#notiClear').addEventListener('click', () => { input.value = ''; syncClear(); apply(false); input.focus(); });
  input.addEventListener('input', syncClear);
  input.addEventListener('input', () => apply(true));
  channel.addEventListener('change', () => apply(false));

  /* ---------- 무한 스크롤: 20건씩 (서버 커서), 최근 30일까지 — 이전 기록은 날짜 검색 ---------- */
  const io = new IntersectionObserver((entries) => {
    if (entries[0].isIntersecting && !panel.hidden && live && !done && !fetching) fetchPage(false);
  }, { root: panel, rootMargin: '0px 0px 80px 0px' });
  $('#notiEndSearch').addEventListener('click', () => { panel.scrollTo({ top: 0, behavior: 'smooth' }); input.focus(); });

  if (panel.dataset.initialQuery) input.value = panel.dataset.initialQuery;
  syncClear();
  if (state === 'filter-held') setResult('held');
  else if (input.value) apply(false);
  else { syncGroups(); syncEnd(); }

  if (state === 'end') {
    // 미리보기: 끝 안내 줄
    done = true;
    syncEnd();
    panel.scrollTop = panel.scrollHeight;
  } else if (state === 'loading') {
    loadingRow.hidden = false;   // 불러오는 중 줄을 멈춘 상태로 보여 줌
    fetching = true;
    panel.scrollTop = panel.scrollHeight;
  } else if (!done) {
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
  // 바꿀 때마다 저장: 알림 받기 = POST /admin/notifications/push, 고객 발송 = POST /admin/notifications/settings
  // 저장에 실패하면 되돌림 (undo). 미리보기는 화면에서만
  const save = async (url, body, undo) => {
    if (!live) return;
    try {
      const res = await fetch(url, { method: 'POST', body: new URLSearchParams(body) });
      if (!res.ok) throw new Error(res.status);
    } catch (e) {
      undo();
      sync();
    }
  };
  const saveSettings = (undo) => save('/admin/notifications/settings', {
    enabled: on('enabled'), quiet: on('quiet'), quietStart: quietRows[0].dataset.value, quietEnd: quietRows[1].dataset.value, sms: on('sms'),
  }, undo);
  $$('[data-switch]').forEach((b) => b.addEventListener('click', () => {
    if (b.disabled) return;
    const before = on(b.dataset.switch);
    b.setAttribute('aria-pressed', String(!before));
    sync();
    const undo = () => b.setAttribute('aria-pressed', String(before));
    if (b.dataset.switch === 'push') save('/admin/notifications/push', { enabled: !before }, undo);
    else saveSettings(undo);
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
    const row = target;
    const before = row.dataset.value;
    const show = () => { row.querySelector('[data-quiet-text]').textContent = toDisplay(row.dataset.value); };
    row.dataset.value = `${String(h24).padStart(2, '0')}:${val('minute')}`;
    show();
    picker.hidden = true;
    if (row.dataset.value !== before) saveSettings(() => { row.dataset.value = before; show(); });
  });

  sync();
  if (state === 'picker') openPicker(quietRows[0]);
  if (state === 'picker-end') openPicker(quietRows[1]);
})();
