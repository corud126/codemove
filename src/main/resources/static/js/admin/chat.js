/*
 * 관리자 채팅 관리 (SCR-A-006 ~ SCR-A-010) — 화면 개발 단계 시뮬레이션 (백엔드 연동 없음)
 * 페이지 루트 data 속성으로 동작을 나눈다: data-chat-list | data-chat-detail | data-chat-settings | data-chat-entry | data-chat-answers
 * 화면 상태: ?state=... (각 템플릿 상단 주석 참고)
 */
(function () {
  const params = new URLSearchParams(location.search);
  const state = params.get('state') || '';
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));
  const open = (el) => { if (el) el.hidden = false; };
  const close = (el) => { if (el) el.hidden = true; };
  const firstUse = document.querySelector('use');
  const SPRITE = firstUse ? firstUse.getAttribute('href').split('#')[0] : '/images/admin/icons.svg';
  const icon = (name, size) => `<svg class="icon" width="${size}" height="${size}"><use href="${SPRITE}#i-${name}"/></svg>`;
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  // 뒤로 가기: 같은 사이트에서 들어왔으면 이전 페이지로(알림 내역 → 채팅 상세 등), 아니면 링크의 기본 경로로
  $$('[data-history-back]').forEach((a) => a.addEventListener('click', (e) => {
    const ref = document.referrer;
    if (ref && new URL(ref).origin === location.origin && new URL(ref).pathname !== location.pathname && history.length > 1) {
      e.preventDefault();
      history.back();
    }
  }));

  // 헤더 드롭다운 메뉴 공통 (목록 필터 · 상세 대화 설정)
  function bindDropdown(root) {
    const toggle = $('[data-menu-toggle]', root);
    const menu = $('[data-menu]', root);
    if (!toggle || !menu) return { show() {}, hide() {} };
    const set = (v) => { menu.hidden = !v; toggle.setAttribute('aria-expanded', String(v)); };
    toggle.addEventListener('click', (e) => { e.stopPropagation(); set(menu.hidden); });
    document.addEventListener('click', (e) => { if (!menu.hidden && !menu.contains(e.target)) set(false); });
    return { show: () => set(true), hide: () => set(false) };
  }

  // 상단 검색바 공통
  function bindSearch(root, onChange) {
    const bar = $('[data-search]', root);
    const input = $('[data-search-input]', root);
    const btn = $('[data-search-open]', root);
    // 검색바가 열려 있는 동안 헤더 검색 버튼 강조 (스토리보드 A-006 B · A-007)
    new MutationObserver(() => btn.setAttribute('aria-pressed', String(!bar.hidden))).observe(bar, { attributes: true, attributeFilter: ['hidden'] });
    btn.addEventListener('click', () => { open(bar); input.focus(); });
    $('[data-search-close]', root).addEventListener('click', () => { input.value = ''; close(bar); onChange(''); });
    input.addEventListener('input', () => onChange(input.value));
    const clear = $('[data-search-clear]', root);
    if (clear) clear.addEventListener('click', () => { input.value = ''; onChange(''); input.focus(); });
    return { bar, input };
  }

  /* ---------- SCR-A-006 채팅 목록 ---------- */
  const listRoot = $('[data-chat-list]');
  if (listRoot) {
    const box = $('[data-rooms]', listRoot);
    const rooms = $$('[data-name]', box);
    const LABELS = { all: '전체 채팅', waiting: '답변 대기', unread: '안 읽은 채팅' };
    const SORTS = { latest: '최신 순', unread: '안 읽은 순' };
    let filter = LABELS[params.get('filter')] ? params.get('filter') : 'all';
    let sort = 'latest';
    let query = '';
    const dropdown = bindDropdown(listRoot);
    const search = bindSearch(listRoot, (q) => { query = q; render(); });

    if (/\/admin\/chats\/?$|\/chat\/home/.test(document.referrer) || params.get('filter')) open($('[data-back-link]', listRoot));

    function render() {
      const q = query.trim().toLowerCase();
      const visible = rooms.filter((r) => {
        if (filter === 'waiting' && r.dataset.waiting !== '1') return false;
        if (filter === 'unread' && r.dataset.unread === '0') return false;
        return !q || `${r.dataset.name} ${r.dataset.msg}`.toLowerCase().includes(q);
      }).sort((a, b) => (sort === 'unread' ? b.dataset.unread - a.dataset.unread : 0) || b.dataset.activity - a.dataset.activity);
      rooms.forEach((r) => { r.hidden = !visible.includes(r); });
      visible.forEach((r) => box.appendChild(r));
      $('[data-count]', listRoot).textContent = visible.length;
      $('[data-count-label]', listRoot).textContent = LABELS[filter];
      $$('[data-sort-label]', listRoot).forEach((el) => { el.textContent = SORTS[sort]; });
      $('[data-empty]', listRoot).hidden = visible.length > 0;
      const count = $('[data-search-count]', listRoot);
      count.hidden = !q;
      count.textContent = `${visible.length}건`;
      const clear = $('[data-search-clear]', listRoot);
      if (clear) clear.hidden = !q;
      $$('[data-filter]', listRoot).forEach((b) => {
        const on = b.dataset.filter === filter;
        b.classList.toggle('bg-[#edf3ed]', on && filter !== 'waiting');
        b.classList.toggle('text-[#346538]', on && filter !== 'waiting');
        b.classList.toggle('bg-[#fff0ee]', on && filter === 'waiting');
        b.classList.toggle('text-[#b34c43]', on && filter === 'waiting');
        b.classList.toggle('text-[#4d554f]', !on);
      });
    }

    $$('[data-filter]', listRoot).forEach((b) => b.addEventListener('click', () => { filter = b.dataset.filter; dropdown.hide(); render(); }));
    $('[data-sort-open]', listRoot).addEventListener('click', () => {
      dropdown.hide();
      $$('input[name="chatSort"]', listRoot).forEach((i) => { i.checked = i.value === sort; });
      open($('#sortModal'));
    });
    $('[data-sort-apply]', listRoot).addEventListener('click', () => {
      sort = ($('input[name="chatSort"]:checked', listRoot) || {}).value || 'latest';
      close($('#sortModal'));
      render();
    });

    render();
    if (state === 'menu') dropdown.show();
    if (state === 'sort') open($('#sortModal'));
    if (state === 'empty') { open(search.bar); search.input.value = '없는고객'; query = '없는고객'; render(); }
  }

  /* ---------- SCR-A-007 채팅 상세 ---------- */
  const detailRoot = $('[data-chat-detail]');
  if (detailRoot) {
    // /admin/chats/{고객 ID} → 더미 채팅방 (목록 · 알림 패널과 같은 ID)
    const ROOMS = {
      luxury: ['홍길동', '혹시 사다리차 추가 비용이 발생하나요?', 1], luxury2: ['김명품', '엘리베이터를 사용하지 못하는데요?', 1],
      park789: ['박철수', '네, 알겠습니다. 내일 뵙겠습니다.', 0], choi00: ['최지우', '에어컨 이전 설치도 같이 되나요?', 1],
      kang22: ['강민주', '포장 박스는 언제 가져다 주시나요?', 1], lee01: ['이서준', '견적서 확인했습니다.', 0],
      yoon44: ['윤아름', '귀중품은 따로 챙겨야 하나요?', 1], song90: ['송지호', '이사 시작 시간은 몇 시인가요?', 0],
      jung11: ['정현우', '주소 변경은 어디서 하나요?', 0], han26: ['한유진', '계약금 입금 완료했습니다.', 0],
      oh03: ['오도현', '방문 견적 일정을 변경하고 싶어요.', 0], moon08: ['문지안', '침대 분해와 조립도 부탁드려요.', 0],
      lim19: ['임지수', '주차 공간 사진 보내드립니다.', 0], kim24: ['김도윤', '카드 결제가 가능한가요?', 0],
      jimin31: ['이지민', '친절한 안내 감사합니다.', 0],
    };
    const idMatch = location.pathname.match(/\/admin\/chats\/([^/]+)$/);
    const room = (idMatch && ROOMS[decodeURIComponent(idMatch[1])]) || ROOMS.luxury;
    const area = $('[data-messages-area]', detailRoot);
    const list = $('[data-messages]', detailRoot);
    const waitingMsg = $('[data-waiting]', list);
    $('[data-room-name]', detailRoot).textContent = room[0];
    $('[data-room-message]', detailRoot).textContent = room[1];
    if (!room[2]) {
      close($('[data-room-waiting]', detailRoot));
      delete waitingMsg.dataset.waiting;
      waitingMsg.removeAttribute('data-swipe');
      $('[data-text]', waitingMsg).className = 'rounded-2xl rounded-tl-md bg-white p-3 text-[13px] leading-5 text-[#343b36] shadow-sm';
      close($('[data-waiting-hint]', waitingMsg));
    }

    const opts = { hideAi: false, waitingOnly: false };
    let query = '';
    const dropdown = bindDropdown(detailRoot);
    const search = bindSearch(detailRoot, (q) => { query = q; filterMessages(); });

    function filterMessages() {
      const q = query.trim().toLowerCase();
      let shown = 0;
      $$('[data-role]', list).forEach((m) => {
        const hide = (opts.hideAi && m.dataset.role === 'ai')
          || (opts.waitingOnly && !('waiting' in m.dataset))
          || (q && !$('[data-text]', m).textContent.toLowerCase().includes(q));
        m.hidden = hide;
        if (!hide) shown += 1;
      });
      $('[data-empty]', detailRoot).hidden = shown > 0;
      const count = $('[data-search-count]', detailRoot);
      count.hidden = !q;
      count.textContent = `${shown}건`;
    }

    $$('[data-option]', detailRoot).forEach((b) => b.addEventListener('click', () => {
      opts[b.dataset.option] = !opts[b.dataset.option];
      b.setAttribute('aria-pressed', String(opts[b.dataset.option]));
      filterMessages();
    }));

    // ④ 답변 대기 메시지 왼쪽으로 밀어 답장
    const reply = $('[data-reply]', detailRoot);
    let replyText = '';
    function setReply(text) {
      replyText = text;
      reply.hidden = !text;
      $('[data-reply-text]', detailRoot).textContent = text;
      if (text) input.focus();
    }
    $$('[data-swipe]', list).forEach((m) => {
      let startX = null;
      m.addEventListener('pointerdown', (e) => { startX = e.clientX; });
      m.addEventListener('pointermove', (e) => {
        if (startX === null) return;
        const dx = Math.min(0, Math.max(-60, e.clientX - startX));
        m.style.transform = `translateX(${dx}px)`;
      });
      const end = (e) => {
        if (startX !== null && startX - e.clientX > 45) setReply($('[data-text]', m).textContent);
        startX = null;
        m.style.transform = '';
      };
      m.addEventListener('pointerup', end);
      m.addEventListener('pointercancel', end);
      m.addEventListener('pointerleave', (e) => { if (startX !== null) end(e); });
    });
    $('[data-reply-cancel]', detailRoot).addEventListener('click', () => setReply(''));

    // ⑤ 입력 · ⑥ 전송 → 관리자 메시지 추가
    const form = $('[data-send-form]', detailRoot);
    const input = $('[data-send-input]', detailRoot);
    const sendBtn = $('[data-send-button]', detailRoot);
    input.addEventListener('input', () => { sendBtn.disabled = !input.value.trim(); });
    function appendAdmin(text, replyTo) {
      const el = document.createElement('div');
      el.className = 'ml-auto flex max-w-[84%] flex-col items-end gap-1';
      el.dataset.role = 'admin';
      el.innerHTML = `<span class="text-[10px] font-bold text-[#346538]">관리자</span>
        <div class="rounded-2xl rounded-tr-md bg-[#346538] p-3 text-[13px] leading-5 text-white shadow-sm">${replyTo ? `<span class="mb-2 block rounded-lg border-l-2 border-[#d8b65d] bg-white/20 px-2 py-1 text-[11px] opacity-90">${esc(replyTo)}</span>` : ''}<span data-text>${esc(text)}</span></div>`;
      list.appendChild(el);
      filterMessages();
      area.scrollTo({ top: area.scrollHeight, behavior: 'smooth' });
    }
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const text = input.value.trim();
      if (!text) return;
      appendAdmin(text, replyText);
      input.value = '';
      sendBtn.disabled = true;
      setReply('');
    });

    if (state === 'settings') dropdown.show();
    if (state === 'replied') appendAdmin('사다리차 비용은 층수에 따라 달라집니다.');
    if (state === 'reply') setReply(room[1]);
    if (state === 'empty') { open(search.bar); search.input.value = '없는내용'; query = '없는내용'; filterMessages(); }
  }

  /* ---------- SCR-A-008 채팅 운영 설정 ---------- */
  const settingsRoot = $('[data-chat-settings]');
  if (settingsRoot) {
    const toggles = {};
    $$('[data-toggle]', settingsRoot).forEach((t) => {
      toggles[t.dataset.toggle] = t;
      t.addEventListener('click', () => { t.setAttribute('aria-pressed', String(t.getAttribute('aria-pressed') !== 'true')); syncHours(); });
    });
    const timeBtns = $$('[data-time]', settingsRoot);
    function syncHours() {
      const on = toggles.hours.getAttribute('aria-pressed') === 'true';
      timeBtns.forEach((b) => { b.disabled = !on; });
    }

    // ③ 시간 선택 (24시간 표기 ↔ 오전/오후 · 시 · 분)
    const sheet = $('#timeSheet');
    const pick = (k) => $(`[data-pick="${k}"]`, sheet);
    let target = null;
    function openTime(btn) {
      target = btn;
      const [h, m] = btn.textContent.trim().split(':').map(Number);
      pick('period').value = h >= 12 ? '오후' : '오전';
      pick('hour').value = String(h % 12 || 12).padStart(2, '0');
      pick('minute').value = String(m - (m % 10)).padStart(2, '0');
      $('[data-time-title]', sheet).textContent = btn.dataset.time === 'start' ? '시작 시간' : '종료 시간';
      open(sheet);
      ['period', 'hour', 'minute'].forEach((k) => {
        const sel = pick(k);
        const rowH = sel.scrollHeight / sel.options.length;
        sel.scrollTop = sel.selectedIndex * rowH - (sel.clientHeight - rowH) / 2;
      });
    }
    timeBtns.forEach((b) => b.addEventListener('click', () => openTime(b)));
    $('[data-time-save]', sheet).addEventListener('click', () => {
      const h12 = Number(pick('hour').value);
      const h24 = pick('period').value === '오후' ? (h12 % 12) + 12 : (h12 === 12 ? 0 : h12);
      target.textContent = `${String(h24).padStart(2, '0')}:${pick('minute').value}`;
      close(sheet);
    });

    // ④ 대응 규칙
    const ruleModal = $('#ruleModal');
    const ruleInput = $('[data-rule-input]', ruleModal);
    const ruleSave = $('[data-rule-save]', ruleModal);
    const ruleText = $('[data-rule-text]', settingsRoot);
    const syncRule = () => { ruleSave.disabled = !ruleInput.value.trim(); };
    $('[data-rule-open]', settingsRoot).addEventListener('click', () => { ruleInput.value = ruleText.textContent; syncRule(); open(ruleModal); });
    ruleInput.addEventListener('input', syncRule);
    ruleSave.addEventListener('click', () => { ruleText.textContent = ruleInput.value.trim(); close(ruleModal); });

    if (state === 'off') { toggles.enabled.setAttribute('aria-pressed', 'false'); toggles.hours.setAttribute('aria-pressed', 'false'); }
    syncHours();
    if (state === 'rule') $('[data-rule-open]', settingsRoot).click();
    if (state === 'time') openTime(timeBtns[0]);
  }

  /* ---------- SCR-A-009 첫화면/퀵메뉴 설정 ---------- */
  const entryRoot = $('[data-chat-entry]');
  if (entryRoot) {
    // 답변 설정 관리(SCR-A-010)의 답변 주제 = 퀵메뉴 후보
    const TOPICS = [
      { id: 1, title: '이사 견적', description: '이사 비용과 방문 견적 과정을 안내합니다.' },
      { id: 2, title: '예약 방법', description: '상담부터 예약 확정까지의 절차를 안내합니다.' },
      { id: 3, title: '포장 이사', description: '포장 서비스 범위와 준비 사항을 확인합니다.' },
      { id: 4, title: '사다리차 이용', description: '사다리차 필요 여부와 추가 비용을 안내합니다.' },
      { id: 5, title: '보관 이사', description: '짐 보관 기간과 보관료를 안내합니다.' },
      { id: 6, title: '입주 청소', description: '이사 전후 청소 서비스 범위를 안내합니다.' },
    ];
    const MAX = 4;
    let menus = state === 'full' ? TOPICS.slice(0, 4) : TOPICS.slice(0, 2);
    let deleting = null;
    const grid = $('[data-quick-grid]', entryRoot);
    const greeting = $('[data-greeting]', entryRoot);
    const toast = $('[data-toast]', entryRoot);
    let toastTimer;
    function showToast(msg) {
      toast.textContent = msg;
      open(toast);
      clearTimeout(toastTimer);
      toastTimer = setTimeout(() => close(toast), 2000);
    }

    function renderGrid() {
      grid.innerHTML = Array.from({ length: MAX }, (_, i) => {
        const m = menus[i];
        if (!m) {
          return `<button type="button" data-menu-add class="aspect-square rounded-2xl border border-dashed border-[#bfc9c0] bg-[#fafbfa] text-xs font-bold text-[#718074] hover:bg-[#f3f6f3]">
            <span class="mx-auto mb-2 grid h-8 w-8 place-items-center rounded-full bg-[#edf3ed] text-lg text-[#346538]">+</span>등록</button>`;
        }
        return `<div class="relative aspect-square rounded-2xl border border-[#dce6dc] bg-[#f1f6f1] p-3">
          <button type="button" data-menu-remove="${m.id}" aria-label="${esc(m.title)} 삭제" class="absolute right-2 top-2 grid h-6 w-6 place-items-center rounded-full bg-white text-[#a34d45] shadow-sm">${icon('x', 13)}</button>
          <div class="flex h-full flex-col items-center justify-center text-center">
            <span class="grid h-10 w-10 place-items-center rounded-xl bg-white text-[#346538] shadow-sm">${icon('chat', 19)}</span>
            <strong class="mt-3 text-xs text-[#344037]">${esc(m.title)}</strong>
          </div></div>`;
      }).join('');
    }
    grid.addEventListener('click', (e) => {
      if (e.target.closest('[data-menu-add]')) openMenu();
      const rm = e.target.closest('[data-menu-remove]');
      if (rm) { deleting = Number(rm.dataset.menuRemove); open($('#deleteModal')); }
    });
    $('[data-delete-confirm]', entryRoot).addEventListener('click', () => {
      menus = menus.filter((m) => m.id !== deleting);
      close($('#deleteModal'));
      renderGrid();
    });

    // ④ 퀵메뉴 조회
    const menuLayer = $('#menuLayer');
    const query = $('[data-menu-query]', menuLayer);
    const candidates = $('[data-menu-candidates]', menuLayer);
    function renderCandidates() {
      const q = query.value.trim().toLowerCase();
      const list = TOPICS.filter((t) => !q || `${t.title} ${t.description}`.toLowerCase().includes(q));
      candidates.innerHTML = list.map((t) => {
        const registered = menus.some((m) => m.id === t.id);
        const full = menus.length >= MAX;
        return `<button type="button" data-pick-topic="${t.id}" ${registered || full ? 'disabled' : ''} class="flex w-full items-center gap-3 rounded-2xl bg-white p-4 text-left disabled:opacity-45">
          <span class="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-[#edf3ed] text-[#346538]">${icon('chat', 17)}</span>
          <span class="min-w-0 flex-1"><strong class="block text-sm text-[#333b35]">${esc(t.title)}</strong>
          <span class="mt-1 block truncate text-xs text-[#8a918c]">${esc(t.description)}</span></span>
          <span class="text-xs font-bold text-[#346538]">${registered ? '등록됨' : '선택'}</span></button>`;
      }).join('') || '<p class="py-16 text-center text-sm text-[#858d87]">검색 결과가 없습니다.</p>';
    }
    function openMenu() {
      if (menus.length >= MAX) { showToast('퀵메뉴는 최대 4개까지 등록할 수 있습니다.'); return; }
      query.value = '';
      renderCandidates();
      open(menuLayer);
    }
    query.addEventListener('input', renderCandidates);
    candidates.addEventListener('click', (e) => {
      const b = e.target.closest('[data-pick-topic]');
      if (!b || b.disabled) return;
      menus.push(TOPICS.find((t) => t.id === Number(b.dataset.pickTopic)));
      close(menuLayer);
      renderGrid();
    });

    // ① 인사말 수정
    const gModal = $('#greetingModal');
    const gInput = $('[data-greeting-input]', gModal);
    const gSave = $('[data-greeting-save]', gModal);
    $('[data-greeting-open]', entryRoot).addEventListener('click', () => { gInput.value = greeting.textContent; gSave.disabled = false; open(gModal); });
    gInput.addEventListener('input', () => { gSave.disabled = !gInput.value.trim(); });
    gSave.addEventListener('click', () => { greeting.textContent = gInput.value.trim(); close(gModal); });

    // ⑤ 미리 보기 — 열릴 때마다 현재 인사말·퀵메뉴로 갱신
    const preview = $('#previewLayer');
    function fillPreview() {
      $('[data-preview-greeting]', preview).textContent = greeting.textContent;
      $('[data-preview-menus]', preview).innerHTML = menus.map((m) => `<button type="button" class="h-14 w-full rounded-xl bg-[#f2f4f1] px-4 text-sm font-bold text-[#465048]">${esc(m.title)}</button>`).join('');
    }
    new MutationObserver(() => { if (!preview.hidden) fillPreview(); }).observe(preview, { attributes: true, attributeFilter: ['hidden'] });

    renderGrid();
    if (state === 'greeting') $('[data-greeting-open]', entryRoot).click();
    if (state === 'preview') open(preview);
    if (state === 'menu') openMenu();
    if (state === 'delete') { deleting = menus[0].id; open($('#deleteModal')); }
    if (state === 'full') setTimeout(() => showToast('퀵메뉴는 최대 4개까지 등록할 수 있습니다.'), 100);
  }

  /* ---------- SCR-A-010 답변 설정 관리 ---------- */
  const answersRoot = $('[data-chat-answers]');
  if (answersRoot) {
    const box = $('[data-topics]', answersRoot);
    // 서버 렌더링된 주제 카드 → 데이터
    let topics = $$('[data-topic]', box).map((sec) => ({
      id: Number(sec.dataset.topic),
      title: $('[data-topic-title]', sec).textContent,
      introduction: $('[data-topic-intro]', sec).textContent,
      lorebooks: $$('[data-lorebook]', sec).map((lb) => ({ keywords: lb.dataset.keywords, content: $('[data-lorebook-content]', lb).textContent })),
    }));
    let expanded = topics.length ? topics[0].id : null;
    let editing = null; // 'new' | topic id
    let deleting = null;
    const queryInput = $('[data-topic-query]', answersRoot);

    function chips(keywords) {
      return keywords.split('#').map((k) => k.trim()).filter(Boolean)
        .map((k) => `<span class="rounded-full bg-[#e9f1e9] px-2 py-1 text-[10px] font-bold text-[#346538]">#${esc(k)}</span>`).join('');
    }
    function render() {
      const q = queryInput.value.trim().toLowerCase();
      const list = topics.filter((t) => !q || `${t.title} ${t.introduction}`.toLowerCase().includes(q));
      box.innerHTML = list.map((t) => `<section data-topic="${t.id}" class="overflow-hidden rounded-2xl border border-white bg-white shadow-[0_3px_12px_rgba(36,58,39,.05)]">
        <div role="button" tabindex="0" data-topic-toggle aria-expanded="${expanded === t.id}" class="flex w-full cursor-pointer items-center gap-2 p-4 text-left">
          <span class="min-w-0 flex-1"><strong class="block text-sm text-[#303832]">${esc(t.title)}</strong>
          <span class="mt-1 block truncate text-xs text-[#8a918c]">${esc(t.introduction)}</span></span>
          <button type="button" data-topic-edit aria-label="${esc(t.title)} 수정" class="grid h-8 w-8 place-items-center rounded-lg bg-[#f2f5f2] text-[#346538]">${icon('edit', 15)}</button>
          <button type="button" data-topic-delete aria-label="${esc(t.title)} 삭제" class="grid h-8 w-8 place-items-center rounded-lg bg-[#fff0ee] text-[#b64b42]">${icon('trash', 15)}</button>
        </div>
        ${expanded === t.id ? `<div data-topic-body class="border-t border-[#edf0ed] bg-[#fafbfa] px-4 py-3">${t.lorebooks.map((lb) => `<div class="mb-3 last:mb-0"><div class="flex flex-wrap gap-1">${chips(lb.keywords)}</div><p class="mt-2 text-xs leading-5 text-[#626a64]">${esc(lb.content)}</p></div>`).join('')}</div>` : ''}
      </section>`).join('');
      $('[data-empty]', answersRoot).hidden = list.length > 0;
    }
    queryInput.addEventListener('input', render);

    box.addEventListener('click', (e) => {
      const sec = e.target.closest('[data-topic]');
      if (!sec) return;
      const id = Number(sec.dataset.topic);
      if (e.target.closest('[data-topic-edit]')) { openEditor(id); return; }
      if (e.target.closest('[data-topic-delete]')) { deleting = id; open($('#topicDeleteModal')); return; }
      if (e.target.closest('[data-topic-toggle]')) { expanded = expanded === id ? null : id; render(); }
    });
    box.addEventListener('keydown', (e) => {
      if ((e.key === 'Enter' || e.key === ' ') && e.target.matches('[data-topic-toggle]')) { e.preventDefault(); e.target.click(); }
    });
    $('[data-topic-delete-confirm]', answersRoot).addEventListener('click', () => {
      topics = topics.filter((t) => t.id !== deleting);
      close($('#topicDeleteModal'));
      render();
    });

    // A. 답변 등록 / 수정
    const editor = $('#topicEditor');
    const titleInput = $('[data-draft-title]', editor);
    const introInput = $('[data-draft-intro]', editor);
    const lbBox = $('[data-lorebooks]', editor);
    const saveBtn = $('[data-topic-save]', editor);
    const syncSave = () => { saveBtn.disabled = !titleInput.value.trim() || !introInput.value.trim(); };

    function lorebookHtml(lb, i) {
      return `<section class="relative rounded-[18px] bg-white p-4" data-lb>
        <div class="mb-3 flex items-center justify-between"><strong class="text-xs text-[#59615b]" data-lb-title>로어북 ${i + 1}</strong>
          <button type="button" data-lb-remove aria-label="로어북 삭제" class="grid h-8 w-8 place-items-center rounded-lg bg-[#fff0ee] text-[#b64b42]">${icon('trash', 15)}</button></div>
        <label class="block"><span class="mb-2 block text-[10px] leading-4 text-[#7e8680]">키워드를 #으로 구분해 입력해주세요. 띄어쓰기 시 #이 자동 생성됩니다.</span>
          <input type="text" data-lb-keywords value="${esc(lb.keywords)}" placeholder="#견적 #비용 #가격" class="h-11 w-full rounded-xl border border-[#dfe4df] bg-[#f7f8f7] px-3 text-sm outline-none focus:border-[#89a18b]"></label>
        <label class="mt-4 block"><span class="mb-2 block text-[10px] leading-4 text-[#7e8680]">키워드 언급 시 고객에게 전달할 내용을 입력해주세요. (글자 수 제한 없음)</span>
          <textarea data-lb-content placeholder="고객에게 전달할 답변을 입력하세요" class="h-28 w-full resize-none rounded-xl border border-[#dfe4df] bg-[#f7f8f7] p-3 text-sm leading-5 outline-none focus:border-[#89a18b]">${esc(lb.content)}</textarea></label>
      </section>`;
    }
    function renumber() { $$('[data-lb-title]', lbBox).forEach((el, i) => { el.textContent = `로어북 ${i + 1}`; }); }
    function openEditor(id) {
      editing = id || 'new';
      const t = id ? topics.find((x) => x.id === id) : { title: '', introduction: '', lorebooks: [{ keywords: '', content: '' }] };
      $('[data-editor-title]', editor).textContent = id ? '답변 수정' : '답변 등록';
      saveBtn.textContent = id ? '수정' : '등록';
      titleInput.value = t.title;
      introInput.value = t.introduction;
      lbBox.innerHTML = t.lorebooks.map(lorebookHtml).join('');
      syncSave();
      open(editor);
    }
    $('[data-topic-new]', answersRoot).addEventListener('click', () => openEditor());
    titleInput.addEventListener('input', syncSave);
    introInput.addEventListener('input', syncSave);
    $('[data-lorebook-add]', editor).addEventListener('click', () => {
      lbBox.insertAdjacentHTML('beforeend', lorebookHtml({ keywords: '', content: '' }, lbBox.children.length));
    });
    lbBox.addEventListener('click', (e) => {
      const rm = e.target.closest('[data-lb-remove]');
      if (rm) { rm.closest('[data-lb]').remove(); renumber(); }
    });
    // 키워드: 띄어쓰기 시 # 자동 생성
    lbBox.addEventListener('keydown', (e) => {
      if (e.key !== ' ' || !e.target.matches('[data-lb-keywords]')) return;
      e.preventDefault();
      const el = e.target;
      const start = el.selectionStart;
      const before = el.value.slice(0, start).trimEnd();
      const after = el.value.slice(el.selectionEnd).trimStart();
      const sep = before.endsWith('#') ? ' ' : ' #';
      el.value = `${before}${sep}${after}`;
      el.setSelectionRange(before.length + sep.length, before.length + sep.length);
    });
    saveBtn.addEventListener('click', () => {
      if (saveBtn.disabled) return;
      const saved = {
        id: editing === 'new' ? Date.now() : editing,
        title: titleInput.value.trim(),
        introduction: introInput.value.trim(),
        lorebooks: $$('[data-lb]', lbBox).map((s) => ({ keywords: $('[data-lb-keywords]', s).value.trim(), content: $('[data-lb-content]', s).value.trim() }))
          .filter((lb) => lb.keywords || lb.content),
      };
      topics = editing === 'new' ? [saved, ...topics] : topics.map((t) => (t.id === saved.id ? saved : t));
      expanded = saved.id;
      close(editor);
      render();
    });

    render();
    if (state === 'register') openEditor();
    if (state === 'edit') openEditor(topics[0].id);
    if (state === 'delete') { deleting = topics[0].id; open($('#topicDeleteModal')); }
    if (state === 'empty') { queryInput.value = '없는주제'; render(); }
  }
})();
