/*
 * 견적 관리 화면 그룹 (모의 동작, 백엔드 없음)
 *  SCR-A-001 견적 관리   #estimateList     분류 필터 · 날짜 검색 · 페이지(4건) · 삭제 확인 · 분류별 현황
 *  SCR-A-002 견적 수정   #estimateEdit     문서 배율/드래그 · 수정→저장 확인 · 견적 승인
 *  SCR-A-003/004 견적서 등록 #estimateRegister  첨부 → AI 인식(모의) → 수정 → 미리보기 → 등록
 *  공통 문서 뷰어 [data-viewer] (templates/admin/estimate/sheet.html)
 */
(function () {
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
  const layer = window.CodeMoveAdmin || { open: (el) => { el.hidden = false; }, close: (el) => { el.hidden = true; } };
  const toast = (opts) => window.AdminToast && window.AdminToast.show(opts);
  const state = (root) => (root && root.dataset.state) || '';

  /* ---------- 공통 유틸 ---------- */
  function formatShortDate(value, deleting) {
    const d = value.replace(/\D/g, '').slice(0, 6);
    if (d.length >= 5) return `${d.slice(0, 2)}.${d.slice(2, 4)}.${d.slice(4)}`;
    if (d.length >= 3) return `${d.slice(0, 2)}.${d.slice(2)}${d.length === 4 && !deleting ? '.' : ''}`;
    if (d.length === 2 && !deleting) return `${d}.`;
    return d;
  }
  function bindShortDate(input, onChange) {
    input.addEventListener('input', (e) => {
      input.value = formatShortDate(input.value, (e.inputType || '').startsWith('delete'));
      if (onChange) onChange(input.value);
    });
  }
  function today() {
    const n = new Date();
    return `${String(n.getFullYear()).slice(2)}.${String(n.getMonth() + 1).padStart(2, '0')}.${String(n.getDate()).padStart(2, '0')}`;
  }
  const toNumber = (v) => Number(String(v || '').replace(/\D/g, '')) || 0;
  const money = (n) => n.toLocaleString('ko-KR');

  // 화면 하단 짧은 안내 (다운로드·저장 등)
  function snack(message) {
    const frame = $('#adminFrame') || document.body;
    let el = $('#estimateSnack');
    if (!el) {
      el = document.createElement('div');
      el.id = 'estimateSnack';
      el.className = 'pointer-events-none absolute inset-x-6 bottom-[96px] z-[120] rounded-xl bg-[#27372a]/90 px-4 py-3 text-center text-[11px] font-semibold text-white shadow-lg transition-opacity';
      el.setAttribute('role', 'status');
      frame.appendChild(el);
    }
    el.textContent = message;
    el.style.opacity = '1';
    el.hidden = false;
    clearTimeout(el._t);
    el._t = setTimeout(() => { el.style.opacity = '0'; }, 2200);
  }

  /* ---------- 문서 뷰어 / 견적서 ---------- */
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
      const id = $('#estimateEdit')?.dataset.id || 'new';
      snack(`명품익스프레스_견적서_${id}.pdf 다운로드를 시작합니다.`);
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
    if (['movingCost', 'extraCost', 'departureLadder', 'destinationLadder'].includes(changed)) {
      const total = toNumber(f('movingCost').value) + toNumber(f('extraCost').value);
      f('total').value = money(total);
    }
    if (['movingCost', 'extraCost', 'departureLadder', 'destinationLadder', 'total'].includes(changed)) {
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

  // 문서 값 읽기/쓰기 (등록 단계 간 복사, 인식 결과 채우기)
  function getSheet(viewer) {
    const data = { fields: {}, items: [], choices: [] };
    $$('[data-field]', viewer).forEach((i) => { data.fields[i.dataset.field] = i.value; });
    $$('[data-item-row]', viewer).forEach((row) => {
      const names = $$('[data-item-name]', row);
      const qtys = $$('[data-item-qty]', row);
      names.forEach((n, i) => data.items.push([n.value, qtys[i].value]));
    });
    data.rows = $$('[data-item-row]', viewer).length;
    data.choices = $$('[data-choice]', viewer).map((b) => b.getAttribute('aria-pressed') === 'true');
    return data;
  }
  function setSheet(viewer, data) {
    Object.entries(data.fields || {}).forEach(([k, v]) => {
      const i = $(`[data-field="${k}"]`, viewer);
      if (i) i.value = v;
    });
    if (data.items) {
      setRowCount(viewer, data.rows || Math.max(8, Math.ceil(data.items.length / 4)));
      const names = $$('[data-item-name]', viewer);
      const qtys = $$('[data-item-qty]', viewer);
      names.forEach((n, i) => {
        n.value = data.items[i] ? data.items[i][0] : '';
        qtys[i].value = data.items[i] ? data.items[i][1] : '';
      });
    }
    if (data.choices) $$('[data-choice]', viewer).forEach((b, i) => pressChoice(b, !!data.choices[i]));
  }

  $$('[data-viewer]').forEach(initViewer);

  /* ---------- SCR-A-001 견적 관리 ---------- */
  const list = $('#estimateList');
  if (list) {
    const PER_PAGE = 4;
    const rowsBox = $('#estimateRows');
    const filter = $('#estimateFilter');
    const dateInput = $('#dateSearchInput');
    const popover = $('#datePopover');
    const toggle = $('#dateSearchToggle');
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
        const tone = { '견적 검토': 'bg-[#fbefed] text-[#b9574f]', '견적 승인': 'bg-[#edf3f8] text-[#4b759b]', '견적 확정': 'bg-[#edf4ed] text-[#346538]' };
        Object.assign(row.dataset, { row: 'new', status: added.status, name: added.name, date: added.date });
        const cells = row.children;
        cells[1].className = `justify-self-center rounded-full px-2 py-1 text-[9px] font-bold ${tone[added.status]}`;
        cells[1].textContent = added.status;
        cells[2].textContent = added.name;
        cells[3].textContent = added.date;
        rowsBox.insertBefore(row, first);
      }
    } catch (e) { /* 무시 */ }

    const rows = () => $$('[data-row]', rowsBox);

    function render() {
      const status = filter.value;
      const q = query.replace(/\D/g, '');
      const matched = rows()
        .filter((r) => status === '전체' || r.dataset.status === status)
        .filter((r) => !q || r.dataset.date.replace(/\D/g, '').startsWith(q))
        .sort((a, b) => b.dataset.date.localeCompare(a.dataset.date));
      const pages = Math.max(1, Math.ceil(matched.length / PER_PAGE));
      page = Math.min(page, pages);
      rows().forEach((r) => { r.hidden = true; });
      matched.slice((page - 1) * PER_PAGE, page * PER_PAGE).forEach((r, i) => {
        rowsBox.insertBefore(r, $('#estimateEmpty'));
        r.hidden = false;
        $('[data-no]', r).textContent = (page - 1) * PER_PAGE + i + 1;
      });
      $('#estimateEmpty').hidden = matched.length > 0;

      const pager = $('#estimatePager');
      pager.hidden = matched.length <= PER_PAGE;
      pager.innerHTML = '';
      for (let p = 1; p <= pages; p += 1) {
        const b = document.createElement('button');
        b.type = 'button';
        b.textContent = p;
        b.setAttribute('aria-label', `${p}페이지로 이동`);
        b.className = `grid h-8 w-8 place-items-center rounded-lg text-[11px] font-bold transition ${p === page ? 'bg-[#346538] text-white' : 'bg-[#f3f5f3] text-[#707871] hover:bg-[#e7ece7]'}`;
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

    filter.addEventListener('change', () => { page = 1; render(); });
    const setPopover = (open) => { popover.hidden = !open; toggle.setAttribute('aria-expanded', String(open)); if (open) dateInput.focus(); };
    toggle.addEventListener('click', () => setPopover(popover.hidden));
    bindShortDate(dateInput, (v) => { query = v; page = 1; render(); });
    $('#dateSearchSubmit').addEventListener('click', () => { query = dateInput.value; page = 1; render(); });
    dateInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') { query = dateInput.value; page = 1; render(); } });

    rowsBox.addEventListener('click', (e) => {
      const row = e.target.closest('[data-row]');
      if (!row) return;
      if (e.target.closest('[data-delete]')) {
        deleting = row;
        layer.open('#deleteConfirm');
        return;
      }
      if (row.dataset.href && row.dataset.row !== 'new') location.href = row.dataset.href;
    });
    rowsBox.addEventListener('keydown', (e) => {
      const row = e.target.closest('[data-row]');
      if (row && e.key === 'Enter' && row.dataset.href) location.href = row.dataset.href;
    });
    $('#deleteConfirmOk').addEventListener('click', () => {
      if (deleting) {
        const name = deleting.dataset.name;
        deleting.remove();
        deleting = null;
        render();
        toast({ title: '견적 삭제 알림', message: `${name} 고객님의 견적 기록이 삭제되었습니다.`, href: '/admin/estimates' });
      }
      layer.close('#deleteConfirm');
    });

    // 상태 미리보기
    const s = state(list);
    if (s === 'date-search') setPopover(true);
    if (s === 'empty') { setPopover(true); query = dateInput.value; }
    if (s === 'delete-confirm') { deleting = rows()[0]; layer.open('#deleteConfirm'); }
    render();
  }

  /* ---------- SCR-A-002 견적 수정 ---------- */
  const edit = $('#estimateEdit');
  if (edit) {
    const viewer = $('[data-viewer]', edit);
    const approveBtn = $('#approveButton');
    const approveHiddenByStatus = approveBtn.hidden;

    const applyEditing = (on) => {
      setEditing(viewer, on);
      approveBtn.hidden = on || approveHiddenByStatus || edit.dataset.approved === 'true';
    };
    $('[data-edit-toggle]', viewer).addEventListener('click', () => {
      if (viewer.classList.contains('is-editing')) layer.open('#saveConfirm');
      else applyEditing(true);
    });
    $('#saveConfirmOk').addEventListener('click', () => {
      layer.close('#saveConfirm');
      applyEditing(false);
      snack('견적서가 저장되었습니다.');
    });
    $('#approveConfirmOk').addEventListener('click', () => {
      layer.close('#approveConfirm');
      const badge = $('#approvalBadge');
      badge.textContent = `승인일 ${today()}`;
      badge.classList.remove('bg-[#fff4e8]', 'text-[#ad6d32]');
      badge.classList.add('bg-[#edf4ed]', 'text-[#346538]');
      edit.dataset.approved = 'true';
      approveBtn.hidden = true;
      toast({ title: '견적 승인 알림', message: `${edit.dataset.name} 고객님의 견적이 '견적 승인'으로 변경되었습니다.`, href: '/admin/estimates' });
    });
    $$('[data-goto-doc]', edit).forEach((b) => b.addEventListener('click', () => $('[data-tab="doc"]', edit).click()));

    const s = state(edit);
    if (s === 'editing' || s === 'save-confirm') applyEditing(true);
    if (s === 'save-confirm') layer.open('#saveConfirm');
    if (s === 'approve-confirm') layer.open('#approveConfirm');
  }

  /* ---------- SCR-A-003/004 견적서 등록 ---------- */
  const reg = $('#estimateRegister');
  if (reg) {
    const draft = $('#draftViewer [data-viewer]');
    const preview = $('#previewViewer [data-viewer]');
    const card = $('#attachCard');
    const sprite = ($('use')?.getAttribute('href') || '').split('#')[0];
    let mode = 'photo';
    let current = 1;
    setEditing(draft, true);

    const OFFICE_ITEMS = [['모니터', '6'], ['컴퓨터', '3'], ['공기청정기', '1'], ['소형 냉장고', '1'], ['전자레인지', '1'], ['프린터/복합기', '1'],
      ['TV', '1'], ['의자', '6'], ['테이블', '2'], ['원형 테이블', '1'], ['파티션', '3'], ['대형 캐비넷', '2'], ['책상', '2'], ['데스크 수납함', '3'], ['보조 서랍장', '3']];
    function recognized(kind, unrecognized) {
      const name = kind === 'pdf' ? '' : (unrecognized ? '담당자 미인식' : '김민준');
      const date = kind === 'pdf' ? '' : (unrecognized ? '견적일 미인식' : '26.09.04');
      return {
        fields: {
          companyName: '명품익스프레스', customer: name, phone: kind === 'pdf' ? '010-0000-0000' : '010-4821-7735',
          estimateDate: date, moveDate: date || '26.09.04', transportDate: date || '26.09.04',
          origin: '출발지 미인식', destination: '도착지 미인식', volume: '3.5톤',
          note: kind === 'pdf' ? 'PDF에서 인식된 견적 정보입니다.' : '이미지에서 인식된 견적 정보입니다.',
          group0: '전자기기', group1: '가구', group2: '', group3: '',
          option0: '0', option1: '0', option2: '0', option3: '0', option4: '0', option5: '0', option6: '0', option7: '0',
          movingCost: '0', extraCost: '0', total: '1,320,000', deposit: '132,000', balance: '1,188,000',
          departureLadder: '0', destinationLadder: '0', ladderSubtotal: '0',
        },
        items: OFFICE_ITEMS,
        rows: 8,
      };
    }

    function renderSteps() {
      const steps = mode === 'pdf' ? [['첨부', 1], ['미리보기', 3], ['견적 등록', 4]] : [['첨부', 1], ['수정', 2], ['미리보기', 3], ['견적 등록', 4]];
      const idx = steps.findIndex(([, s]) => s === current);
      $('#registerSteps').innerHTML = `<div class="flex items-center">${steps.map(([label], i) => {
        const active = i === idx;
        const done = i < idx;
        const dot = done
          ? `<svg class="icon" width="13" height="13"><use href="${sprite}#i-check"/></svg>`
          : i + 1;
        return `<div class="flex min-w-0 flex-col items-center gap-1.5">
            <span class="grid h-7 w-7 place-items-center rounded-full text-[10px] font-extrabold ${active ? 'bg-[#346538] text-white' : done ? 'bg-[#dce9dd] text-[#346538]' : 'bg-[#eef0ee] text-[#929994]'}">${dot}</span>
            <span class="whitespace-nowrap text-[8px] font-bold ${active ? 'text-[#346538]' : 'text-[#8a918c]'}">${label}</span>
          </div>${i < steps.length - 1 ? `<span class="mx-2 mb-5 h-px flex-1 ${done ? 'bg-[#8eaa91]' : 'bg-[#dfe4df]'}"></span>` : ''}`;
      }).join('')}</div>`;
    }

    function goto(step) {
      if (step === 3 && current === 2) setSheet(preview, getSheet(draft));
      current = step;
      $$('[data-step]', reg).forEach((el) => { el.hidden = Number(el.dataset.step) !== step; });
      // 프로토타입: STEP 1 은 pt-6, STEP 2~4 는 pt-5
      reg.classList.toggle('pt-6', step === 1);
      reg.classList.toggle('pt-5', step !== 1);
      renderSteps();
      if (step === 3) $('[data-back-label]', reg).textContent = mode === 'pdf' ? '첨부로' : '수정으로';
      if (step === 4) prepareRegister();
      const scroller = reg.closest('.overflow-y-auto');
      if (scroller) scroller.scrollTop = 0;
    }

    // STEP 1 첨부
    const setPreviewImages = (url) => $$('[data-preview-img]', card).forEach((img) => {
      img.hidden = !url;
      if (url) img.src = url; else img.removeAttribute('src');
    });
    function showAttachment(fileName, withPreview) {
      $('#selectedFile').hidden = !fileName;
      $('[data-file-name]', card).textContent = fileName || '';
      $('#photoPreview').hidden = !withPreview;
      $('#attachThumb').hidden = !fileName;
    }
    function resetAttachment() {
      setPreviewImages('');
      showAttachment('', false);
      $('#photoInput').value = '';
      $('#pdfInput').value = '';
    }
    function analyze(kind, unrecognized) {
      $('#analyzing').hidden = false;
      setTimeout(() => {
        $('#analyzing').hidden = true;
        mode = kind;
        const data = recognized(kind, unrecognized);
        setSheet(draft, data);
        setSheet(preview, data);
        goto(kind === 'pdf' ? 3 : 2);
      }, 1400);
    }

    $('#photoInput').addEventListener('change', (e) => {
      const file = e.target.files[0];
      if (!file) return;
      layer.close('#attachChooser');
      setPreviewImages(URL.createObjectURL(file));
      showAttachment(file.name, true);
    });
    $('#pdfInput').addEventListener('change', (e) => {
      const file = e.target.files[0];
      if (!file) return;
      layer.close('#attachChooser');
      setPreviewImages('');
      showAttachment(file.name, false);
      $('#registerName').value = '';
      $('#registerDate').value = '';
      analyze('pdf');
    });
    $('#retakeButton').addEventListener('click', resetAttachment);
    $('#analyzeButton').addEventListener('click', () => analyze('photo'));

    // STEP 2 ~ 4
    $$('[data-goto-step]', reg).forEach((b) => b.addEventListener('click', () => goto(Number(b.dataset.gotoStep))));
    $('#previewBack').addEventListener('click', () => {
      if (mode === 'pdf') { resetAttachment(); mode = 'photo'; goto(1); } else goto(2);
    });

    const submit = $('#registerSubmit');
    const nameInput = $('#registerName');
    const dateInput = $('#registerDate');
    function finalInfo() {
      if (mode === 'pdf') return { name: nameInput.value.trim(), date: dateInput.value.trim() };
      const f = getSheet(preview).fields;
      return { name: f.customer.trim(), date: f.estimateDate.trim() };
    }
    function validate() {
      const { name, date } = finalInfo();
      const missing = !name || !date || name.includes('미인식') || date.includes('미인식');
      submit.disabled = missing;
      $('#unrecognizedNotice').hidden = mode === 'pdf' || !missing;
    }
    function prepareRegister() {
      $('[data-step4-label]', reg).textContent = mode === 'pdf' ? 'STEP 3' : 'STEP 4';
      $('[data-photo-only]', reg).hidden = mode === 'pdf';
      $('[data-pdf-only]', reg).hidden = mode !== 'pdf';
      validate();
    }
    nameInput.addEventListener('input', validate);
    bindShortDate(dateInput, validate);

    submit.addEventListener('click', () => {
      if (submit.disabled) return;
      const { name, date } = finalInfo();
      const status = $('#registerStatus').value;
      try {
        sessionStorage.setItem('newEstimate', JSON.stringify({ name, date, status }));
        sessionStorage.setItem('adminToast', JSON.stringify({
          title: '견적서 등록 완료 알림',
          message: `${name} 님의 신규 견적서가 등록되었습니다. 내용을 확인해 주세요.`,
          href: reg.dataset.listUrl || '/admin/estimates',
        }));
      } catch (e) { /* 무시 */ }
      location.href = reg.dataset.listUrl || '/admin/estimates';
    });

    // 상태 미리보기
    const s = state(reg);
    const sample = () => { showAttachment('photo.jpg', true); };
    switch (s) {
      case 'chooser': layer.open('#attachChooser'); break;
      case 'photo-preview': sample(); break;
      case 'analyzing': sample(); $('#photoPreview').hidden = true; $('#analyzing').hidden = false; break;
      case 'step2': case 'step3': case 'step4': case 'unrecognized': {
        const data = recognized('photo', s === 'unrecognized');
        setSheet(draft, data);
        setSheet(preview, data);
        mode = 'photo';
        if (s === 'step2') goto(2);
        else if (s === 'step3') { current = 2; goto(3); } else { current = 3; goto(4); }
        break;
      }
      case 'pdf-preview': case 'pdf-register': {
        mode = 'pdf';
        const data = recognized('pdf');
        setSheet(preview, data);
        goto(s === 'pdf-preview' ? 3 : 4);
        break;
      }
      default: renderSteps();
    }
  }
})();
