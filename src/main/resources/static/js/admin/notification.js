/*
 * 알림 관리 화면 동작 (SCR-A-014 알림 내역 검색, SCR-A-015 메시징 운영 설정)
 * 화면 개발 단계: 서버 저장 없이 화면 안에서만 상태를 바꾼다.
 */
(function () {
  /* ---------- SCR-A-014 알림 내역: 회원명·알림 내용 검색 ---------- */
  const history = document.querySelector('[data-noti-history]');
  if (history) {
    const input = history.querySelector('[data-history-query]');
    const clear = history.querySelector('[data-history-clear]');
    const count = history.querySelector('[data-history-count]');
    const empty = history.querySelector('[data-history-empty]');
    const cards = [...history.querySelectorAll('[data-history-list] > a')];

    const apply = () => {
      const q = input.value.trim().toLowerCase();
      let shown = 0;
      cards.forEach((card) => {
        const match = !q || card.dataset.search.toLowerCase().includes(q);
        card.hidden = !match;
        if (match) shown += 1;
      });
      count.textContent = shown + '건';
      count.hidden = !q;
      clear.hidden = !q;
      empty.hidden = shown > 0;
    };

    input.addEventListener('input', apply);
    clear.addEventListener('click', () => { input.value = ''; apply(); input.focus(); });
    if (history.dataset.initialQuery) { input.value = history.dataset.initialQuery; apply(); }
  }

  /* ---------- SCR-A-015 메시징 운영 설정 ---------- */
  const settings = document.querySelector('[data-messaging-settings]');
  if (!settings) return;

  const toggle = (name) => settings.querySelector(`[data-toggle="${name}"]`);
  const isOn = (btn) => btn.getAttribute('aria-pressed') === 'true';
  const picker = settings.querySelector('#timePicker');
  const selects = {
    period: picker.querySelector('[data-picker="period"]'),
    hour: picker.querySelector('[data-picker="hour"]'),
    minute: picker.querySelector('[data-picker="minute"]'),
  };
  let target = null;

  // 전체 가동이 꺼지면 경고 표시 + 하위 설정 비활성, 에티켓 시간이 꺼지면 시간 버튼 비활성
  const sync = () => {
    const enabled = isOn(toggle('enabled'));
    const quiet = isOn(toggle('quiet'));
    settings.querySelector('[data-paused-alert]').hidden = enabled;
    settings.querySelectorAll('[data-dependent]').forEach((s) => s.classList.toggle('opacity-60', !enabled));
    ['quiet', 'sms'].forEach((n) => { toggle(n).disabled = !enabled; });
    settings.querySelectorAll('[data-time]').forEach((b) => { b.disabled = !enabled || !quiet; });
  };

  const toDisplay = (hhmm) => {
    const [h, m] = hhmm.split(':').map(Number);
    return `${h >= 12 ? '오후' : '오전'} ${String(h % 12 || 12).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
  };

  const openPicker = (btn) => {
    target = btn;
    const [h, m] = btn.dataset.value.split(':').map(Number);
    selects.period.value = h >= 12 ? '오후' : '오전';
    selects.hour.value = String(h % 12 || 12).padStart(2, '0');
    selects.minute.value = String(m).padStart(2, '0');
    picker.querySelector('[data-picker-title]').textContent =
      btn.dataset.time === 'start' ? '에티켓 시작 시간' : '에티켓 종료 시간';
    picker.hidden = false;
    // 선택된 항목이 목록 가운데 오도록 스크롤
    Object.values(selects).forEach((s) => {
      const rowH = s.scrollHeight / s.options.length;
      s.scrollTop = Math.max(0, (s.selectedIndex - 1) * rowH);
    });
  };

  settings.addEventListener('click', (e) => {
    const t = e.target.closest('[data-toggle]');
    if (t && !t.disabled) {
      t.setAttribute('aria-pressed', String(!isOn(t)));
      sync();
      return;
    }
    const timeBtn = e.target.closest('[data-time]');
    if (timeBtn && !timeBtn.disabled) {
      openPicker(timeBtn);
      return;
    }
    if (e.target.closest('[data-picker-done]') && target) {
      const h12 = Number(selects.hour.value);
      const h24 = selects.period.value === '오후' ? (h12 % 12) + 12 : (h12 === 12 ? 0 : h12);
      target.dataset.value = `${String(h24).padStart(2, '0')}:${selects.minute.value}`;
      target.textContent = toDisplay(target.dataset.value);
      picker.hidden = true;
    }
  });

  sync();
  if (settings.dataset.state === 'picker') openPicker(settings.querySelector('[data-time="start"]'));
})();
