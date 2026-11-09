/*
 * 견적 관리 화면 그룹
 *  SCR-A-001 견적 관리   #estimateList     분류 필터 · 검색(이름·날짜) · 페이지(8건) · 삭제 확인 · 분류별 현황
 *  SCR-A-002 견적 확인   #estimateEdit     문서 확대(두 손가락·맞춤) · 수정→저장 확인 · 견적 승인 · 하단 버튼 · 견적 정보(승인일·만료 카운트다운)
 *  SCR-A-003/004 견적서 등록 #regFlow      첨부 → (사진) 읽는 중 → 확인·수정 → 등록 / (PDF) 확인 → 정보 입력 → 등록. 단계 이동은 ?state= 주소 (아직 모의 동작)
 *  공통 문서 [data-viewer] + .estimate-sheet (templates/admin/estimate/sheet.html)
 * 서버 연동: 목록 삭제 · 견적서 저장 · 승인 (AdminEstimateController). 미리보기(/dev, 모델 없음)에서는 서버를 부르지 않고 모의 동작
 */
(function () {
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
  const el$ = (t) => (typeof t === 'string' ? $(t) : t);
  const layer = window.CodeMoveAdmin || { open: (t) => { el$(t).hidden = false; }, close: (t) => { el$(t).hidden = true; } };
  const toast = (opts) => window.AdminToast && window.AdminToast.show(opts);
  const state = (root) => (root && root.dataset.state) || '';

  /* ---------- 공통 유틸 ---------- */
  const toNumber = (v) => Number(String(v || '').replace(/\D/g, '')) || 0;
  const money = (n) => n.toLocaleString('ko-KR');
  const pad = (n) => String(n).padStart(2, '0');
  const stamp = (d) => `${String(d.getFullYear()).slice(2)}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;

  /* 서버 요청 (JSON). 응답에 ok(성공 여부)를 붙여 돌려준다 */
  async function post(url, data) {
    try {
      const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data || {}) });
      return Object.assign({ ok: res.ok }, await res.json().catch(() => ({})));
    } catch (_) {
      return { ok: false, message: '잠시 후 다시 시도해 주세요.' };
    }
  }
  /* 새로고침 뒤 띄울 안내 (overlays.js 가 adminToast 를 꺼내 보여줌) */
  function toastAfterReload(opts) {
    try { sessionStorage.setItem('adminToast', JSON.stringify(opts)); } catch (_) { /* 저장 불가 시 생략 */ }
  }

  // 짧은 안내 (저장·다운로드 등) — 확대 안내와 같은 회색 알약. 하단 버튼이 있으면 그 바로 위, 없으면 하단 탭 위
  function snack(message) {
    const bar = $$('[data-actionbar]').find((b) => !b.hidden && b.offsetParent);
    let el = $('#estimateSnack');
    if (!el) {
      el = document.createElement('p');
      el.id = 'estimateSnack';
      el.setAttribute('role', 'status');
      el.style.cssText = 'position:absolute;left:50%;z-index:120;display:flex;align-items:center;height:31px;padding:0 14px;border-radius:999px;'
        + 'background:#70746d;color:#f4f4f2;font-size:14px;white-space:nowrap;pointer-events:none;transform:translateX(-50%);transition:opacity .4s';
    }
    if (bar) {
      bar.appendChild(el);
      Object.assign(el.style, { bottom: '100%', marginBottom: '4px' });
      const hint = $('[data-zoom-hint]', bar);
      if (hint) hint.style.opacity = '0';
    } else {
      ($('#adminFrame') || document.body).appendChild(el);
      Object.assign(el.style, { bottom: 'calc(var(--nav) + 20px)', marginBottom: '0' });
    }
    el.textContent = message;
    el.style.opacity = '1';
    clearTimeout(el._t);
    el._t = setTimeout(() => { el.style.opacity = '0'; }, 2000);
  }

  /* ---------- 문서 뷰어 / 견적서 (견적 확인) ---------- */
  function initViewer(viewer) {
    const viewport = $('[data-viewport]', viewer);
    const page = $('[data-page]', viewer);
    const zoomLabel = $('[data-zoom="0"]', viewer);
    let zoom = 100;
    let pan = { x: 0, y: 0 };
    const pointers = new Map();
    let drag = null;
    let pinch = null;

    const apply = () => {
      if (zoom <= 100) pan = { x: 0, y: 0 };
      page.style.transform = `translate(${pan.x}px, ${pan.y}px) scale(${zoom / 100})`;
      zoomLabel.textContent = `${zoom}%`;
    };
    const setZoom = (z) => { zoom = Math.min(180, Math.max(60, Math.round(z))); apply(); };

    $$('[data-zoom]', viewer).forEach((b) => b.addEventListener('click', () => {
      const step = Number(b.dataset.zoom);
      setZoom(step === 0 ? 100 : zoom + step);
    }));

    viewport.addEventListener('pointerdown', (e) => {
      if (e.target.closest('input, button') && viewer.classList.contains('is-editing')) return;
      pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
      viewport.setPointerCapture(e.pointerId);
      if (pointers.size === 2) {
        const [a, b] = [...pointers.values()];
        pinch = { dist: Math.hypot(a.x - b.x, a.y - b.y), zoom };
        drag = null;
      } else {
        drag = { x: e.clientX, y: e.clientY, px: pan.x, py: pan.y };
      }
      viewport.classList.add('is-dragging');
    });
    viewport.addEventListener('pointermove', (e) => {
      if (!pointers.has(e.pointerId)) return;
      pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (pinch && pointers.size === 2) {
        const [a, b] = [...pointers.values()];
        setZoom(pinch.zoom * Math.hypot(a.x - b.x, a.y - b.y) / pinch.dist);
      } else if (drag && zoom > 100) {
        pan = { x: drag.px + e.clientX - drag.x, y: drag.py + e.clientY - drag.y };
        apply();
      }
    });
    const end = (e) => {
      pointers.delete(e.pointerId);
      if (pointers.size < 2) pinch = null;
      if (!pointers.size) { drag = null; viewport.classList.remove('is-dragging'); }
    };
    viewport.addEventListener('pointerup', end);
    viewport.addEventListener('pointercancel', end);

    $('[data-download]', viewer)?.addEventListener('click', () => {
      snack('견적서 PDF 다운로드를 시작합니다.');
    });

    initSheet(viewer);
    setEditing(viewer, viewer.classList.contains('is-editing'));
  }

  function setEditing(viewer, editing) {
    viewer.classList.toggle('is-editing', editing);
    $$('.estimate-sheet input', viewer).forEach((i) => { i.readOnly = !editing; });
    $$('.estimate-sheet [data-choice], [data-item-row-change]', viewer).forEach((b) => { b.disabled = !editing; });
    const toggle = $('[data-edit-toggle]', viewer);
    if (toggle) toggle.textContent = editing ? '저장' : '수정';
    updateRowButtons(viewer);
  }

  function pressChoice(btn, on) {
    btn.setAttribute('aria-pressed', String(on));
    const box = $('[data-box]', btn);
    if (box) box.textContent = on ? '■' : '□';
  }

  function updateRowButtons(viewer) {
    const count = $$('[data-item-row]', viewer).length;
    const label = $('[data-item-row-count]', viewer);
    if (label) label.textContent = `${count}행`;
    const editing = viewer.classList.contains('is-editing');
    $$('[data-item-row-change]', viewer).forEach((b) => {
      const dir = Number(b.dataset.itemRowChange);
      b.disabled = !editing || (dir < 0 ? count <= 1 : count >= 14);
    });
  }

  function setRowCount(viewer, count) {
    const box = $('[data-item-rows]', viewer);
    let rows = $$('[data-item-row]', box);
    while (rows.length < count) {
      const clone = rows[rows.length - 1].cloneNode(true);
      $$('input', clone).forEach((i) => { i.value = ''; });
      box.appendChild(clone);
      rows = $$('[data-item-row]', box);
    }
    while (rows.length > count && rows.length > 1) {
      rows.pop().remove();
    }
    $$('input', box).forEach((i) => { i.readOnly = !viewer.classList.contains('is-editing'); });
    updateRowButtons(viewer);
  }

  function recalc(viewer, changed) {
    const f = (name) => $(`[data-field="${name}"]`, viewer);
    if (['departureLadder', 'destinationLadder'].includes(changed)) {
      const subtotal = toNumber(f('departureLadder').value) + toNumber(f('destinationLadder').value);
      f('ladderSubtotal').value = money(subtotal);
      f('extraCost').value = money(subtotal);
    }
    const isOption = /^option\d+$/.test(changed);
    if (isOption) {
      const option = $$('[data-field^="option"]', viewer).filter((i) => /^option\d+$/.test(i.dataset.field)).reduce((sum, i) => sum + toNumber(i.value), 0);
      f('optionCost').value = money(option);
    }
    // 총 비용 = 이사비용 + 사다리비용 + 옵션비용
    if (isOption || ['movingCost', 'extraCost', 'optionCost', 'departureLadder', 'destinationLadder'].includes(changed)) {
      const total = toNumber(f('movingCost').value) + toNumber(f('extraCost').value) + toNumber(f('optionCost').value);
      f('total').value = money(total);
    }
    if (isOption || ['movingCost', 'extraCost', 'optionCost', 'departureLadder', 'destinationLadder', 'total'].includes(changed)) {
      const total = toNumber(f('total').value);
      const deposit = Math.round(total * 0.1);
      f('deposit').value = money(deposit);
      f('balance').value = money(total - deposit);
    }
  }

  function initSheet(viewer) {
    viewer.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-choice]');
      if (btn && !btn.disabled) {
        const radio = btn.closest('[data-radio]');
        const group = radio || btn.closest('[data-option-row], [data-transport]');
        const wasOn = btn.getAttribute('aria-pressed') === 'true';
        $$('[data-choice]', group).forEach((b) => pressChoice(b, false));
        pressChoice(btn, radio ? true : !wasOn);
        return;
      }
      const rowBtn = e.target.closest('[data-item-row-change]');
      if (rowBtn && !rowBtn.disabled) {
        setRowCount(viewer, $$('[data-item-row]', viewer).length + Number(rowBtn.dataset.itemRowChange));
      }
    });
    viewer.addEventListener('input', (e) => {
      const input = e.target;
      if (input.dataset.money) {
        const n = toNumber(input.value);
        input.value = input.value.replace(/\D/g, '') ? money(n) : '';
        recalc(viewer, input.dataset.field);
      }
    });
  }

  $$('[data-viewer]').forEach(initViewer);

  /* ---------- SCR-A-001 견적 관리 ---------- */
  const list = $('#estimateList');
  if (list) {
    const PER_PAGE = 8;
    const rowsBox = $('#estimateRows');
    const filterBox = $('#estimateFilter');
    const searchBar = $('#estimateSearchBar');
    const searchInput = $('#estimateSearchInput');
    const toggle = $('#estimateSearchToggle');
    const CHIP = { '견적 검토': 'chip-review', '견적 승인': 'chip-progress', '견적 확정': 'chip-done', '만료': 'chip-expired' };
    let status = '전체';
    let query = '';
    let page = 1;
    let deleting = null;

    // 견적서 등록에서 막 등록한 건을 맨 위에 추가 (모의)
    try {
      const added = JSON.parse(sessionStorage.getItem('newEstimate') || 'null');
      if (added) {
        sessionStorage.removeItem('newEstimate');
        const first = $('[data-row]', rowsBox);
        const row = first.cloneNode(true);
        Object.assign(row.dataset, { row: 'new', status: added.status, name: added.name, date: added.date });
        const chip = $('[data-chip]', row);
        chip.className = `chip ${CHIP[added.status] || 'chip-review'}`;
        chip.textContent = added.status;
        $('[data-name-cell]', row).textContent = added.name;
        $('[data-date-cell]', row).textContent = added.date;
        rowsBox.insertBefore(row, first);
      }
    } catch (e) { /* 무시 */ }

    const rows = () => $$('[data-row]', rowsBox);

    // 검색: 숫자만 입력하면 날짜(YY.MM.DD) 앞자리 일치, 그 외에는 이름에 포함
    // 날짜 검색어 → 목록 날짜(YY.MM.DD)와 비교할 함수. 날짜 형태가 아니면 null
    // 입력 중에도 결과가 따라오도록 마지막 숫자는 앞부분만 맞으면 통과시킨다.
    // 숫자만: 2 → 26 → 2603 → 260325 / 2 → 20 → 2026 → 20260325 / 03 → 0325(월일)
    // 구분자: 26.03.25 · 2026-03-25 · 2026.03 · 3.25 · 3월 25일 · 2025년 11월
    function dateMatcher(q) {
      const text = q.replace(/년|월/g, '.').replace(/일/g, '').trim();
      if (!/^[\d.\s/-]+$/.test(text)) return null;

      if (!/[.\s/-]/.test(text)) {
        return (date) => {
          const d = date.replace(/\D/g, '');               // YYMMDD
          return d.startsWith(text) || `20${d}`.startsWith(text) || d.slice(2).startsWith(text)
            || d.slice(2, 4) === `0${text}`;               // 3 → 3월 (3.25 · 3월 입력 시작)
        };
      }

      const parts = text.split(/[.\s/-]+/).filter(Boolean);
      const typing = !/[.\s/-]$/.test(text);              // 마지막 칸을 아직 입력 중
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

    function matchesQuery(r) {
      const q = query.trim();
      if (!q) return true;
      const byDate = dateMatcher(q);
      return byDate ? byDate(r.dataset.date) : r.dataset.name.includes(q);
    }

    function render() {
      const matched = rows()
        .filter((r) => status === '전체' || r.dataset.status === status)
        .filter(matchesQuery)
        .sort((a, b) => b.dataset.date.localeCompare(a.dataset.date));
      const pages = Math.max(1, Math.ceil(matched.length / PER_PAGE));
      page = Math.min(page, pages);
      rows().forEach((r) => { r.hidden = true; });
      matched.slice((page - 1) * PER_PAGE, page * PER_PAGE).forEach((r) => {
        rowsBox.insertBefore(r, $('#estimateEmpty'));
        r.hidden = false;
      });
      $('#estimateEmpty').hidden = matched.length > 0;

      // 페이지: 선택만 연초록 배경 + 초록 글씨, 나머지는 회색 글씨만
      const pager = $('#estimatePager');
      pager.hidden = matched.length <= PER_PAGE;
      pager.innerHTML = '';
      for (let p = 1; p <= pages; p += 1) {
        const b = document.createElement('button');
        b.type = 'button';
        b.textContent = p;
        b.setAttribute('aria-label', `${p}페이지로 이동`);
        b.className = `grid h-9 w-9 place-items-center rounded-full text-[15px] ${p === page ? 'bg-[#eef4f0] font-bold text-[#346538]' : 'font-medium text-[#888] hover:bg-[#eef0ee]'}`;
        b.addEventListener('click', () => { page = p; render(); });
        pager.appendChild(b);
      }

      // 분류별 현황 (전체 기준)
      const all = rows();
      $$('[data-stat]').forEach((s) => {
        const count = all.filter((r) => r.dataset.status === s.dataset.stat).length;
        const pct = all.length ? Math.round((count / all.length) * 100) : 0;
        $('[data-stat-text]', s).textContent = `${count}건 (${pct}%)`;
        $('[data-stat-bar]', s).style.width = `${pct}%`;
      });
    }

    // 분류 필터 (세그먼트)
    filterBox.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-filter]');
      if (!btn) return;
      status = btn.dataset.filter;
      $$('[data-filter]', filterBox).forEach((b) => b.setAttribute('aria-selected', String(b === btn)));
      page = 1;
      render();
    });

    // 검색창 열기/닫기 · 입력하는 대로 검색
    const setSearch = (open) => {
      searchBar.hidden = !open;
      toggle.setAttribute('aria-expanded', String(open));
      if (open) searchInput.focus();
      else if (query) { searchInput.value = ''; query = ''; page = 1; render(); }
    };
    toggle.addEventListener('click', () => setSearch(searchBar.hidden));
    searchInput.addEventListener('input', () => { query = searchInput.value; page = 1; render(); });
    $('#estimateSearchClear').addEventListener('click', () => {
      if (searchInput.value) { searchInput.value = ''; query = ''; page = 1; render(); searchInput.focus(); }
      else setSearch(false);
    });

    // 견적 승인·견적 확정은 고객에게 보낸 견적서를 바로 보도록 견적서 탭으로 연다 (그 외는 견적 정보 탭)
    const openHref = (row) => (['견적 승인', '견적 확정'].includes(row.dataset.status) ? `${row.dataset.href}?tab=doc` : row.dataset.href);
    rowsBox.addEventListener('click', (e) => {
      const row = e.target.closest('[data-row]');
      if (!row) return;
      if (e.target.closest('[data-delete]')) {
        deleting = row;
        layer.open('#deleteConfirm');
        return;
      }
      if (row.dataset.href && row.dataset.row !== 'new') location.href = openHref(row);
    });
    rowsBox.addEventListener('keydown', (e) => {
      const row = e.target.closest('[data-row]');
      if (row && e.key === 'Enter' && row.dataset.href) location.href = openHref(row);
    });
    $('#deleteConfirmOk').addEventListener('click', async () => {
      if (deleting) {
        const row = deleting;
        const name = row.dataset.name;
        deleting = null;
        if (list.dataset.live === 'true') {
          const result = await post(`${row.dataset.href}/delete`);
          if (!result.ok) {
            layer.close('#deleteConfirm');
            snack(result.message || '삭제하지 못했어요.');
            return;
          }
        }
        row.remove();
        render();
        toast({ title: '견적 삭제 알림', message: `${name} 고객님의 견적 기록이 삭제되었습니다.`, href: '/admin/estimates' });
      }
      layer.close('#deleteConfirm');
    });

    // 대시보드 등에서 ?status=review|approved|confirmed|expired 로 들어오면 그 분류 필터를 선택
    const preset = { review: '견적 검토', approved: '견적 승인', confirmed: '견적 확정', expired: '만료' }[new URLSearchParams(location.search).get('status')];
    if (preset) $(`[data-filter="${preset}"]`, filterBox)?.click();

    // 상태 미리보기
    const s = state(list);
    if (s === 'date-search') setSearch(true);
    if (s === 'empty') { setSearch(true); query = searchInput.value; }
    if (s === 'delete-confirm') { deleting = rows()[0]; layer.open('#deleteConfirm'); }
    render();
  }


  /* ---------- SCR-A-002 견적 확인 ---------- */
  const edit = $('#estimateEdit');
  if (edit) {
    const viewer = $('[data-viewer]', edit);
    const toggle = $('[data-edit-toggle]', viewer);
    const approveBtn = $('#approveButton');
    const cancel = $('#editCancel');
    const notice = $('#noDateNotice');
    const bar = $('[data-actionbar]', edit);
    const moveDate = $('[data-field="moveDate"]', edit);
    const approveHiddenByStatus = approveBtn.hidden;
    const SECONDARY = 'h-14 flex-1 rounded-[14px] bg-[#eef0ee] text-[16px] font-semibold text-[#555]';
    const PRIMARY = 'btn-primary flex-[2]';

    // 하단 버튼: 보기 [수정][견적 승인] · 수정 중 [취소][저장] · 승인 후 [수정]만(초록) · 작업일이 비면 승인 비활성 (ERD Q6)
    function syncBar() {
      const editing = viewer.classList.contains('is-editing');
      cancel.hidden = !editing;
      toggle.className = editing || approveBtn.hidden ? PRIMARY : SECONDARY;
      approveBtn.disabled = !moveDate.value.trim();
      notice.hidden = !approveBtn.disabled || approveBtn.hidden;
      bar.hidden = toggle.hidden && approveBtn.hidden;
    }
    const applyEditing = (on) => {
      setEditing(viewer, on);
      approveBtn.hidden = on || approveHiddenByStatus || edit.dataset.approved === 'true';
      syncBar();
    };
    moveDate.addEventListener('input', syncBar);
    toggle.addEventListener('click', () => {
      if (viewer.classList.contains('is-editing')) layer.open('#saveConfirm');
      else applyEditing(true);
    });
    // [취소]: 고친 내용을 버리고 보기 상태로
    cancel.addEventListener('click', () => {
      const p = new URLSearchParams(location.search);
      p.delete('state');
      location.href = `${location.pathname}${p.toString() ? `?${p}` : ''}`;
    });

    // 견적 정보: 승인일 · 만료 줄 (수정 일시·관리자 같은 기록은 화면에 표시 안 함)
    function setSum(key, text) {
      const row = $(`[data-sum="${key}"]`, edit);
      row.hidden = false;
      const v = $('[data-sum-value]', row);
      if (text) v.textContent = text;
      return v;
    }
    // 만료까지 남은 시간 (24시간 미만 HH:MM:SS, 이상 N일 N시간)
    let timer;
    function countdown(dd) {
      clearInterval(timer);
      let left = Number(dd.dataset.countdown);
      const draw = () => {
        dd.textContent = left >= 86400
          ? `${Math.floor(left / 86400)}일 ${Math.floor((left % 86400) / 3600)}시간 후 만료`
          : `${pad(Math.floor(left / 3600))}:${pad(Math.floor((left % 3600) / 60))}:${pad(left % 60)} 후 만료`;
        left = Math.max(0, left - 1);
      };
      draw();
      timer = setInterval(draw, 1000);
    }
    const cd = $('[data-countdown]', edit);
    if (cd && !$('[data-sum="expires"]', edit).hidden) countdown(cd);

    // 문서 칸을 그대로 모아 서버로 (형식 해석·합계 계산은 서버: QuoteService.save)
    function collectSheet() {
      const v = (name) => ($(`[data-field="${name}"]`, viewer) || {}).value || '';
      const pressed = (b) => !!b && b.getAttribute('aria-pressed') === 'true';
      const picked = $('[data-radio="workMethod"] [data-choice][aria-pressed="true"]', viewer);
      return {
        companyName: v('companyName'), customer: v('customer'), phone: v('phone'), estimateDate: v('estimateDate'),
        origin: v('origin'), moveDate: v('moveDate'), destination: v('destination'), transportDate: v('transportDate'),
        items: $$('[data-item-row]', viewer).map((row) => $$('input', row).map((i) => i.value)),
        options: $$('[data-option-row]', viewer).map((row) => {
          const [a, b] = $$('[data-choice]', row);
          return { on1: pressed(a), on2: pressed(b), cost: ($('input', row) || {}).value || '' };
        }),
        workMethod: picked ? picked.dataset.value : '',
        transports: $$('[data-transport]', viewer).map((row) => {
          const [a, b] = $$('[data-choice]', row);
          return { from: pressed(a), to: pressed(b) };
        }),
        volume: v('volume'), note: v('note'), movingCost: v('movingCost'),
        fromLadderFloor: v('fromLadderFloor'), departureLadder: v('departureLadder'),
        toLadderFloor: v('toLadderFloor'), destinationLadder: v('destinationLadder'),
      };
    }
    const url = edit.dataset.url; // 미리보기(/dev)에는 없음 → 모의 동작
    const reloadDoc = () => { location.href = `${url}?tab=doc`; };

    $('#saveConfirmOk').addEventListener('click', async () => {
      layer.close('#saveConfirm');
      if (url) {
        const result = await post(url, collectSheet());
        if (!result.ok) { snack(result.message || '저장하지 못했어요.'); return; }
        toastAfterReload({ title: '견적서 저장', message: '견적서가 저장되었습니다.', href: '/admin/estimates' });
        reloadDoc();
        return;
      }
      applyEditing(false);
      snack('견적서가 저장되었습니다.');
    });
    $('#approveConfirmOk').addEventListener('click', async () => {
      layer.close('#approveConfirm');
      if (url) {
        const result = await post(`${url}/approve`);
        if (!result.ok) { snack(result.message || '승인하지 못했어요.'); return; }
        toastAfterReload({ title: '견적 승인 알림', message: `${edit.dataset.name} 고객님의 견적이 '견적 승인'으로 변경되었습니다.`, href: '/admin/estimates' });
        reloadDoc();
        return;
      }
      const badge = $('#approvalBadge');
      badge.className = 'chip chip-progress';
      badge.textContent = '견적 승인';
      edit.dataset.approved = 'true';
      approveBtn.hidden = true;
      syncBar();
      const now = new Date();
      setSum('approved', stamp(now));
      if (edit.dataset.source === 'request') {   // 만료는 견적 요청(01)만: 승인 + 72시간
        const v = setSum('expires');
        v.className = 'font-semibold tabular-nums text-[#d9701a]';
        v.dataset.countdown = String(72 * 3600);
        countdown(v);
      }
      toast({ title: '견적 승인 알림', message: `${edit.dataset.name} 고객님의 견적이 '견적 승인'으로 변경되었습니다.`, href: '/admin/estimates' });
    });

    // 문서 확대: 확대하면 배율·맞춤 알약, 처음 만지면 '두 손가락' 안내가 사라짐
    const zoomLabel = $('[data-zoom-pill] [data-zoom="0"]', edit);
    const zoomPill = $('[data-zoom-pill]', edit);
    const hint = $('[data-zoom-hint]', edit);
    new MutationObserver(() => {
      zoomPill.hidden = zoomLabel.textContent.trim() === '100%';
      if (!zoomPill.hidden) hint.style.opacity = '0';
    }).observe(zoomLabel, { childList: true, characterData: true, subtree: true });
    $('[data-viewport]', edit).addEventListener('pointerdown', () => { hint.style.opacity = '0'; }, { once: true });

    // 제목 줄 다운로드는 견적서 탭에서만
    $$('[data-tab]', edit).forEach((t) => t.addEventListener('click', () => {
      $$('[data-doc-only]', edit).forEach((el) => { el.hidden = t.dataset.tab !== 'doc'; });
    }));

    // 고객 영상 탭 '견적서 수정 ›': 견적서 탭으로 넘어가 수정 시작
    $('[data-video-edit]', edit)?.addEventListener('click', () => {
      $('[data-tab="doc"]', edit).click();
      if (!viewer.classList.contains('is-editing')) toggle.click();
    });

    // 상태 미리보기
    const s = state(edit);
    syncBar();
    if (s === 'editing' || s === 'save-confirm') applyEditing(true);
    if (s === 'save-confirm') layer.open('#saveConfirm');
    if (s === 'approve-confirm') layer.open('#approveConfirm');
  }

  /* ---------- SCR-A-003/004 견적서 등록 ---------- */
  const reg = $('#regFlow');
  if (reg) {
    const s = state(reg);
    const params = new URLSearchParams(location.search);
    const go = (q) => { location.href = `${location.pathname}?${q}`; };
    const next = $('#reviewNext', reg);

    // 1. 첨부: 파일을 고르면 확인 화면으로
    $$('[data-attach]', reg).forEach((input) => input.addEventListener('change', () => {
      const file = input.files[0];
      if (file) go(/pdf$/i.test(file.type) || /\.pdf$/i.test(file.name) ? 'state=pdf-preview' : 'state=photo-preview');
    }));
    // 읽는 중: 첨부에서 넘어온 경우만 자동 이동 (?state=analyzing 으로 직접 열면 멈춤)
    if (s === 'analyzing' && params.get('auto')) setTimeout(() => go('state=review'), 1800);

    // 2. 토글 (작업 형태는 하나만, 운반 방법·옵션은 켜고 끄기) · 분류 세그먼트
    reg.addEventListener('click', (e) => {
      const t = e.target.closest('.reg-toggle');
      if (t) {
        const radio = t.closest('[data-radio]');
        if (radio) $$('.reg-toggle', radio).forEach((b) => b.setAttribute('aria-pressed', String(b === t)));
        else t.setAttribute('aria-pressed', String(t.getAttribute('aria-pressed') !== 'true'));
      }
      const seg = e.target.closest('[data-segment] [role="radio"]');
      if (seg) $$('[role="radio"]', seg.parentElement).forEach((x) => x.setAttribute('aria-checked', String(x === seg)));
    });

    // 금액: 총 비용 = 이사비용 + 사다리 + 옵션, 계약금 10%
    function recalcReg() {
      const f = (n) => toNumber(($(`[data-field="${n}"]`, reg) || {}).value);
      const option = $$('[data-option]', reg).reduce((sum, i) => sum + toNumber(i.value), 0);
      const total = f('movingCost') + f('fromLadderCost') + f('toLadderCost') + option;
      const set = (k, v) => { const el = $(`[data-sum="${k}"]`, reg); if (el) el.textContent = money(v); };
      set('option', option);
      set('total', total);
      set('deposit', Math.round(total * 0.1));
      set('balance', total - Math.round(total * 0.1));
    }

    // 확인이 필요한 항목: 개수 · 바로가기 · [다음] 활성화
    function checkNext() {
      if (!next) return;
      next.disabled = $$(s === 'pdf-review' ? '[data-pdf-required]' : '[data-required]', reg).some((i) => !i.value.trim());
    }
    function updateMissing() {
      const box = $('#missingSummary', reg);
      if (box) {
        const left = $$('input[data-missing]', reg);
        $('[data-missing-count]', box).textContent = left.length;
        $$('[data-jump]', box).forEach((b) => { b.hidden = !$(`[data-field="${b.dataset.jump}"][data-missing]`, reg); });
        box.hidden = left.length === 0;
      }
      checkNext();
    }
    $$('[data-jump]', reg).forEach((b) => b.addEventListener('click', () => {
      const input = $(`[data-field="${b.dataset.jump}"]`, reg);
      input.scrollIntoView({ behavior: 'smooth', block: 'center' });
      setTimeout(() => input.focus({ preventScroll: true }), 300);
    }));
    next?.addEventListener('click', () => { if (!next.disabled) location.href = next.dataset.href; });

    reg.addEventListener('input', (e) => {
      const i = e.target;
      if (i.matches('[data-money]')) {
        i.value = i.value.replace(/\D/g, '') ? money(toNumber(i.value)) : '';
        recalcReg();
      }
      if (i.matches('[data-date], [data-field$="Date"]')) {   // YY.MM.DD 자동 점
        const d = i.value.replace(/\D/g, '').slice(0, 6);
        const del = (e.inputType || '').startsWith('delete');
        i.value = d.length >= 5 ? `${d.slice(0, 2)}.${d.slice(2, 4)}.${d.slice(4)}` : d.length >= 3 ? `${d.slice(0, 2)}.${d.slice(2)}` : (d.length === 2 && !del ? `${d}.` : d);
      }
      if (i.matches('[data-missing]') && i.value.trim()) i.removeAttribute('data-missing');
      updateMissing();
    });

    // 크게 보기: 사진 단계는 원본 사진, PDF·등록 단계는 견적서 문서 (?state=full-view · ?state=register&full=open)
    const full = $('#fullView');
    const showDoc = s === 'register' || s === 'pdf-preview' || s === 'pdf-review';
    $('[data-full="photo"]', full).hidden = showDoc;
    $('[data-full="doc"]', full).hidden = !showDoc;
    $('[data-full-title]', full).textContent = !showDoc ? 'photo.jpg'
      : (reg.dataset.source === 'pdf' || s.startsWith('pdf') ? '견적서_정도현_230914.pdf' : '견적서 미리보기');
    if (s === 'full-view' || params.get('full') === 'open') layer.open('#fullView');

    // 3. 회원 연결 (?state=register&picker=open)
    $('#memberPicker').addEventListener('click', (e) => {
      const b = e.target.closest('[data-member]');
      if (!b) return;
      const label = $('#memberLinked');
      if (label) {
        label.textContent = b.dataset.name ? `${b.dataset.name} · ${b.dataset.phone}` : '연결 안 함';
        label.className = b.dataset.name ? 'font-semibold text-[#292929]' : 'text-[#b0b4b1]';
      }
      layer.close('#memberPicker');
    });
    if (params.get('picker') === 'open') layer.open('#memberPicker');

    // [견적 관리에 등록]: 목록 맨 위에 추가 + 완료 알림 (모의)
    $('#registerSubmit', reg)?.addEventListener('click', () => {
      const submit = $('#registerSubmit', reg);
      const status = ($('[data-segment] [aria-checked="true"]', reg) || {}).textContent || '견적 검토';
      try {
        sessionStorage.setItem('newEstimate', JSON.stringify({ name: submit.dataset.name, date: submit.dataset.date, status: status.trim() }));
        sessionStorage.setItem('adminToast', JSON.stringify({
          title: '견적서 등록 완료',
          message: `${submit.dataset.name} 고객님의 견적서가 등록되었습니다.`,
          href: '/admin/estimates',
        }));
      } catch (err) { /* 무시 */ }
    });

    updateMissing();
  }
})();
