/*
 * SCR-A-023 회원 관리 (templates/admin/member/list.html) — 더미 데이터 — 고객(TBL_MEMBER)만 조회 (ERD Q11)
 *  필터: 활성·비활성(IS_ACTIVE, 목록 맨 앞 칩) · 로그인 방식은 상세에만 · 삭제 기능 없음
 *  검색(이름·초성 / 숫자 → 휴대폰 번호·가입일) · 페이지(8명) · 행 클릭 → 상세
 */
(function () {
  const root = document.getElementById('memberList');
  if (!root) return;
  const $ = (sel, el = document) => el.querySelector(sel);
  const $$ = (sel, el = document) => [...el.querySelectorAll(sel)];
  const layer = window.CodeMoveAdmin || { open: (el) => { el.hidden = false; }, close: (el) => { el.hidden = true; } };
  const PER_PAGE = 8;
  const LOGIN = { '01': '카카오', '02': '네이버', '03': '애플' };
  const BADGE = {
    phone: '<span class="grid h-[22px] w-[22px] place-items-center rounded-full bg-white text-[#346538] ring-1 ring-[#346538]"><svg class="icon" width="12" height="12"><use href="/images/admin/icons-v2.svg#i-phone"/></svg></span>',
    '01': '<span class="grid h-[22px] w-[22px] place-items-center rounded-full bg-[#fee500]"><img alt="" class="h-3 w-3" src="/images/user/2a814.svg"></span>',
    '02': '<span class="grid h-[22px] w-[22px] place-items-center rounded-full bg-[#03c75a]"><img alt="" class="h-[10px] w-[10px]" src="/images/user/4a055.svg"></span>',
    '03': '<span class="grid h-[22px] w-[22px] place-items-center overflow-hidden rounded-full bg-black"><img alt="" class="h-[22px] w-[22px]" src="/images/user/38175.png"></span>',
  };

  const box = $('#memberRows');
  const searchBar = $('#memberSearchBar');
  const input = $('#memberSearchInput');
  const toggle = $('#memberSearchToggle');
  const detail = $('#memberDetail');
  const selectedOf = (boxEl, attr) => $(`[${attr}][aria-selected="true"]`, boxEl).getAttribute(attr);
  let active = selectedOf($('#activeFilter'), 'data-active-filter');
  let query = input.value;
  let page = 1;

  const INITIALS = 'ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ';
  const initials = (v) => [...v].map((c) => {
    const code = c.charCodeAt(0) - 0xac00;
    return code >= 0 && code <= 11171 ? INITIALS[Math.floor(code / 588)] : c;
  }).join('');
  const digits = (v) => v.replace(/\D/g, '');
  const full = (d) => (d && d !== '-' ? `20${d}` : '-');
  const socials = (r) => (r.dataset.socials === '-' ? [] : r.dataset.socials.split(';').map((s) => s.split(':')));
  const rows = () => $$('[data-row]', box);

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


  // 검색: 010 으로 시작하는 7자리 이상 → 휴대폰 번호 / 날짜 형태 → 가입일 / 그 외 → 이름·초성
  const isPhone = (q) => /^[\d\s-]+$/.test(q) && digits(q).startsWith('010') && digits(q).length >= 7;
  function matches(r) {
    if (active !== '전체' && r.dataset.active !== active) return false;
    const q = query.trim();
    if (!q) return true;
    if (isPhone(q)) return digits(r.dataset.phone).includes(digits(q));
    const byDate = dateMatcher(q);
    return byDate ? byDate(r.dataset.joined) : (r.dataset.name.includes(q) || initials(r.dataset.name).includes(q));
  }

  function render() {
    const matched = rows().filter(matches);
    const pages = Math.max(1, Math.ceil(matched.length / PER_PAGE));
    page = Math.min(page, pages);
    rows().forEach((r) => { r.hidden = true; });
    matched.slice((page - 1) * PER_PAGE, page * PER_PAGE).forEach((r) => { r.hidden = false; });
    $('#memberEmpty').hidden = matched.length > 0;

    const pager = $('#memberPager');
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
  }

  function openDetail(row) {
    const d = row.dataset;
    const withdrawn = d.withdrawn !== '-';
    const values = {
      initial: d.name[0], name: d.name, phone: d.phone,
      verified: d.verified === 'Y' ? '완료' : '미인증',
      active: d.active === 'Y' ? '활성' : (withdrawn ? '비활성 (탈퇴)' : '비활성'),
      joined: full(d.joined), terms: full(d.terms), withdrawn: full(d.withdrawn),
    };
    $$('[data-f]', detail).forEach((el) => { if (el.dataset.f in values) el.textContent = values[el.dataset.f]; });
    const vChip = $('[data-f="verifiedChip"]', detail);
    vChip.className = `chip ${d.verified === 'Y' ? 'chip-done' : 'chip-review'}`;
    vChip.textContent = d.verified === 'Y' ? '본인인증 완료' : '미인증';
    const aChip = $('[data-f="activeChip"]', detail);
    aChip.hidden = d.active === 'Y';
    aChip.textContent = withdrawn ? '탈퇴' : '비활성';
    $('[data-f-row="withdrawn"]', detail).hidden = !withdrawn;

    const list = $('[data-f="logins"]', detail);
    list.innerHTML = '';
    const items = socials(row).length ? socials(row) : [['phone', null]];
    items.forEach(([code, date]) => {
      const li = $('#loginItemTemplate').content.firstElementChild.cloneNode(true);
      $('[data-badge]', li).outerHTML = BADGE[code];
      $('[data-label]', li).textContent = code === 'phone' ? '휴대전화' : LOGIN[code];
      const dateEl = $('[data-date]', li);
      if (date) dateEl.textContent = `연결일 ${full(date)}`; else dateEl.remove();
      list.appendChild(li);
    });
    layer.open(detail);
  }

  function bindSegment(boxEl, attr, set) {
    boxEl.addEventListener('click', (e) => {
      const btn = e.target.closest(`[${attr}]`);
      if (!btn) return;
      $$(`[${attr}]`, boxEl).forEach((b) => b.setAttribute('aria-selected', String(b === btn)));
      set(btn.getAttribute(attr));
      page = 1;
      render();
    });
  }
  bindSegment($('#activeFilter'), 'data-active-filter', (v) => { active = v; });

  box.addEventListener('click', (e) => {
    const row = e.target.closest('[data-row]');
    if (!row) return;
    openDetail(row);
  });
  box.addEventListener('keydown', (e) => {
    const row = e.target.closest('[data-row]');
    if (row && e.target === row && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); openDetail(row); }
  });

  const setSearch = (open) => {
    searchBar.hidden = !open;
    toggle.setAttribute('aria-expanded', String(open));
    if (open) input.focus();
    else if (query) { input.value = ''; query = ''; page = 1; render(); }
  };
  toggle.addEventListener('click', () => setSearch(searchBar.hidden));
  input.addEventListener('input', () => { query = input.value; page = 1; render(); });
  $('#memberSearchClear').addEventListener('click', () => {
    if (input.value) { input.value = ''; query = ''; page = 1; render(); input.focus(); } else setSearch(false);
  });

  // 상태 미리보기
  const s = root.dataset.state;
  render();
  if (s === 'search' || s === 'empty') setSearch(true);
  if (s === 'detail') openDetail(rows()[0]);
  if (s === 'withdrawn') openDetail(rows().find((r) => r.dataset.row === '25'));
})();
