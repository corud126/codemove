/*
 * 관리자 공통 레이어 (SCR-A-017 전체 메뉴 · SCR-A-018 메뉴 검색 · SCR-A-019 알림 패널/상단 팝업)
 * 열고 닫기는 admin.js(data-open / data-close)가 담당하고, 여기서는 레이어 내부 동작만 처리한다.
 *  - 전체 메뉴: 하단 탭 '전체'의 화면, 열려 있는 동안 '전체' 탭 선택 표시
 *  - 메뉴 검색: 이름 또는 초성(예: ㅎㅇ) 검색 + 일치 부분 강조, 최근 검색(localStorage), 결과 없음
 *  - 알림 패널: 전체·안 읽음 탭, 행 클릭 시 읽음 처리, 모두 읽음, 빈 상태
 *  - 상단 팝업 알림: window.AdminToast.show({ title, message, href })
 *  - 상태 확인용 ?overlay=menu|search|search-initial|search-query|search-empty|notify|notify-unread|notify-read|notify-empty|toast
 */
(function () {
  const INITIALS = 'ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ';
  const $ = (sel) => document.querySelector(sel);

  function toInitials(value) {
    return [...value].map((ch) => {
      const code = ch.charCodeAt(0) - 0xac00;
      return code >= 0 && code <= 11171 ? INITIALS[Math.floor(code / 588)] : ch;
    }).join('');
  }

  // 레이어가 열릴 때(hidden 제거) 콜백
  function onOpen(el, callback) {
    if (!el) return;
    new MutationObserver(() => { if (!el.hidden) callback(); })
      .observe(el, { attributes: true, attributeFilter: ['hidden'] });
  }

  /* ---------- SCR-A-017 전체 메뉴 ---------- */
  // 하단 탭 '전체'의 화면 → 열려 있는 동안 '전체' 탭을 선택 표시하고, 닫히면 원래 선택으로 되돌린다
  const menu = $('#allMenu');
  const navLinks = [...document.querySelectorAll('.admin-nav a')];
  const navClasses = navLinks.map((a) => a.className);
  function selectMenuTab(on) {
    navLinks.forEach((a, i) => {
      if (!on) { a.className = navClasses[i]; return; }
      const isMenu = a.dataset.open === '#allMenu';
      ['is-selected', 'font-semibold', 'text-[#346538]'].forEach((c) => a.classList.toggle(c, isMenu));
      a.classList.toggle('text-[#9a9f9b]', !isMenu);
    });
  }
  if (menu) {
    new MutationObserver(() => selectMenuTab(!menu.hidden))
      .observe(menu, { attributes: true, attributeFilter: ['hidden'] });
  }

  /* ---------- SCR-A-018 메뉴 검색 ---------- */
  // 이름 또는 초성(ㅎㅇ) 검색, 일치 부분 초록 강조 · 검색어가 없으면 최근 검색(localStorage, 최근 5개)
  const RECENT_KEY = 'adminMenuRecent';
  const RECENT_MAX = 5;
  const search = $('#globalSearch');
  const input = $('#globalSearchInput');
  const items = search ? [...search.querySelectorAll('[data-search-item]')] : [];
  let demoRecent = null; // ?overlay=search 미리보기용 (저장하지 않음)

  function loadRecent() {
    if (demoRecent) return demoRecent;
    try {
      const list = JSON.parse(localStorage.getItem(RECENT_KEY) || '[]');
      return Array.isArray(list) ? list : [];
    } catch (e) { return []; }
  }

  function saveRecent(list) {
    demoRecent = null;
    try { localStorage.setItem(RECENT_KEY, JSON.stringify(list)); } catch (e) { /* storage 사용 불가 시 무시 */ }
  }

  function addRecent(label) {
    saveRecent([label, ...loadRecent().filter((l) => l !== label)].slice(0, RECENT_MAX));
  }

  function renderRecent() {
    const box = $('#globalSearchRecentList');
    box.textContent = '';
    loadRecent().forEach((label) => {
      const item = items.find((el) => el.dataset.searchItem === label);
      if (!item) return; // 없어진 메뉴는 건너뜀
      const chip = document.createElement('a');
      chip.href = item.getAttribute('href');
      chip.className = 'inline-flex h-9 items-center rounded-full bg-[#f2f3f1] px-3.5 text-[14px] text-[#555]';
      chip.textContent = label;
      chip.addEventListener('click', () => addRecent(label));
      box.append(chip);
    });
    return box.children.length > 0;
  }

  // 공백을 뺀 글자 기준 위치(start ~ start+length)를 원래 이름의 글자 위치로 바꿔 강조
  function highlight(el, label, start, length) {
    const chars = [...label];
    const idx = chars.map((ch, i) => (/\s/.test(ch) ? -1 : i)).filter((i) => i >= 0);
    const on = new Set(start < 0 ? [] : idx.slice(start, start + length));
    el.textContent = '';
    chars.forEach((ch, i) => {
      if (!on.has(i)) { el.append(ch); return; }
      const b = document.createElement('b');
      b.className = 'font-semibold text-[#346538]';
      b.textContent = ch;
      el.append(b);
    });
  }

  function runSearch() {
    if (!search) return;
    const query = input.value.trim().replace(/\s/g, '');
    const initialMode = query.length > 0 && [...query].every((ch) => INITIALS.includes(ch));
    let count = 0;
    items.forEach((item) => {
      const label = item.dataset.searchItem;
      const flat = label.replace(/\s/g, '');
      let start = -1;
      if (query) start = initialMode ? (toInitials(flat).startsWith(query) ? 0 : -1) : flat.indexOf(query);
      item.hidden = start < 0;
      if (start >= 0) count += 1;
      highlight(item.querySelector('[data-search-label]'), label, start, query.length);
    });
    // 숨긴 행이 뒤에 있어도 마지막으로 보이는 행은 구분선 없음
    const visible = items.filter((item) => !item.hidden);
    items.forEach((item) => item.classList.toggle('border-none', item === visible[visible.length - 1]));
    $('#globalSearchRecent').hidden = !!query || !renderRecent();
    $('#globalSearchResults').hidden = !query || count === 0;
    $('#globalSearchEmpty').hidden = !query || count > 0;
  }

  if (search) {
    input.addEventListener('input', runSearch);
    items.forEach((item) => item.addEventListener('click', () => addRecent(item.dataset.searchItem)));
    $('#globalSearchRecentClear').addEventListener('click', () => { saveRecent([]); runSearch(); });
    // 열릴 때 검색어를 비우고 바로 입력
    onOpen(search, () => {
      input.value = '';
      runSearch();
      setTimeout(() => input.focus(), 0);
    });
  }

  /* ---------- SCR-A-019 알림 패널 ---------- */
  // 탭(전체 / 안 읽음) · 행 클릭 = 읽음 처리 후 LINK_URL 이동 · [모두 읽음] (안 읽은 알림이 없으면 비활성)
  // TODO 백엔드 연결 시 읽음 처리(IS_READ = 'Y')를 서버에 저장
  const panel = $('#notificationPanel');
  const readAll = $('#notificationReadAll');
  let notificationTab = 'all';
  let notificationsEmpty = false; // 최근 30일 알림 자체가 없는 상태

  function syncUnread() {
    if (!panel) return;
    const unread = notificationsEmpty ? 0 : panel.querySelectorAll('[data-notification][data-unread]').length;
    const countEl = $('#notificationUnreadCount');
    readAll.disabled = unread === 0;
    countEl.textContent = unread;
    countEl.hidden = unread === 0;
  }

  function applyNotifications() {
    if (!panel) return;
    syncUnread();
    panel.querySelectorAll('[data-notification-tab]').forEach((tab) => {
      tab.classList.toggle('is-active', tab.dataset.notificationTab === notificationTab);
      tab.setAttribute('aria-selected', String(tab.dataset.notificationTab === notificationTab));
    });
    let shown = 0;
    panel.querySelectorAll('[data-notification-group]').forEach((group) => {
      let rows = 0;
      group.querySelectorAll('[data-notification]').forEach((row) => {
        const show = !notificationsEmpty && (notificationTab === 'all' || row.hasAttribute('data-unread'));
        row.hidden = !show;
        if (show) rows += 1;
      });
      group.hidden = rows === 0;
      shown += rows;
    });
    $('#notificationEmpty').hidden = shown > 0;
    $('#notificationEmptyText').textContent = notificationTab === 'unread' && !notificationsEmpty
      ? '새 알림이 없습니다.' : '최근 30일 알림이 없습니다.';
  }

  function markAllRead() {
    panel?.querySelectorAll('[data-notification][data-unread]').forEach((row) => row.removeAttribute('data-unread'));
    applyNotifications();
  }

  if (panel) {
    readAll.addEventListener('click', markAllRead);
    panel.querySelectorAll('[data-notification-tab]').forEach((tab) => tab.addEventListener('click', () => {
      notificationTab = tab.dataset.notificationTab;
      applyNotifications();
    }));
    // 행 클릭: 읽음 표시 후 링크로 이동 (기본 동작 유지)
    panel.querySelectorAll('[data-notification]').forEach((row) => row.addEventListener('click', () => {
      row.removeAttribute('data-unread');
      syncUnread();
    }));
    applyNotifications();
  }

  /* ---------- 상단 팝업 알림 ---------- */
  const toast = $('#adminToast');
  const card = toast?.querySelector('[data-toast-card]');
  let toastTimer = null;
  let startX = null;
  let swiped = false;

  function hideToast() {
    clearTimeout(toastTimer);
    if (toast) toast.hidden = true;
  }

  function showToast({ title, message, href } = {}) {
    if (!toast) return;
    if (title) card.querySelector('[data-toast-title]').textContent = title;
    if (message) card.querySelector('[data-toast-message]').textContent = message;
    card.querySelector('[data-toast-time]').textContent = '지금';
    card.setAttribute('href', href || '#');
    card.style.transform = '';
    card.style.opacity = '';
    toast.hidden = false;
    // 애니메이션 재시작
    card.style.animation = 'none';
    void card.offsetWidth;
    card.style.animation = '';
    clearTimeout(toastTimer);
    toastTimer = setTimeout(hideToast, 10000);
  }

  if (card) {
    card.addEventListener('pointerdown', (e) => { startX = e.clientX; swiped = false; card.setPointerCapture(e.pointerId); });
    card.addEventListener('pointermove', (e) => {
      if (startX === null) return;
      const dx = Math.max(0, e.clientX - startX);
      if (dx > 4) swiped = true;
      card.style.transform = dx ? `translateX(${dx}px)` : '';
      card.style.opacity = dx ? String(Math.max(0, 1 - dx / 160)) : '';
    });
    const end = (e) => {
      if (startX === null) return;
      const dx = e.clientX - startX;
      startX = null;
      if (dx > 80) hideToast();
      else { card.style.transform = ''; card.style.opacity = ''; }
    };
    card.addEventListener('pointerup', end);
    card.addEventListener('pointercancel', end);
    card.addEventListener('click', (e) => {
      if (swiped || card.getAttribute('href') === '#') { e.preventDefault(); if (!swiped) hideToast(); return; }
      hideToast();
    });
  }

  window.AdminToast = { show: showToast, hide: hideToast };

  // 다른 화면에서 넘어온 팝업 (예: 견적서 등록 완료 후 견적 관리로 이동)
  try {
    const pending = sessionStorage.getItem('adminToast');
    if (pending) {
      sessionStorage.removeItem('adminToast');
      showToast(JSON.parse(pending));
    }
  } catch (e) { /* storage 사용 불가 시 무시 */ }

  /* ---------- 상태 확인용 ?overlay= ---------- */
  const overlay = new URLSearchParams(location.search).get('overlay');
  const open = (id) => { const el = document.getElementById(id); if (el) el.hidden = false; };
  switch (overlay) {
    case 'menu': open('allMenu'); break;
    // 메뉴 검색은 전체 메뉴에서 여는 화면이므로 뒤에 전체 메뉴를 함께 연다 (뒤로 = 전체 메뉴)
    case 'search':
      if (!loadRecent().length) demoRecent = ['견적 관리', '회원 관리', '일정 관리']; // 기록이 없으면 미리보기용 칩
      open('allMenu'); open('globalSearch');
      break;
    case 'search-initial':
    case 'search-query':
    case 'search-empty':
      open('allMenu'); open('globalSearch');
      setTimeout(() => {
        input.value = { 'search-initial': 'ㅎㅇ', 'search-query': '예약', 'search-empty': '없는메뉴' }[overlay];
        runSearch();
      }, 0);
      break;
    case 'notify': open('notificationPanel'); break;
    case 'notify-unread': notificationTab = 'unread'; applyNotifications(); open('notificationPanel'); break;
    case 'notify-read': markAllRead(); open('notificationPanel'); break;
    case 'notify-empty': notificationsEmpty = true; applyNotifications(); open('notificationPanel'); break;
    case 'toast':
      showToast({
        title: '견적서 등록 완료',
        message: '홍길동 고객님의 견적서가 등록되었습니다.',
        href: '/admin/estimates',
      });
      break;
    default:
  }
})();
