/*
 * 내 이사 · 견적 상세 · 예약 상세 동작
 *  [data-doc-viewer]          견적서 원본 뷰어 (100% = 화면 너비에 맞춤, 최대 300%)
 *                             두 손가락 핀치 · Ctrl+휠 · 더블탭으로 확대, 한 손가락/마우스로 끌어 이동
 *  data-zoom="fit"            맞춤(100%)으로 되돌리기          ?doc=open&zoom=150 → 확대 상태로 열기
 *  data-doc-download          견적서 PDF 다운로드 (DB 연동 단계에서 실제 파일로 연결)
 *  data-accordion="#id"       결제 금액 펼침/접기 (토글 버튼 svg 에 .open)
 *  data-request-edit          요청사항 수정 ↔ 저장
 */
(function () {
  const DOC_WIDTH = 580, ZOOM_MIN = 100, ZOOM_MAX = 300;

  function toast(message) {
    let el = document.querySelector('.save-toast');
    if (!el) {
      el = document.createElement('div');
      el.className = 'save-toast';
      document.body.appendChild(el);
    }
    el.textContent = message;
    el.hidden = false;
    clearTimeout(el._timer);
    el._timer = setTimeout(() => { el.hidden = true; }, 2200);
  }

  // ── 견적서 원본 뷰어 ──
  document.querySelectorAll('[data-doc-viewer]').forEach((body) => {
    const sheet = body.closest('.ed-doc-sheet');
    const doc = body.querySelector('.vendor-estimate');
    const pill = sheet.querySelector('.ed-doc-zoom');
    const value = sheet.querySelector('[data-zoom-value]');
    const padTop = () => parseFloat(getComputedStyle(body).paddingTop) || 0;
    let zoom = ZOOM_MIN;

    const scale = (z) => (body.clientWidth / DOC_WIDTH) * (z / 100);

    // 확대 기준점(fx, fy: body 기준 좌표)이 화면에서 제자리에 있도록 스크롤 보정
    function setZoom(next, fx, fy) {
      next = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, next));
      if (!body.clientWidth) { zoom = next; return; }
      const before = scale(zoom), after = scale(next), top = padTop();
      if (fx == null) { fx = body.clientWidth / 2; fy = body.clientHeight / 2; }
      const docX = (body.scrollLeft + fx) / before;
      const docY = (body.scrollTop + fy - top) / before;
      zoom = next;
      render();
      body.scrollLeft = docX * after - fx;
      body.scrollTop = docY * after + top - fy;
    }

    function render() {
      doc.style.setProperty('--quote-zoom', scale(zoom).toFixed(4));
      const zoomed = Math.round(zoom) !== ZOOM_MIN;
      pill.hidden = !zoomed;
      value.textContent = Math.round(zoom) + '%';
      sheet.classList.toggle('is-zoomed', zoomed);
    }

    // 레이어가 열리거나 프레임 크기가 바뀌면 맞춤 배율을 다시 계산
    new ResizeObserver(() => { if (body.clientWidth) render(); }).observe(body);

    const pointers = new Map();
    let drag = null, pinch = null;
    const local = (x, y) => { const r = body.getBoundingClientRect(); return [x - r.left, y - r.top]; };
    const startDrag = (p) => { drag = { x: p.x, y: p.y, left: body.scrollLeft, top: body.scrollTop }; };

    body.addEventListener('pointerdown', (e) => {
      body.setPointerCapture(e.pointerId);
      pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (pointers.size === 2) {
        const [a, b] = [...pointers.values()];
        pinch = { dist: Math.hypot(a.x - b.x, a.y - b.y) || 1, zoom };
        drag = null;
      } else if (pointers.size === 1) {
        startDrag(pointers.get(e.pointerId));
        body.classList.add('is-dragging');
      }
    });
    body.addEventListener('pointermove', (e) => {
      if (!pointers.has(e.pointerId)) return;
      pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (pinch && pointers.size >= 2) {
        const [a, b] = [...pointers.values()];
        const [mx, my] = local((a.x + b.x) / 2, (a.y + b.y) / 2);
        setZoom(pinch.zoom * Math.hypot(a.x - b.x, a.y - b.y) / pinch.dist, mx, my);
      } else if (drag) {
        body.scrollLeft = drag.left - (e.clientX - drag.x);
        body.scrollTop = drag.top - (e.clientY - drag.y);
      }
    });
    const end = (e) => {
      pointers.delete(e.pointerId);
      if (pointers.size < 2) pinch = null;
      if (pointers.size === 1) startDrag([...pointers.values()][0]);
      if (!pointers.size) { drag = null; body.classList.remove('is-dragging'); }
    };
    body.addEventListener('pointerup', end);
    body.addEventListener('pointercancel', end);

    body.addEventListener('wheel', (e) => {
      if (!e.ctrlKey) return;
      e.preventDefault();
      const [fx, fy] = local(e.clientX, e.clientY);
      setZoom(zoom * Math.exp(-e.deltaY * 0.002), fx, fy);
    }, { passive: false });

    body.addEventListener('dblclick', (e) => {
      const [fx, fy] = local(e.clientX, e.clientY);
      setZoom(Math.round(zoom) === ZOOM_MIN ? 150 : ZOOM_MIN, fx, fy);
    });

    sheet.querySelector('[data-zoom="fit"]').addEventListener('click', () => {
      setZoom(ZOOM_MIN);
      body.scrollLeft = 0;
      body.scrollTop = 0;
    });

    // ?zoom=150 : 확대 상태로 열기 (시안처럼 문서 오른쪽 위 영역을 보여줌)
    const initial = Number(new URLSearchParams(location.search).get('zoom'));
    if (initial > ZOOM_MIN) {
      zoom = Math.min(ZOOM_MAX, initial);
      requestAnimationFrame(() => {
        render();
        body.scrollLeft = (body.scrollWidth - body.clientWidth) * 0.85;
        body.scrollTop = padTop() + 58 * scale(zoom);
      });
    }
    render();
  });

  document.addEventListener('click', (e) => {
    if (e.target.closest('[data-doc-download]')) {
      toast('견적서 PDF 다운로드는 준비 중이에요.');
      return;
    }

    const acc = e.target.closest('[data-accordion]');
    if (acc) {
      const panel = document.querySelector(acc.dataset.accordion);
      if (!panel) return;
      panel.hidden = !panel.hidden;
      acc.setAttribute('aria-expanded', String(!panel.hidden));
      const icon = acc.querySelector('svg');
      if (icon) icon.classList.toggle('open', !panel.hidden);
      return;
    }

    const edit = e.target.closest('[data-request-edit]');
    if (edit) {
      const card = document.querySelector(edit.dataset.requestEdit);
      if (!card) return;
      const editing = card.classList.toggle('editing');
      card.contentEditable = editing ? 'true' : 'false';
      card.setAttribute('role', editing ? 'textbox' : '');
      edit.textContent = editing ? '저장' : '수정';
      if (editing) card.focus();
      else toast('요청사항을 저장했어요.');
    }
  });

  // 요청사항 편집 상태로 시작한 경우 (?edit=true)
  document.querySelectorAll('.reservation-request-card.editing').forEach((card) => { card.contentEditable = 'true'; });
})();
