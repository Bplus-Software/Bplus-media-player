'use strict';
// Resumable, stall-proof installer download. Pure Node (fs + a fetch function),
// so it is testable outside Electron. Retries resume with HTTP Range from the
// bytes already on disk; a stalled connection (no data for stallMs) is aborted
// and resumed instead of hanging forever.
const fs = require('fs');

async function downloadResumable({ fetchFn, url, dest, expectedSize = 0, onProgress = () => {}, onLog = () => {}, maxAttempts = 8, stallMs = 30000, headers = {} }) {
  const part = dest + '.part';
  let total = expectedSize || 0;
  let lastErr = null;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    let have = 0;
    try { have = fs.statSync(part).size; } catch (e) { have = 0; }
    if (total && have > total) { try { fs.unlinkSync(part); } catch (e) {} have = 0; }
    if (total && have === total) break;
    const ac = new AbortController();
    let timer = null;
    const arm = () => { clearTimeout(timer); timer = setTimeout(() => ac.abort(new Error('stalled: no data for ' + Math.round(stallMs / 1000) + 's')), stallMs); };
    try {
      arm();
      const h = Object.assign({}, headers);
      if (have > 0) h.Range = 'bytes=' + have + '-';
      const r = await fetchFn(url, { headers: h, signal: ac.signal });
      if (r.status === 416 && total && have >= total) break;
      if (!r.ok || !r.body) throw new Error('HTTP ' + r.status);
      let append = have > 0;
      if (have > 0 && r.status !== 206) { append = false; have = 0; onLog('server ignored Range, restarting from 0'); }
      const cl = Number(r.headers.get('content-length')) || 0;
      if (cl) total = append ? have + cl : cl;
      const out = fs.createWriteStream(part, { flags: append ? 'a' : 'w' });
      let received = have;
      try {
        const reader = r.body.getReader();
        for (;;) {
          arm();
          const { done, value } = await reader.read();
          if (done) break;
          received += value.length;
          if (!out.write(value)) await new Promise(res => out.once('drain', res));
          onProgress({ received, total, attempt });
        }
      } finally {
        clearTimeout(timer);
        await new Promise(res => out.end(() => res()));
      }
      if (total && received < total) throw new Error('connection closed early at ' + received + ' of ' + total + ' bytes');
      lastErr = null;
      break;
    } catch (e) {
      clearTimeout(timer);
      lastErr = (ac.signal.aborted && ac.signal.reason ? ac.signal.reason : e);
      onLog('attempt ' + attempt + '/' + maxAttempts + ' failed: ' + String(lastErr && lastErr.message || lastErr));
      await new Promise(res => setTimeout(res, Math.min(4000, 400 * attempt)));
    }
  }
  let size = 0;
  try { size = fs.statSync(part).size; } catch (e) {}
  if (lastErr || (total && size !== total) || size === 0) {
    throw new Error(String(lastErr && lastErr.message || lastErr || 'incomplete download') + ' (have ' + size + ' of ' + total + ' bytes; partial file kept for resume)');
  }
  try { fs.unlinkSync(dest); } catch (e) {}
  fs.renameSync(part, dest);
  return { path: dest, size };
}
module.exports = { downloadResumable };
