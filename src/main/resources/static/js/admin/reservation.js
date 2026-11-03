/*
 * SCR-A-011 예약 관리 (templates/admin/reservation/list.html) — 더미 데이터
 *  분류 필터(전체 · 예약 완료 · 예약 취소 — 이사 완료는 예약 완료에 포함) · 검색(이름·휴대폰·예약 번호·예약일)
 *  페이지(8건, 예약일 내림차순) · 상세 팝업(상태·금액·결제·취소 정보) · [결제 완료] → 결제 상태·결제일 변경(A→B) · 분류별 현황
 *  [일정 변경] 예약일 입력 + 예약 시간은 고정 시간대(오전 8:00 · 10:00 / 오후 1:00 · 3:00 · 5:00) 중 선택
 */
(function () {
  const root = document.getElementById('reservationList');
  if (!root) return;
  const $ = (sel, el = document) => el.querySelector(sel);
  const $$ = (sel, el = document) => [...el.querySelectorAll(sel)];
  const layer = window.CodeMoveAdmin || { open: (el) => { el.hidden = false; }, close: (el) => { el.hidden = true; } };
  const PER_PAGE = 8;
  const TODAY = '26.09.28'; // 더미 기준일 ([결제 완료] 시 계약금 결제일)
  const STATUS_CHIP = { '예약 완료': 'chip-done', '이사 완료': 'chip-done', '예약 취소': 'chip-cancel' };
  const PAY_CHIP = { '결제 대기': 'chip-review', '결제 완료': 'chip-done', '결제 취소': 'chip-cancel' };

  const box = $('#reservationRows');
  const filterBox = $('#reservationFilter');
  const searchBar = $('#reservationSearchBar');
  const input = $('#reservationSearchInput');
  const toggle = $('#reservationSearchToggle');
  const detail = $('#reservationDetail');
  let status = $('[data-filter][aria-selected="true"]', filterBox).dataset.filter;
  let query = input.value;
  let page = 1;
  let selected = null;

  const rows = () => $$('[data-row]', box);
  const listStatus = (r) => (r.dataset.status === '예약 취소' ? '예약 취소' : '예약 완료');
  const setChip = (el, map, value) => { el.className = `chip ${map[value]}`; el.textContent = value; };
  const digits = (v) => v.replace(/\D/g, '');
  const won = (n) => `${Math.round(n).toLocaleString('ko-KR')}원`;
  const no = (r) => String(r.dataset.row).padStart(4, '0');
  // 예약 번호는 생성 시점의 이사일 기준 — 일정 변경 후에도 그대로 (처음 계산한 값을 보관)
  const reservationNo = (r) => (r.dataset.rsvNo ||= `ME-20${digits(r.dataset.movingDate)}-${no(r)}`);
  const estimateNo = (r) => `QT-20${digits(r.dataset.applied)}-${no(r)}`;       // 견적일 기준 (더미: 신청일)

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


  // 검색: 'ME-' → 예약 번호 / 010 으로 시작하는 7자리 이상 → 휴대폰 번호 / 날짜 형태 → 예약일(MOVE_DATE) / 그 외 → 이름
  const isPhone = (q) => /^[\d\s-]+$/.test(q) && digits(q).startsWith('010') && digits(q).length >= 7;
  function matches(r) {
    const q = query.trim();
    if (!q) return true;
    if (/^me-?/i.test(q)) return reservationNo(r).toLowerCase().includes(q.toLowerCase());
    if (isPhone(q)) return digits(r.dataset.phone).includes(digits(q));
    const byDate = dateMatcher(q);
    return byDate ? byDate(r.dataset.movingDate) : r.dataset.name.includes(q);
  }

  function render() {
    const matched = rows()
      .filter((r) => status === '전체' || listStatus(r) === status)
      .filter(matches)
      .sort((a, b) => b.dataset.movingDate.localeCompare(a.dataset.movingDate));
    const pages = Math.max(1, Math.ceil(matched.length / PER_PAGE));
    page = Math.min(page, pages);
    rows().forEach((r) => { r.hidden = true; });
    matched.slice((page - 1) * PER_PAGE, page * PER_PAGE).forEach((r) => {
      box.insertBefore(r, $('#reservationEmpty'));
      r.hidden = false;
    });
    $('#reservationEmpty').hidden = matched.length > 0;

    const pager = $('#reservationPager');
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

    const all = rows();
    $$('[data-stat]').forEach((s) => {
      const count = all.filter((r) => listStatus(r) === s.dataset.stat).length;
      const pct = all.length ? Math.round((count / all.length) * 100) : 0;
      $('[data-stat-text]', s).textContent = `${count}건 (${pct}%)`;
      $('[data-stat-bar]', s).style.width = `${pct}%`;
    });
  }

  function renderPayment(row) {
    const d = row.dataset;
    setChip($('[data-d="paymentBadge"]', detail), PAY_CHIP, d.payment);
    const paid = d.paidAt && d.paidAt !== '-';
    $$('[data-paid-row]', detail).forEach((el) => { el.hidden = !paid; });
    $('[data-d="paidAt"]', detail).textContent = paid ? d.paidAt : '';
    $('#paymentComplete').hidden = d.payment !== '결제 대기';
  }

  function openDetail(row) {
    selected = row;
    const d = row.dataset;
    const total = Number(d.total);
    const deposit = Math.round(total * 0.1);
    const set = (key, value) => { $(`[data-d="${key}"]`, detail).textContent = value; };
    setChip($('[data-d="status"]', detail), STATUS_CHIP, d.status); // 상세에서만 이사 완료 구분
    set('applied', d.applied);
    set('member', `${d.name} · ${d.phone}`);
    set('estimateNo', estimateNo(row));
    set('reservationNo', reservationNo(row));
    set('service', d.service);
    set('pack', d.pack);
    set('movingDate', d.movingDate);
    set('movingTime', d.movingTime);
    set('origin', d.origin);
    set('destination', d.destination);
    set('memo', d.memo === '-' ? '-' : d.memo);
    set('total', won(total));
    set('deposit', won(deposit));
    set('balance', won(total - deposit));
    const cancelled = d.status === '예약 취소';
    $('[data-cancel-box]', detail).hidden = !cancelled;
    if (cancelled) { set('cancelledAt', d.cancelledAt); set('cancelledBy', d.cancelledBy); }
    $('[data-actions]', detail).hidden = d.status !== '예약 완료'; // 이사 완료·예약 취소는 변경 불가
    renderPayment(row);
    layer.open(detail);
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
    selected.dataset.paidAt = TODAY;
    renderPayment(selected);
  });

  filterBox.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-filter]');
    if (!btn) return;
    status = btn.dataset.filter;
    $$('[data-filter]', filterBox).forEach((b) => b.setAttribute('aria-selected', String(b === btn)));
    page = 1;
    render();
  });

  const setSearch = (open) => {
    searchBar.hidden = !open;
    toggle.setAttribute('aria-expanded', String(open));
    if (open) input.focus();
    else if (query) { input.value = ''; query = ''; page = 1; render(); }
  };
  toggle.addEventListener('click', () => setSearch(searchBar.hidden));
  input.addEventListener('input', () => { query = input.value; page = 1; render(); });
  $('#reservationSearchClear').addEventListener('click', () => {
    if (input.value) { input.value = ''; query = ''; page = 1; render(); input.focus(); } else setSearch(false);
  });

  /* ---------- 예약 상태 변경 (예약 완료 건만) ---------- */
  const toast = (title, message) => {
    if (window.AdminToast) window.AdminToast.show({ title, message, href: '/admin/reservations' });
  };
  // YY.MM.DD 자동 점 (견적 관리 검색과 같은 방식)
  function formatShortDate(value, deleting) {
    const d = value.replace(/\D/g, '').slice(0, 6);
    if (d.length >= 5) return `${d.slice(0, 2)}.${d.slice(2, 4)}.${d.slice(4)}`;
    if (d.length >= 3) return `${d.slice(0, 2)}.${d.slice(2)}${d.length === 4 && !deleting ? '.' : ''}`;
    if (d.length === 2 && !deleting) return `${d}.`;
    return d;
  }
  const validDate = (v) => {
    const m = /^(\d{2})\.(\d{2})\.(\d{2})$/.exec(v);
    if (!m) return false;
    const dt = new Date(2000 + Number(m[1]), Number(m[2]) - 1, Number(m[3]));
    return dt.getMonth() === Number(m[2]) - 1 && dt.getDate() === Number(m[3]) && v >= TODAY; // 오늘 이후만
  };

  // 예약 시간: 고정 시간대(08:00 · 10:00 · 13:00 · 15:00 · 17:00) 중 하나를 고름
  const reschedule = $('#rescheduleDialog');
  const dateInput = $('#rescheduleDate');
  const timeBox = $('#rescheduleTime');
  const saveBtn = $('#rescheduleSave');
  let pickedTime = '';
  const setTime = (v) => {
    pickedTime = v;
    $$('[data-time]', timeBox).forEach((b) => b.setAttribute('aria-checked', String(b.dataset.time === v)));
  };
  const validate = () => {
    const changed = selected && (dateInput.value !== selected.dataset.movingDate || pickedTime !== selected.dataset.movingTime);
    saveBtn.disabled = !(validDate(dateInput.value) && pickedTime && changed);
  };
  dateInput.addEventListener('input', (e) => { dateInput.value = formatShortDate(dateInput.value, (e.inputType || '').startsWith('delete')); validate(); });
  timeBox.addEventListener('click', (e) => {
    const b = e.target.closest('[data-time]');
    if (b) { setTime(b.dataset.time); validate(); }
  });
  function openReschedule() {
    if (!selected) return;
    dateInput.value = selected.dataset.movingDate;
    setTime(selected.dataset.movingTime);
    validate();
    layer.open(reschedule);
  }
  $('#rescheduleOpen').addEventListener('click', openReschedule);
  $('#rescheduleForm').addEventListener('submit', (e) => {
    e.preventDefault();
    if (saveBtn.disabled || !selected) return;
    reservationNo(selected); // 번호 고정
    selected.dataset.movingDate = dateInput.value;
    selected.dataset.movingTime = pickedTime;
    $('[data-date-cell]', selected).textContent = dateInput.value;
    $('[data-d="movingDate"]', detail).textContent = dateInput.value;
    $('[data-d="movingTime"]', detail).textContent = pickedTime;
    layer.close(reschedule);
    render(); // 예약일 정렬 유지
    toast('일정 변경 알림', '일정이 변경되었습니다');
  });

  const cancelConfirm = $('#reservationCancelConfirm');
  $('#reservationCancelOpen').addEventListener('click', () => layer.open(cancelConfirm));
  $('#reservationCancelOk').addEventListener('click', () => {
    if (!selected) return;
    const d = selected.dataset;
    d.status = '예약 취소';
    d.cancelledAt = TODAY;
    d.cancelledBy = '관리자';                              // CANCELLED_BY 02
    if (d.payment === '결제 대기') d.payment = '결제 취소'; // PAY_STATUS 03
    setChip($('[data-list-chip]', selected), STATUS_CHIP, '예약 취소');
    layer.close(cancelConfirm);
    openDetail(selected); // 취소 박스 표시 · 버튼 숨김
    render();
    toast('예약 취소 알림', `${d.name} 고객님의 예약이 취소되었습니다.`);
  });

  // 상태 미리보기
  const s = root.dataset.state;
  rows().forEach(reservationNo); // 예약 번호 고정
  // 대시보드 등에서 ?status=done|cancel 로 들어오면 그 분류 필터를 선택
  const preset = { done: '예약 완료', cancel: '예약 취소' }[new URLSearchParams(location.search).get('status')];
  if (preset) $(`[data-filter="${preset}"]`, filterBox)?.click();
  render();
  if (s === 'search' || s === 'empty') setSearch(true);
  if (s === 'detail' || s === 'paid') {
    const row = rows().find((r) => r.dataset.row === '6');
    if (s === 'paid') { row.dataset.payment = '결제 완료'; row.dataset.paidAt = TODAY; }
    openDetail(row);
  }
  if (s === 'cancel-detail') openDetail(rows().find((r) => r.dataset.row === '7'));
  if (s === 'done-detail') openDetail(rows().find((r) => r.dataset.row === '21'));
  if (s === 'reschedule' || s === 'cancel-confirm') {
    openDetail(rows().find((r) => r.dataset.row === '6'));
    if (s === 'reschedule') openReschedule(); else layer.open(cancelConfirm);
  }
})();
