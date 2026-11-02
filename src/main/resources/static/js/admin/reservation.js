/*
 * SCR-A-011 예약 관리 (모의 동작)
 *  분류 필터 · 검색(이름·휴대전화·날짜) · 페이지(4건) · 예약 상세 팝업 · [결제 완료] 시 결제 상태 변경(A→B)
 */
(function () {
  const root = document.getElementById('reservationList');
  if (!root) return;
  const $ = (sel, el = document) => el.querySelector(sel);
  const $$ = (sel, el = document) => [...el.querySelectorAll(sel)];
  const layer = window.CodeMoveAdmin;
  const PER_PAGE = 4;
  const PAY_TONE = {
    '결제 대기': ['bg-[#fff4e8]', 'text-[#ad6d32]'],
    '결제 완료': ['bg-[#edf4ed]', 'text-[#346538]'],
    '결제 취소': ['bg-[#fbefed]', 'text-[#b9574f]'],
  };
  const ALL_TONES = Object.values(PAY_TONE).flat();

  const box = $('#reservationRows');
  const filter = $('#reservationFilter');
  const query = $('#reservationQuery');
  const search = $('#reservationSearch');
  const toggle = $('#reservationSearchToggle');
  const detail = $('#reservationDetail');
  let page = 1;
  let selected = null;

  const rows = () => $$('[data-row]', box);

  function setTone(el, payment) {
    el.classList.remove(...ALL_TONES);
    el.classList.add(...PAY_TONE[payment]);
    el.textContent = payment;
  }

  function render() {
    const status = filter.value;
    const q = query.value.trim().toLowerCase();
    const qDigits = q.replace(/\D/g, '');
    const matched = rows()
      .filter((r) => status === '전체' || r.dataset.status === status)
      .filter((r) => {
        if (!q) return true;
        const d = r.dataset;
        if (`${d.name}`.toLowerCase().includes(q)) return true;
        return qDigits.length > 0 && /^[\d.\-\s]+$/.test(q)
          && (d.phone.replace(/\D/g, '').includes(qDigits) || d.applied.replace(/\D/g, '').includes(qDigits)
            || d.movingDate.replace(/\D/g, '').includes(qDigits));
      })
      .sort((a, b) => b.dataset.applied.localeCompare(a.dataset.applied));
    const pages = Math.max(1, Math.ceil(matched.length / PER_PAGE));
    page = Math.min(page, pages);
    rows().forEach((r) => { r.hidden = true; });
    matched.slice((page - 1) * PER_PAGE, page * PER_PAGE).forEach((r, i) => {
      box.insertBefore(r, $('#reservationEmpty'));
      r.hidden = false;
      $('[data-no]', r).textContent = (page - 1) * PER_PAGE + i + 1;
    });
    $('#reservationEmpty').hidden = matched.length > 0;

    const pager = $('#reservationPager');
    pager.hidden = pages <= 1;
    pager.innerHTML = '';
    for (let p = 1; p <= pages; p += 1) {
      const b = document.createElement('button');
      b.type = 'button';
      b.textContent = p;
      b.setAttribute('aria-label', `${p}페이지로 이동`);
      b.className = `grid h-8 w-8 place-items-center rounded-lg text-[10px] font-bold ${p === page ? 'bg-[#346538] text-white' : 'bg-[#f3f5f3] text-[#717972]'}`;
      b.addEventListener('click', () => { page = p; render(); });
      pager.appendChild(b);
    }
  }

  function openDetail(row) {
    selected = row;
    const d = row.dataset;
    const id = Number(d.row);
    const set = (key, value) => { $(`[data-d="${key}"]`, detail).textContent = value; };
    const status = $('[data-d="status"]', detail);
    status.textContent = d.status;
    status.className = `rounded-full px-2 py-1 text-[9px] font-bold ${d.status === '예약 취소' ? 'bg-[#fbefed] text-[#b9574f]' : 'bg-[#edf4ed] text-[#346538]'}`;
    set('applied', d.applied);
    set('member', `${d.name} · ${d.phone}`);
    set('estimateNo', `EST-${String(((id - 1) % 11) + 1).padStart(4, '0')}`);
    set('reservationNo', `RSV-${String(id).padStart(4, '0')}`);
    set('service', d.service);
    set('movingDate', d.movingDate);
    set('movingTime', d.movingTime);
    set('origin', d.origin);
    set('destination', d.destination);
    renderPayment(d.payment);
    layer.open(detail);
  }

  function renderPayment(payment) {
    setTone($('[data-d="paymentBadge"]', detail), payment);
    $('[data-d="paymentText"]', detail).textContent = `${payment}(132,000원)`;
    $('#paymentComplete').hidden = payment !== '결제 대기';
  }

  box.addEventListener('click', (e) => {
    const row = e.target.closest('[data-row]');
    if (row) openDetail(row);
  });
  box.addEventListener('keydown', (e) => {
    const row = e.target.closest('[data-row]');
    if (row && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); openDetail(row); }
  });
  $('#paymentComplete').addEventListener('click', () => {
    if (!selected) return;
    selected.dataset.payment = '결제 완료';
    setTone($('[data-pay-badge]', selected), '결제 완료');
    renderPayment('결제 완료');
  });

  filter.addEventListener('change', () => { page = 1; render(); });
  const setSearch = (open) => { search.hidden = !open; toggle.setAttribute('aria-expanded', String(open)); if (open) query.focus(); };
  toggle.addEventListener('click', () => setSearch(search.hidden));
  query.addEventListener('input', () => { page = 1; render(); });
  $('#reservationReset').addEventListener('click', () => { filter.value = '전체'; query.value = ''; page = 1; render(); });

  // 상태 미리보기
  const s = root.dataset.state;
  render();
  if (s === 'search' || s === 'empty') setSearch(true);
  if (s === 'detail' || s === 'paid') {
    const row = rows().find((r) => r.dataset.row === '6');
    if (s === 'paid') { row.dataset.payment = '결제 완료'; setTone($('[data-pay-badge]', row), '결제 완료'); }
    openDetail(row);
  }
})();
