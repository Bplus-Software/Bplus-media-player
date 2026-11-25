const $ = (id) => document.getElementById(id);
// Bilingual UI strings: Arabic first, English second, chosen by the saved language.
const L = (ar, en) => (window.bplusPrefs && window.bplusPrefs.language === 'en') ? en : ar;
// Bidi isolation for numbers/filenames inside Arabic text (keeps 120% as 120%).
const iso = (s) => '\u2066' + s + '\u2069';
const player = $('player');
const dropHint = $('drop-hint');
const osd = $('osd');
const playlistEl = $('playlist');
const plList = $('pl-list');
const plEmpty = $('pl-empty');
const plCount = $('pl-count');
const seek = $('seek');
const seekFill = $('seek-fill');
const seekThumb = $('seek-thumb');
const seekBuffer = $('seek-buffer');
const tCur = $('t-cur');
const tDur = $('t-dur');
const bPlay = $('b-play');
const vol = $('vol');
const volFill = $('vol-fill');
const volNum = $('vol-num');
const bMute = $('b-mute');
const titleLabel = $('title-label');
const tbTitle = $('tb-title');
const bSubVis = $('b-subvis');
const ctxMenu = $('ctx-menu');
const menuPop = $('menu-pop');
const bigPlay = $('big-play');

let playlist = [];
let plSel = -1;
let current = -1;
let seeking = false;
let subTrackUrl = null;
const RATES = [0.5, 0.75, 1, 1.25, 1.5, 2];
let rateIdx = 2;

function fmt(t) {
  if (!isFinite(t) || t < 0) t = 0;
  const h = Math.floor(t / 3600), m = Math.floor((t % 3600) / 60), s = Math.floor(t % 60);
  return [h, m, s].map(n => String(n).padStart(2, '0')).join(':');
}

function showOsd(text) {
  osd.textContent = text;
  osd.style.opacity = 1;
  clearTimeout(showOsd._t);
  showOsd._t = setTimeout(() => { osd.style.opacity = 0; }, 1300);
}

/* ---------- Playlist ---------- */

function renderPlaylist() {
  plList.innerHTML = '';
  plEmpty.style.display = playlist.length ? 'none' : 'block';
  plCount.textContent = playlist.length;
  playlist.forEach((item, i) => {
    const li = document.createElement('li');
    if (i === current) li.classList.add('playing');
    if (i === plSel) li.classList.add('sel');
    const idx = document.createElement('span');
    idx.className = 'idx'; idx.textContent = i + 1;
    const nm = document.createElement('span');
    nm.className = 'nm'; nm.dir = 'auto'; nm.textContent = item.name; nm.title = item.name;
    const up = document.createElement('button');
    up.className = 'mv'; up.title = L('تحريك لأعلى', 'Move up'); up.innerHTML = '<svg data-ic="caret-up"></svg>';
    up.onclick = (ev) => { ev.stopPropagation(); moveItem(i, -1); };
    const dn = document.createElement('button');
    dn.className = 'mv'; dn.title = L('تحريك لأسفل', 'Move down'); dn.innerHTML = '<svg data-ic="caret-down"></svg>';
    dn.onclick = (ev) => { ev.stopPropagation(); moveItem(i, 1); };
    const rm = document.createElement('button');
    rm.className = 'rm'; rm.textContent = '✕'; rm.title = L('إزالة من القائمة', 'Remove from list');
    rm.onclick = (ev) => { ev.stopPropagation(); removeAt(i); };
    li.append(idx, nm, up, dn, rm);
    li.onclick = () => { plSel = i; playAt(i); };
    li.oncontextmenu = (ev) => {
      ev.preventDefault(); ev.stopPropagation(); plSel = i; renderPlaylist();
      showPop(ctxMenu, { getBoundingClientRect: () => ({ left: ev.clientX, right: ev.clientX, top: ev.clientY, bottom: ev.clientY }) }, (c) => {
        popItem(c, L('تشغيل', 'Play'), null, () => playAt(i), { icon: 'm-play' });
        popItem(c, L('إزالة من القائمة', 'Remove from list'), 'Del', () => removeAt(i), { icon: 'm-minus' });
        popSep(c);
        popItem(c, L('حذف الملف (سلة المحذوفات)', 'Delete file (Recycle Bin)'), 'Shift+Del', () => trashAt(i), { icon: 'm-trash' });
      });
    };
    plList.appendChild(li);
  });
  BPLUS_ICONS.hydrate(plList);
}

/* Same-format sibling expansion (Settings > Playback). */
async function expandSiblings(entries) {
  if (!window.bplusPrefs || !window.bplusPrefs.addSiblings || entries.length !== 1) return null;
  try {
    const sibs = await window.bplus.siblingEntries(entries[0].path);
    if (!Array.isArray(sibs) || sibs.length < 2) return null;
    const idx = sibs.findIndex(x => x.path === entries[0].path);
    return { entries: sibs, index: idx < 0 ? 0 : idx };
  } catch (e) { return null; }
}

async function addWithSiblings(entries) {
  const ex = await expandSiblings(entries);
  if (!ex) { addEntries(entries); return; }
  const base = playlist.length;
  playlist.push(...ex.entries);
  renderPlaylist();
  showOsd((window.bplusPrefs.language === 'ar' ? 'أُضيفت ملفات المجلد: ' : 'Folder files added: ') + ex.entries.length);
  playAt(base + ex.index);
}

function addEntries(entries) {
  const wasEmpty = playlist.length === 0;
  playlist.push(...entries);
  renderPlaylist();
  if (wasEmpty && playlist.length) playAt(0);
  else if (entries.length) showOsd(L('أُضيف ', 'Added ') + iso(String(entries.length)) + L(' ملف', ' file(s)'));
}

function removeAt(i) {
  if(i===current){savePosition();playbackSerial++;window.bplus.cancelPreparation();prep.hidden=true;}
  playlist.splice(i, 1);
  if (i === current) { activeResumeKey=null;current = -1; player.removeAttribute('src'); player.load(); setPlayingUI(false); titleLabel.textContent = ''; tbTitle.textContent = ''; }
  else if (i < current) current--;
  if (plSel === i) plSel = -1; else if (plSel > i) plSel--;
  renderPlaylist();
}
function moveItem(i, d) {
  const j = i + d;
  if (j < 0 || j >= playlist.length) return;
  [playlist[i], playlist[j]] = [playlist[j], playlist[i]];
  if (current === i) current = j; else if (current === j) current = i;
  if (plSel === i) plSel = j; else if (plSel === j) plSel = i;
  renderPlaylist();
}
async function trashAt(i) {
  const item = playlist[i]; if (!item) return;
  const en = window.BPLUS_SETTINGS && window.BPLUS_SETTINGS.prefs.language === 'en';
  hidePops();
  const r = await window.bplus.confirmDialog({
    title: en ? BPLUS_I18N.tr('Delete file') : 'حذف الملف',
    message: en ? BPLUS_I18N.tr('Move "{0}" to the Recycle Bin?').replace('{0}', item.name) : `نقل "${item.name}" إلى سلة المحذوفات؟`,
    detail: en ? BPLUS_I18N.tr('You can restore it from the Recycle Bin later.') : 'يمكنك استعادته لاحقًا من سلة المحذوفات.',
    buttons: en ? [BPLUS_I18N.tr('Move to Recycle Bin'), BPLUS_I18N.tr('Cancel')] : ['نقل إلى سلة المحذوفات', 'إلغاء']
  });
  if (r !== 0) return;
  const res = await window.bplus.trashItem(item.path);
  if (res && res.ok) { removeAt(i); showOsd(en ? 'Moved to Recycle Bin' : 'تم النقل إلى سلة المحذوفات'); }
  else showOsd(en ? 'Could not delete the file' : 'تعذر حذف الملف');
}
function sortPlaylist() {
  const P = (window.BPLUS_SETTINGS && window.BPLUS_SETTINGS.prefs) || {};
  const key = P.sortBy || 'name', dir = P.sortDir === -1 ? -1 : 1;
  const cur = playlist[current], sel = playlist[plSel];
  const val = (it) => key === 'size' ? (it.size || 0) : key === 'date' ? (it.mtime || 0) : key === 'type' ? ((it.name.split('.').pop() || '').toLowerCase()) : (it.name || '').toLowerCase();
  playlist.sort((a, b) => { const x = val(a), y = val(b); return (typeof x === 'string' ? x.localeCompare(y) : x - y) * dir; });
  current = playlist.indexOf(cur);
  plSel = playlist.indexOf(sel);
  renderPlaylist();
}

