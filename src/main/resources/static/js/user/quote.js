/*
 * 이사 견적 6단계 (SCR-U-003~009)
 *  ?step=1~6&state=... 로 캡처 상태를 바로 연다 (templates/user/quote/index.html 상단 주석 참고).
 *  ?voice=on  쉬운 모드 음성 안내 켜짐 상태 (E-C21)
 *  화면 개발 단계: AI 분석·주소 검색·견적 금액은 더미 데이터로 흉내 낸다.
 */
(function () {
  const root = document.getElementById('quote');
  if (!root) return;

  const $ = (sel, el = document) => el.querySelector(sel);
  const $$ = (sel, el = document) => Array.from(el.querySelectorAll(sel));
  const IMG = root.dataset.img || '/images/user/';
  const TOTAL = 6;
  const SIZE_FACTOR = { 소형: 0.75, 중형: 1, 대형: 1.25 };
  const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
  const TODAY = new Date(2026, 1, 16); // 더미 기준일 (캡처: 2026년 2월 16일 이후 선택 가능)
  const NEXT_LABELS = {
    standard: ['다음', '다음', '다음', '다음', '다음', '견적 요청'],
    easy: ['영상 분석하기', '인식 품목 확인', '주소 입력하기', '날짜 선택하기', '견적 확인하기', '견적 요청하기'],
  };
  const VOICE_GUIDES = [
    '영상 올리기 단계입니다. 집 안 전체가 보이도록 촬영한 동영상을 올려주세요. 동영상 선택 버튼을 누르면 됩니다.',
    '짐 확인 단계입니다. 올려주신 영상에서 가구와 짐을 찾고 있습니다. 잠시 기다려주세요.',
    '이사 정보 단계입니다. 현재 사는 집의 종류와 원하는 포장 방식을 차례로 선택해주세요.',
    '주소 입력 단계입니다. 출발할 곳과 도착할 곳의 주소를 차례로 입력해주세요.',
    '날짜 선택 단계입니다. 달력에서 이사할 날짜를 고르고, 시간을 선택해주세요. 날짜를 아직 모르면 아직 날짜를 모르겠어요를 누르세요.',
    '견적 확인 단계입니다. 예상 금액과 입력한 이사 정보를 확인한 뒤 견적 요청하기를 눌러주세요.',
  ];
  const VOICE_GUIDE_DONE = '영상 분석이 끝났습니다. 찾은 짐 목록을 확인해주세요. 정보가 다르면 품목을 눌러 수정하고, 빠진 짐은 빠진 품목 추가하기를 누르세요.';

  /* 품목 카탈로그: 이름 → [아이콘, 중형 1개 기준 CBM] */
  const CATALOG = {
    냉장고: ['item-fridge', 1.8], 김치냉장고: ['item-fridge', 0.9], 침대: ['item-bed', 2.4], 소파: ['item-sofa', 2.1],
    이사박스: ['item-box', 4.6 / 13], 세탁기: ['item-washer', 1.2], 건조기: ['item-washer', 1.0],
    테이블: ['item-table', 0.7 / 0.75], '식탁·테이블': ['item-table', 0.7 / 0.75], 책상: ['item-table', 0.9],
    TV: ['item-tv', 0.4], TV장: ['item-table', 0.6], 옷장: ['item-wardrobe', 2.0], 서랍장: ['item-wardrobe', 0.9],
    책장: ['item-wardrobe', 1.0], 수납장: ['item-wardrobe', 0.8], 화장대: ['item-table', 0.6],
  };
  const catalog = (name) => CATALOG[name] || ['item-box', 0.5];

  const MOCK_ADDRESSES = [
    { road: '서울 강남구 테헤란로 123', jibun: '지번 역삼동 123-45' },
    { road: '서울 강남구 테헤란로 152', jibun: '지번 역삼동 678-9' },
    { road: '서울 강남구 강남대로 396', jibun: '지번 역삼동 831-2' },
    { road: '서울 서초구 서초대로 456', jibun: '지번 서초동 1321-5' },
    { road: '경기 성남시 분당구 판교역로 235', jibun: '지번 삼평동 681' },
    { road: '경기 수원시 영통구 광교중앙로 145', jibun: '지번 이의동 1336' },
  ];

  const newItem = (name, size, count, review) => {
    const [icon, base] = catalog(name);
    return { name, size, count, base, icon, review: !!review };
  };

  const state = {
    step: 1,
    video: '',
    analysis: 'analyzing',
    items: [
      newItem('냉장고', '중형', 1), newItem('침대', '중형', 1), newItem('소파', '중형', 1, true),
      newItem('이사박스', '중형', 13), newItem('세탁기', '중형', 1), newItem('테이블', '소형', 1),
    ],
    house: '', pack: '',
    addr: { from: { sel: null, detail: '' }, to: { sel: null, detail: '' } },
    view: { y: 2026, m: 2 },
    date: null,
    time: '오전 10:00',
    dateUnknown: false,
    voice: false,
  };
  let analysisTimer = 0;

  const mode = () => (window.CodeMove && CodeMove.mode ? CodeMove.mode() : 'standard');
  const open = (sel) => { const el = $(sel); if (el) el.hidden = false; };
  const close = (sel) => { const el = $(sel); if (el) el.hidden = true; };
  const cbmOf = (it) => it.base * SIZE_FACTOR[it.size] * it.count;
  const fmt = (n) => n.toFixed(1);
  const dateLabel = (d) => `${d.m + 1}월 ${d.d}일 (${WEEKDAYS[new Date(d.y, d.m, d.d).getDay()]})`;

  /* ── 단계 렌더링 ── */
  function canNext() {
    switch (state.step) {
      case 1: return !!state.video;
      case 2: return state.analysis === 'done';
      case 3: return !!(state.house && state.pack);
      case 4: return !!(state.addr.from.sel && state.addr.to.sel);
      case 5: return !!state.date;
      default: return true;
    }
  }

  function renderChrome() {
    $$('[data-q-step]', root).forEach((s) => { s.hidden = Number(s.dataset.qStep) !== state.step; });
    $('[data-q-progress]', root).style.width = `${(state.step / TOTAL) * 100}%`;
    $('[data-q-stepno]', root).textContent = `${state.step}/${TOTAL}`;
    const next = $('[data-q-next]', root);
    next.textContent = NEXT_LABELS[mode() === 'easy' ? 'easy' : 'standard'][state.step - 1];
    next.disabled = !canNext();
    next.classList.toggle('primary', canNext());
  }

  function renderUpload() {
    const card = $('[data-q-upload]', root);
    card.classList.toggle('uploaded', !!state.video);
    $('.q-upload-empty', card).hidden = !!state.video;
    $('.q-upload-done', card).hidden = !state.video;
    if (state.video) $('[data-q-video-name]', card).textContent = state.video;
  }

  function renderAnalysis() {
    $$('[data-q-analysis]', root).forEach((el) => { el.hidden = el.dataset.qAnalysis !== state.analysis; });
  }

  function renderItems() {
    const list = $('[data-q-items]', root);
    const tpl = $('#qItemRow');
    list.replaceChildren(...state.items.map((it, i) => {
      const li = tpl.content.firstElementChild.cloneNode(true);
      const row = $('.q-item', li);
      row.dataset.qItem = i;
      row.setAttribute('aria-label', `${it.name} 수정`);
      $('.q-item-icon img', li).src = `${IMG}${it.icon}.svg`;
      const name = $('.q-item-copy strong', li);
      name.textContent = it.name;
      if (it.review) {
        const em = document.createElement('em');
        em.textContent = '확인 필요';
        name.append(em);
      }
      $('.q-item-copy small', li).textContent = `${it.size} · ${it.count}개`;
      $('b', row).textContent = `${fmt(cbmOf(it))} CBM`;
      return li;
    }));
    $('[data-q-total-count]', root).textContent = totalCount();
    $('[data-q-total-cbm]', root).textContent = fmt(totalCbm());
  }
  const totalCount = () => state.items.reduce((s, it) => s + it.count, 0);
  const totalCbm = () => state.items.reduce((s, it) => s + cbmOf(it), 0);

  function renderOptions() {
    $$('[data-q-house]', root).forEach((b) => setOption(b, b.dataset.qHouse === state.house));
    $$('[data-q-pack]', root).forEach((b) => setOption(b, b.dataset.qPack === state.pack));
  }
  function setOption(btn, on) {
    btn.classList.toggle('active', on);
    btn.setAttribute('aria-checked', on);
  }

  function renderSummary() {
    const unknown = state.dateUnknown || !state.date;
    $('[data-q-price]', root).textContent = unknown ? '400,000 ~ 480,000' : '410,000';
    $('[data-q-sum-date]', root).hidden = unknown;
    $('[data-q-sum-date-pick]', root).hidden = !unknown;
    if (!unknown) {
      $('[data-q-sum-date]', root).textContent = `${dateLabel(state.date)} ${state.time === '시간 상관없음' ? '· 시간 상관없음' : state.time}`;
    }
    const short = (sel) => (sel ? sel.road.split(' ').slice(1).join(' ') : '—');
    $('[data-q-sum-from]', root).textContent = short(state.addr.from.sel);
    $('[data-q-sum-to]', root).textContent = short(state.addr.to.sel);
    $('[data-q-sum-type]', root).textContent = state.house && state.pack ? `${state.house} · ${state.pack}` : '—';
    $('[data-q-sum-items]', root).textContent = `${totalCount()}개 · ${fmt(totalCbm())} CBM`;
    $('[data-q-note]', root).textContent = unknown ? '날짜에 따라 실제 금액은 달라질 수 있어요.' : '담당자 검토 후 금액이 확정돼요.';
  }

  function render() {
    renderChrome();
    if (state.step === 1) renderUpload();
    if (state.step === 2) { renderAnalysis(); renderItems(); }
    if (state.step === 3) renderOptions();
    if (state.step === 5) renderCalendar();
    if (state.step === 6) renderSummary();
  }

  function go(step) {
    clearTimeout(analysisTimer);
    state.step = Math.min(TOTAL, Math.max(1, step));
    if (state.step === 2 && state.analysis !== 'done') {
      state.analysis = 'analyzing';
      analysisTimer = setTimeout(() => { state.analysis = 'done'; render(); speak(); }, 1800);
    }
    render();
    CodeMove.scroller().scrollTo(0, 0);
    try {
      const url = new URL(location.href);
      url.searchParams.set('step', state.step);
      url.searchParams.delete('state');
      history.replaceState(null, '', url);
    } catch (_) { /* URL 갱신 불가 환경 무시 */ }
    speak();
  }

  /* ── 1. 영상 업로드 ── */
  $('[data-q-video]', root).addEventListener('change', (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    state.video = file.name;
    state.analysis = 'analyzing';
    render();
  });

  /* ── 2. 품목 수정·추가 시트 ── */
  let draft = null;
  let draftIndex = -1; // -1 = 새 품목 추가

  /* keepDraft: 종류 변경 시트에서 돌아올 때는 수정 중인 값(크기·수량·종류)을 유지 */
  function openEdit(index, keepDraft) {
    draftIndex = index;
    if (!keepDraft && index >= 0) draft = { ...state.items[index] };
    renderEdit();
    close('#qPickSheet');
    open('#qEditSheet');
  }

  function renderEdit() {
    const sheet = $('#qEditSheet');
    $('[data-q-edit-icon]', sheet).src = `${IMG}${draft.icon}.svg`;
    $('[data-q-edit-name]', sheet).textContent = draft.name;
    $$('[data-size]', sheet).forEach((b) => {
      const on = b.dataset.size === draft.size;
      b.classList.toggle('active', on);
      b.setAttribute('aria-checked', on);
    });
    $('[data-q-edit-count]', sheet).textContent = draft.count;
    $('[data-q-edit-cbm]', sheet).textContent = `${fmt(cbmOf(draft))} CBM`;
    $('[data-q-delete]', sheet).hidden = draftIndex < 0;
    $('[data-q-save]', sheet).textContent = draftIndex < 0 ? '품목 추가' : '수정 완료';
  }

  let pickMode = 'type';
  function openPicker(m) {
    pickMode = m;
    const sheet = $('#qPickSheet');
    $('[data-q-pick-title]', sheet).textContent = m === 'type' ? '종류 변경' : '품목 추가';
    $('[data-q-pick-back]', sheet).hidden = m !== 'type';
    const search = $('[data-q-pick-search]', sheet);
    search.value = '';
    search.placeholder = m === 'type' ? '품목 검색' : '품목 이름을 검색하세요';
    filterPicker('');
    const current = m === 'type' && draft ? draft.name : '';
    let panel = 'furniture';
    $$('[data-q-pick]', sheet).forEach((b) => {
      const on = b.dataset.qPick === current;
      b.classList.toggle('active', on);
      if (on) panel = b.closest('[data-panel]').dataset.panel;
    });
    $$('[data-tab]', sheet).forEach((t) => t.classList.toggle('active', t.dataset.tab === panel));
    $$('[data-panel]', sheet).forEach((p) => { p.hidden = p.dataset.panel !== panel; });
    $('.q-pick-scroll', sheet).scrollTop = 0;
    close('#qEditSheet');
    open('#qPickSheet');
  }

  function filterPicker(q) {
    const sheet = $('#qPickSheet');
    const query = q.trim();
    $('.q-pick-body', sheet).classList.toggle('q-searching', !!query);
    let any = false;
    $$('[data-q-pick]', sheet).forEach((b) => {
      const show = !query || b.dataset.qPick.includes(query);
      b.parentElement.hidden = !show;
      if (show && query) any = true;
    });
    $('[data-q-pick-empty]', sheet).hidden = !query || any;
  }

  $('[data-q-items]', root).addEventListener('click', (e) => {
    const row = e.target.closest('[data-q-item]');
    if (row) openEdit(Number(row.dataset.qItem));
  });
  $('[data-q-add]', root).addEventListener('click', () => { draft = null; openPicker('add'); });

  $('#qEditSheet').addEventListener('click', (e) => {
    const size = e.target.closest('[data-size]');
    if (size) { draft.size = size.dataset.size; renderEdit(); return; }
    if (e.target.closest('[data-q-minus]')) { draft.count = Math.max(1, draft.count - 1); renderEdit(); return; }
    if (e.target.closest('[data-q-plus]')) { draft.count += 1; renderEdit(); return; }
    if (e.target.closest('[data-q-open-type]')) { openPicker('type'); return; }
    if (e.target.closest('[data-q-delete]')) {
      state.items.splice(draftIndex, 1);
      close('#qEditSheet');
      render();
      return;
    }
    if (e.target.closest('[data-q-save]')) {
      const saved = { ...draft, review: false };
      if (draftIndex < 0) state.items.push(saved);
      else state.items[draftIndex] = saved;
      close('#qEditSheet');
      render();
    }
  });

  $('#qPickSheet').addEventListener('click', (e) => {
    if (e.target.closest('[data-q-pick-back]')) { openEdit(draftIndex, true); return; }
    const pick = e.target.closest('[data-q-pick]');
    if (!pick) return;
    const name = pick.dataset.qPick;
    const [icon, base] = catalog(name);
    if (pickMode === 'type') {
      Object.assign(draft, { name, icon, base });
    } else {
      draft = newItem(name, '중형', 1);
      draftIndex = -1;
    }
    openEdit(draftIndex, true);
  });
  $('[data-q-pick-search]').addEventListener('input', (e) => filterPicker(e.target.value));

  /* ── 3. 이사 정보 ── */
  root.addEventListener('click', (e) => {
    const house = e.target.closest('[data-q-house]');
    const pack = e.target.closest('[data-q-pack]');
    if (!house && !pack) return;
    if (house) state.house = house.dataset.qHouse;
    if (pack) state.pack = pack.dataset.qPack;
    renderOptions();
    renderChrome();
  });

  /* ── 4. 주소 ── */
  const addrBlocks = $$('[data-q-addr]', root);

  function showResults(block, query) {
    const box = $('[data-q-addr-results]', block);
    const q = query.trim();
    if (!q) { box.hidden = true; dimOthers(); return; }
    const found = MOCK_ADDRESSES.filter((a) => a.road.includes(q) || a.jibun.includes(q));
    if (!found.length) {
      const empty = document.createElement('div');
      empty.className = 'q-addr-empty';
      empty.textContent = '검색 결과가 없어요. 다른 주소로 검색해보세요.';
      box.replaceChildren(empty);
    } else {
      box.replaceChildren(...found.map((a) => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'action q-addr-result';
        btn.dataset.road = a.road;
        const strong = document.createElement('strong');
        const at = a.road.indexOf(q);
        if (at >= 0) {
          const em = document.createElement('em');
          em.textContent = a.road.slice(at, at + q.length);
          strong.append(a.road.slice(0, at), em, a.road.slice(at + q.length));
        } else {
          strong.textContent = a.road;
        }
        const small = document.createElement('small');
        small.textContent = a.jibun;
        btn.append(strong, small);
        return btn;
      }));
    }
    box.hidden = false;
    dimOthers();
  }

  function dimOthers() {
    const openBlock = addrBlocks.find((b) => !$('[data-q-addr-results]', b).hidden);
    addrBlocks.forEach((b) => b.classList.toggle('is-dim', !!openBlock && b !== openBlock && addrBlocks.indexOf(b) > addrBlocks.indexOf(openBlock)));
    addrBlocks.forEach((b) => $('[data-q-addr-search]', b).classList.toggle('is-active', b === openBlock));
  }

  function renderAddr(block) {
    const data = state.addr[block.dataset.qAddr];
    const selected = !!data.sel;
    block.classList.toggle('is-selected', selected);
    $('[data-q-addr-selected]', block).hidden = !selected;
    $('[data-q-addr-detail]', block).hidden = !selected;
    if (selected) {
      $('[data-q-addr-road]', block).textContent = data.sel.road;
      $('[data-q-addr-jibun]', block).textContent = data.sel.jibun;
      const detail = $('[data-q-addr-detail] input', block);
      detail.value = data.detail;
      $('[data-q-addr-detail]', block).classList.toggle('is-filled', !!data.detail.trim());
    }
  }

  function selectAddr(block, road, detail = '') {
    const data = state.addr[block.dataset.qAddr];
    data.sel = MOCK_ADDRESSES.find((a) => a.road === road) || null;
    data.detail = detail;
    $('[data-q-addr-results]', block).hidden = true;
    dimOthers();
    renderAddr(block);
    renderChrome();
  }

  addrBlocks.forEach((block) => {
    const input = $('[data-q-addr-search] input', block);
    input.addEventListener('input', () => showResults(block, input.value));
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); showResults(block, input.value); } });
    $('[data-q-addr-go]', block).addEventListener('click', () => { showResults(block, input.value); input.focus(); });
    $('[data-q-addr-results]', block).addEventListener('click', (e) => {
      const r = e.target.closest('[data-road]');
      if (!r) return;
      selectAddr(block, r.dataset.road);
      $('[data-q-addr-detail] input', block).focus();
    });
    $('[data-q-addr-reset]', block).addEventListener('click', () => {
      state.addr[block.dataset.qAddr] = { sel: null, detail: '' };
      input.value = '';
      renderAddr(block);
      renderChrome();
      input.focus();
    });
    const detail = $('[data-q-addr-detail] input', block);
    const field = detail.closest('.q-addr-field');
    detail.addEventListener('input', () => {
      state.addr[block.dataset.qAddr].detail = detail.value;
      $('[data-q-addr-detail]', block).classList.toggle('is-filled', !!detail.value.trim());
    });
    detail.addEventListener('blur', () => field.classList.remove('is-active'));
  });

  /* ── 5. 날짜 ── */
  const sameMonth = (y, m, d) => y === d.getFullYear() && m === d.getMonth();

  function renderCalendar() {
    const { y, m } = state.view;
    $('[data-q-cal-title]', root).textContent = `${y}년 ${m + 1}월`;
    $('[data-q-cal-prev]', root).disabled = y < TODAY.getFullYear() || sameMonth(y, m, TODAY) || (y === TODAY.getFullYear() && m < TODAY.getMonth());
    const first = new Date(y, m, 1).getDay();
    const days = new Date(y, m + 1, 0).getDate();
    const prevDays = new Date(y, m, 0).getDate();
    const cells = Math.ceil((first + days) / 7) * 7;
    const nodes = [];
    for (let i = 0; i < cells; i += 1) {
      const span = document.createElement('span');
      const d = i - first + 1;
      if (d < 1 || d > days) {
        span.className = 'muted';
        span.textContent = d < 1 ? prevDays + d : d - days;
        span.setAttribute('aria-hidden', 'true');
      } else {
        const past = sameMonth(y, m, TODAY) && d < TODAY.getDate();
        const picked = state.date && state.date.y === y && state.date.m === m && state.date.d === d;
        span.className = picked ? 'picked' : past ? 'muted' : 'selectable';
        span.textContent = d;
        if (!past) {
          span.dataset.day = d;
          span.setAttribute('role', 'button');
          span.tabIndex = 0;
          span.setAttribute('aria-label', `${m + 1}월 ${d}일`);
          if (picked) span.setAttribute('aria-pressed', 'true');
        }
      }
      nodes.push(span);
    }
    $('[data-q-days]', root).replaceChildren(...nodes);
    const timeRow = $('[data-q-time-row]', root);
    timeRow.hidden = !state.date;
    $('[data-q-time-label]', root).textContent = state.time;
  }

  $('[data-q-days]', root).addEventListener('click', (e) => {
    const day = e.target.closest('[data-day]');
    if (!day) return;
    state.date = { y: state.view.y, m: state.view.m, d: Number(day.dataset.day) };
    state.dateUnknown = false;
    renderCalendar();
    renderChrome();
  });
  function moveMonth(delta) {
    const d = new Date(state.view.y, state.view.m + delta, 1);
    state.view = { y: d.getFullYear(), m: d.getMonth() };
    renderCalendar();
  }
  $('[data-q-cal-prev]', root).addEventListener('click', () => moveMonth(-1));
  $('[data-q-cal-next]', root).addEventListener('click', () => moveMonth(1));

  function openTime() {
    const sheet = $('#qTimeSheet');
    $('[data-q-time-title]', sheet).textContent = state.date ? dateLabel(state.date) : '';
    $$('.q-time-slots [data-q-time]', sheet).forEach((b) => b.classList.toggle('active', b.dataset.qTime === state.time));
    open('#qTimeSheet');
  }
  $('[data-q-time-row]', root).addEventListener('click', openTime);
  $('#qTimeSheet').addEventListener('click', (e) => {
    const slot = e.target.closest('[data-q-time]');
    if (!slot) return;
    state.time = slot.dataset.qTime;
    close('#qTimeSheet');
    renderCalendar();
  });

  $('[data-q-skip-date]').addEventListener('click', () => {
    state.dateUnknown = true;
    state.date = null;
    close('#qUnknownSheet');
    go(6);
  });

  /* ── 6. 예상 견적 ── */
  root.addEventListener('click', (e) => {
    const goto = e.target.closest('[data-q-goto]');
    if (goto) go(Number(goto.dataset.qGoto));
  });

  /* ── 하단 버튼 · 뒤로 ── */
  $('[data-q-next]', root).addEventListener('click', () => {
    if (!canNext()) return;
    if (state.step === TOTAL) {
      stopVoice();
      location.href = root.dataset.completeUrl || '/quote/complete';
      return;
    }
    go(state.step + 1);
  });
  $('[data-q-back]', root).addEventListener('click', (e) => {
    if (state.step > 1) { e.preventDefault(); go(state.step - 1); return; }
    stopVoice();
    // 1단계: 같은 사이트에서 들어왔으면 이전 화면으로, 아니면 링크(홈)로 이동
    let sameOrigin = false;
    try { sameOrigin = !!document.referrer && new URL(document.referrer).origin === location.origin; } catch (_) { /* 무시 */ }
    if (sameOrigin && history.length > 1) { e.preventDefault(); history.back(); }
  });

  /* ── 쉬운 모드 음성 안내 (Web Speech API) ── */
  const voiceBtn = $('[data-q-voice]', root);
  function renderVoice() {
    voiceBtn.classList.toggle('active', state.voice);
    voiceBtn.setAttribute('aria-pressed', state.voice);
    voiceBtn.setAttribute('aria-label', state.voice ? '음성 안내 끄기' : '음성 안내 켜기');
    $('[data-q-voice-label]', voiceBtn).textContent = state.voice ? '음성 안내 켜짐' : '음성으로 듣기';
    $('span', voiceBtn).hidden = !state.voice;
  }
  function stopVoice() {
    if (window.speechSynthesis) window.speechSynthesis.cancel();
  }
  function speak() {
    if (!state.voice || mode() !== 'easy' || !window.speechSynthesis || typeof SpeechSynthesisUtterance === 'undefined') return;
    stopVoice();
    const text = state.step === 2 && state.analysis === 'done' ? VOICE_GUIDE_DONE : VOICE_GUIDES[state.step - 1];
    const u = new SpeechSynthesisUtterance(text);
    u.lang = 'ko-KR';
    u.rate = 0.88;
    window.speechSynthesis.speak(u);
  }
  voiceBtn.addEventListener('click', () => {
    state.voice = !state.voice;
    renderVoice();
    if (state.voice) speak(); else stopVoice();
  });
  let lastMode = mode();
  document.addEventListener('codemove:mode', () => {
    if (mode() === lastMode) return; // app.js 가 클릭마다 이벤트를 보낼 수 있어 실제 전환만 처리
    lastMode = mode();
    if (lastMode !== 'easy') stopVoice();
    renderChrome();
  });
  window.addEventListener('pagehide', stopVoice);

  /* ── URL 상태로 바로 열기 (?step=&state=) ── */
  function preset(step, st) {
    const view = st === 'unknown' ? { y: 2026, m: 1 } : { y: 2026, m: 2 };
    if (step >= 2 || st === 'uploaded') state.video = '우리집_촬영영상.mp4';
    if (step >= 2) state.analysis = step === 2 && st === 'analyzing' ? 'analyzing' : 'done';
    if (step >= 4 || (step === 3 && st === 'selected')) { state.house = '원룸'; state.pack = '일반포장'; }
    if (step >= 5) {
      state.addr.from = { sel: MOCK_ADDRESSES[0], detail: '301호' };
      state.addr.to = { sel: MOCK_ADDRESSES[3], detail: '' };
    }
    if ((step === 5 && (st === 'picked' || st === 'time')) || (step === 6 && st !== 'undecided')) state.date = { y: 2026, m: 2, d: 18 };
    if (step === 6 && st === 'undecided') state.dateUnknown = true;
    state.view = view;
    state.step = step;
    render();
    addrBlocks.forEach(renderAddr);

    if (step === 2 && st === 'edit') openEdit(2);
    if (step === 2 && st === 'type') { draft = { ...state.items[2] }; draftIndex = 2; openPicker('type'); }
    if (step === 2 && st === 'add') { draft = null; openPicker('add'); }
    if (step === 4) {
      const [from, to] = addrBlocks;
      if (st === 'search' || st === 'noresult') {
        const input = $('[data-q-addr-search] input', from);
        input.value = st === 'search' ? '서울' : '테헤란로 9999';
        showResults(from, input.value);
      }
      if (st === 'done') {
        selectAddr(from, MOCK_ADDRESSES[0].road, '301호');
        selectAddr(to, MOCK_ADDRESSES[3].road);
        $('[data-q-addr-detail] .q-addr-field', to).classList.add('is-active');
      }
    }
    if (step === 5 && st === 'time') openTime();
    if (step === 5 && st === 'unknown') open('#qUnknownSheet');
  }

  const params = new URLSearchParams(location.search);
  const startStep = Math.min(TOTAL, Math.max(1, parseInt(params.get('step'), 10) || 1));
  const startState = params.get('state') || '';
  state.voice = params.get('voice') === 'on';
  renderVoice();
  preset(startStep, startState);
  speak();
})();
