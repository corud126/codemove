/*
 * 사용자 앱 공통 동작 (data 속성 기반)
 *  data-open="#id"     클릭 시 대상 요소 열기 (hidden 해제)
 *  data-close          클릭 시 가장 가까운 [data-layer] 닫기 (또는 data-close="#id")
 *  data-tab="name"     같은 [data-tabs] 안에서 탭 전환, 패널은 [data-panel="name"]
 *  data-back           history.back()
 *  data-mode           일반/쉬운 모드 전환 (localStorage 유지, URL ?mode=easy|standard 우선)
 *
 * 쉬운 모드: .app-shell 에 .easy-mode, 하단 탭에 .easy-bottom-nav 가 붙는다.
 *  data-easy-only      쉬운 모드에서만 표시
 *  data-standard-only  일반 모드에서만 표시
 *  data-easy-class="x" 쉬운 모드일 때만 클래스 x 추가
 *  화면 JS는 CodeMove.mode() 로 현재 모드를 읽고, 'codemove:mode' 이벤트로 전환을 감지한다.
 */
(function () {
  const MODE_KEY = 'codemove.mode';

  function open(target) {
    const el = typeof target === 'string' ? document.querySelector(target) : target;
    if (el) el.hidden = false;
  }

  function close(target) {
    const el = typeof target === 'string' ? document.querySelector(target) : target;
    if (el) el.hidden = true;
  }

  let currentMode = 'standard';

  function applyMode(mode) {
    currentMode = mode === 'easy' ? 'easy' : 'standard';
    const easy = currentMode === 'easy';
    document.documentElement.dataset.mode = currentMode;
    document.querySelectorAll('.app-shell').forEach((shell) => shell.classList.toggle('easy-mode', easy));
    document.querySelectorAll('.bottom-nav').forEach((nav) => nav.classList.toggle('easy-bottom-nav', easy));
    document.querySelectorAll('[data-easy-class]').forEach((el) => {
      el.dataset.easyClass.split(' ').forEach((cls) => el.classList.toggle(cls, easy));
    });
    document.querySelectorAll('.mode-toggle [data-mode]').forEach((btn) => {
      btn.classList.toggle('mode-active', btn.dataset.mode === currentMode);
    });
    document.dispatchEvent(new CustomEvent('codemove:mode', { detail: currentMode }));
  }

  document.addEventListener('click', (e) => {
    const opener = e.target.closest('[data-open]');
    if (opener) {
      e.preventDefault();
      open(opener.dataset.open);
      return;
    }

    const closer = e.target.closest('[data-close]');
    if (closer) {
      e.preventDefault();
      close(closer.dataset.close || closer.closest('[data-layer]'));
      return;
    }

    const tab = e.target.closest('[data-tab]');
    if (tab) {
      const group = tab.closest('[data-tabs]');
      if (!group) return;
      group.querySelectorAll('[data-tab]').forEach((t) => t.classList.toggle('active', t === tab));
      group.querySelectorAll('[data-panel]').forEach((p) => {
        p.hidden = p.dataset.panel !== tab.dataset.tab;
      });
      return;
    }

    const back = e.target.closest('[data-back="true"]');
    if (back) {
      e.preventDefault();
      history.back();
      return;
    }

    const mode = e.target.closest('.mode-toggle [data-mode]');
    if (mode) {
      try { localStorage.setItem(MODE_KEY, mode.dataset.mode); } catch (_) { /* 저장 불가 환경 무시 */ }
      applyMode(mode.dataset.mode);
    }
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && e.target.matches('[role="button"]')) e.target.click();
    if (e.key === 'Escape') document.querySelectorAll('[data-layer]:not([hidden])').forEach(close);
  });

  let saved = 'standard';
  try { saved = localStorage.getItem(MODE_KEY) || 'standard'; } catch (_) { /* 기본값 사용 */ }
  const urlMode = new URLSearchParams(location.search).get('mode');
  if (urlMode === 'easy' || urlMode === 'standard') {
    saved = urlMode;
    try { localStorage.setItem(MODE_KEY, saved); } catch (_) { /* 저장 불가 환경 무시 */ }
  }
  applyMode(saved);

  // 실제 스크롤되는 요소: 프레임 안의 보이는 .screen (프레임 배율은 user-layout.html head 스크립트가 맞춘다)
  function scroller() {
    const screen = [...document.querySelectorAll('.app-shell > .screen')].find((el) => el.offsetParent !== null);
    return screen || document.scrollingElement;
  }

  // 확인용: ?scroll=end 로 열면 맨 아래로 스크롤
  if (new URLSearchParams(location.search).get('scroll') === 'end') {
    addEventListener('load', () => { const el = scroller(); el.scrollTop = el.scrollHeight; });
  }

  window.CodeMove = { open, close, mode: () => currentMode, scroller };
})();
