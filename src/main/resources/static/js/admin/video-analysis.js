/* 영상 분석 테스트 (templates/admin/video-analysis/index.html)
 *  업로드 분석 POST /admin/video-analysis/videos (video, durationSec, uploadStartedAt)
 *  JSON 직접 입력 POST /admin/video-analysis/manual {raw, baseRunId}
 *  기록 보기 GET /runs/{id} · 다시 분석 POST /runs/{id}/rerun · 삭제 POST /runs/{id}/delete · 기준표 다시 읽기 POST /catalog/reload */
(function () {
  const root = document.getElementById('videoAnalysis');
  if (!root) return;
  const BASE = '/admin/video-analysis';
  const $ = (s, el) => (el || root).querySelector(s);
  const maxMb = Number(root.dataset.maxMb);
  const maxSec = Number(root.dataset.maxSec);
  const enabled = root.dataset.enabled === 'true';
  const METHOD = { STANDARD: 'AI 기준표 선택', EXACT: '이름 일치', ALIAS: '별칭', AMBIGUOUS: '복수 후보', UNMAPPED: '매핑 실패' };
  const SIZE = { '01': '소형', '02': '중형', '03': '대형' };

  const esc = (v) => String(v == null ? '' : v).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const num = (v, unit) => (v == null ? '—' : v + (unit || ''));
  const ms = (v) => (v == null ? '—' : v >= 1000 ? (v / 1000).toFixed(1) + '초' : v + 'ms');

  async function post(url, data) {
    try {
      const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data || {}) });
      return Object.assign({ ok: res.ok }, await res.json().catch(() => ({})));
    } catch (_) {
      return { ok: false, message: '서버에 연결하지 못했어요.' };
    }
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
      if (duration != null && duration > maxSec) problems.push(`${maxSec}초 초과`);
      info.textContent = `${file.name} · ${mb.toFixed(1)}MB · ${duration == null ? '길이 모름' : duration + '초'} · ${file.type || '형식 모름'}`;
      if (problems.length) setStatus('올릴 수 없어요: ' + problems.join(', '), true);
      else if (!enabled) setStatus('Gemini API 키가 없어 분석할 수 없어요. 아래 JSON 직접 입력은 쓸 수 있어요.', true);
      analyzeBtn.disabled = problems.length > 0 || !enabled;
    };
    video.onloadedmetadata = () => done(video.duration);
    video.onerror = () => done(NaN);
    video.src = url;
  });

  /* ---------- 업로드 분석 (진행률 표시를 위해 XHR) ---------- */
  analyzeBtn.addEventListener('click', () => {
    const file = fileInput.files[0];
    if (!file) return;
    const form = new FormData();
    form.append('video', file);
    if (duration != null) form.append('durationSec', duration);
    form.append('uploadStartedAt', Date.now());
    analyzeBtn.disabled = true;
    const xhr = new XMLHttpRequest();
    xhr.open('POST', BASE + '/videos');
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) setStatus(`업로드 중 ${Math.round((e.loaded / e.total) * 100)}%`);
    };
    xhr.upload.onload = () => setStatus('AI 분석 중… (영상 길이에 따라 수십 초 걸릴 수 있어요)');
    xhr.onload = () => {
      analyzeBtn.disabled = false;
      let body = {};
      try { body = JSON.parse(xhr.responseText); } catch (_) { /* 빈 응답 */ }
      if (xhr.status !== 200) { setStatus(body.message || `실패 (${xhr.status})`, true); return; }
      setStatus(body.success ? '분석 완료' : `분석 실패: ${body.errorMessage || body.errorCode}`, !body.success);
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

  /* ---------- 기준표 다시 읽기 ---------- */
  $('[data-reload]').addEventListener('click', async () => {
    const res = await post(BASE + '/catalog/reload');
    const el = $('[data-catalog-status]');
    if (!res.ok) { alert('다시 읽지 못했어요.'); return; }
    el.innerHTML = res.error ? `<span style="color:#ef5f3c">${esc(res.error)}</span>` : `품목 ${res.items}개 · 규격 ${res.rows}행 (다시 읽음)`;
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
      e.target.textContent = '분석 중…';
      const res = await post(`${BASE}/runs/${id}/rerun`);
      if (!res.ok) { alert(res.message || '다시 분석하지 못했어요.'); e.target.textContent = '다시 분석'; return; }
      location.reload();
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
    const lines = r ? r.lines : [];
    const head = `
      <div class="flex items-center justify-between">
        <h3 class="text-[15px] font-bold">결과 <span class="text-[12px] font-normal text-[#888]">${esc(run.id)}${run.baseRunId ? ' · 기준 ' + esc(run.baseRunId) : ''}</span></h3>
        <span class="rounded-full px-2 py-0.5 text-[11px] font-semibold" style="background:${run.success ? '#eef4ec' : '#fdeee9'};color:${run.success ? '#4a6741' : '#ef5f3c'}">${run.success ? '성공' : '실패 ' + esc(run.errorCode)}</span>
      </div>
      ${run.errorMessage ? `<p class="mt-2 text-[12px] text-[#ef5f3c]">${esc(run.errorMessage)}</p>` : ''}
      ${(run.parseErrors || []).map((m) => `<p class="mt-1 text-[12px] text-[#ef5f3c]">✕ ${esc(m)}</p>`).join('')}
      ${(run.parseWarnings || []).map((m) => `<p class="mt-1 text-[12px] text-[#d9701a]">! ${esc(m)}</p>`).join('')}`;

    const totals = r ? `
      <div class="mt-4 grid grid-cols-3 gap-2 text-center">
        <div class="rounded-[12px] bg-[#f6f7f6] py-2"><b class="block text-[16px]">${esc(r.totalCbm)}</b><span class="text-[11px] text-[#888]">총 부피(㎥)</span></div>
        <div class="rounded-[12px] bg-[#f6f7f6] py-2"><b class="block text-[16px]">${esc(r.totalKg)}</b><span class="text-[11px] text-[#888]">총 무게(kg)${r.kgMissing ? ' · ' + r.kgMissing + '줄 없음' : ''}</span></div>
        <div class="rounded-[12px] bg-[#f6f7f6] py-2"><b class="block text-[16px]">${r.reviewCount}</b><span class="text-[11px] text-[#888]">확인 필요</span></div>
      </div>
      <p class="mt-2 text-[11px] text-[#888]">표준 규격 기준 추정값이에요. 포장 · 분해 · 적재율은 견적 단계에서 따로 반영해요.</p>` : '';

    const items = lines.map((l) => {
      const m = l.item;
      const ai = m.ai;
      const std = m.standard;
      return `
        <li class="py-3">
          <div class="flex items-start justify-between gap-2">
            <div><b>${esc(ai.detectedName)}</b> <span class="text-[#888]">× ${ai.quantity} · ${esc(ai.sizeGrade || '크기 없음')}</span></div>
            <span class="shrink-0 rounded-full px-2 py-0.5 text-[11px] font-semibold" style="background:${m.needsReview ? '#fff4e8' : '#eef4ec'};color:${m.needsReview ? '#d9701a' : '#4a6741'}">${esc(METHOD[m.method] || m.method)}</span>
          </div>
          <p class="mt-1 text-[12px] text-[#555]">→ ${m.standardName ? esc(m.standardName) + ' ' + esc(SIZE[m.sizeCode]) + (std && std.sizeName ? ' (' + esc(std.sizeName) + ')' : '') : '<span style="color:#ef5f3c">미매핑</span>'}
            ${ai.standardName && ai.standardName !== m.standardName ? ` · AI 선택 "${esc(ai.standardName)}"` : ''}</p>
          <p class="mt-0.5 text-[12px] text-[#555]">부피 ${num(l.unitCbm)} × ${ai.quantity} = <b>${num(l.totalCbm, '㎥')}</b> · 무게 ${l.unitKg == null ? '기준 없음' : l.unitKg + 'kg × ' + ai.quantity + ' = <b>' + l.totalKg + 'kg</b>'}</p>
          ${std && std.basis ? `<p class="mt-0.5 text-[11px] text-[#888]">근거: ${esc(std.basis)}</p>` : ''}
          ${ai.evidence ? `<p class="mt-0.5 text-[11px] text-[#888]">단서: ${esc(ai.evidence)}</p>` : ''}
          ${(m.reasons || []).map((x) => `<p class="mt-0.5 text-[12px] text-[#d9701a]">! ${esc(x)}</p>`).join('')}
        </li>`;
    }).join('');

    const tokens = u ? `입력 ${u.promptTokens} · 출력 ${u.outputTokens} · 생각 ${u.thoughtsTokens} · 합계 ${u.totalTokens}${u.promptByModality && Object.keys(u.promptByModality).length ? ' (' + Object.entries(u.promptByModality).map(([k, v]) => k + ' ' + v).join(', ') + ')' : ''}` : '—';
    const cost = c ? (c.amount == null ? '단가 미설정' : `${c.amount} ${esc(c.currency)} (단가 기준일 ${esc(c.asOf)})`) : '—';
    const meta = `
      <dl class="mt-4 space-y-1.5 border-t border-[#eef0ee] pt-3 text-[12px]">
        ${run.video ? `<div class="flex justify-between gap-3"><dt class="text-[#888]">영상</dt><dd class="text-right">${esc(run.video.fileName)} · ${(run.video.sizeBytes / 1048576).toFixed(1)}MB · ${num(run.video.durationSec, '초')}</dd></div>` : ''}
        <div class="flex justify-between gap-3"><dt class="text-[#888]">모델</dt><dd class="text-right">${esc(run.model || '—')} ${run.modelVersion ? '(' + esc(run.modelVersion) + ')' : ''} · 프롬프트 ${esc(run.promptVersion)}</dd></div>
        <div class="flex justify-between gap-3"><dt class="text-[#888]">토큰</dt><dd class="text-right">${tokens}</dd></div>
        <div class="flex justify-between gap-3"><dt class="text-[#888]">비용</dt><dd class="text-right">${cost}</dd></div>
        <div class="flex justify-between gap-3"><dt class="text-[#888]">시간</dt><dd class="text-right">업로드 ${ms(t.uploadMs)} · 저장 ${ms(t.saveMs)} · API ${ms(t.apiMs)} · 매핑 ${ms(t.mappingMs)} · 전체 ${ms(t.totalMs)}${run.retries ? ' · 재시도 ' + run.retries : ''}</dd></div>
      </dl>`;

    const raw = run.rawResponse ? `
      <details class="mt-3"><summary class="cursor-pointer text-[13px] font-semibold">AI 원본 응답</summary>
        <pre class="mt-2 max-h-60 overflow-auto whitespace-pre-wrap rounded-[12px] bg-[#f6f7f6] p-3 text-[11px]">${esc(pretty(run.rawResponse))}</pre>
        <button type="button" data-edit-raw class="mt-2 text-[13px] font-semibold text-[#4a6741]">이 JSON 을 고쳐서 다시 계산</button>
      </details>` : '';

    box.innerHTML = head + totals + (lines.length ? `<ul class="mt-3 divide-y divide-[#eef0ee]">${items}</ul>` : '') + meta + raw;
    box.hidden = false;
    const edit = $('[data-edit-raw]', box);
    if (edit) edit.addEventListener('click', () => {
      $('[data-manual]').value = pretty(run.rawResponse);
      $('[data-base-run]').value = run.id;
      $('[data-manual]').scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
    box.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function pretty(text) {
    try { return JSON.stringify(JSON.parse(text), null, 2); } catch (_) { return text; }
  }
})();
