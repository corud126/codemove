/*
 * 관리자 공통 레이어 (SCR-A-017 전체 메뉴 · SCR-A-018 통합 메뉴 검색 · SCR-A-019 알림 패널/상단 팝업)
 * 열고 닫기는 admin.js(data-open / data-close)가 담당하고, 여기서는 레이어 내부 동작만 처리한다.
 *  - 전체 서비스 접기/펼치기
 *  - 메뉴 검색: 이름 또는 초성(예: ㅎㅇ) 검색, 결과 수 표시, 결과 없음
 *  - 상단 팝업 알림: window.AdminToast.show({ title, message, href })
 *  - 상태 확인용 ?overlay=menu|menu-collapsed|search|search-initial|search-empty|notify|notify-empty|toast
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
  const menuToggle = $('#allMenuToggle');
  function setMenuExpanded(expanded) {
    if (!menuToggle) return;
    menuToggle.setAttribute('aria-expanded', String(expanded));
    menuToggle.classList.toggle('border-b', expanded);
    $('#allMenuList').hidden = !expanded;
    menuToggle.querySelector('[data-chevron]').style.transform = expanded ? '' : 'rotate(-90deg)';
  }
  menuToggle?.addEventListener('click', () => setMenuExpanded(menuToggle.getAttribute('aria-expanded') !== 'true'));

  /* ---------- SCR-A-018 통합 메뉴 검색 ---------- */
  const search = $('#globalSearch');
  const input = $('#globalSearchInput');
  function runSearch() {
    const query = input.value.trim().replace(/\s/g, '');
    const initialMode = query.length > 0 && [...query].every((ch) => INITIALS.includes(ch));
    let count = 0;
    search.querySelectorAll('[data-search-item]').forEach((item) => {
      const label = item.dataset.searchItem.replace(/\s/g, '');
      const match = !query || (initialMode
        ? toInitials(label).startsWith(query)
        : label.toLowerCase().includes(query.toLowerCase()));
      item.hidden = !match;
      if (match) count += 1;
    });
    const countEl = $('#globalSearchCount');
    countEl.hidden = !query;
    countEl.textContent = `${count}개 결과`;
    $('#globalSearchEmpty').hidden = count > 0;
    $('#globalSearchHint').hidden = !!query;
  }
  input?.addEventListener('input', runSearch);
  onOpen(search, () => {
    input.value = '';
    runSearch();
    setTimeout(() => input.focus(), 0);
  });

  /* ---------- SCR-A-019 알림 패널 ---------- */
  function setNotificationsEmpty(empty) {
    document.querySelectorAll('[data-notification-group]').forEach((g) => { g.hidden = empty; });
    $('#notificationEmpty').hidden = !empty;
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
    card.querySelector('[data-toast-time]').textContent =
      new Date().toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit', hour12: true });
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
    case 'menu-collapsed': open('allMenu'); setMenuExpanded(false); break;
    case 'search': open('globalSearch'); break;
    case 'search-initial':
    case 'search-empty':
      open('globalSearch');
      setTimeout(() => { input.value = overlay === 'search-initial' ? 'ㅎㅇ' : '없는메뉴'; runSearch(); }, 0);
      break;
    case 'notify': open('notificationPanel'); break;
    case 'notify-empty': setNotificationsEmpty(true); open('notificationPanel'); break;
    case 'toast':
      showToast({
        title: '견적서 등록 완료 알림',
        message: '@회원 님의 신규 견적서가 등록되었습니다. 내용을 확인해 주세요.',
        href: '/admin/estimates',
      });
      break;
    default:
  }
})();