let resumePositions;try{resumePositions=JSON.parse(localStorage.getItem('bplus-resume')||'{}')}catch{resumePositions={}}
let activeResumeKey=null;
function resumeKey(p){return /^[a-z]:[\\/]/i.test(p)?p.replace(/\\/g,'/').toLowerCase():p}
function savePosition(){
 if(!activeResumeKey||!Number.isFinite(player.currentTime)||player.readyState<1)return;
 const time=player.ended?0:player.currentTime;
 resumePositions[activeResumeKey]={time,updated:Date.now()};
 const entries=Object.entries(resumePositions).sort((a,b)=>b[1].updated-a[1].updated).slice(0,500);
 resumePositions=Object.fromEntries(entries);try{localStorage.setItem('bplus-resume',JSON.stringify(resumePositions))}catch{}
}
let lastPositionSave=0;player.addEventListener('timeupdate',()=>{if(Date.now()-lastPositionSave>1500){savePosition();lastPositionSave=Date.now()}});
player.addEventListener('pause',savePosition);window.addEventListener('beforeunload',savePosition);
let playbackSerial=0;
const prep=document.createElement('div');prep.id='media-preparation';prep.hidden=true;prep.innerHTML='<strong></strong><span></span><button type="button">إلغاء / Cancel</button>';document.getElementById('video-wrap').append(prep);
prep.querySelector('button').onclick=()=>{playbackSerial++;window.bplus.cancelPreparation();prep.hidden=true;};
window.bplus.onPreparation(info=>{if(info.state==='idle'){prep.hidden=true;return}prep.hidden=false;prep.querySelector('strong').textContent=window.bplusPrefs?.language==='en'?'Preparing for playback locally':'تجهيز الملف للتشغيل على جهازك';prep.querySelector('span').textContent=info.name+' · '+fmt(info.seconds||0);});
function mediaFailure(code){const en=window.bplusPrefs?.language==='en';const messages={ENGINE_MISSING:en?'Offline decoder is missing. Reinstall B+.':'محرك التشغيل غير موجود. أعد تثبيت B+.',DISK_FULL:en?'Not enough free disk space for preparation.':'المساحة غير كافية لتجهيز الملف.',NO_READABLE_STREAM:en?'No readable media stream. The file may be damaged or protected.':'تعذر قراءة الوسائط. قد يكون الملف تالفًا أو محميًا.',CONVERSION_FAILED:en?'This file could not be prepared. The codec may be unsupported, damaged or protected.':'تعذر تجهيز الملف. قد يكون الترميز غير مدعوم أو الملف تالفًا أو محميًا.'};return messages[code]||(en?'Could not open this media file.':'تعذر فتح ملف الوسائط.');}
async function playAt(i, forcePlay=false) {
  if (i < 0 || i >= playlist.length) return;
  savePosition();const serial=++playbackSerial;
  if(typeof setOffArmed==='function'){cancelOff();setOffArmed(false);}
  current = i;const item = playlist[i];
  player.pause();player.removeAttribute('src');player.load();audioViz.hidden=true;
  activeResumeKey=null;
  const key=resumeKey(item.path);const resume=resumePositions[key]?.time||0;
  titleLabel.textContent = item.name;tbTitle.textContent = item.name + '  |  B+ Media Player';
  dropHint.classList.add('hidden');renderPlaylist();if(window.BPLUS_RECENT)window.BPLUS_RECENT.add(item);
  async function loadPrepared(result){
   if(serial!==playbackSerial)return;
   activeResumeKey=key;
   player.addEventListener('loadedmetadata',()=>{
    if(serial===playbackSerial&&resume>0&&resume<player.duration-.5){player.currentTime=resume;showOsd(L('استكمال من ','Resuming from ')+iso(fmt(resume)));}
   },{once:true});
   await new Promise((resolve,reject)=>{let timer;const clean=()=>{clearTimeout(timer);player.removeEventListener('loadedmetadata',ready);player.removeEventListener('error',failed)};const ready=()=>{clean();resolve()};const failed=()=>{clean();reject(Error('DECODE_FAILED'))};player.addEventListener('loadedmetadata',ready);player.addEventListener('error',failed);timer=setTimeout(failed,20000);player.src=result.url;});
   if(serial!==playbackSerial)return;
   if(forcePlay||window.bplusPrefs?.autoplay!==false)await player.play();
  }
  try{
   let result;
   try{result=await window.bplus.prepareMedia(item.path,false);}
   catch(engineError){if(serial!==playbackSerial)return;if(!String(engineError).includes('ENGINE_MISSING'))throw engineError;result={url:item.url,converted:false,engineOffline:true};}
   if(serial!==playbackSerial)return;
   try{await loadPrepared(result)}catch(error){if(serial!==playbackSerial)return;if(result.converted)throw error;try{result=await window.bplus.prepareMedia(item.path,true)}catch(engineError){if(result.engineOffline)throw error;throw engineError}await loadPrepared(result);}
   if(serial!==playbackSerial)return;
   const sub=window.bplusPrefs?.autoSub===false?null:await window.bplus.autoSubtitle(item.path);
   if(serial!==playbackSerial)return;
   if(sub)setSubtitle(sub,true);else clearSubtitle();
  }catch(error){if(serial!==playbackSerial||String(error).includes('CANCELED'))return;prep.hidden=false;prep.querySelector('strong').textContent=mediaFailure(String(error).split(':').pop().trim());prep.querySelector('span').textContent=item.name;setPlayingUI(false);}
}

function next() { const i = nextIndex(); if (i < playlist.length) playAt(i); }
function prev() { if (current > 0) playAt(current - 1); }

function togglePlay() {
  if (!player.getAttribute('src')) {if(current>=0)playAt(current,true);return;}
  if (player.paused) player.play().catch(()=>showOsd(mediaFailure('CONVERSION_FAILED'))); else player.pause();
}

function stepRate(d) {
  rateIdx = Math.min(RATES.length - 1, Math.max(0, rateIdx + d));
  setRate(rateIdx);
  showOsd(L('السرعة ', 'Speed ') + iso(RATES[rateIdx] + '×'));
}

function setRate(i) {
  rateIdx = i;
  player.playbackRate = RATES[i];
}

function toggleFullscreen() {
  if (document.fullscreenElement) document.exitFullscreen();
  else document.documentElement.requestFullscreen();
}

/* ---------- Clean fullscreen: hide all bars, peek on mouse move ---------- */
let pureFs = false, peekTimer = null;
function setPureFs(on) {
  pureFs = on;
  document.body.classList.toggle('fs-pure', on);
  if (!on) document.body.classList.remove('fs-peek');
}
function togglePureFullscreen() {
  const en = window.bplusPrefs?.language === 'en';
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen().then(() => { setPureFs(true); showOsd(en ? 'Clean fullscreen: move the mouse to show bars' : 'ملء الشاشة بدون قوائم: حرّك الماوس لإظهار القوائم'); }).catch(() => {});
  } else {
    setPureFs(!pureFs);
    showOsd(pureFs ? (en ? 'Clean view: bars hidden' : 'عرض نظيف: القوائم مخفية') : (en ? 'Bars visible' : 'القوائم ظاهرة'));
  }
}
document.addEventListener('fullscreenchange', () => { if (!document.fullscreenElement) setPureFs(false); });
let fsHideTimer = null, fsCursorTimer = null;
document.addEventListener('mousemove', (e) => {
  if (!pureFs || !document.fullscreenElement) return;
  const onBar = e.target.closest && e.target.closest('#titlebar, #controls');
  const edge = e.clientY <= 6 || e.clientY >= window.innerHeight - 6;
  if (onBar || edge) {
    document.body.classList.add('fs-peek');
    clearTimeout(peekTimer);
  } else if (document.body.classList.contains('fs-peek')) {
    clearTimeout(peekTimer);
    peekTimer = setTimeout(() => document.body.classList.remove('fs-peek'), 350);
  }
  document.body.classList.add('fs-cursor');
  clearTimeout(fsCursorTimer);
  fsCursorTimer = setTimeout(() => document.body.classList.remove('fs-cursor'), 1200);
});

/* ---------- Snapshot ---------- */

