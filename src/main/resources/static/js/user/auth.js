/*
 * 온보딩 · 로그인 · 회원가입 화면 동작 (G-A01~A13)
 *  [data-onboarding]   슬라이드 전환 (data-autoplay="true" 일 때 3.5초 자동 전환)
 *  [data-phone-input]  휴대폰 번호 010-0000-0000 형식 자동 입력
 *  [data-phone-login]  인증하기 → 인증번호 입력칸 노출, 타이머, 로그인 버튼 활성화
 *  [data-signup]       이름·번호·개인정보 동의 시 '다음' 활성화 (입력값은 sessionStorage 에 임시 보관)
 *  [data-verify]       6자리 인증번호 입력칸 이동·붙여넣기, 타이머, 재발송
 *  .is-disabled 버튼/링크는 클릭 무시
 */
(function () {
  const STORE_KEY = 'codemove.signup';

  function load() {
    try { return JSON.parse(sessionStorage.getItem(STORE_KEY)) || {}; } catch (_) { return {}; }
  }
  function save(patch) {
    try { sessionStorage.setItem(STORE_KEY, JSON.stringify(Object.assign(load(), patch))); } catch (_) { /* 저장 불가 환경 무시 */ }
  }

  function formatPhone(value) {
    const n = value.replace(/\D/g, '').slice(0, 11);
    if (n.length <= 3) return n;
    if (n.length <= 7) return `${n.slice(0, 3)}-${n.slice(3)}`;
    return `${n.slice(0, 3)}-${n.slice(3, 7)}-${n.slice(7)}`;
  }
  const digits = (value) => value.replace(/\D/g, '');

  function setEnabled(el, enabled) {
    if (!el) return;
    el.classList.toggle('is-disabled', !enabled);
    el.setAttribute('aria-disabled', String(!enabled));
  }

  /* 카운트다운 타이머. el.dataset.seconds 부터 시작 */
  function startTimer(el, seconds) {
    if (!el) return;
    clearInterval(el._timer);
    let left = seconds ?? Number(el.dataset.seconds || 180);
    const render = () => {
      el.classList.toggle('expired', left === 0);
      el.textContent = left === 0 ? '시간 만료'
        : `${String(Math.floor(left / 60)).padStart(2, '0')}:${String(left % 60).padStart(2, '0')}`;
    };
    render();
    el._timer = setInterval(() => {
      left = Math.max(0, left - 1);
      render();
      if (left === 0) clearInterval(el._timer);
    }, 1000);
  }

  document.addEventListener('click', (e) => {
    const disabled = e.target.closest('.is-disabled');
    if (disabled) { e.preventDefault(); e.stopPropagation(); }
  }, true);

  document.querySelectorAll('[data-phone-input]').forEach((input) => {
    input.addEventListener('input', () => { input.value = formatPhone(input.value); });
  });

  document.querySelectorAll('[data-user-name]').forEach((el) => {
    const { name } = load();
    if (name) el.textContent = name;
  });

  /* 온보딩 */
  const onboarding = document.querySelector('[data-onboarding]');
  if (onboarding) {
    const slides = onboarding.querySelectorAll('[data-slide]');
    const dots = onboarding.querySelectorAll('[data-slide-to]');
    let current = Number([...slides].find((s) => !s.hidden)?.dataset.slide || 1);
    let timer;
    const show = (n) => {
      current = n;
      slides.forEach((s) => { s.hidden = Number(s.dataset.slide) !== n; });
      dots.forEach((d) => d.classList.toggle('pager-active', Number(d.dataset.slideTo) === n));
    };
    const autoplay = () => {
      clearInterval(timer);
      timer = setInterval(() => show((current % slides.length) + 1), 3500);
    };
    dots.forEach((d) => d.addEventListener('click', () => {
      show(Number(d.dataset.slideTo));
      if (onboarding.dataset.autoplay === 'true') autoplay();
    }));
    if (onboarding.dataset.autoplay === 'true') autoplay();
  }

  /* 휴대전화 로그인 */
  const phoneLogin = document.querySelector('[data-phone-login]');
  if (phoneLogin) {
    const phone = phoneLogin.querySelector('[data-phone-input]');
    const code = phoneLogin.querySelector('[data-code-input]');
    const send = phoneLogin.querySelector('[data-send-code]');
    const group = phoneLogin.querySelector('[data-code-group]');
    const timerEl = phoneLogin.querySelector('[data-timer]');
    const submit = phoneLogin.querySelector('[data-login-submit]');
    let sent = phoneLogin.dataset.sent === 'true';

    const sync = () => {
      const phoneReady = digits(phone.value).length === 11;
      send.classList.toggle('active', phoneReady);
      setEnabled(send, phoneReady);
      setEnabled(submit, sent && digits(code.value).length === 6);
    };
    phone.addEventListener('input', sync);
    code.addEventListener('input', () => {
      code.value = digits(code.value).slice(0, 6);
      code.classList.remove('is-focused');
      sync();
    });
    send.addEventListener('click', () => {
      if (send.classList.contains('is-disabled')) return;
      sent = true;
      send.textContent = '재인증';
      group.hidden = false;
      code.value = '';
      phoneLogin.querySelector('[data-phone-echo]').textContent = phone.value;
      startTimer(timerEl, 180);
      code.focus();
      sync();
    });
    if (sent) startTimer(timerEl);
    sync();
  }

  /* 회원가입 본인인증 정보 */
  const signup = document.querySelector('[data-signup]');
  if (signup) {
    const name = signup.querySelector('[data-name-input]');
    const phone = signup.querySelector('[data-phone-input]');
    const check = signup.querySelector('[data-terms-check]');
    const submit = signup.querySelector('[data-signup-submit]');
    const agreed = signup.dataset.agreed === 'true';
    const stored = load();
    if (!name.value && stored.name) name.value = stored.name;
    if (!phone.value && stored.phone) phone.value = stored.phone;
    check.classList.toggle('terms-checked', agreed);

    const sync = () => {
      setEnabled(submit, name.value.trim().length > 0 && digits(phone.value).length === 11 && agreed);
      save({ name: name.value.trim(), phone: phone.value });
    };
    name.addEventListener('input', sync);
    phone.addEventListener('input', sync);
    sync();
  }

  /* 인증번호 입력 */
  const verify = document.querySelector('[data-verify]');
  if (verify) {
    const boxes = [...verify.querySelectorAll('.otp-box')];
    const timerEl = verify.querySelector('[data-timer]');
    const submit = verify.querySelector('[data-verify-submit]');

    const sync = () => {
      boxes.forEach((b) => b.classList.toggle('otp-filled', b.value !== ''));
      const ready = boxes.every((b) => b.value !== '');
      submit.classList.toggle('primary', ready);
      submit.classList.toggle('next-button', !ready);
      setEnabled(submit, ready);
    };
    const fill = (text, from) => {
      const nums = digits(text).split('');
      let i = from;
      nums.forEach((d) => { if (i < boxes.length) boxes[i++].value = d; });
      boxes[Math.min(i, boxes.length - 1)].focus();
      sync();
    };

    boxes.forEach((box, i) => {
      box.addEventListener('focus', () => box.select());
      box.addEventListener('input', () => {
        const value = digits(box.value);
        box.value = '';
        if (value) fill(value, i); else sync();
      });
      box.addEventListener('keydown', (e) => {
        if (e.key === 'Backspace' && !box.value && i > 0) boxes[i - 1].focus();
      });
      box.addEventListener('paste', (e) => {
        e.preventDefault();
        fill(e.clipboardData.getData('text'), i);
      });
    });

    verify.querySelector('[data-otp-resend]').addEventListener('click', () => {
      boxes.forEach((b) => { b.value = ''; });
      startTimer(timerEl, 180);
      boxes[0].focus();
      sync();
    });

    startTimer(timerEl);
    sync();
  }
})();
