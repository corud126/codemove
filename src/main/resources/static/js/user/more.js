/*
 * 더보기 화면 그룹 (G-H01~H14) (서버 연동: 견적내역·예약내역·후기, 미리보기 /dev 는 시뮬레이션)
 *  후기 작성: 별점, 사진 첨부(최대 5장), 등록 → POST /more/reviews (multipart) → 후기 목록(?state=done). 미리보기(/dev)는 서버 없이 시뮬레이션
 *  견적내역·예약내역 (목록은 서버 값): [data-filter-toggle] / [data-filter] 로 목록 필터, 견적 도착 카드 남은 시간 [data-countdown]
 *  내 정보 수정: 휴대폰 번호 입력 → 인증번호 발송(3분 타이머) → 6자리 입력 → 변경 (POST /more/profile/phone/send · /more/profile/phone, 미리보기는 시뮬레이션)
 *  알림: 탭(전체·내 이사), 행 클릭 시 읽음, 모두 읽음
 */
(function () {
  const $ = (sel, root) => (root || document).querySelector(sel);
  const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));
  const REVIEW_KEY = 'codemove.review.draft';

  /* ---------- 공통: 목록 필터 ---------- */
  function applyFilter(sheet, key) {
    const list = $('[data-filter-list]');
    if (!list) return;
    let visible = 0;
    $$('[data-status]', list).forEach((row) => {
      const show = key === 'all' || row.dataset.status === key;
      row.hidden = !show;
      if (show) visible++;
    });
    // 예약내역: 보이는 항목이 없는 연도 섹션 숨김
    $$('.past-reservation-year', list).forEach((sec) => {
      sec.hidden = !$$('[data-status]', sec).some((r) => !r.hidden);
    });
    const empty = $('[data-filter-empty]', list);
    if (empty) empty.hidden = visible > 0;

    $$('[data-filter]', sheet).forEach((opt) => {
      const on = opt.dataset.filter === key;
      opt.classList.toggle('selected', on);
      const mark = opt.querySelector('b, span:only-child');
      if (mark) mark.hidden = !on;
    });
    const picked = $(`[data-filter="${key}"]`, sheet);
    const label = $('[data-filter-label]');
    if (picked && label) label.textContent = (picked.querySelector('span') || picked).firstChild.textContent.trim();
  }

  function setToggleOpen(open) {
    $$('[data-filter-toggle]').forEach((t) => t.classList.toggle('open', open));
  }

  document.addEventListener('click', (e) => {
    const toggle = e.target.closest('[data-filter-toggle]');
    if (toggle) {
      const sheet = $(toggle.dataset.filterToggle);
      if (sheet) {
        sheet.hidden = !sheet.hidden;
        setToggleOpen(!sheet.hidden);
      }
      return;
    }
    const opt = e.target.closest('[data-filter]');
    if (opt) {
      const sheet = opt.closest('[data-layer]');
      applyFilter(sheet, opt.dataset.filter);
      if (sheet) sheet.hidden = true;
      setToggleOpen(false);
      return;
    }
    if (e.target.closest('[data-close]')) setToggleOpen(false);
  });

  /* ---------- 견적내역: 견적 도착 남은 시간 (24시간 이상 N일 N시간, 미만 HH:MM:SS — move.js 와 같은 규칙) ---------- */
  const pad = (n) => String(n).padStart(2, '0');
  $$('[data-countdown]').forEach((el) => {
    let left = Number(el.dataset.countdown);
    if (!el.dataset.countdown || !Number.isFinite(left)) return;
    const suffix = el.dataset.suffix || '';
    const target = el.querySelector('[data-countdown-text]') || el;
    const draw = () => {
      const text = left >= 86400
        ? `${Math.floor(left / 86400)}일 ${Math.floor((left % 86400) / 3600)}시간`
        : `${pad(Math.floor(left / 3600))}:${pad(Math.floor((left % 3600) / 60))}:${pad(left % 60)}`;
      target.textContent = `${text}${suffix}`;
      left = Math.max(0, left - 1);
    };
    draw();
    setInterval(draw, 1000);
  });

  /* ---------- 알림: 탭(전체·내 이사), 읽음 처리, 모두 읽음 ---------- */
  const notiFeed = $('#notificationFeed');
  if (notiFeed) {
    const markRead = (row) => {
      row.classList.remove('unread');
      const dot = row.querySelector(':scope > i');
      if (dot) dot.remove();
    };
    const showTab = (key) => {
      let visible = 0;
      $$('[data-noti-tab]').forEach((t) => {
        const on = t.dataset.notiTab === key;
        t.classList.toggle('active', on);
        t.setAttribute('aria-selected', String(on));
      });
      $$('.notification-row', notiFeed).forEach((row) => {
        const show = key === 'all' || row.dataset.tab === key;
        row.hidden = !show;
        if (show) visible++;
      });
      $('#notificationEmpty').hidden = visible > 0;
    };
    document.addEventListener('click', (e) => {
      const tab = e.target.closest('[data-noti-tab]');
      if (tab) { showTab(tab.dataset.notiTab); return; }
      if (e.target.closest('#readAll')) { $$('.notification-row.unread', notiFeed).forEach(markRead); return; }
      const row = e.target.closest('.notification-row');
      if (row) {
        markRead(row);
        if (row.dataset.href) location.href = row.dataset.href;
      }
    });
    const initial = new URLSearchParams(location.search).get('tab');
    if (initial === 'move') showTab(initial);
  }

  /* ---------- 내 정보 수정: 휴대폰 번호 변경 ---------- */
  const phoneSheet = $('#phoneSheet');
  if (phoneSheet) {
    const nextPhone = $('#nextPhone');
    const sendBtn = $('#sendCode');
    const codeGroup = $('#codeGroup');
    const code = $('#verifyCode');
    const timerEl = $('#codeTimer');
    const hintPhone = $('#codeHintPhone');
    const confirm = $('#confirmPhone');
    let timer = null;
    let seconds = 0;

    const formatPhone = (v) => {
      const n = v.replace(/\D/g, '').slice(0, 11);
      if (n.length <= 3) return n;
      if (n.length <= 7) return n.slice(0, 3) + '-' + n.slice(3);
      return n.slice(0, 3) + '-' + n.slice(3, 7) + '-' + n.slice(7);
    };
    const phoneReady = () => nextPhone.value.replace(/\D/g, '').length === 11;
    const update = () => {
      sendBtn.classList.toggle('active', phoneReady());
      confirm.classList.toggle('is-disabled', codeGroup.hidden || code.value.length !== 6 || seconds === 0);
    };
    const renderTimer = () => {
      const mm = String(Math.floor(seconds / 60)).padStart(2, '0');
      const ss = String(seconds % 60).padStart(2, '0');
      timerEl.textContent = seconds === 0 ? '시간 만료' : mm + ':' + ss;
      timerEl.classList.toggle('expired', seconds === 0);
    };
    const startTimer = (from) => {
      clearInterval(timer);
      seconds = from;
      renderTimer();
      timer = setInterval(() => {
        seconds = Math.max(0, seconds - 1);
        renderTimer();
        if (seconds === 0) { clearInterval(timer); update(); }
      }, 1000);
    };
    const reset = () => {
      clearInterval(timer);
      seconds = 0;
      nextPhone.value = '';
      code.value = '';
      codeGroup.hidden = true;
      sendBtn.textContent = '인증하기';
      showError('');
      update();
    };

    nextPhone.addEventListener('input', () => {
      nextPhone.value = formatPhone(nextPhone.value);
      showError('');
      codeGroup.hidden = true;
      sendBtn.textContent = '인증하기';
      code.value = '';
      clearInterval(timer);
      seconds = 0;
      update();
    });
    code.addEventListener('input', () => {
      code.value = code.value.replace(/\D/g, '').slice(0, 6);
      update();
    });
    // 서버 연동 (로그인 화면): 발송 · 확인 주소가 있을 때. 미리보기(/dev)는 시뮬레이션
    const sendUrl = phoneSheet.dataset.sendUrl;
    const changeUrl = phoneSheet.dataset.changeUrl;
    const errorEl = $('#phoneError');
    const showError = (message) => {
      if (!errorEl) return;
      errorEl.textContent = message || '';
      errorEl.hidden = !message;
    };
    async function post(url, data) {
      try {
        const res = await fetch(url, { method: 'POST', body: new URLSearchParams(data) });
        return Object.assign({ ok: res.ok }, await res.json().catch(() => ({})));
      } catch (_) {
        return { ok: false, message: '잠시 후 다시 시도해 주세요.' };
      }
    }
    let busy = false;

    sendBtn.addEventListener('click', async () => {
      if (!phoneReady() || busy) return;
      let from = 180;
      if (sendUrl) {
        busy = true;
        const res = await post(sendUrl, { phone: nextPhone.value });
        busy = false;
        if (!res.ok) { showError(res.message); return; }
        from = res.seconds || 180;
      }
      showError('');
      codeGroup.hidden = false;
      sendBtn.textContent = '재인증';
      hintPhone.textContent = nextPhone.value;
      code.value = '';
      startTimer(from);
      update();
      code.focus();
    });
    confirm.addEventListener('click', async () => {
      if (confirm.classList.contains('is-disabled') || busy) return;
      let phone = nextPhone.value;
      if (changeUrl) {
        busy = true;
        const res = await post(changeUrl, { phone: nextPhone.value, code: code.value });
        busy = false;
        if (res.redirect) { location.href = res.redirect; return; }
        if (!res.ok) { showError(res.message); return; }
        phone = res.phone;
      }
      // 알림 단계에서 추가: 번호 변경 완료 안내
      $('#currentPhone').textContent = phone;
      phoneSheet.hidden = true;
      reset();
    });
    phoneSheet.addEventListener('click', (e) => {
      if (e.target.closest('[data-close]')) reset();
    });

    // ?state=verify 로 열린 경우: 캡처 상태(02:59) 그대로 표시, 재인증 시 타이머 시작
    if (!codeGroup.hidden) seconds = 179;
    update();
  }

  /* ---------- 후기 작성 (별점·라벨, 글자 수, 사진 최대 5장, 등록 버튼 활성화) ---------- */
  const reviewForm = $('#reviewForm');
  if (reviewForm) {
    const ratingRow = $('#ratingRow');
    const ratingLabel = $('#ratingLabel');
    const grid = $('#reviewPhotoGrid');
    const input = $('#reviewPhotoInput');
    const addBtn = $('#reviewPhotoAdd');
    const count = $('#photoCount');
    const editor = $('#reviewText');
    const textCount = $('#reviewCount');
    const submit = $('#reviewSubmit');
    const LABELS = ['별점을 선택해주세요', '별로예요', '아쉬워요', '보통이에요', '좋아요', '최고예요'];
    const MAX = 5;
    let rating = Number(ratingRow.dataset.rating) || 0;

    const updateSubmit = () => { submit.disabled = !(rating > 0 && editor.value.trim()); };
    const renderStars = () => {
      $$('[data-star]', ratingRow).forEach((b) => {
        const n = Number(b.dataset.star);
        b.classList.toggle('rated', n <= rating);
        b.setAttribute('aria-checked', String(n === rating));
      });
      ratingLabel.textContent = LABELS[rating];
      ratingLabel.classList.toggle('rated', rating > 0);
      updateSubmit();
    };
    const renderCount = () => { textCount.textContent = editor.value.length + '/' + editor.maxLength; updateSubmit(); };
    const photos = () => $$('.review-photo-preview', grid);
    const updatePhotos = () => {
      const n = photos().length;
      count.textContent = n + '/' + MAX;
      addBtn.hidden = n >= MAX;
    };
    const addPreview = (src, name, file) => {
      const box = document.createElement('div');
      box.className = 'review-photo-preview';
      box._file = file; // 등록 때 보낼 원본 파일
      const img = document.createElement('img');
      img.src = src;
      img.alt = name;
      const rm = document.createElement('button');
      rm.type = 'button';
      rm.className = 'review-photo-remove';
      rm.setAttribute('aria-label', name + ' 삭제');
      rm.innerHTML = '<svg aria-hidden="true" width="10" height="10" viewBox="0 0 12 12" fill="none"><path d="M2.5 2.5l7 7M9.5 2.5l-7 7" stroke="#fff" stroke-width="2" stroke-linecap="round"/></svg>';
      box.append(img, rm);
      grid.insertBefore(box, addBtn);
      updatePhotos();
    };

    ratingRow.addEventListener('click', (e) => {
      const star = e.target.closest('[data-star]');
      if (!star) return;
      rating = Number(star.dataset.star);
      renderStars();
    });
    grid.addEventListener('click', (e) => {
      const rm = e.target.closest('.review-photo-remove');
      if (rm) { rm.closest('.review-photo-preview').remove(); updatePhotos(); }
    });
    input.addEventListener('change', () => {
      const remaining = MAX - photos().length;
      Array.from(input.files || []).slice(0, remaining).forEach((file) => {
        const reader = new FileReader();
        reader.onload = () => {
          if (typeof reader.result === 'string' && photos().length < MAX) addPreview(reader.result, file.name, file);
        };
        reader.readAsDataURL(file);
      });
      input.value = '';
    });
    editor.addEventListener('input', renderCount);

    // 등록 실패 안내 (다른 화면의 저장 토스트와 같은 모양)
    const toast = (message) => {
      let el = $('.save-toast');
      if (!el) {
        el = document.createElement('div');
        el.className = 'save-toast';
        el.setAttribute('role', 'status');
        document.body.appendChild(el);
      }
      el.textContent = message;
      el.hidden = false;
      clearTimeout(el._timer);
      el._timer = setTimeout(() => { el.hidden = true; }, 2200);
    };

    reviewForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (submit.disabled) return;
      if (reviewForm.dataset.live === 'true') {
        const data = new FormData();
        data.append('reservation', reviewForm.elements.reservation.value);
        data.append('rating', String(rating));
        data.append('content', editor.value.trim());
        photos().forEach((p) => { if (p._file) data.append('photos', p._file); });
        submit.disabled = true;
        let result;
        try {
          const res = await fetch(reviewForm.action, { method: 'POST', body: data });
          result = Object.assign({ ok: res.ok }, await res.json().catch(() => ({})));
        } catch (_) {
          result = { ok: false };
        }
        if (result.ok && result.redirect) { location.href = result.redirect; return; }
        toast(result.message || '잠시 후 다시 시도해 주세요.');
        updateSubmit();
        return;
      }
      const draft = {
        rating,
        text: editor.value.trim(),
        photos: photos().map((p) => p.querySelector('img').src).filter((s) => s.startsWith('data:')),
      };
      try { sessionStorage.setItem(REVIEW_KEY, JSON.stringify(draft)); } catch (_) { /* 저장 불가 시 더미 표시 */ }
      const path = location.pathname.replace(/(\/reviews\/new|\/review-new)$/, '/reviews');
      location.href = path + '?state=done';
    });
    renderCount();
    updatePhotos();
  }

  /* ---------- 후기 목록: 방금 쓴 후기 반영 ---------- */
  const submitted = $('#submittedReview');
  if (submitted) {
    let draft = null;
    try { draft = JSON.parse(sessionStorage.getItem(REVIEW_KEY) || 'null'); } catch (_) { draft = null; }
    if (draft) {
      $('[data-review-stars]', submitted).textContent = '★'.repeat(draft.rating) + '☆'.repeat(5 - draft.rating);
      $('[data-review-text]', submitted).textContent = draft.text;
      const box = $('[data-review-photos]', submitted);
      (draft.photos || []).forEach((src) => {
        const img = document.createElement('img');
        img.src = src;
        img.alt = '후기 사진';
        box.appendChild(img);
      });
      box.hidden = false;
      try { sessionStorage.removeItem(REVIEW_KEY); } catch (_) { /* 무시 */ }
      const toast = $('#reviewToast');
      if (toast) setTimeout(() => { toast.hidden = true; }, 2500);
    }
    // .my-review-photos 는 display:grid !important 라 hidden 이 먹지 않으므로 비어 있으면 제거
    const photoBox = $('[data-review-photos]', submitted);
    if (photoBox && !photoBox.children.length) photoBox.remove();
  } else {
    // 실제 등록 후(?state=done): 방금 쓴 후기는 서버 목록 맨 위에 있으므로 토스트만 잠시 보여준다
    const toast = $('#reviewToast');
    if (toast) setTimeout(() => { toast.hidden = true; }, 2500);
  }
})();