function takeSnapshot() {
  if (!player.src || !player.videoWidth) { showOsd(L('لا يوجد فيديو', 'No video')); return; }
  const c = document.createElement('canvas');
  c.width = player.videoWidth;
  c.height = player.videoHeight;
  c.getContext('2d').drawImage(player, 0, 0);
  window.bplus.saveSnapshot(c.toDataURL('image/png'));
  showOsd(L('حفظ اللقطة...', 'Saving snapshot...'));
}

/* ---------- Subtitles ---------- */

function srtToVtt(srt) {
  let t = srt.replace(/\r+/g, '').replace(/^﻿/, '');
  t = 'WEBVTT\n\n' + t
    .split(/\n\n+/)
    .map(block => block.replace(/^\d+\n/, '').replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, '$1.$2'))
    .filter(block => block.includes('-->'))
    .join('\n\n');
  return t;
}

function clearSubtitle() {
  const old = player.querySelector('track');
  if (old) old.remove();
  if (subTrackUrl) { URL.revokeObjectURL(subTrackUrl); subTrackUrl = null; }
  bSubVis.className = 'ctl txt off';
}

function setSubtitle(sub, silent) {
  clearSubtitle();
  let content = sub.content;
  if (sub.name.toLowerCase().endsWith('.srt') || !/^\s*WEBVTT/.test(content)) {
    content = srtToVtt(content);
  }
  subTrackUrl = URL.createObjectURL(new Blob([content], { type: 'text/vtt' }));
  const track = document.createElement('track');
  track.kind = 'subtitles';
  track.label = sub.name;
  track.srclang = 'ar';
  track.src = subTrackUrl;
  track.default = true;
  player.appendChild(track);
  track.track.mode = 'showing';
  bSubVis.className = 'ctl txt on';
  if (!silent) showOsd(L('الترجمة: ', 'Subtitles: ') + iso(sub.name));
}

function toggleSubtitleVisible() {
  const tracks = player.textTracks;
  if (!tracks.length) return;
  const t = tracks[0];
  if (t.mode === 'showing') { t.mode = 'hidden'; bSubVis.className = 'ctl txt off'; showOsd(L('إخفاء الترجمة', 'Subtitles hidden')); }
  else { t.mode = 'showing'; bSubVis.className = 'ctl txt on'; showOsd(L('إظهار الترجمة', 'Subtitles shown')); }
}

