/*
 * SCR-A-023 회원 관리 (시뮬레이션 — 백엔드 없음)
 *  ① 분류 필터  ② 등록(+신규 가입 알림)  ③ 검색(이름·아이디·초성)/초기화
 *  ④ 행 클릭 → 상세  ⑤ 권한 변경  ⑥ 삭제  ⑦ 페이지(4명씩)
 *  ?state=search|empty|detail|register|edit|delete 로 각 상태 바로 열기
 */
(function () {
  const page = document.getElementById('memberPage');
  if (!page) return;

  const PAGE_SIZE = 4;
  const rowsBox = document.getElementById('memberRows');
  const pager = document.getElementById('memberPager');
  const empty = document.getElementById('memberEmpty');
  const filterSel = document.getElementById('memberFilter');
  const searchBox = document.getElementById('memberSearch');
  const searchToggle = document.getElementById('memberSearchToggle');
  const queryInput = document.getElementById('memberQuery');
  const toast = document.getElementById('memberToast');
  const layers = {
    detail: document.getElementById('memberDetail'),
    register: document.getElementById('memberRegister'),
    edit: document.getElementById('memberEdit'),
    delete: document.getElementById('memberDelete'),
  };

  const INITIALS = 'ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ';
  const initials = (v) => [...v].map((c) => {
    const code = c.charCodeAt(0) - 0xac00;
    return code >= 0 && code <= 11171 ? INITIALS[Math.floor(code / 588)] : c;
  }).join('');

  const template = rowsBox.querySelector('[data-member]').cloneNode(true);
  let members = [...rowsBox.querySelectorAll('[data-member]')].map((el) => ({
    id: Number(el.dataset.id), name: el.dataset.name, userId: el.dataset.userId,
    type: el.dataset.type, phone: el.dataset.phone, joined: el.dataset.joined,
  }));
  let current = 1;
  let selected = null;

  function filtered() {
    const f = filterSel.value;
    const q = queryInput.value.trim().toLowerCase();
    return members.filter((m) => (f === '전체' || m.type === f) && (!q
      || m.name.toLowerCase().includes(q) || m.userId.toLowerCase().includes(q) || initials(m.name).includes(q)));
  }

  function rowEl(m, no) {
    const el = template.cloneNode(true);
    el.hidden = false;
    el.dataset.id = m.id;
    el.querySelector('[data-no]').textContent = no;
    const badge = el.querySelector('[data-type-badge]');
    badge.textContent = m.type;
    badge.classList.remove('bg-[#fff1e8]', 'text-[#ba6a35]', 'bg-[#edf3f8]', 'text-[#4d7599]');
    badge.classList.add(...(m.type === '관리자' ? ['bg-[#fff1e8]', 'text-[#ba6a35]'] : ['bg-[#edf3f8]', 'text-[#4d7599]']));
    el.querySelector('[data-name-text]').textContent = m.name;
    el.querySelector('[data-member-open="edit"]').setAttribute('aria-label', `${m.name} 권한 수정`);
    el.querySelector('[data-member-open="delete"]').setAttribute('aria-label', `${m.name} 삭제`);
    return el;
  }

  function render() {
    const list = filtered();
    const pageCount = Math.max(1, Math.ceil(list.length / PAGE_SIZE));
    current = Math.min(current, pageCount);
    const start = (current - 1) * PAGE_SIZE;
    rowsBox.replaceChildren(...list.slice(start, start + PAGE_SIZE).map((m, i) => rowEl(m, start + i + 1)));
    empty.hidden = list.length > 0;
    pager.hidden = pageCount <= 1;
    pager.replaceChildren(...Array.from({ length: pageCount }, (_, i) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.dataset.page = i + 1;
      b.textContent = i + 1;
      b.className = 'grid h-8 w-8 place-items-center rounded-lg text-[10px] font-bold '
        + (i + 1 === current ? 'bg-[#346538] text-white' : 'bg-[#f3f5f3] text-[#717972]');
      return b;
    }));
  }

  function fill(layer, m) {
    layer.querySelectorAll('[data-f]').forEach((el) => {
      const key = el.dataset.f;
      el.textContent = key === 'initial' ? m.name[0] : (m[key] || '-');
    });
  }

  function openLayer(type, m) {
    selected = m || null;
    const layer = layers[type];
    if (m) fill(layer, m);
    if (type === 'edit') layer.querySelectorAll('[name="memberType"]').forEach((r) => { r.checked = r.value === m.type; });
    if (type === 'register') resetRegister();
    layer.hidden = false;
  }

  function showToast(text) {
    toast.textContent = text;
    toast.hidden = false;
    clearTimeout(showToast.t);
    showToast.t = setTimeout(() => { toast.hidden = true; }, 2200);
  }

  function setSearch(open) {
    searchBox.hidden = !open;
    searchToggle.setAttribute('aria-expanded', String(open));
    searchToggle.classList.toggle('border-[#346538]', open);
    searchToggle.classList.toggle('bg-[#edf3ed]', open);
    searchToggle.classList.toggle('text-[#346538]', open);
    searchToggle.classList.toggle('border-[#dfe4df]', !open);
    searchToggle.classList.toggle('text-[#59625b]', !open);
    if (open) queryInput.focus();
  }

  // 목록 이벤트
  page.addEventListener('click', (e) => {
    const pageBtn = e.target.closest('[data-page]');
    if (pageBtn) { current = Number(pageBtn.dataset.page); render(); return; }

    const opener = e.target.closest('[data-member-open]');
    const row = e.target.closest('[data-member]');
    const m = row && members.find((x) => x.id === Number(row.dataset.id));
    if (opener) { openLayer(opener.dataset.memberOpen, m); return; }
    if (row && m) openLayer('detail', m);
  });

  filterSel.addEventListener('change', () => { current = 1; render(); });
  queryInput.addEventListener('input', () => { current = 1; render(); });
  searchToggle.addEventListener('click', () => setSearch(searchBox.hidden));
  document.getElementById('memberReset').addEventListener('click', () => {
    filterSel.value = '전체';
    queryInput.value = '';
    current = 1;
    render();
    queryInput.focus();
  });

  // C 권한 변경 저장
  document.getElementById('memberEditSave').addEventListener('click', () => {
    if (!selected) return;
    const type = layers.edit.querySelector('[name="memberType"]:checked').value;
    selected.type = type;
    layers.edit.hidden = true;
    render();
    showToast(`${selected.name} 님의 권한이 '${type}'(으)로 변경되었습니다.`);
  });

  // D 삭제
  document.getElementById('memberDeleteConfirm').addEventListener('click', () => {
    if (!selected) return;
    members = members.filter((m) => m.id !== selected.id);
    layers.delete.hidden = true;
    render();
    showToast('회원이 삭제되었습니다.');
  });

  // B 등록
  const form = document.getElementById('memberRegisterForm');
  const submit = form.querySelector('[type="submit"]');
  const typeBox = document.getElementById('memberTypeSelect');
  const typeList = typeBox.querySelector('[data-select-list]');
  const typeToggle = typeBox.querySelector('[data-select-toggle]');

  function validate() {
    submit.disabled = !['name', 'phone', 'joined'].every((k) => form.elements[k].value.trim());
  }
  function setType(v) {
    form.elements.type.value = v;
    typeBox.querySelector('[data-select-label]').textContent = v;
    typeBox.querySelectorAll('[data-select-option]').forEach((o) => {
      const on = o.dataset.selectOption === v;
      o.classList.toggle('bg-[#edf3ed]', on);
      o.classList.toggle('text-[#346538]', on);
      o.classList.toggle('text-[#4d554f]', !on);
    });
  }
  function setTypeList(open) {
    typeList.hidden = !open;
    typeToggle.setAttribute('aria-expanded', String(open));
    typeToggle.classList.toggle('border-[#346538]', open);
    typeToggle.querySelector('svg').classList.toggle('rotate-180', open);
  }
  function resetRegister() {
    form.reset();
    setType('고객');
    setTypeList(false);
    validate();
  }

  typeToggle.addEventListener('click', () => setTypeList(typeList.hidden));
  typeBox.addEventListener('click', (e) => {
    const opt = e.target.closest('[data-select-option]');
    if (opt) { setType(opt.dataset.selectOption); setTypeList(false); }
  });
  form.elements.phone.addEventListener('input', (e) => {
    const d = e.target.value.replace(/\D/g, '').slice(0, 11);
    e.target.value = d.length > 7 ? `${d.slice(0, 3)}-${d.slice(3, 7)}-${d.slice(7)}` : d.length > 3 ? `${d.slice(0, 3)}-${d.slice(3)}` : d;
  });
  form.elements.joined.addEventListener('input', (e) => {
    const d = e.target.value.replace(/\D/g, '').slice(0, 8);
    e.target.value = d.length > 6 ? `${d.slice(0, 4)}.${d.slice(4, 6)}.${d.slice(6)}` : d.length > 4 ? `${d.slice(0, 4)}.${d.slice(4)}` : d;
  });
  form.addEventListener('input', validate);
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    if (submit.disabled) return;
    const id = Math.max(0, ...members.map((m) => m.id)) + 1;
    const m = {
      id, name: form.elements.name.value.trim(), userId: `member${id}`, type: form.elements.type.value,
      phone: form.elements.phone.value.trim(), joined: form.elements.joined.value.trim(),
    };
    members.push(m);
    layers.register.hidden = true;
    filterSel.value = '전체';
    queryInput.value = '';
    current = Math.ceil(members.length / PAGE_SIZE);
    render();
    showToast(m.type === '고객' ? '회원이 등록되었습니다. 신규 가입 알림을 발송했습니다.' : '관리자가 등록되었습니다.');
  });

  // 초기 상태 (?state=)
  render();
  const state = new URLSearchParams(location.search).get('state');
  if (state === 'search') setSearch(true);
  if (state === 'empty') { setSearch(true); queryInput.value = '없는회원'; render(); }
  if (state === 'detail' || state === 'edit' || state === 'delete') openLayer(state, members[0]);
  if (state === 'register') {
    openLayer('register');
    setTypeList(true);
  }
})();
