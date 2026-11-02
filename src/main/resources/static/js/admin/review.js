/*
 * SCR-A-012 리뷰 관리 (모의 동작)
 *  별점 필터 · [답변 달기] → 입력 → [답변 등록](알림 발송) / [취소] · 관리자 답변 연필 → 수정 → 체크로 완료
 */
(function () {
  const root = document.getElementById('reviewList');
  if (!root) return;
  const $ = (sel, el = document) => el.querySelector(sel);
  const $$ = (sel, el = document) => [...el.querySelectorAll(sel)];

  const filter = $('#ratingFilter');
  function applyFilter() {
    const value = filter.value;
    let shown = 0;
    $$('[data-review]').forEach((card) => {
      const match = value === '전체' || card.dataset.rating === value;
      card.hidden = !match;
      if (match) shown += 1;
    });
    $('#reviewEmpty').hidden = shown > 0;
  }
  filter.addEventListener('change', applyFilter);

  function closeReply(card) {
    $('[data-reply-form]', card).hidden = true;
    $('[data-reply-input]', card).value = '';
    $('[data-reply-open]', card).hidden = false;
  }

  $('#reviewCards').addEventListener('click', (e) => {
    const card = e.target.closest('[data-review]');
    if (!card) return;

    if (e.target.closest('[data-reply-open]')) {
      // 한 번에 하나의 답변 작성만 연다
      $$('[data-review]').forEach((c) => { if (c !== card && !$('[data-reply-form]', c).hidden) closeReply(c); });
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
      if (window.AdminToast) {
        window.AdminToast.show({
          title: '리뷰 답변 등록 알림',
          message: `${card.dataset.name} 고객님의 리뷰에 관리자 답변이 등록되었습니다.`,
          href: '/admin/reviews',
        });
      }
      return;
    }
    const toggle = e.target.closest('[data-answer-toggle]');
    if (toggle) {
      const answer = $('[data-answer]', card);
      const input = $('[data-answer-input]', answer);
      const editing = answer.classList.toggle('is-editing');
      toggle.setAttribute('aria-label', editing ? '답변 수정 완료' : '답변 수정');
      if (editing) input.focus();
      else $('[data-answer-text]', answer).textContent = input.value.trim() || $('[data-answer-text]', answer).textContent;
    }
  });

  // 상태 미리보기: 답변 작성 카드로 스크롤
  if (root.dataset.state === 'reply') {
    const card = $('[data-review="2"]');
    const scroller = root.closest('.overflow-y-auto');
    if (card && scroller) scroller.scrollTop = card.offsetTop - 140;
    if (card) $('[data-reply-input]', card).focus({ preventScroll: true });
  }
})();