/* ---------- Audio cover art + visualizations ---------- */
const audioViz = $('audio-viz');
const audioCover = $('audio-cover');
const vizCanvas = $('viz-canvas');
let vizRaf = 0;
function stopViz() { if (vizRaf) cancelAnimationFrame(vizRaf); vizRaf = 0; vizCanvas.hidden = true; }
function startViz(style) {
  vizCanvas.hidden = false;
  let analyser = null;
  try { window.BPLUS_SETTINGS.audio(); analyser = window.BPLUS_SETTINGS.getAudio().analyser || null; } catch (e) {}
  const g2 = vizCanvas.getContext('2d');
  const freq = new Uint8Array(analyser ? analyser.frequencyBinCount : 1024);
  const ffreq = new Float32Array(analyser ? analyser.frequencyBinCount : 1024);
  const wave = new Uint8Array(analyser ? analyser.fftSize : 2048);
  const t0 = performance.now();
  const timeDomain = style === 'wave' || style === 'line';
  const N = style === 'spectrum' ? 40 : style === 'dots' ? 24 : (style === 'ripple' || style === 'logo') ? 5 : 110;
  // Log-spaced bands across the audible range, so every column maps to real
  // musical content: bass on the left, treble on the right, no dead zones.
  let sampleRate = 48000;
  try { sampleRate = (window.BPLUS_SETTINGS.getAudio().ctx || {}).sampleRate || 48000; } catch (e) {}
  const binHz = (sampleRate / 2) / freq.length;
  const fMin = 55, fMax = Math.min(16000, (sampleRate / 2) * 0.92);
  const bandBin = (f) => Math.max(1, Math.min(freq.length - 1, Math.round(f / binHz)));
  const bands = [];
  for (let i = 0; i < N; i++) {
    const lo = bandBin(fMin * Math.pow(fMax / fMin, i / N));
    const hi = Math.max(lo, bandBin(fMin * Math.pow(fMax / fMin, (i + 1) / N)) - 1);
    bands.push([lo, hi]);
  }
  const sm = new Float32Array(N);
  const bandLo = new Float32Array(N);
  const bandHi = new Float32Array(N);
  let freqSeeded = false;
  const hist = new Float32Array(N);
  const peakCap = new Float32Array(N);
  let rmsPeak = 0.05;
  let env = 0;
  let histPhase = 0;
  let seeded = false;
  let fade = 0; // soft in/out: appears on play / style switch, disappears on pause
  const sparks = []; // Circles style: dash particles bursting outward with the beat
  let prevBass = 0;
  function frame() {
    if (audioViz.hidden || vizCanvas.hidden) { vizRaf = 0; return; }
    vizRaf = requestAnimationFrame(frame);
    const fadeT = player.paused ? 0 : 1;
    fade += (fadeT - fade) * (fadeT > fade ? 0.11 : 0.055); // quick in, gentle out
    if (Math.abs(fadeT - fade) < 0.004) fade = fadeT;
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const cw = vizCanvas.clientWidth, chh = vizCanvas.clientHeight;
    if (!cw || !chh) return;
    const pw = Math.round(cw * dpr), ph = Math.round(chh * dpr);
    if (vizCanvas.width !== pw || vizCanvas.height !== ph) { vizCanvas.width = pw; vizCanvas.height = ph; }
    const W = vizCanvas.width, H = vizCanvas.height, mid = H / 2;
    g2.clearRect(0, 0, W, H);
    if (fade <= 0.003) return; // fully faded out: stay on rAF, draw nothing
    let accent = (getComputedStyle(document.documentElement).getPropertyValue('--accent-viz') || '').trim();
    if (!/^#[0-9a-f]{6}$/i.test(accent)) accent = '#2f89fc';
    let live = false;
    if (analyser && !player.paused) {
      try {
        if (timeDomain) { analyser.getByteTimeDomainData(wave); live = wave.some(v => Math.abs(v - 128) > 2); }
        else { analyser.getFloatFrequencyData(ffreq); live = ffreq.some(v => v > -95); }
      } catch (e) { live = false; }
    }
    const t = (performance.now() - t0) / 1000;
    g2.fillStyle = accent; g2.strokeStyle = accent;
    if (timeDomain) {
      // scrolling RMS history: voice-memo look with real musical dynamics
      let v;
      if (live) {
        let sum = 0;
        for (let j = 0; j < wave.length; j++) { const d = wave[j] - 128; sum += d * d; }
        const rms = Math.sqrt(sum / wave.length);
        rmsPeak = Math.max(rms, rmsPeak * 0.995, 0.05);
        const nv = Math.min(1, (rms / rmsPeak) * 0.9);
        env += (nv - env) * (nv > env ? 0.55 : 0.10); // snap up on beats, glide down smoothly
        v = env;
        if (!seeded) { hist.fill(v); seeded = true; } // no tapered ramp on style switch
      } else if (!player.paused) {
        v = 0.12 + 0.05 * Math.sin(t * 1.15) + 0.03 * Math.sin(t * 2.6);
        env = v;
      } else {
        v = hist[N - 1];
      }
      if (!player.paused) {
        hist[N - 1] += (v - hist[N - 1]) * 0.45;
        histPhase ^= 1;
        if (histPhase) hist.copyWithin(0, 1); // half-speed scroll: smooth, not jittery
      }
      const hv = i => (hist[Math.max(0, i - 1)] + 2 * hist[i] + hist[Math.min(N - 1, i + 1)]) / 4;
      if (style === 'wave') {
        g2.beginPath();
        for (let i = 0; i < N; i++) { const x = i / (N - 1) * W; i ? g2.lineTo(x, mid - hv(i) * H * 0.34) : g2.moveTo(x, mid - hv(i) * H * 0.34); }
        for (let i = N - 1; i >= 0; i--) g2.lineTo(i / (N - 1) * W, mid + hv(i) * H * 0.34);
        g2.closePath();
        g2.globalAlpha = 0.75 * fade; g2.fill();
        g2.globalAlpha = fade; g2.lineWidth = 1.6 * dpr; g2.lineJoin = 'round';
        g2.beginPath();
        for (let i = 0; i < N; i++) { const x = i / (N - 1) * W; i ? g2.lineTo(x, mid - hv(i) * H * 0.34) : g2.moveTo(x, mid - hv(i) * H * 0.34); }
        g2.stroke();
      } else {
        // line: calm envelope graph
        g2.globalAlpha = fade;
        g2.lineWidth = 2.2 * dpr; g2.lineJoin = 'round'; g2.lineCap = 'round';
        g2.beginPath();
        for (let i = 0; i < N; i++) { const x = i / (N - 1) * W; const y = H * 0.78 - hv(i) * H * 0.55; i ? g2.lineTo(x, y) : g2.moveTo(x, y); }
        g2.stroke();
        g2.globalAlpha = 0.2 * fade; g2.lineWidth = dpr;
        g2.beginPath(); g2.moveTo(0, H * 0.78); g2.lineTo(W, H * 0.78); g2.stroke();
      }
      g2.globalAlpha = 1;
      return;
    }
    // frequency styles: absolute dynamics (beats punch through) with a slow
    // per-band AGC that only balances long-run loudness, so loud steady bands
    // keep moving and quiet treble bands stay visible across the whole width.
    const tv = new Float32Array(N);
    for (let i = 0; i < N; i++) {
      if (live) {
        // per-band rolling min-max window (float dB data): each band's own
        // recent range maps to 0..1 with ~1-3s adaptation, so bass, mids and
        // treble all move with the music - loud bands can't pin at the top and
        // quiet bands can't flatline at zero.
        const [lo, hi] = bands[i];
        let peak = -Infinity;
        for (let k = lo; k <= hi; k++) { const b = ffreq[k]; if (b > peak) peak = b; }
        if (!freqSeeded) { bandLo[i] = peak - 3; bandHi[i] = peak + 3; }
        if (peak > bandHi[i]) bandHi[i] = peak; else bandHi[i] -= 0.05;
        if (peak < bandLo[i]) bandLo[i] = peak; else bandLo[i] += 0.03;
        const range = Math.max(bandHi[i] - bandLo[i], 6);
        tv[i] = Math.min(1, Math.max(0, (peak - bandLo[i]) / range));
      } else if (!player.paused) {
        const env = Math.sin(Math.PI * i / (N - 1));
        tv[i] = env * (0.28 + 0.10 * Math.sin(t * 1.05 + i * 0.3)) + 0.05;
        freqSeeded = false;
      } else {
        tv[i] = sm[i]; // frozen while paused, fading out
      }
    }
    if (live) freqSeeded = true;
    if (!player.paused) {
      for (let i = 0; i < N; i++) {
        const v = tv[i];
        sm[i] += (v - sm[i]) * (v > sm[i] ? 0.62 : 0.16); // fast attack on the beat, smooth decay
      }
      // falling peak caps: hold each band's recent peak, then glide down
      for (let i = 0; i < N; i++) peakCap[i] = Math.max(sm[i], peakCap[i] - 0.009);
    }
    if (style === 'spectrum') {
      const bw = W / N, r = Math.min(2.5 * dpr, bw * 0.22);
      for (let i = 0; i < N; i++) {
        const h = Math.max(2.5 * dpr, sm[i] * H * 0.78);
        g2.globalAlpha = fade * (0.45 + 0.55 * Math.min(1, sm[i] * 1.5));
        g2.beginPath();
        g2.roundRect(i * bw + bw * 0.27, mid - h / 2, bw * 0.46, h, r);
        g2.fill();
        const ch = Math.max(h + 2 * dpr, peakCap[i] * H * 0.78);
        g2.globalAlpha = 0.9 * fade;
        g2.fillRect(i * bw + bw * 0.27, mid - ch / 2 - dpr, bw * 0.46, 2 * dpr);
      }
      g2.globalAlpha = 1;
    } else if (style === 'dots') {
      // LED columns of round dots, centered like the bars: same accent, same
      // heights, lit dots over faint unlit ones - one visual language.
      const bw = W / N;
      const maxDots = Math.max(6, Math.floor(H * 0.78 / (9 * dpr)));
      const stepY = (H * 0.78) / maxDots;
      const rD = Math.min(bw * 0.20, stepY * 0.34, 3.4 * dpr);
      const half = (maxDots - 1) / 2;
      for (let i = 0; i < N; i++) {
        const lit = sm[i] * half + 0.5;
        for (let d = 0; d < maxDots; d++) {
          const on = Math.abs(d - half) < lit;
          g2.globalAlpha = fade * (on ? 0.35 + 0.65 * Math.min(1, sm[i] * 1.4) : 0.10);
          const y = mid + (d - half) * stepY;
          g2.beginPath(); g2.arc(i * bw + bw / 2, y, rD, 0, 7); g2.fill();
        }
      }
      g2.globalAlpha = 1;
    } else if (style === 'ripple') {
      // Circles: clean concentric rings only, expanding with the music.
      const cx = W / 2, cy = mid;
      const maxR = Math.min(W, H) * 0.40; // sized so a fully pulsed ring stays on canvas
      let lvl = 0; for (let i = 0; i < N; i++) lvl += sm[i]; lvl /= N;
      for (let i = 0; i < N; i++) {
        const base = maxR * (0.26 + 0.74 * i / (N - 1));
        const r = base * (1 + sm[i] * 0.22); // ring expands with its band
        g2.globalAlpha = fade * (0.18 + 0.80 * Math.min(1, sm[i] * 1.4)); // brightness follows the hit
        g2.lineWidth = (2.6 + 2.4 * Math.min(1, sm[i] * 1.4)) * (1 - 0.35 * i / (N - 1)) * dpr; // beats draw thicker
        g2.beginPath(); g2.arc(cx, cy, r, 0, 7); g2.stroke();
      }
      g2.globalAlpha = 1;
    } else if (style === 'logo') {
      // B+ Logo: the B+ mark in the centre with rays bursting outward around it.
      const cx = W / 2, cy = mid;
      const maxR = Math.min(W, H) * 0.40;
      let lvl = 0; for (let i = 0; i < N; i++) lvl += sm[i]; lvl /= N;
      // sparks: short round dashes around the rings, bursting outward on the
      // beat and with the overall level, fading as they travel
      if (!player.paused) {
        const bursts = (sm[0] - prevBass > 0.10) ? 4 : (Math.random() < lvl * 0.55 ? 1 : 0);
        for (let b = 0; b < bursts && sparks.length < 40; b++) {
          sparks.push({ a: Math.random() * Math.PI * 2, r: maxR * (0.30 + Math.random() * 0.35), v: (1.1 + Math.random() * 1.6) * dpr, len: (7 + Math.random() * 14) * dpr, w: (2.4 + Math.random() * 1.6) * dpr, life: 1 });
        }
      }
      prevBass = sm[0];
      g2.lineCap = 'round';
      for (let i = sparks.length - 1; i >= 0; i--) {
        const s = sparks[i];
        if (!player.paused) { s.r += s.v; s.life -= 0.022; }
        if (s.life <= 0 || s.r - s.len > maxR * 1.5) { sparks.splice(i, 1); continue; }
        g2.globalAlpha = fade * s.life * 0.85;
        g2.lineWidth = s.w;
        const dx = Math.cos(s.a), dy = Math.sin(s.a);
        g2.beginPath(); g2.moveTo(cx + dx * s.r, cy + dy * s.r); g2.lineTo(cx + dx * (s.r + s.len), cy + dy * (s.r + s.len)); g2.stroke();
      }
      g2.lineCap = 'butt';
      // B+ mark at the centre, pulsing gently with the overall level
      const fs = maxR * 0.34 * (1 + lvl * 0.06);
      g2.globalAlpha = fade * (0.55 + 0.45 * Math.min(1, lvl * 1.6));
      g2.font = `700 ${fs}px Cairo, 'Segoe UI', sans-serif`;
      g2.textAlign = 'center'; g2.textBaseline = 'middle';
      g2.fillText('B+', cx, cy + fs * 0.04);
      g2.globalAlpha = 1;
    }
  }
  if (!vizRaf) frame();
}

/* ---------- Cover-art equalizer bars (audio-reactive when possible) ---------- */
let eqRaf = 0;
function stopCoverEq() { if (eqRaf) cancelAnimationFrame(eqRaf); eqRaf = 0; }
function startCoverEq() {
  stopCoverEq();
  const eq = $('eq');
  const spans = eq.querySelectorAll('span');
  let analyser = null;
  try { window.BPLUS_SETTINGS.audio(); analyser = window.BPLUS_SETTINGS.getAudio().analyser || null; } catch (e) {}
  if (!analyser) return; // no audio graph: keep the gentle CSS fallback animation
  const freq = new Uint8Array(analyser.frequencyBinCount);
  let sampleRate = 48000;
  try { sampleRate = (window.BPLUS_SETTINGS.getAudio().ctx || {}).sampleRate || 48000; } catch (e) {}
  const binHz = (sampleRate / 2) / freq.length;
  const fMin = 55, fMax = Math.min(16000, (sampleRate / 2) * 0.92);
  const bandBin = (f) => Math.max(1, Math.min(freq.length - 1, Math.round(f / binHz)));
  const N = spans.length;
  const bands = [];
  for (let i = 0; i < N; i++) {
    const lo = bandBin(fMin * Math.pow(fMax / fMin, i / N));
    bands.push([lo, Math.max(lo, bandBin(fMin * Math.pow(fMax / fMin, (i + 1) / N)) - 1)]);
  }
  const sm = new Float32Array(N), bandPeak = new Float32Array(N);
  spans.forEach(s => { s.style.animation = 'none'; });
  function frame() {
    if (audioViz.hidden || eq.style.display === 'none' || !vizCanvas.hidden) { eqRaf = 0; return; }
    eqRaf = requestAnimationFrame(frame);
    if (player.paused) return;
    try { analyser.getByteFrequencyData(freq); } catch (e) { return; }
    for (let i = 0; i < N; i++) {
      const [lo, hi] = bands[i];
      let sum = 0, peak = 0;
      for (let k = lo; k <= hi; k++) { const b = freq[k]; sum += b; if (b > peak) peak = b; }
      const v = Math.sqrt((sum / (hi - lo + 1) * 0.5 + peak * 0.5) / 255);
      bandPeak[i] = Math.max(v, bandPeak[i] * 0.9985, 0.35);
      const t = Math.min(1, (v / bandPeak[i]) * 0.95);
      sm[i] += (t - sm[i]) * (t > sm[i] ? 0.62 : 0.16);
      spans[i].style.height = (14 + sm[i] * 86) + '%';
    }
  }
  eqRaf = requestAnimationFrame(frame);
}
async function updateAudioViz() {
  stopViz();
  $('eq').style.display = '';
  audioCover.hidden = true;
  audioViz.classList.remove('viz-live', 'viz-s', 'viz-m', 'viz-l');
  const vizSize = (window.bplusPrefs && window.bplusPrefs.vizSize) || 'small';
  audioViz.classList.add(vizSize === 'large' ? 'viz-l' : vizSize === 'medium' ? 'viz-m' : 'viz-s');
  if (!player.getAttribute('src') || player.videoWidth > 0) { audioViz.hidden = true; return; }
  const item = playlist[current];
  if (!item) { audioViz.hidden = true; return; }
  audioViz.hidden = false;
  const style = (window.bplusPrefs && window.bplusPrefs.vizStyle) || 'cover';
  if (style === 'cover') {
    $('viz-box').hidden = true;
    startCoverEq();
    try {
      const cover = await window.bplus.coverExtract(item.path);
      if (cover && !audioViz.hidden && playlist[current] === item) { audioCover.src = cover; audioCover.hidden = false; }
    } catch {}
  } else {
    $('viz-box').hidden = false;
    stopCoverEq();
    $('eq').style.display = 'none';
    audioViz.classList.add('viz-live');
    startViz(style);
  }
}
window.bplusUpdateViz = updateAudioViz;
player.addEventListener('loadedmetadata', updateAudioViz);
player.addEventListener('play', () => audioViz.classList.remove('paused'));
player.addEventListener('pause', () => audioViz.classList.add('paused'));

/* ---------- Repeat + shuffle (playlist panel) ---------- */
let repeatMode = localStorage.getItem('bplus-repeat') || (window.bplusPrefs && window.bplusPrefs.loop ? 'one' : 'off');
let shuffleOn = localStorage.getItem('bplus-shuffle') === '1';
function syncRepeatShuffleUI() {
  const b = $('pl-repeat');
  b.classList.toggle('on', repeatMode !== 'off');
  b.querySelector('.rep-one').hidden = repeatMode !== 'one';
  b.title = L('تكرار: ', 'Repeat: ') + (repeatMode === 'off' ? L('إيقاف', 'Off') : repeatMode === 'all' ? L('الكل', 'All') : L('واحد', 'One'));
  $('pl-shuffle').classList.toggle('on', shuffleOn);
  $('pl-shuffle').title = shuffleOn ? L('عشوائي: يعمل', 'Shuffle: on') : L('تشغيل عشوائي', 'Shuffle');
  player.loop = repeatMode === 'one';
}
window.bplusSetLoop = (on) => { repeatMode = on ? 'one' : 'off'; localStorage.setItem('bplus-repeat', repeatMode); syncRepeatShuffleUI(); };
function nextIndex() {
  if (shuffleOn && playlist.length > 1) {
    let i = current;
    while (i === current) i = Math.floor(Math.random() * playlist.length);
    return i;
  }
  return current + 1;
}
$('pl-repeat').onclick = () => {
  repeatMode = repeatMode === 'off' ? 'all' : repeatMode === 'all' ? 'one' : 'off';
  localStorage.setItem('bplus-repeat', repeatMode);
  syncRepeatShuffleUI();
  showOsd(L('التكرار: ', 'Repeat: ') + (repeatMode === 'off' ? L('إيقاف', 'Off') : repeatMode === 'all' ? L('الكل', 'All') : L('واحد', 'One')));
};
$('pl-sort').onclick = (ev) => {
  ev.stopPropagation();
  const P = window.BPLUS_SETTINGS.prefs;
  showPop(menuPop, $('pl-sort'), (c) => {
    popHead(c, L('ترتيب القائمة', 'Sort playlist'));
    [['name', L('الاسم', 'Name'), 'm-text'], ['date', L('تاريخ التعديل', 'Date modified'), 'm-cal'], ['size', L('الحجم', 'Size'), 'm-size'], ['type', L('النوع', 'Type'), 'm-tag']].forEach(([k, label, ic]) => {
      popItem(c, label, null, () => { P.sortBy = k; window.BPLUS_SETTINGS.save(); sortPlaylist(); }, { checkable: true, checked: P.sortBy === k, icon: ic });
    });
    popSep(c);
    popItem(c, L('تصاعدي', 'Ascending'), null, () => { P.sortDir = 1; window.BPLUS_SETTINGS.save(); sortPlaylist(); }, { checkable: true, checked: P.sortDir !== -1, icon: 'm-up' });
    popItem(c, L('تنازلي', 'Descending'), null, () => { P.sortDir = -1; window.BPLUS_SETTINGS.save(); sortPlaylist(); }, { checkable: true, checked: P.sortDir === -1, icon: 'm-down' });
    popSep(c);
    popItem(c, L('عكس الترتيب', 'Reverse order'), null, () => { const cur = playlist[current], sel = playlist[plSel]; playlist.reverse(); current = playlist.indexOf(cur); plSel = playlist.indexOf(sel); renderPlaylist(); }, { icon: 'm-swap' });
    popItem(c, L('خلط الترتيب', 'Shuffle order'), null, () => { const cur = playlist[current], sel = playlist[plSel]; for (let i = playlist.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [playlist[i], playlist[j]] = [playlist[j], playlist[i]]; } current = playlist.indexOf(cur); plSel = playlist.indexOf(sel); renderPlaylist(); }, { icon: 'm-shuf' });
  });
};
$('pl-shuffle').onclick = () => {
  shuffleOn = !shuffleOn;
  localStorage.setItem('bplus-shuffle', shuffleOn ? '1' : '0');
  syncRepeatShuffleUI();
  showOsd(shuffleOn ? L('تشغيل عشوائي', 'Shuffle on') : L('تشغيل بالترتيب', 'Playing in order'));
};
syncRepeatShuffleUI();

/* ---------- Playback UI ---------- */

function setPlayingUI(playing) {
  bPlay.querySelector('.ic-play').style.display = playing ? 'none' : '';
  bPlay.querySelector('.ic-pause').style.display = playing ? '' : 'none';
  const bpPlay = bigPlay.querySelector('.bp-play');
  const bpPause = bigPlay.querySelector('.bp-pause');
  clearTimeout(setPlayingUI._t);
  if (playing) {
    bpPlay.style.display = 'none'; bpPause.style.display = '';
    bigPlay.classList.remove('bp-fade');
    bigPlay.hidden = !player.src;
    setPlayingUI._t = setTimeout(() => bigPlay.classList.add('bp-fade'), 550);
  } else {
    bpPause.style.display = 'none'; bpPlay.style.display = '';
    bigPlay.classList.remove('bp-fade');
    bigPlay.hidden = !player.src;
  }
}

function updateSeekUI() {
  const pct = isFinite(player.duration) && player.duration > 0 ? (player.currentTime / player.duration) * 100 : 0;
  seekFill.style.width = pct + '%';
  seekThumb.style.left = pct + '%';
  if (!seeking && isFinite(player.duration)) seek.value = Math.round((player.currentTime / player.duration) * 1000);
  tCur.textContent = fmt(player.currentTime);
}

player.addEventListener('play', () => setPlayingUI(true));
player.addEventListener('pause', () => setPlayingUI(false));
/* Shut down the computer after the current file. Off by default and reset whenever another file loads. */
let offArmed=false,offTimer=0;
const offBtn=$('b-off'),offDlg=$('off-dlg');
function setOffArmed(v){offArmed=!!v;offBtn.classList.toggle('armed',offArmed);offBtn.title=offArmed?L('إيقاف الكمبيوتر بعد هذا الفيديو: مفعّل','Shutdown after this video: on'):L('إيقاف الكمبيوتر بعد انتهاء هذا الفيديو','Shut down computer after this video');}
function cancelOff(){clearInterval(offTimer);offTimer=0;offDlg.hidden=true;}
function startOffCountdown(){
  let n=30;offDlg.hidden=false;
  $('off-t').textContent=L('سيتم إيقاف الكمبيوتر خلال','Shutting down the computer in');
  $('off-cancel').textContent=L('إلغاء','Cancel');
  const tick=()=>{$('off-n').textContent=n;if(n<=0){cancelOff();window.bplus.shutdown&&window.bplus.shutdown();return;}n--;};
  tick();offTimer=setInterval(tick,1000);
}
offBtn.addEventListener('click',()=>{setOffArmed(!offArmed);showOsd(offArmed?L('سيتم إيقاف الكمبيوتر بعد هذا الفيديو','Shutdown after this video: on'):L('تم إلغاء إيقاف الكمبيوتر','Shutdown after this video: off'));});
$('off-cancel').addEventListener('click',()=>{cancelOff();setOffArmed(false);showOsd(L('تم إلغاء إيقاف الكمبيوتر','Shutdown after this video: off'));});
document.addEventListener('keydown',e=>{if(!offDlg.hidden&&e.key==='Escape'){e.stopPropagation();$('off-cancel').click();}},true);
setOffArmed(false);
player.addEventListener('ended', () => {
  if (offArmed) { startOffCountdown(); return; }
  if (repeatMode === 'one') return; /* native loop replays */
  if (repeatMode === 'all' && !shuffleOn && current >= playlist.length - 1) { playAt(0); return; }
  if (repeatMode === 'all' || window.bplusPrefs?.autoNext !== false) next();
});
player.addEventListener('timeupdate', updateSeekUI);

/* ---------- Seek hover thumbnail (video only, offline) ---------- */
const seekTip = $('seek-tip');
const tipCanvas = $('seek-tip-canvas');
const tipCtx = tipCanvas.getContext('2d');
const tipTime = seekTip.querySelector('.tip-time');
const thumbVideo = document.createElement('video');
thumbVideo.muted = true; thumbVideo.preload = 'auto'; thumbVideo.style.display = 'none';
document.body.append(thumbVideo);
let tipTimer = 0, tipPending = -1;
player.addEventListener('loadedmetadata', () => {
  const src = player.currentSrc || player.getAttribute('src') || '';
  if (player.videoWidth > 0 && src && src !== thumbVideo.getAttribute('src')) thumbVideo.src = src;
  if (player.videoWidth === 0) { thumbVideo.removeAttribute('src'); thumbVideo.load(); hideSeekTip(); }
});
function hideSeekTip() { seekTip.hidden = true; tipPending = -1; clearTimeout(tipTimer); }
seek.addEventListener('mousemove', (e) => {
  if (!window.bplusPrefs || !window.bplusPrefs.thumbPreview || player.videoWidth === 0 || !isFinite(player.duration) || !thumbVideo.getAttribute('src')) { hideSeekTip(); return; }
  const r = seek.getBoundingClientRect();
  const frac = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width));
  tipPending = frac * player.duration;
  const x = Math.min(r.width - 86, Math.max(86, frac * r.width));
  seekTip.style.left = x + 'px';
  seekTip.hidden = false;
  tipTime.textContent = fmt(tipPending) + ' / ' + fmt(player.duration);
  clearTimeout(tipTimer);
  tipTimer = setTimeout(() => { try { thumbVideo.currentTime = tipPending; } catch (e2) {} }, 130);
});
thumbVideo.addEventListener('seeked', () => {
  if (seekTip.hidden || tipPending < 0) return;
  const vw = thumbVideo.videoWidth, vh = thumbVideo.videoHeight;
  if (!vw || !vh) return;
  const scale = Math.max(tipCanvas.width / vw, tipCanvas.height / vh);
  const dw = vw * scale, dh = vh * scale;
  tipCtx.drawImage(thumbVideo, (tipCanvas.width - dw) / 2, (tipCanvas.height - dh) / 2, dw, dh);
});
seek.addEventListener('mouseleave', hideSeekTip);
player.addEventListener('durationchange', () => { tDur.textContent = fmt(player.duration); updateSeekUI(); });
player.addEventListener('progress', () => {
  try {
    if (player.buffered.length && isFinite(player.duration) && player.duration > 0) {
      seekBuffer.style.width = (player.buffered.end(player.buffered.length - 1) / player.duration) * 100 + '%';
    }
  } catch (_) {}
});
player.addEventListener('error', () => {
  if (player.error && playlist[current]) showOsd(L('تعذر تشغيل الملف: ', 'Could not play: ') + iso(playlist[current].name));
});

