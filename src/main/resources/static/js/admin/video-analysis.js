const rows = c.items.map((it) => `<tr>/* 영상 분석 테스트 (templates/admin/video-analysis/index.html)
 *  업로드 분석 POST /admin/video-analysis/videos (video, durationSec, uploadStartedAt)
 *  JSON 직접 입력 POST /admin/video-analysis/manual {raw, baseRunId}
 *  기록 보기 GET /runs/{id} · 정답 비교 GET /runs/{id}/score?set= · 다시 분석 POST /runs/{id}/rerun · 삭제 POST /runs/{id}/delete
 *  기준 데이터 다시 읽기 POST /catalog/reload */
(function () {
  const root = document.getElementById('videoAnalysis');
  if (!root) return;
  const BASE = '/admin/video-analysis';
  const $ = (s, el) => (el || root).querySelector(s);
  const maxMb = Number(root.dataset.maxMb);
  const minSec = Number(root.dataset.minSec);
  const maxSec = Number(root.dataset.maxSec);
  const enabled = root.dataset.enabled === 'true';
  const testsets = (root.dataset.testsets || '').split(',').filter(Boolean);
  /* 공통코드 MAPPING_TYPE */
  const TYPE = { EXACT: '01 정확 일치', ALIAS: '02 별칭', AI_GUESS: '03 AI 후보', UNMAPPED: '04 미매핑' };
  const SIZE = { '01': '소형', '02': '중형', '03': '대형' };
  const ERRORS = { '01': '파일 형식 오류', '02': '용량·길이 초과', '03': '영상 품질 불량', '04': '집 안 영상 아님(품목 없음)', '05': 'AI 시간 초과',
    '06': 'AI 사용량 한도', '07': 'AI 응답 형식 오류', '08': 'AI 호출 실패', '09': 'AI 키 없음', '10': 'AI 서버 혼잡' };

  const esc = (v) => String(v == null ? '' : v).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const num = (v, unit) => (v == null ? '—' : v + (unit || ''));
  const ms = (v) => (v == null ? '—' : v >= 1000 ? (v / 1000).toFixed(1) + '초' : v + 'ms');
  const pct = (v) => (v == null ? '—' : (Number(v) * 100).toFixed(1) + '%');

  async function post(url, data) {
    try {
      const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data || {}) });
      return Object.assign({ ok: res.ok }, await res.json().catch(() => ({})));
    } catch (_) {
      return { ok: false, message: '서버에 연결하지 못했어요.' };
    }
  }

  /* 실험 조건: 비우면 서버 설정값 */
  function options() {
    const fps = $('[data-opt-fps]').value;
    return { model: $('[data-opt-model]').value, fps: fps ? Number(fps) : null, prompt: $('[data-opt-prompt]').value,
      noFallback: $('[data-opt-nofallback]').checked };
  }

  /* ---------- 영상 선택 · 길이 확인 ---------- */
  const fileInput = $('[data-video]');
  const info = $('[data-video-info]');
  const analyzeBtn = $('[data-analyze]');
  const status = $('[data-analyze-status]');
  let duration = null;

  function setStatus(text, error) {
    status.hidden = !text;
    status.textContent = text || '';
    status.style.color = error ? '#ef5f3c' : '#555';
  }

  fileInput.addEventListener('change', () => {
    const file = fileInput.files[0];
    duration = null;
    analyzeBtn.disabled = true;
    setStatus('');
    if (!file) { info.hidden = true; return; }
    const mb = file.size / 1048576;
    info.hidden = false;
    info.textContent = `${file.name} · ${mb.toFixed(1)}MB · 길이 확인 중…`;
    const video = document.createElement('video');
    video.preload = 'metadata';
    const url = URL.createObjectURL(file);
    const done = (sec) => {
      URL.revokeObjectURL(url);
      duration = Number.isFinite(sec) ? Math.round(sec * 10) / 10 : null;
      const problems = [];
      if (mb > maxMb) problems.push(`${maxMb}MB 초과`);
      if (duration != null && duration > maxSec) problems.push(`${maxSec / 60}분 초과`);
      if (duration != null && duration < minSec) problems.push(`${minSec}초 미만`);
      info.textContent = `${file.name} · ${mb.toFixed(1)}MB · ${duration == null ? '길이 모름' : duration + '초'} · ${file.type || '형식 모름'}${mb > 14 ? ' · Files API 로 전송' : ''}`;
      if (problems.length) setStatus('올릴 수 없어요: ' + problems.join(', '), true);
      else if (!enabled) setStatus('Gemini API 키가 없어 분석할 수 없어요. 아래 JSON 직접 입력은 쓸 수 있어요.', true);
      analyzeBtn.disabled = problems.length > 0 || !enabled;
    };
    video.onloadedmetadata = () => done(video.duration);
    video.onerror = () => done(NaN);
    video.src = url;
  });

  /* 비동기 분석 기다리기: 3초마다 GET /runs/{id}/status, 끝나면 onDone(run). 15분이 넘으면 멈춤 */
  function pollRun(id, onProgress, onDone) {
    const started = Date.now();
    const tick = async () => {
      let s = null;
      try {
        const res = await fetch(`${BASE}/runs/${id}/status`);
        s = res.ok ? await res.json() : null;
      } catch (_) { /* 잠깐 끊김: 다음에 다시 */ }
      if (s && s.state === 'DONE') { onDone(s.run); return; }
      if (!s && Date.now() - started > 10000) { onProgress('분석 상태를 찾지 못했어요.'); return; }
      if (Date.now() - started > 15 * 60 * 1000) { onProgress('너무 오래 걸려 확인을 멈췄어요. 실행 기록에서 확인해 주세요.'); return; }
      onProgress(`AI 분석 중… ${s ? s.elapsedSec : Math.round((Date.now() - started) / 1000)}초`);
      setTimeout(tick, 3000);
    };
    tick();
  }

  /* ---------- 업로드 분석 (진행률 표시를 위해 XHR) ---------- */
  analyzeBtn.addEventListener('click', () => {
    const file = fileInput.files[0];
    if (!file) return;
    const form = new FormData();
    form.append('video', file);
    if (duration != null) form.append('durationSec', duration);
    const opt = options();
    if (opt.model) form.append('model', opt.model);
    if (opt.fps) form.append('fps', opt.fps);
    if (opt.prompt) form.append('prompt', opt.prompt);
    if (opt.noFallback) form.append('noFallback', 'true');
    form.append('uploadStartedAt', Date.now());
    form.append('async', 'true');
    analyzeBtn.disabled = true;
    const xhr = new XMLHttpRequest();
    xhr.open('POST', BASE + '/videos');
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) setStatus(`업로드 중 ${Math.round((e.loaded / e.total) * 100)}%`);
    };
    xhr.upload.onload = () => setStatus('영상 저장 중…');
    xhr.onload = () => {
      let body = {};
      try { body = JSON.parse(xhr.responseText); } catch (_) { /* 빈 응답 */ }
      if (xhr.status === 202) {
        // 비동기: 실행 ID 를 받고 상태를 확인하며 기다림
        pollRun(body.id, setStatus, (run) => {
          analyzeBtn.disabled = false;
          setStatus(run.success ? '분석 완료' : `분석 실패: ${run.errorCode} ${ERRORS[run.errorCode] || ''} — ${run.errorMessage || ''}`, !run.success);
          showResult(run);
        });
        return;
      }
      analyzeBtn.disabled = false;
      if (xhr.status !== 200) {
        setStatus((body.errorCode ? body.errorCode + ' ' : '') + (body.message || `실패 (${xhr.status})`), true);
        if (body.outcome) { const box = $('[data-result]'); box.innerHTML = '<h3 class="text-[15px] font-bold">고객에게 보일 안내 (업로드에서 막힘)</h3><div class="mt-2">' + outcomeHtml(body.outcome) + '</div>'; box.hidden = false; }
        return;
      }
      setStatus(body.success ? '분석 완료' : `분석 실패: ${body.errorCode} ${ERRORS[body.errorCode] || ''} — ${body.errorMessage || ''}`, !body.success);
      showResult(body);
    };
    xhr.onerror = () => { analyzeBtn.disabled = false; setStatus('서버에 연결하지 못했어요.', true); };
    xhr.send(form);
  });

  /* ---------- JSON 직접 입력 ---------- */
  $('[data-manual-run]').addEventListener('click', async () => {
    const res = await post(BASE + '/manual', { raw: $('[data-manual]').value, baseRunId: $('[data-base-run]').value });
    if (!res.ok) { alert(res.message || '계산하지 못했어요.'); return; }
    showResult(res);
  });

  /* ---------- 기준 데이터 다시 읽기 ---------- */
  $('[data-reload]').addEventListener('click', async () => {
    const res = await post(BASE + '/catalog/reload');
    const el = $('[data-catalog-status]');
    if (!res.ok) { alert('다시 읽지 못했어요.'); return; }
    el.innerHTML = `품목 ${res.items}개 · 규격 ${res.rows}행 · 별칭 ${res.aliases}개 · 트럭 ${res.trucks}종 (다시 읽음)`
      + (res.error ? `<span class="block" style="color:#ef5f3c">${esc(res.error)}</span>` : '');
  });

  /* ---------- 실행 기록: 보기 · 다시 분석 · 삭제 ---------- */
  root.addEventListener('click', async (e) => {
    const li = e.target.closest('[data-run]');
    if (!li) return;
    const id = li.dataset.run;
    if (e.target.closest('[data-run-view]')) {
      const res = await fetch(`${BASE}/runs/${id}`);
      if (res.ok) showResult(await res.json());
    } else if (e.target.closest('[data-run-rerun]')) {
      const btn = e.target.closest('[data-run-rerun]');
      btn.textContent = '분석 중…';
      btn.disabled = true;
      const res = await post(`${BASE}/runs/${id}/rerun`, Object.assign(options(), { async: true }));
      if (!res.ok) { alert(res.message || '다시 분석하지 못했어요.'); btn.textContent = '다시 분석'; btn.disabled = false; return; }
      pollRun(res.id, (text) => { btn.textContent = text; }, (run) => {
        btn.textContent = '다시 분석';
        btn.disabled = false;
        showResult(run);
        refreshCompare();
      });
    } else if (e.target.closest('[data-run-delete]')) {
      if (!confirm('이 기록과 영상을 지울까요?')) return;
      const res = await post(`${BASE}/runs/${id}/delete`);
      if (res.ok) li.remove();
    }
  });

  /* ---------- 결과 그리기 ---------- */
  function showResult(run) {
    const box = $('[data-result]');
    const r = run.result;
    const u = run.usage;
    const c = run.cost;
    const t = run.timings || {};
    const ai = run.ai;
    const load = run.load;
    const lines = r ? r.lines : [];
    const head = `
      <div class="flex items-center justify-between">
        <h3 class="text-[15px] font-bold">결과 <span class="text-[12px] font-normal text-[#888]">${esc(run.id)}${run.baseRunId ? ' · 기준 ' + esc(run.baseRunId) : ''}</span></h3>
        <span class="rounded-full px-2 py-0.5 text-[11px] font-semibold" style="background:${run.success ? '#eef4ec' : '#fdeee9'};color:${run.success ? '#4a6741' : '#ef5f3c'}">${run.success ? '성공' : esc(run.errorCode) + ' ' + esc(ERRORS[run.errorCode] || '')}</span>
      </div>
      ${run.errorMessage ? `<p class="mt-2 text-[12px] text-[#ef5f3c]">${esc(run.errorMessage)}</p>` : ''}
      ${(run.parseErrors || []).map((m) => `<p class="mt-1 text-[12px] text-[#ef5f3c]">✕ ${esc(m)}</p>`).join('')}
      ${(run.parseWarnings || []).map((m) => `<p class="mt-1 text-[12px] text-[#d9701a]">! ${esc(m)}</p>`).join('')}
      ${ai && ai.rooms && ai.rooms.length ? `<p class="mt-2 text-[12px] text-[#555]">본 공간: ${ai.rooms.map(esc).join(' → ')}</p>` : ''}
      ${ai && ai.memo ? `<p class="mt-1 whitespace-pre-wrap rounded-[12px] bg-[#fff8ee] p-2.5 text-[12px] text-[#8a5a14]">방문 견적 메모: ${esc(ai.memo)}</p>` : ''}`;

    const totals = r ? `
      <div class="mt-4 grid grid-cols-3 gap-2 text-center">
        <div class="rounded-[12px] bg-[#f6f7f6] py-2"><b class="block text-[16px]">${esc(r.totalCbm)}</b><span class="text-[11px] text-[#888]">물품 부피(㎥)</span></div>
        <div class="rounded-[12px] bg-[#f6f7f6] py-2"><b class="block text-[16px]">${load ? esc(load.ton) + '톤' : '—'}</b><span class="text-[11px] text-[#888]">${load ? esc(load.label) : '트럭 기준 없음'}</span></div>
        <div class="rounded-[12px] bg-[#f6f7f6] py-2"><b class="block text-[16px]">${r.reviewCount}</b><span class="text-[11px] text-[#888]">확인 필요</span></div>
      </div>
      <p class="mt-2 text-[11px] text-[#888]">${load ? `적재 부피 ${esc(load.loadCbm)}㎥ = 물품 ${esc(load.itemCbm)}㎥ × 적재계수 ${esc(load.loadFactor)} · ` : ''}무게 ${esc(r.totalKg)}kg${r.kgMissing ? ` (무게 기준 없는 ${r.kgMissing}줄 제외)` : ''}. 표준 규격 기준 추정값이에요.</p>` : '';

    const scoreBox = r && testsets.length ? `
      <div class="mt-4 rounded-[12px] border border-[#eef0ee] p-3">
        <div class="flex items-center gap-2 text-[13px]">
          <b class="shrink-0">정답 비교</b>
          <select data-score-set class="h-8 min-w-0 flex-1 rounded-[8px] border border-[#dfe3df] px-2 text-[12px]">
            <option value="">정답 목록 선택</option>${testsets.map((s) => `<option value="${esc(s)}">${esc(s)}</option>`).join('')}
          </select>
        </div>
        <div data-score class="mt-2 text-[12px]"></div>
      </div>` : '';

    const items = lines.map((l) => {
      const m = l.item;
      const a = m.ai;
      const std = m.standard;
      const tv = a.inch ? ` · ${a.inch}인치${a.wallMounted ? ' 벽걸이' : ''}` : (a.wallMounted ? ' · 벽걸이' : '');
      return `
        <li class="py-3">
          <div class="flex items-start justify-between gap-2">
            <div><b>${esc(a.detectedName || a.itemName)}</b> <span class="text-[#888]">× ${a.quantity}${a.room ? ' · ' + esc(a.room) : ''}${tv}</span></div>
            <span class="shrink-0 rounded-full px-2 py-0.5 text-[11px] font-semibold" style="background:${m.needsReview ? '#fff4e8' : '#eef4ec'};color:${m.needsReview ? '#d9701a' : '#4a6741'}">${esc(TYPE[m.type] || m.type)}</span>
          </div>
          <p class="mt-1 text-[12px] text-[#555]">AI: ${esc(a.itemName || '—')} ${esc(a.sizeCode || '')} → ${m.standardName ? esc(m.standardName) + ' ' + esc(SIZE[m.sizeCode]) + (std && std.sizeName ? ' (' + esc(std.sizeName) + ')' : '') : '<span style="color:#ef5f3c">미매핑</span>'}</p>
          <p class="mt-0.5 text-[12px] text-[#555]">부피 ${num(l.unitCbm)} × ${a.quantity} = <b>${num(l.totalCbm, '㎥')}</b> · 무게 ${l.unitKg == null ? '기준 없음' : l.unitKg + 'kg × ' + a.quantity + ' = <b>' + l.totalKg + 'kg</b>'}</p>
          ${a.evidence ? `<p class="mt-0.5 text-[11px] text-[#888]">근거: ${esc(a.evidence)}</p>` : ''}
          ${(m.reasons || []).map((x) => `<p class="mt-0.5 text-[12px] text-[#d9701a]">! ${esc(x)}</p>`).join('')}
        </li>`;
    }).join('');

    const tokens = u ? `입력 ${u.promptTokens} · 출력 ${u.outputTokens} · 생각 ${u.thoughtsTokens} · 합계 ${u.totalTokens}${u.promptByModality && Object.keys(u.promptByModality).length ? ' (' + Object.entries(u.promptByModality).map(([k, v]) => k + ' ' + v).join(', ') + ')' : ''}` : '—';
    const cost = c ? (c.amount == null ? '단가 미설정' : `${c.amount} ${esc(c.currency)} (단가 기준일 ${esc(c.asOf)})`) : '—';
    const meta = `
      <dl class="mt-4 space-y-1.5 border-t border-[#eef0ee] pt-3 text-[12px]">
        ${run.video ? `<div class="flex justify-between gap-3"><dt class="text-[#888]">영상</dt><dd class="text-right">${esc(run.video.fileName)} · ${(run.video.sizeBytes / 1048576).toFixed(1)}MB · ${num(run.video.durationSec, '초')}${run.video.transfer ? ' · ' + esc(run.video.transfer) : ''}</dd></div>` : ''}
        <div class="flex justify-between gap-3"><dt class="text-[#888]">모델</dt><dd class="text-right">${esc(run.model || '—')} ${run.modelVersion ? '(' + esc(run.modelVersion) + ')' : ''}${run.thinkingLevel ? ' · 생각 ' + esc(run.thinkingLevel) : ''}${run.fps ? ' · 초당 ' + run.fps + '프레임' : ''} · 프롬프트 ${esc(run.promptVersion)}</dd></div>
        <div class="flex justify-between gap-3"><dt class="text-[#888]">토큰</dt><dd class="text-right">${tokens}</dd></div>
        <div class="flex justify-between gap-3"><dt class="text-[#888]">비용</dt><dd class="text-right">${cost}</dd></div>
        <div class="flex justify-between gap-3"><dt class="text-[#888]">시간</dt><dd class="text-right">업로드 ${ms(t.uploadMs)} · 저장 ${ms(t.saveMs)}${t.aiFileMs ? ' · AI 파일 전송 ' + ms(t.aiFileMs) : ''} · API ${ms(t.apiMs)} · 매핑 ${ms(t.mappingMs)} · 전체 ${ms(t.totalMs)}${run.retries ? ' · 재시도 ' + run.retries : ''}</dd></div>
      </dl>`;

    const raw = run.rawResponse ? `
      <details class="mt-3"><summary class="cursor-pointer text-[13px] font-semibold">AI 원본 응답</summary>
        <pre class="mt-2 max-h-60 overflow-auto whitespace-pre-wrap rounded-[12px] bg-[#f6f7f6] p-3 text-[11px]">${esc(pretty(run.rawResponse))}</pre>
        <button type="button" data-edit-raw class="mt-2 text-[13px] font-semibold text-[#4a6741]">이 JSON 을 고쳐서 다시 계산</button>
      </details>` : '';

    const customerBox = r && lines.length ? `
      <div class="mt-4 rounded-[12px] border border-[#eef0ee] p-3">
        <div class="flex items-center justify-between gap-2 text-[13px]">
          <b>고객 · 관리자에게 전달될 값</b>
          <a href="${BASE}/runs/${esc(run.id)}/customer" target="_blank" class="shrink-0 text-[12px] font-semibold text-[#4a6741]">고객 화면으로 보기 ↗</a>
        </div>
        <div data-customer class="mt-2 text-[12px]"></div>
      </div>` : '';
    const outcomeBox = `
      <div class="mt-4 rounded-[12px] border border-[#eef0ee] p-3">
        <div class="flex items-center justify-between gap-2 text-[13px]">
          <b>고객에게 보일 안내</b>
          <label class="text-[11px] text-[#888]">시도 <select data-outcome-attempt class="h-7 rounded-[6px] border border-[#dfe3df] px-1 text-[11px]"><option value="">기록대로</option><option value="1">1회째</option><option value="2">2회째</option><option value="3">3회째</option></select></label>
        </div>
        <div data-outcome class="mt-2"></div>
      </div>`;
    box.innerHTML = head + outcomeBox + totals + scoreBox + customerBox + (lines.length ? `<ul class="mt-3 divide-y divide-[#eef0ee]">${items}</ul>` : '') + meta + raw;
    box.hidden = false;
    const edit = $('[data-edit-raw]', box);
    if (edit) edit.addEventListener('click', () => {
      $('[data-manual]').value = pretty(run.rawResponse);
      $('[data-base-run]').value = run.id;
      $('[data-manual]').scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
    const outcomeEl = $('[data-outcome]', box);
    const attemptSel = $('[data-outcome-attempt]', box);
    showOutcome(run.id, outcomeEl, '');
    attemptSel.addEventListener('change', () => showOutcome(run.id, outcomeEl, attemptSel.value));
    const customer = $('[data-customer]', box);
    if (customer) showCustomer(run.id, customer);
    const select = $('[data-score-set]', box);
    if (select) {
      select.addEventListener('change', () => showScore(run.id, select.value, $('[data-score]', box)));
      const guess = testsets.find((s) => s.includes('noperson')) || testsets[0];
      if (run.video && guess) { select.value = guess; showScore(run.id, guess, $('[data-score]', box)); }
    }
    box.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  /* 조건별 비교표 (GET /compare?set=) */
  const compareSel = $('[data-compare-set]');
  const fmtStat = (s, unit, pctMode) => {
    if (!s || s.avg == null) return '—';
    const f = (v) => (pctMode ? (Number(v) * 100).toFixed(0) + '%' : v + (unit || ''));
    return s.min === s.max ? f(s.avg) : `${f(s.avg)}<br><span style="color:#999">${f(s.min)}~${f(s.max)}</span>`;
  };
  async function showCompare(set) {
    const el = $('[data-compare]');
    el.innerHTML = '';
    if (!set) return;
    const res = await fetch(`${BASE}/compare?set=${encodeURIComponent(set)}`);
    if (!res.ok) { el.textContent = '비교하지 못했어요.'; return; }
    const t = await res.json();
    if (!t.rows.length) { el.textContent = '이 영상으로 분석한 기록이 없어요.'; return; }
    const errs = (e) => Object.entries(e).map(([k, v]) => `${k}×${v}`).join(' ') || '';
    el.innerHTML = `
      <p class="mb-2 text-[12px]">목표(정답 기준) 톤: <b>${t.expectedTon == null ? '—' : esc(t.expectedTon) + '톤'}</b> ${esc(t.expectedLabel || '')}</p>
      <table class="w-full min-w-[640px] border-collapse">
        <thead><tr class="text-left text-[#888]"><th class="py-1 pr-2">모델 · 프롬프트 · 프레임</th><th class="pr-2">성공/시도</th><th class="pr-2">재현율</th><th class="pr-2">정밀도</th><th class="pr-2">CBM 오차</th><th class="pr-2">톤 (일치)</th><th class="pr-2">API 시간</th><th class="pr-2">입력 토큰</th><th>1회 비용</th></tr></thead>
        <tbody>${t.rows.map((r) => `
          <tr class="border-t border-[#eef0ee] align-top">
            <td class="py-1.5 pr-2"><b>${esc(r.model)}</b><br>${esc(r.promptVersion)} · ${esc(r.fps)}</td>
            <td class="pr-2">${r.successes}/${r.attempts}${r.attempts > r.successes ? `<br><span style="color:#ef5f3c">${esc(errs(r.errors))}</span>` : ''}</td>
            <td class="pr-2">${fmtStat(r.recall, '', true)}</td>
            <td class="pr-2">${fmtStat(r.precision, '', true)}</td>
            <td class="pr-2">${fmtStat(r.cbmErrorPct, '%')}</td>
            <td class="pr-2">${fmtStat(r.ton, '톤')}<br><span style="color:${r.tonMatch ? '#4a6741' : '#999'}">일치 ${r.tonMatch}/${r.successes}</span></td>
            <td class="pr-2">${fmtStat(r.apiSec, '초')}</td>
            <td class="pr-2">${fmtStat(r.inputTokens, '')}</td>
            <td>${fmtStat(r.cost, '$')}</td>
          </tr>`).join('')}</tbody>
      </table>`;
  }
  const refreshCompare = () => { if (compareSel.value) showCompare(compareSel.value); };
  compareSel.addEventListener('change', () => showCompare(compareSel.value));
  if (testsets.length) { compareSel.value = testsets.find((s) => s.includes('noperson')) || testsets[0]; showCompare(compareSel.value); }

  /* 고객에게 보일 안내 (AnalysisOutcomePolicy): 제목 · 설명 · 촬영 요령 · 버튼 · 관리자 표시 · 남은 횟수 */
  const OUTCOME_COLOR = { OK: '#4a6741', PARTIAL: '#d9701a', RETAKE: '#ef5f3c', RETRY_LATER: '#ef5f3c' };
  const OUTCOME_NAME = { OK: '정상', PARTIAL: '일부만 보임', RETAKE: '다시 찍기', RETRY_LATER: '잠시 후 다시' };
  function outcomeHtml(o) {
    const buttons = o.actions.map((a) => a.primary
      ? `<span class="block h-10 rounded-full bg-[#4a6741] text-center text-[13px] font-bold leading-10 text-white">${esc(a.label)}</span>`
      : `<span class="block py-1.5 text-center text-[12px] text-[#8a8a8a]">${esc(a.label)}</span>`).join('');
    return `
      <div class="rounded-[12px] bg-[#fafbfa] p-3">
        <p class="text-[11px] font-semibold" style="color:${OUTCOME_COLOR[o.type]}">${esc(OUTCOME_NAME[o.type] || o.type)}${o.reason ? ' · ' + esc(o.reason) : ''}</p>
        <p class="mt-1 text-[14px] font-bold">${esc(o.title)}</p>
        <p class="mt-1 text-[12px] text-[#555]">${esc(o.message)}</p>
        ${o.tips.length ? `<ul class="mt-2 list-disc pl-4 text-[11px] text-[#777]">${o.tips.map((t) => `<li>${esc(t)}</li>`).join('')}</ul>` : ''}
        <div class="mt-3 space-y-1">${buttons}</div>
      </div>
      <p class="mt-2 text-[11px] text-[#888]">관리자 표시: <b style="color:#292929">${esc(o.adminLabel)}</b> · 시도 ${o.attemptsUsed}회째 · 남은 다시 찍기/재분석 ${o.attemptsLeft}회</p>`;
  }

  async function showOutcome(id, el, attempt) {
    const res = await fetch(`${BASE}/runs/${id}/outcome${attempt ? '?attempt=' + attempt : ''}`);
    el.innerHTML = res.ok ? outcomeHtml(await res.json()) : '<p class="text-[12px] text-[#888]">불러오지 못했어요.</p>';
  }

  /* 흐름에 붙이면 고객 확인 화면 · 관리자 요청 품목으로 넘어갈 값 (부피는 기준표 기준) */
  async function showCustomer(id, el) {
    const res = await fetch(`${BASE}/runs/${id}/customer-items`);
    if (!res.ok) { el.textContent = '불러오지 못했어요.'; return; }
    const c = await res.json();
    const rows = c.items.map((it) => `<tr>
        <td class="py-0.5">${esc(it.name)}${it.review ? ' <span style="color:#d9701a">확인 필요</span>' : ''}${it.inAppCatalog ? '' : ' <span style="color:#ef5f3c">(앱 목록에 없음)</span>'}</td>
        <td>${esc(it.size)}</td><td class="text-right">${it.count}</td><td class="text-right">${it.standardCbm == null ? '— (미매핑)' : esc(it.standardCbm)}</td></tr>`).join('');
    el.innerHTML = `
      <p class="text-[11px] text-[#888]">품목 ${c.items.length}줄 · 기준표 부피 합계 <b style="color:#292929">${esc(c.standardTotalCbm)}㎥</b>. 확인 필요 줄은 고객이 확인 화면에서 고치게 돼요.</p>
      <details class="mt-1"><summary class="cursor-pointer text-[12px] font-semibold">품목별 (고객 확인 화면 · 관리자 요청 품목에 들어갈 값)</summary>
        <table class="mt-1 w-full text-[11px]"><thead><tr class="text-[#888]"><th class="text-left">품목</th><th class="text-left">크기</th><th class="text-right">개수</th><th class="text-right">부피㎥</th></tr></thead><tbody>${rows}</tbody></table>
      </details>`;
  }

  /* 정답 목록 대비: 정밀도 · 재현율 · F1 · CBM 오차, 품목별 정답/AI 개수 */
  async function showScore(id, set, el) {
    el.innerHTML = '';
    if (!set) return;
    const res = await fetch(`${BASE}/runs/${id}/score?set=${encodeURIComponent(set)}`);
    if (!res.ok) { el.textContent = '비교하지 못했어요.'; return; }
    const body = await res.json();
    const s = body.score;
    const exp = body.expectedLoad;
    const det = body.detectedLoad;
    const rows = s.rows.map((r) => `<tr style="${r.expected !== r.detected ? 'color:#d9701a' : ''}"><td class="py-0.5">${esc(r.name)}</td><td class="text-right">${r.expected}</td><td class="text-right">${r.detected}</td><td class="text-right">${esc(r.expectedCbm)}</td><td class="text-right">${esc(r.detectedCbm)}</td></tr>`).join('');
    el.innerHTML = `
      <div class="grid grid-cols-4 gap-1.5 text-center">
        <div class="rounded-[8px] bg-[#f6f7f6] py-1.5"><b class="block">${pct(s.precision)}</b><span class="text-[11px] text-[#888]">정밀도</span></div>
        <div class="rounded-[8px] bg-[#f6f7f6] py-1.5"><b class="block">${pct(s.recall)}</b><span class="text-[11px] text-[#888]">재현율</span></div>
        <div class="rounded-[8px] bg-[#f6f7f6] py-1.5"><b class="block">${pct(s.f1)}</b><span class="text-[11px] text-[#888]">F1</span></div>
        <div class="rounded-[8px] bg-[#f6f7f6] py-1.5"><b class="block">${s.cbmErrorPct > 0 ? '+' : ''}${esc(s.cbmErrorPct)}%</b><span class="text-[11px] text-[#888]">CBM 오차</span></div>
      </div>
      <p class="mt-2 text-[12px]" style="color:${exp && det && Number(exp.ton) === Number(det.ton) ? '#4a6741' : '#d9701a'}">톤: 정답 기준 <b>${exp ? esc(exp.ton) + '톤' : '—'}</b>${exp ? ' (' + esc(exp.label) + ', 적재 ' + esc(exp.loadCbm) + '㎥)' : ''} · AI <b>${det ? esc(det.ton) + '톤' : '—'}</b>${det ? ' (' + esc(det.label) + ', 적재 ' + esc(det.loadCbm) + '㎥)' : ''}</p>
      <p class="mt-2 text-[11px] text-[#888]">품목표 품목: 정답 ${s.expectedPieces}개 · AI ${s.detectedPieces}개 · 일치 ${s.matchedPieces}개 / CBM 정답 ${esc(s.expectedCbm)} · AI ${esc(s.detectedCbm)} / 표에 없는 물건: 정답 ${s.expectedOther} · AI 미매핑 ${s.detectedOther}</p>
      <details class="mt-1"><summary class="cursor-pointer text-[12px] font-semibold">품목별 비교</summary>
        <table class="mt-1 w-full text-[11px]"><thead><tr class="text-[#888]"><th class="text-left">품목</th><th class="text-right">정답</th><th class="text-right">AI</th><th class="text-right">정답㎥</th><th class="text-right">AI㎥</th></tr></thead><tbody>${rows}</tbody></table>
      </details>`;
  }

  function pretty(text) {
    try { return JSON.stringify(JSON.parse(text), null, 2); } catch (_) { return text; }
  }
})();
