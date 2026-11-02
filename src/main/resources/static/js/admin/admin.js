/*
 * 관리자 공통 동작 (data 속성 기반) — 사용자 앱(js/user/app.js)과 같은 규칙
 *  data-open="#id"     대상 레이어 열기
 *  data-close          가장 가까운 [data-layer] 닫기 (또는 data-close="#id")
 *  data-tab="name"     같은 [data-tabs] 안에서 탭 전환, 패널은 [data-panel="name"]
 *  data-confirm="#id"  확인 팝업 열기 (data-open 과 동일, 의미 구분용)
 */
(function () {
  function resolve(target) {
    return typeof target === 'string' ? document.querySelector(target) : target;
  }

  function open(target) {
    const el = resolve(target);
    if (el) el.hidden = false;
  }

  function close(target) {
    const el = resolve(target);
    if (el) el.hidden = true;
  }

  document.addEventListener('click', (e) => {
    const opener = e.target.closest('[data-open], [data-confirm]');
    if (opener) {
      e.preventDefault();
      open(opener.dataset.open || opener.dataset.confirm);
      return;
    }

    const closer = e.target.closest('[data-close]');
    if (closer) {
      e.preventDefault();
      close(closer.dataset.close || closer.closest('[data-layer]'));
      return;
    }

    // 레이어 바깥(스크림) 클릭 시 닫기
    if (e.target.matches('[data-layer]')) {
      close(e.target);
      return;
    }

    const tab = e.target.closest('[data-tab]');
    if (tab) {
      const group = tab.closest('[data-tabs]');
      if (!group) return;
      group.querySelectorAll('[data-tab]').forEach((t) => t.classList.toggle('is-active', t === tab));
      group.querySelectorAll('[data-panel]').forEach((p) => {
        p.hidden = p.dataset.panel !== tab.dataset.tab;
      });
    }
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') document.querySelectorAll('[data-layer]:not([hidden])').forEach(close);
  });

  // PC(폭 480px 이상): 430 × 874 프레임이 창에 다 들어오도록 비율 유지 축소
  function fitFrame() {
    const zoom = innerWidth < 480 ? 1 : Math.min(1, (innerHeight - 48) / 874, (innerWidth - 32) / 430);
    document.documentElement.style.setProperty('--frame-zoom', zoom.toFixed(3));
  }
  fitFrame();
  addEventListener('resize', fitFrame);

  window.CodeMoveAdmin = { open, close };
})();