seek.addEventListener('input', () => {
  seeking = true;
  const pct = seek.value / 10;
  seekFill.style.width = pct + '%';
  seekThumb.style.left = pct + '%';
});
seek.addEventListener('change', () => {
  if (isFinite(player.duration)) player.currentTime = (seek.value / 1000) * player.duration;
  seeking = false;
});

function updateVolUI() {
  volFill.style.width = (vol.value / 2) + '%';
  volNum.textContent = vol.value;
  const muted = player.muted || vol.value === '0';
  bMute.querySelector('.ic-vol').style.display = muted ? 'none' : '';
  bMute.querySelector('.ic-muted').style.display = muted ? '' : 'none';
}

vol.addEventListener('input', () => {
  showOsd(L('الصوت ', 'Volume ') + iso(vol.value + '%'));
  const v = +vol.value;
  player.volume = Math.min(v, 100) / 100;
  player.muted = v === 0;
  window.bplusVolumeBoost = v > 100 ? v / 100 : 1;
  if (window.BPLUS_SETTINGS) { if (v > 100) window.BPLUS_SETTINGS.audio(); if (window.BPLUS_SETTINGS.applyAudio) window.BPLUS_SETTINGS.applyAudio(); }
  updateVolUI();
});

/* Mouse wheel over the video area adjusts volume. */
$('video-wrap').addEventListener('wheel', (e) => {
  e.preventDefault();
  if (!e.deltaY) return;
  const up = e.deltaY < 0;
  if (up && player.muted) player.muted = false;
  vol.value = Math.min(200, Math.max(0, +vol.value + (up ? 5 : -5)));
  vol.dispatchEvent(new Event('input'));
}, { passive: false });

