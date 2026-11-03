/*
 * 관리자 상담함(admin/chat/list) · 채팅방(admin/chat/detail) — 더미 동작 SCR-A-005~007
 * 화면 상태: ?tab= · ?state= (각 템플릿 상단 주석 참고). 시트 열기/닫기는 admin.js(data-open · data-close)
 */
(function () {
  const params = new URLSearchParams(location.search);
  const state = params.get('state') || '';
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  /* ---------- 채팅함 (SCR-A-005·006) ---------- */
  const listRoot = $('[data-chat-list]');
  if (listRoot) {
    const box = $('[data-rooms]', listRoot);
    const rooms = $$('[data-name]', box);
    const TABS = { waiting: '02', active: '03', ended: '04', bot: '01' };
    const SORTS = { latest: '최신 순', unread: '안 읽은 순' };
    let tab = TABS[params.get('tab')] ? params.get('tab') : 'waiting';
    let sort = 'latest';
    let query = '';
    const menu = $('[data-menu]', listRoot);
    const menuBtn = $('[data-menu-toggle]', listRoot);
    const setMenu = (v) => { menu.hidden = !v; menuBtn.setAttribute('aria-expanded', String(v)); };
    menuBtn.addEventListener('click', (e) => { e.stopPropagation(); setMenu(menu.hidden); });
    document.addEventListener('click', (e) => { if (!menu.hidden && !menu.contains(e.target)) setMenu(false); });

    const bar = $('[data-search]', listRoot);
    const input = $('[data-search-input]', listRoot);
    const searchBtn = $('[data-search-open]', listRoot);
    const setSearch = (v) => { bar.hidden = !v; searchBtn.setAttribute('aria-expanded', String(v)); if (!v) { input.value = ''; query = ''; } render(); };
    searchBtn.addEventListener('click', () => { setSearch(bar.hidden); if (!bar.hidden) input.focus(); });
    $('[data-search-close]', listRoot).addEventListener('click', () => setSearch(false));
    input.addEventListener('input', () => { query = input.value; render(); });

    function render() {
      const q = query.trim().toLowerCase();
      const digits = q.replace(/\D/g, '');
      const searching = !!q;
      const visible = rooms.filter((r) => {
        if (searching) {
          return `${r.dataset.name} ${r.dataset.msg}`.toLowerCase().includes(q)
            || (digits.length >= 3 && r.dataset.phone.replace(/\D/g, '').includes(digits));
        }
        return r.dataset.status === TABS[tab];
      }).sort((a, b) => {
        if (!searching && tab === 'waiting') return b.dataset.wait - a.dataset.wait; // 오래 기다린 순
        return (sort === 'unread' ? b.dataset.unread - a.dataset.unread : 0) || b.dataset.activity - a.dataset.activity;
      });
      rooms.forEach((r) => {
        r.hidden = !visible.includes(r);
        const waitShown = r.dataset.status === '02' && (searching || tab === 'waiting');
        $('[data-wait-label]', r).hidden = !waitShown;
        $('[data-time]', r).hidden = waitShown;   // 'N분 대기'가 보이면 'N분 전'은 같은 말이라 숨김
      });
      visible.forEach((r) => box.appendChild(r));
      $$('[data-tab]', listRoot).forEach((b) => b.setAttribute('aria-selected', String(!searching && b.dataset.tab === tab)));
      $('[data-count-label]', listRoot).textContent = searching ? '검색 결과' : '채팅';
      $('[data-count]', listRoot).textContent = visible.length;
      const fixed = !searching && tab === 'waiting';
      $('[data-sort-fixed]', listRoot).hidden = !fixed;
      menuBtn.hidden = fixed;
      $('[data-sort-label]', listRoot).textContent = SORTS[sort];
      $$('[data-sort]', listRoot).forEach((b) => b.setAttribute('aria-checked', String(b.dataset.sort === sort)));
      $('[data-empty]', listRoot).hidden = visible.length > 0;
      $('[data-empty-text]', listRoot).textContent = searching ? '일치하는 채팅이 없습니다' : '채팅이 없습니다';
    }

    $$('[data-tab]', listRoot).forEach((b) => b.addEventListener('click', () => {
      tab = b.dataset.tab;
      if (query) setSearch(false); else render();
    }));
    $$('[data-sort]', listRoot).forEach((b) => b.addEventListener('click', () => { sort = b.dataset.sort; setMenu(false); render(); }));

    render();
    if (state === 'search') { setSearch(true); input.value = '김'; query = '김'; render(); }
    if (state === 'empty') { setSearch(true); input.value = '없는고객'; query = '없는고객'; render(); }
  }

  /* ---------- 채팅방 (SCR-A-007) ---------- */
  const detailRoot = $('[data-chat-detail]');
  if (detailRoot) {
    const ME = '김상담'; // 로그인한 관리자
    // MEMBER_ID → [이름, PHONE, 마지막 고객 메시지, STATUS, STARTED_AT, WAITING_AT, ENDED_AT, 담당 ADMIN_NAME, 진행 중인 견적·예약 있음]
    const ROOMS = {
      1015: ['홍길동', '010-2481-3375', '혹시 사다리차 추가 비용이 발생하나요?', '02', '오늘 14:02', '오늘 14:03', '', '', 1],
      1014: ['김명품', '010-7720-1184', '엘리베이터를 사용하지 못하는데요?', '02', '오늘 13:58', '오늘 14:01', '', '', 1],
      1013: ['박철수', '010-3391-0627', '네, 알겠습니다. 내일 뵙겠습니다.', '03', '오늘 15:12', '오늘 15:14', '', '김상담', 1],
      1012: ['최지우', '010-5108-4492', '에어컨 이전 설치도 같이 되나요?', '02', '오늘 13:55', '오늘 13:57', '', '', 0],
      1011: ['강민주', '010-6645-2019', '포장 박스는 언제 가져다 주시나요?', '02', '오늘 13:50', '오늘 13:53', '', '', 1],
      1010: ['이서준', '010-9012-7731', '견적서 확인했습니다.', '04', '오늘 10:02', '오늘 10:05', '오늘 10:21', '이매니저', 1],
      1009: ['윤아름', '010-4473-5580', '귀중품은 따로 챙겨야 하나요?', '02', '오늘 13:45', '오늘 13:48', '', '', 0],
      1008: ['송지호', '010-2290-6613', '이사 시작 시간은 몇 시인가요?', '01', '11.02 16:40', '', '', '', 0],
      1007: ['정현우', '010-8834-1902', '주소 변경은 어디서 하나요?', '03', '11.02 11:20', '11.02 11:22', '', '이매니저', 1],
      1006: ['한유진', '010-1157-4426', '계약금 입금 완료했습니다.', '04', '11.02 09:30', '11.02 09:31', '11.02 09:50', '김상담', 1],
      1005: ['오도현', '010-6302-8875', '방문 견적 일정을 변경하고 싶어요.', '01', '10.26 14:10', '', '', '', 0],
      1004: ['문지안', '010-7469-3308', '침대 분해와 조립도 부탁드려요.', '04', '10.26 10:00', '10.26 10:02', '10.26 10:40', '김상담', 0],
      1003: ['임지수', '010-5521-9064', '주차 공간 사진 보내드립니다.', '04', '10.25 17:20', '10.25 17:21', '10.25 17:45', '이매니저', 0],
      1002: ['김도윤', '010-3087-2251', '카드 결제가 가능한가요?', '01', '10.24 13:05', '', '', '', 0],
      1001: ['이지민', '010-9946-1738', '친절한 안내 감사합니다.', '04', '10.23 15:00', '10.23 15:02', '10.23 15:30', '김상담', 0],
    };
    // 헤더 상태 글자 (CODE_NAME)
    const STATUS = { '01': ['챗봇 응대', 'text-[#888]'], '02': ['대기 중', 'text-[#d9701a]'], '03': ['상담 중', 'text-[#346538]'], '04': ['종료', 'text-[#888]'] };
    // /admin/chats/{MEMBER_ID} (상담함 · 알림 패널 링크) — 없는 번호면 홍길동
    const memberId = location.pathname.split('/').pop();
    const room = (ROOMS[memberId] || ROOMS[1015]).slice();
    const list = $('[data-messages]', detailRoot);
    const area = $('[data-messages-area]', detailRoot);
    const waitingMsg = $('[data-waiting]', list);
    const form = $('[data-send-form]', detailRoot);
    const input = $('[data-send-input]', detailRoot);
    const sendBtn = $('[data-send-button]', detailRoot);
    const hhmm = (s) => s.replace(/^(오늘|\d+\.\d+) /, '');

    function renderRoom() {
      const st = $('[data-room-status]', detailRoot);
      st.textContent = STATUS[room[3]][0];
      st.className = `shrink-0 text-[13px] font-semibold ${STATUS[room[3]][1]}`;
      const ended = room[3] === '04';
      form.hidden = ended;
      $('[data-ended-bar]', detailRoot).hidden = !ended;
      $('[data-end-open]').hidden = ended;
      // 고객 정보 시트
      $('[data-info-initial]').textContent = room[0][0];
      $('[data-info-name]').textContent = room[0];
      $('[data-info-phone]').textContent = room[1];
      $('[data-info-tel]').href = `tel:${room[1].replace(/\D/g, '')}`;
      $('[data-info-links]').hidden = !room[8];
      $('[data-info-started]').textContent = room[4];
      $$('[data-info-waiting-row]').forEach((el) => { el.hidden = !room[5]; });
      $('[data-info-waiting]').textContent = room[5];
      $$('[data-info-ended-row]').forEach((el) => { el.hidden = !room[6]; });
      $('[data-info-ended]').textContent = room[6];
      $('[data-info-admin]').textContent = room[7] || (room[3] === '01' ? '—' : '미배정');
    }
    const clearWaiting = () => {
      if (!('waiting' in waitingMsg.dataset)) return;
      delete waitingMsg.dataset.waiting;
      waitingMsg.removeAttribute('data-swipe');
      $('[data-text]', waitingMsg).className = 'rounded-[4px_18px_18px_18px] bg-white px-[15px] py-[13px] text-[15px] leading-[1.6] text-[#444] shadow-[0_1px_3px_rgba(0,0,0,.03)]';
    };
    $('[data-room-name]', detailRoot).textContent = room[0];
    $$('[data-cust-initial]', list).forEach((el) => { el.textContent = room[0][0]; });
    $$('[data-cust-name]', list).forEach((el) => { el.textContent = room[0]; });
    $('[data-room-message]', detailRoot).textContent = room[2];
    if (room[3] !== '02') clearWaiting();
    if (!room[5]) $('[data-waiting-marker]', list).remove();
    else $('[data-waiting-marker] span', list).textContent = `상담 대기 시작 · ${hhmm(room[5])}`;
    renderRoom();

    // ⋯ 시트 안 버튼(다른 시트 열기)은 ⋯ 시트를 닫는다 (admin.js 는 열기만 처리)
    $$('#menuSheet [data-open]').forEach((b) => b.addEventListener('click', () => { $('#menuSheet').hidden = true; }));

    // 대기 메시지 왼쪽으로 밀어 답장
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
        m.style.transform = `translateX(${Math.min(0, Math.max(-60, e.clientX - startX))}px)`;
      });
      const end = (e) => {
        if (startX !== null && startX - e.clientX > 45 && 'waiting' in m.dataset) setReply($('[data-text]', m).textContent);
        startX = null;
        m.style.transform = '';
      };
      m.addEventListener('pointerup', end);
      m.addEventListener('pointercancel', end);
      m.addEventListener('pointerleave', (e) => { if (startX !== null) end(e); });
    });
    $('[data-reply-cancel]', detailRoot).addEventListener('click', () => setReply(''));

    const scrollEnd = () => area.scrollTo({ top: area.scrollHeight });
    const systemLine = (text) => {
      const el = document.createElement('div');
      el.className = 'text-center';
      el.dataset.role = 'system';
      el.innerHTML = `<span class="rounded-full bg-[#e9ebe7] px-3 py-1 text-[12px] font-medium text-[#888]">${esc(text)}</span>`;
      list.appendChild(el);
    };

    // 보내기 → 관리자 메시지 (안 읽었으면 회색 '1'), 대기 중 → 상담 중 · 담당 = 나
    // 글자를 쓰면 자주 쓰는 답변 아이콘 자리에 보내기 버튼 (카카오톡 방식)
    const syncSend = () => {
      const has = Boolean(input.value.trim());
      sendBtn.disabled = !has;
      sendBtn.hidden = !has;
      $('[data-replies-button]', detailRoot).hidden = has;
    };
    input.addEventListener('input', syncSend);
    function appendAdmin(text, replyTo, time = '14:06') {
      const el = document.createElement('div');
      el.className = 'ml-auto flex max-w-[86%] flex-col items-end gap-1.5';
      el.dataset.role = 'admin';
      el.innerHTML = `<span class="text-[12px] font-semibold text-[#555]">관리자 <span class="font-medium text-[#888]">· ${esc(ME)}</span></span>
        <div class="flex items-end gap-1.5">
          <span class="flex shrink-0 flex-col items-end text-[12px] leading-tight tabular-nums"><span class="font-semibold text-[#888]" data-unread-mark aria-label="안 읽음">1</span><time class="text-[#aaa]">${time}</time></span>
          <div class="rounded-[18px_4px_18px_18px] bg-[#346538] px-[15px] py-[13px] text-[15px] leading-[1.6] text-white">${replyTo ? `<span class="mb-2 block truncate rounded-lg border-l-2 border-[#e8e8cd] bg-white/15 px-2 py-1 text-[13px] text-white/85">${esc(replyTo)}</span>` : ''}<span data-text>${esc(text)}</span></div>
        </div>`;
      list.appendChild(el);
      clearWaiting();
      if (room[3] === '02' || room[3] === '01') { room[3] = '03'; room[7] = ME; renderRoom(); }
      scrollEnd();
      return el;
    }
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const text = input.value.trim();
      if (!text) return;
      appendAdmin(text, replyText);
      input.value = '';
      syncSend();
      setReply('');
    });

    // 자주 쓰는 답변: 검색 · 누르면 입력칸에 넣기
    const replySearch = $('[data-replies-search]');
    const replyItems = $$('[data-reply-content]');
    replySearch.addEventListener('input', () => {
      const q = replySearch.value.trim().toLowerCase();
      let shown = 0;
      replyItems.forEach((b) => {
        const hit = !q || `${$('[data-reply-title]', b).textContent} ${b.dataset.replyContent}`.toLowerCase().includes(q);
        b.hidden = !hit;
        if (hit) shown += 1;
      });
      $('[data-replies-empty]').hidden = shown > 0;
    });
    replyItems.forEach((b) => b.addEventListener('click', () => {
      input.value = b.dataset.replyContent;
      syncSend();
      $('#repliesSheet').hidden = true;
      input.focus();
    }));

    // 상담 종료 → STATUS 04 · ENDED_AT
    function endRoom(time = '14:10') {
      room[3] = '04';
      room[6] = `오늘 ${time}`;
      if (!room[7]) room[7] = ME;
      clearWaiting();
      setReply('');
      systemLine(`상담 종료 · ${time}`);
      renderRoom();
      scrollEnd();
    }
    $('[data-end-ok]').addEventListener('click', () => { $('#endConfirm').hidden = true; endRoom(); });
    if (room[3] === '04' && state !== 'ended') systemLine(`상담 종료 · ${hhmm(room[6])}`);

    const show = (id) => { $(id).hidden = false; };
    if (state === 'info') show('#infoSheet');
    if (state === 'menu') show('#menuSheet');
    if (state === 'replies') show('#repliesSheet');
    if (state === 'attach') show('#attachSheet');
    if (state === 'end-confirm') show('#endConfirm');
    if (state === 'reply') setReply(room[2]);
    if (state === 'typing') { input.value = '사다리차 비용은 층수에 따라 달라집니다.'; syncSend(); }   // 검토용: 글자를 쓰면 보내기 버튼
    if (state === 'replied') appendAdmin('사다리차 비용은 층수에 따라 달라집니다.'); // 스토리보드 B (고객 안 읽음 '1')
    if (state === 'ended') {
      const m = appendAdmin('사다리차 비용은 층수에 따라 달라집니다.');
      $('[data-unread-mark]', m).remove(); // 고객이 읽음
      endRoom();
    }
  }
})();
