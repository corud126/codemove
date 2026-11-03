/*
 * SCR-A-012 리뷰 관리 (templates/admin/review/list.html) — 더미 데이터
 *  별점·답변 드롭다운(건수 갱신) · 내용 3줄 넘치면 [더보기] · 사진 크게 보기
 *  [답변 달기] → 입력 → [답변 등록](답변일 기록·답변 대기 칩 제거, 고객 알림 없음 — 사용자 앱 후기 목록에 표시) / [취소] · 답변 아래 [수정 | 삭제] → 입력창 [취소][저장] / 삭제 확인 후 답변 대기
 */
(function () {
  const root = document.getElementById('reviewList');
  if (!root) return;
  const $ = (sel, el = document) => el.querySelector(sel);
  const $$ = (sel, el = document) => [...el.querySelectorAll(sel)];
  const layer = window.CodeMoveAdmin || { open: (el) => { el.hidden = false; }, close: (el) => { el.hidden = true; } };

  const rating = $('#ratingFilter');
  const reply = $('#replyFilter');
  const cards = () => $$('[data-review]');

  // 글자 드롭다운: 숨긴 select 의 옵션으로 메뉴를 만들고, 고르면 select 값을 바꿔 change 를 보냄
  $$('[data-dropdown]', root).forEach((box) => {
    const select = $('select', box);
    const trigger = $('[data-dd-trigger]', box);
    const menu = $('[data-dd-menu]', box);
    const label = $('[data-dd-label]', box);
    const close = () => { menu.hidden = true; trigger.setAttribute('aria-expanded', 'false'); };
    const render = () => {
      label.textContent = select.selectedOptions[0].textContent;
      menu.innerHTML = '';
      [...select.options].forEach((o) => {
        const on = o.selected;
        const li = document.createElement('li');
        li.innerHTML = `<button type="button" role="option" aria-selected="${on}" class="flex h-10 w-full items-center justify-between gap-3 rounded-[10px] px-3 text-[15px] ${on ? 'font-semibold text-[#346538]' : 'text-[#292929] hover:bg-[#f2f3f1]'}">
          ${o.textContent}${on ? '<svg class="icon" width="16" height="16"><use href="/images/admin/icons-v2.svg#i-check"/></svg>' : ''}</button>`;
        li.firstElementChild.addEventListener('click', () => {
          select.value = o.value;
          select.dispatchEvent(new Event('change'));
          render();
          close();
        });
        menu.appendChild(li);
      });
    };
    trigger.addEventListener('click', () => {
      const open = menu.hidden;
      $$('[data-dd-menu]', root).forEach((m) => { m.hidden = true; });
      menu.hidden = !open;
      trigger.setAttribute('aria-expanded', String(open));
    });
    document.addEventListener('click', (e) => { if (!box.contains(e.target)) close(); });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });
    render();
  });

  function applyFilter() {
    let shown = 0;
    cards().forEach((card) => {
      const answered = card.dataset.answered === 'Y';
      const match = (rating.value === '전체' || card.dataset.rating === rating.value)
        && (reply.value === '전체' || (reply.value === '대기' ? !answered : answered));
      card.hidden = !match;
      if (match) shown += 1;
    });
    $('#reviewCount').textContent = shown;
    $('#reviewEmpty').hidden = shown > 0;
    updateMore();
  }
  rating.addEventListener('change', applyFilter);
  reply.addEventListener('change', applyFilter);

  // 내용이 3줄을 넘는 카드에만 [더보기]
  function updateMore() {
    cards().forEach((card) => {
      if (card.hidden) return;
      const p = $('[data-content]', card);
      const more = $('[data-more]', card);
      if (more.dataset.open === 'Y') return;
      more.hidden = p.scrollHeight <= p.clientHeight + 1;
    });
  }

  function closeReply(card) {
    $('[data-reply-form]', card).hidden = true;
    $('[data-reply-input]', card).value = '';
    $('[data-reply-open]', card).hidden = false;
  }

  $('#reviewCards').addEventListener('click', (e) => {
    const card = e.target.closest('[data-review]');
    if (!card) return;

    const more = e.target.closest('[data-more]');
    if (more) {
      const p = $('[data-content]', card);
      const open = more.dataset.open !== 'Y';
      more.dataset.open = open ? 'Y' : 'N';
      p.classList.toggle('line-clamp-3', !open);
      more.textContent = open ? '접기' : '더보기';
      return;
    }
    const photo = e.target.closest('[data-photo]');
    if (photo) {
      const img = $('#photoViewerImg');
      img.src = $('img', photo).src;
      img.style.objectPosition = photo.dataset.pos;
      layer.open('#photoViewer');
      return;
    }
    if (e.target.closest('[data-reply-open]')) {
      cards().forEach((c) => { if (c !== card && !$('[data-reply-form]', c).hidden) closeReply(c); });
      $('[data-reply-open]', card).hidden = true;
      $('[data-reply-form]', card).hidden = false;
      $('[data-reply-input]', card).focus();
      return;
    }
    if (e.target.closest('[data-reply-cancel]')) { closeReply(card); return; }
    if (e.target.closest('[data-reply-submit]')) {
      const text = $('[data-reply-input]', card).value.trim();
      if (!text) { $('[data-reply-input]', card).focus(); return; }
      const answer = $('[data-answer]', card);
      $('[data-answer-text]', answer).textContent = text;
      $('[data-answer-input]', answer).value = text;
      answer.hidden = false;
      $('[data-reply-form]', card).hidden = true;
      $('[data-pending-chip]', card).hidden = true;
      card.dataset.answered = 'Y';
      return;
    }
    // 답변 수정: [수정] → 입력창 / [취소] 원래 글로 / [저장] 반영
    const answer = $('[data-answer]', card);
    const text = $('[data-answer-text]', answer);
    const input = $('[data-answer-input]', answer);
    if (e.target.closest('[data-answer-edit]')) {
      input.value = text.textContent.trim();
      countAnswer(answer);
      answer.classList.add('is-editing');
      input.focus();
      return;
    }
    if (e.target.closest('[data-answer-cancel]')) { answer.classList.remove('is-editing'); return; }
    if (e.target.closest('[data-answer-save]')) {
      const value = input.value.trim();
      if (!value) { input.focus(); return; }
      text.textContent = value;
      answer.classList.remove('is-editing');
      return;
    }
    // 답변 삭제: 확인 창 → 답변 대기로 되돌림 (REPLY_CONTENT·REPLIED_AT 비움)
    if (e.target.closest('[data-answer-delete]')) {
      deleting = card;
      layer.open(document.getElementById('answerDelete'));
    }
  });

  const countAnswer = (answer) => {
    $('[data-answer-count]', answer).textContent = $('[data-answer-input]', answer).value.length;
  };
  root.addEventListener('input', (e) => {
    const answer = e.target.closest('[data-answer]');
    if (answer && e.target.matches('[data-answer-input]')) countAnswer(answer);
  });
  $$('[data-answer]', root).forEach(countAnswer);

  let deleting = null;
  document.getElementById('answerDeleteOk')?.addEventListener('click', () => {
    if (deleting) {
      $('[data-answer]', deleting).hidden = true;
      $('[data-pending-chip]', deleting).hidden = false;
      $('[data-reply-open]', deleting).hidden = false;
      $('[data-reply-input]', deleting).value = '';
      deleting.dataset.answered = 'N';
      deleting = null;
      applyFilter();
    }
    layer.close(document.getElementById('answerDelete'));
  });

  applyFilter();

  // 상태 미리보기: 답변 작성 카드로 스크롤
  // 미리보기: ?state=filter-menu → 별점 메뉴 열림
  if (root.dataset.state === 'filter-menu') $('[data-dd-trigger]', root).click();
  if (root.dataset.state === 'answer-delete') { deleting = $('[data-review="1"]'); layer.open(document.getElementById('answerDelete')); }
  if (root.dataset.state === 'reply') {
    const card = $('[data-review="2"]');
    const scroller = root.closest('.overflow-y-auto');
    if (card && scroller) scroller.scrollTop = card.getBoundingClientRect().top - scroller.getBoundingClientRect().top - 16;
    if (card) $('[data-reply-input]', card).focus({ preventScroll: true });
  }
})();