bMute.onclick = () => { player.muted = !player.muted; updateVolUI(); };
bPlay.onclick = togglePlay;
bigPlay.onclick = togglePlay;
$('b-next').onclick = next;
$('b-prev').onclick = prev;
$('b-stop').onclick = () => { playbackSerial++;window.bplus.cancelPreparation();prep.hidden=true;audioViz.hidden=true;player.pause(); player.currentTime = 0; updateSeekUI(); };
bSubVis.addEventListener('contextmenu', (e) => { e.preventDefault(); window.bplus.openSubtitleDialog(); });
bSubVis.onclick = toggleSubtitleVisible;
$('b-snap').onclick = takeSnapshot;
$('b-pl').onclick = () => playlistEl.classList.toggle('hidden');
$('tb-pin').onclick = async () => {
  const P = window.BPLUS_SETTINGS && window.BPLUS_SETTINGS.prefs;
  const on = await window.bplus.setAlwaysOnTop(!(P && P.alwaysOnTop));
  if (P) { P.alwaysOnTop = on; window.BPLUS_SETTINGS.save(); }
  $('tb-pin').classList.toggle('on', on);
};
$('tb-open').onclick = () => window.bplus.openFiles();
$('tb-recent').onclick = (ev) => { ev.stopPropagation(); if (window.BPLUS_RECENT) window.BPLUS_RECENT.menu($('tb-recent')); };
$('pl-add').onclick = () => window.bplus.openFiles();
$('pl-clear').onclick = () => { playbackSerial++;window.bplus.cancelPreparation();prep.hidden=true;audioViz.hidden=true;savePosition();activeResumeKey=null;playlist = []; current = -1; player.removeAttribute('src'); player.load(); setPlayingUI(false); titleLabel.textContent = ''; tbTitle.textContent = ''; renderPlaylist(); };

player.addEventListener('click', togglePlay);
player.addEventListener('dblclick',()=>{if(!player.getAttribute('src'))window.bplus.openFiles();else toggleFullscreen()});
dropHint.style.pointerEvents='none';
$('video-wrap').addEventListener('dblclick',e=>{if(e.target!==player&&!player.getAttribute('src'))window.bplus.openFiles()});

/* ---------- Popover menus ---------- */

function popItem(container, label, shortcut, fn, opts = {}) {
  const d = document.createElement('div');
  d.className = 'pop-item' + (opts.checked ? ' check' : '');
  if (opts.checkable) {
    const ck = document.createElement('span');
    ck.className = 'ck'; ck.textContent = opts.checked ? '✓' : '';
    d.appendChild(ck);
  }
  if (opts.icon) {
    const ic = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    ic.setAttribute('data-ic', opts.icon); ic.setAttribute('class', 'pop-ic');
    d.appendChild(ic); BPLUS_ICONS.hydrate(d);
  }
  const l = document.createElement('span');
  if (/[\u0600-\u06FF]/.test(label) && /[A-Za-z]/.test(label)) {
    // Latin words inside Arabic text (e.g. B+) keep their own direction
    label.split(/([A-Za-z][A-Za-z0-9+.\-]*)/).forEach((part) => {
      if (/^[A-Za-z]/.test(part)) { const b = document.createElement('span'); b.textContent = part; b.style.cssText = 'direction:ltr;unicode-bidi:isolate;display:inline-block'; l.appendChild(b); }
      else if (part) l.appendChild(document.createTextNode(part));
    });
  } else l.textContent = label;
  d.appendChild(l);
  if (shortcut) {
    const sc = document.createElement('span');
    sc.className = 'sc'; sc.textContent = shortcut;
    d.appendChild(sc);
  }
  d.onclick = (ev) => { ev.stopPropagation(); hidePops(); fn(); };
  container.appendChild(d);
}

function popSep(container) {
  const hr = document.createElement('hr');
  hr.className = 'pop-sep';
  container.appendChild(hr);
}

function popHead(container, text) {
  const d = document.createElement('div');
  d.className = 'pop-head';
  d.textContent = text;
  container.appendChild(d);
}

function showPop(container, anchor, buildFn) {
  hidePops();
  container.innerHTML = '';
  container.dir = document.documentElement.dir === 'ltr' ? 'ltr' : 'rtl';
  buildFn(container);
  container.hidden = false;
  const r = anchor.getBoundingClientRect();
  const mw = container.offsetWidth, mh = container.offsetHeight;
  let x = document.dir === 'rtl' ? r.right - mw : r.left;
  let y = r.bottom + 2;
  if (y + mh > innerHeight - 8) y = r.top - mh - 2;
  container.style.left = Math.max(4, Math.min(x, innerWidth - mw - 4)) + 'px';
  container.style.top = Math.max(4, y) + 'px';
}

function hidePops() {
  menuPop.hidden = true;
  ctxMenu.hidden = true;
}

$('tb-menu').onclick = (ev) => {
  ev.stopPropagation();
  showPop(menuPop, $('tb-menu'), (c) => {
    popItem(c, L('فتح ملف...', 'Open file...'), 'Ctrl+O', () => window.bplus.openFiles(), { icon: 'm-file' });
    popItem(c, L('فتح مجلد...', 'Open folder...'), 'Ctrl+Shift+O', () => window.bplus.openFolder(), { icon: 'm-folder' });
    popItem(c, L('الملفات الحديثة...', 'Recent files...'), null, () => setTimeout(() => window.BPLUS_RECENT && window.BPLUS_RECENT.menu($('tb-menu')), 0), { icon: 'recent' });
    popSep(c);
    popItem(c, L('تحميل ملف ترجمة...', 'Load subtitles...'), 'Ctrl+L', () => window.bplus.openSubtitleDialog(), { icon: 'm-sub' });
    popItem(c, L('إظهار / إخفاء الترجمة', 'Show / hide subtitles'), 'Ctrl+T', toggleSubtitleVisible, { icon: 'm-eye' });
    popSep(c);
    popItem(c, L('قائمة التشغيل', 'Playlist'), 'Ctrl+P', () => playlistEl.classList.toggle('hidden'), { icon: 'm-list' });
    popItem(c, L('ملء الشاشة', 'Fullscreen'), 'F11', toggleFullscreen, { icon: 'm-full' });
    popItem(c, L('ملء الشاشة بدون قوائم', 'Clean fullscreen'), 'Shift+F', togglePureFullscreen, { icon: 'm-clean' });
    popSep(c);
    popItem(c, L('الإعدادات', 'Settings'), null, () => window.BPLUS_SETTINGS && window.BPLUS_SETTINGS.open(), { icon: 'm-gear' });
    popItem(c, L('حول B+', 'About B+'), null, () => window.dispatchEvent(new Event('bplus-about')), { icon: 'm-info' });
    popItem(c, L('خروج', 'Exit'), null, () => window.bplus.winClose(), { icon: 'm-exit' });
  });
};

$('b-set').onclick = (ev) => {
  ev.stopPropagation();
  showPop(menuPop, $('b-set'), (c) => {
    popHead(c, L('سرعة التشغيل', 'Playback speed'));
    RATES.forEach((r, i) => {
      popItem(c, r + '×', null, () => { setRate(i); showOsd(L('السرعة ', 'Speed ') + r + '×'); },
        { checkable: true, checked: i === rateIdx });
    });
    popSep(c);
    popItem(c, L('حول B+', 'About B+'), null, () => window.dispatchEvent(new Event('bplus-about')), { icon: 'm-info' });
  });
};

$('video-wrap').addEventListener('contextmenu', (e) => {
  e.preventDefault();
  showPop(ctxMenu, { getBoundingClientRect: () => ({ left: e.clientX, right: e.clientX, top: e.clientY, bottom: e.clientY }) }, (c) => {
    popItem(c, L('تشغيل / إيقاف مؤقت', 'Play / pause'), 'Space', togglePlay, { icon: 'm-play' });
    popItem(c, L('التالي', 'Next'), 'PgDn', next, { icon: 'm-next' });
    popItem(c, L('السابق', 'Previous'), 'PgUp', prev, { icon: 'm-prev' });
    popSep(c);
    popItem(c, L('فتح ملف...', 'Open file...'), 'Ctrl+O', () => window.bplus.openFiles(), { icon: 'm-file' });
    popItem(c, L('تحميل ترجمة...', 'Load subtitles...'), 'Ctrl+L', () => window.bplus.openSubtitleDialog(), { icon: 'm-sub' });
    popItem(c, L('لقطة شاشة', 'Snapshot'), null, takeSnapshot, { icon: 'm-cam' });
    popSep(c);
    popItem(c, L('قائمة التشغيل', 'Playlist'), null, () => playlistEl.classList.toggle('hidden'), { icon: 'm-list' });
    popItem(c, L('ملء الشاشة', 'Fullscreen'), 'F11', toggleFullscreen, { icon: 'm-full' });
    popItem(c, L('ملء الشاشة بدون قوائم', 'Clean fullscreen'), 'Shift+F', togglePureFullscreen, { icon: 'm-clean' });
    popSep(c);
    popItem(c, L('الإعدادات', 'Settings'), null, () => window.BPLUS_SETTINGS && window.BPLUS_SETTINGS.open(), { icon: 'm-gear' });
  });
});
document.addEventListener('click', hidePops);

/* ---------- Window controls ---------- */
$('tb-purefs').onclick = togglePureFullscreen;
$('win-min').onclick = () => window.bplus.winMin();
$('win-max').onclick = () => window.bplus.winMaxToggle();
$('win-close').onclick = () => window.bplus.winClose();

/* ---------- Keyboard ---------- */
document.addEventListener('keydown', (e) => {
  const tag = e.target.tagName;
  if (tag === 'SELECT' || (tag === 'INPUT' && e.target.type !== 'range')) return;
  if (document.querySelector('.settings-overlay:not([hidden])')) return;
  const ctrl = e.ctrlKey || e.metaKey;
  if (ctrl && e.key.toLowerCase() === 'o' && e.shiftKey) { e.preventDefault(); window.bplus.openFolder(); return; }
  if (ctrl && e.key.toLowerCase() === 'o') { e.preventDefault(); window.bplus.openFiles(); return; }
  if (ctrl && e.key.toLowerCase() === 'l') { e.preventDefault(); window.bplus.openSubtitleDialog(); return; }
  if (ctrl && e.key.toLowerCase() === 't') { e.preventDefault(); toggleSubtitleVisible(); return; }
  if (ctrl && e.key.toLowerCase() === 'p') { e.preventDefault(); playlistEl.classList.toggle('hidden'); return; }
  if (e.key === 'Delete' && plSel >= 0 && playlist[plSel] && !(e.target && /INPUT|TEXTAREA/.test(e.target.tagName))) { e.preventDefault(); if (e.shiftKey) trashAt(plSel); else removeAt(plSel); return; }
  if (ctrl && e.key === 'ArrowUp') { e.preventDefault(); stepRate(1); return; }
  if (ctrl && e.key === 'ArrowDown') { e.preventDefault(); stepRate(-1); return; }
  if (e.key === 'F11') { e.preventDefault(); toggleFullscreen(); return; }
  if (window.BPLUS_SETTINGS) return;
  if (e.key === 'PageDown') { next(); return; }
  if (e.key === 'PageUp') { prev(); return; }
  if (tag === 'INPUT') return;
  switch (e.key) {
    case ' ': e.preventDefault(); togglePlay(); break;
    case 'ArrowRight': player.currentTime += (window.bplusPrefs?.seekStep || 5); showOsd(iso('+' + (window.bplusPrefs?.seekStep || 5)) + L(' ث', 's')); break;
    case 'ArrowLeft': player.currentTime -= (window.bplusPrefs?.seekStep || 5); showOsd(iso('-' + (window.bplusPrefs?.seekStep || 5)) + L(' ث', 's')); break;
    case 'ArrowUp': e.preventDefault(); vol.value = Math.min(200, +vol.value + 5); vol.dispatchEvent(new Event('input')); break;
    case 'ArrowDown': e.preventDefault(); vol.value = Math.max(0, +vol.value - 5); vol.dispatchEvent(new Event('input')); break;
    case 'f': case 'F': e.shiftKey ? togglePureFullscreen() : toggleFullscreen(); break;
    case 'm': case 'M': bMute.onclick(); break;
  }
});

/* ---------- Drag and drop ---------- */
const wrap = $('video-wrap');
wrap.addEventListener('dragover', (e) => e.preventDefault());
wrap.addEventListener('drop', async (e) => {
  e.preventDefault();
  const entries = [];
  for (const f of e.dataTransfer.files) {
    const p = window.bplus.pathForFile(f);
    if (!p) continue;
    if (/\.(srt|vtt)$/i.test(p)) {
      window.bplus.readSubtitle(p).then(s => setSubtitle(s));
    } else {
      entries.push(p);
    }
  }
  if (entries.length) addWithSiblings(await window.bplus.entriesForPaths(entries));
});

/* ---------- Main-process events ---------- */
window.bplus.onPlaylistAdd(addWithSiblings);
window.bplus.onSubtitleLoad((s) => setSubtitle(s));
window.bplus.onMenuAction((a) => {
  switch (a) {
    case 'toggle': togglePlay(); break;
    case 'next': next(); break;
    case 'prev': prev(); break;
    case 'faster': stepRate(1); break;
    case 'slower': stepRate(-1); break;
    case 'subtitle-toggle': toggleSubtitleVisible(); break;
    case 'playlist-toggle': playlistEl.classList.toggle('hidden'); break;
    case 'fullscreen': toggleFullscreen(); break;
  }
});
window.bplus.onSnapshotSaved((name) => showOsd(name ? L('حُفظت اللقطة: ', 'Snapshot saved: ') + iso(name) : L('أُلغي الحفظ', 'Save canceled')));

player.volume = 0.8;
updateVolUI();
renderPlaylist();

/* ---------- Self-test (BPLUS_TEST) ---------- */
window.bplus.onTestRun(async (entries) => {
  const results = [];
  const wait = (ms) => new Promise(r => setTimeout(r, ms));
  for (const entry of entries) {
    const r = { file: entry.name, ok: false };
    try {
      const played = new Promise((resolve) => {
        const to = setTimeout(() => resolve('timeout'), 10000);
        player.addEventListener('playing', () => { clearTimeout(to); resolve('playing'); }, { once: true });
        player.addEventListener('error', () => { clearTimeout(to); resolve('error'); }, { once: true });
      });
      player.src = entry.url;
      player.muted = true;
      await player.play().catch(() => {});
      const state = await played;
      await wait(3500);
      r.state = state;
      r.currentTime = +player.currentTime.toFixed(2);
      r.readyState = player.readyState;
      r.duration = isFinite(player.duration) ? +player.duration.toFixed(2) : null;
      r.videoWidth = player.videoWidth || 0;
      r.ok = state === 'playing' && player.currentTime > 0.5 && player.readyState >= 2 && !player.error;
      if (player.error) r.error = player.error.code;
      player.pause();
    } catch (err) { r.error = String(err); }
    results.push(r);
  }
  const sub = { ok: false };
  try {
    const vtt = srtToVtt('1\n00:00:00,500 --> 00:00:02,000\nمرحبا بالعالم\n\n2\n00:00:02,500 --> 00:00:04,000\nسطر ثاني\n');
    sub.hasHeader = vtt.startsWith('WEBVTT');
    const url = URL.createObjectURL(new Blob([vtt], { type: 'text/vtt' }));
    const tr = document.createElement('track');
    tr.kind = 'subtitles'; tr.src = url; tr.srclang = 'ar';
    player.appendChild(tr);
    await wait(1500);
    tr.track.mode = 'hidden';
    await wait(1000);
    sub.cues = tr.track.cues ? tr.track.cues.length : 0;
    sub.ok = sub.hasHeader && sub.cues >= 2;
  } catch (err) { sub.error = String(err); }
  window.bplus.sendTestResult({ ok: results.length > 0 && results.every(r => r.ok) && sub.ok, results, sub });
});

let latestLaunchId=0;
async function receiveExternal(payload){
 const entries=Array.isArray(payload)?payload:payload.entries;
 if(!entries?.length)return;
 if(payload.id&&payload.id<=latestLaunchId){window.bplus.acknowledgeLaunch(payload.id);return;}
 latestLaunchId=payload.id||0;savePosition();
 const ex=await expandSiblings(entries);
 playlist=ex?ex.entries:entries;current=-1;renderPlaylist();
 await playAt(ex?ex.index:0,true);if(payload.id)window.bplus.acknowledgeLaunch(payload.id);
}
window.bplus.onExternalMedia(receiveExternal);
window.addEventListener('DOMContentLoaded',async()=>{try{await receiveExternal(await window.bplus.pullLaunch())}catch(e){showOsd(L('تعذر فتح الملف: ','Could not open the file: ')+iso(e.message))}});


/* ---------- Toggled-on buttons use the accent color ---------- */
(function () {
  const mark = (el, on) => { if (el) el.classList.toggle('on', !!on); };
  const sync = () => {
    mark($('b-mute'), player.muted || vol.value === '0');
    mark($('b-speed'), player.playbackRate !== 1);
    mark($('b-pl'), !playlistEl.classList.contains('hidden'));
    mark($('tb-purefs'), pureFs);
    const qa = document.getElementById('quick-av');
    mark($('b-av'), qa && !qa.hidden);
  };
  setInterval(sync, 250);
  sync();
})();
