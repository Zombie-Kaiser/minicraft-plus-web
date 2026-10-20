// Minicraft Plus Revived — web port driver.
// Loads the TeaVM bundle (game.js), delivers the bundled resources into the
// Java-side virtual file system + image store, then drives the frame loop and
// implements window.__platform, the bridge consumed by minicraft.web.Platform.

const $overlay = document.getElementById('overlay');
const $hint = document.getElementById('hint');
const canvas = document.getElementById('c');
const ctx = canvas.getContext('2d', { willReadFrequently: true });

const params = new URLSearchParams(location.search);
const DEBUG = params.has('debug');
const AUTOTEST = params.has('autotest');

const T0 = performance.now();
function blog(...args) {
  console.log('[boot +' + Math.round(performance.now() - T0) + 'ms]', ...args);
}
blog('driver start, DEBUG=' + DEBUG + ' AUTOTEST=' + AUTOTEST);

$overlay.hidden = false;
$overlay.textContent = 'loading\u2026';

function fail(msg) {
  blog('FAIL:', msg);
  $overlay.hidden = false;
  $overlay.textContent = 'error: ' + msg;
  console.error(msg);
  if (AUTOTEST) document.title = 'AUTOTEST-ERROR: ' + msg;
}

window.addEventListener('error', e => console.error('window.onerror:', e.message, e.filename, e.lineno));
window.addEventListener('unhandledrejection', e => console.error('unhandled rejection:', e.reason));

// ============================== java boot state ==============================
let javaStarted = false;
let javaReadyResolve;
const javaReady = new Promise(r => { javaReadyResolve = r; });

// ============================== frame buffer ==============================
let overlayShown = false;
let presentCount = 0;
function hideOverlay() {
  if (overlayShown) return;
  overlayShown = true;
  $overlay.hidden = true;
  $hint.hidden = false;
}

function blitPixels(c2d, w, h, px) {
  if (c2d.canvas.width !== w) c2d.canvas.width = w;
  if (c2d.canvas.height !== h) c2d.canvas.height = h;
  const img = c2d.createImageData(w, h);
  const d = img.data;
  for (let i = 0, n = w * h; i < n; i++) {
    const v = px[i]; // 0xRRGGBB, possibly a negative int32
    const o = i << 2;
    d[o] = (v >>> 16) & 255;
    d[o + 1] = (v >>> 8) & 255;
    d[o + 2] = v & 255;
    d[o + 3] = 255;
  }
  c2d.putImageData(img, 0, 0);
}

// ============================== audio sink ==============================
// Java pushes mixed 16-bit LE mono PCM (44.1 kHz); a ScriptProcessorNode pulls
// from this ring buffer. The AudioContext starts suspended until a user gesture.
const RING_SIZE = 1 << 16; // bytes; Java caps the sink at ~8 KB itself
const audioRing = new Uint8Array(RING_SIZE);
let aRead = 0, aWrite = 0; // monotonic byte counters
let audioCtx = null, audioNode = null;

function ensureAudio() {
  if (audioCtx) return;
  const AC = window.AudioContext || window.webkitAudioContext;
  if (!AC) return;
  try {
    try { audioCtx = new AC({ sampleRate: 44100 }); }
    catch { audioCtx = new AC(); }
  } catch {
    audioCtx = null;
    return;
  }
  audioNode = audioCtx.createScriptProcessor(4096, 0, 2);
  audioNode.onaudioprocess = ev => {
    const L = ev.outputBuffer.getChannelData(0);
    const R = ev.outputBuffer.getChannelData(1);
    const n = L.length;
    for (let i = 0; i < n; i++) {
      if (aRead >= aWrite) { L[i] = 0; R[i] = 0; continue; }
      const b0 = audioRing[aRead % RING_SIZE];
      const b1 = audioRing[(aRead + 1) % RING_SIZE];
      aRead += 2;
      const s = ((b0 | (b1 << 8)) << 16) >> 16; // int16 LE
      L[i] = R[i] = s / 32768;
    }
  };
  audioNode.connect(audioCtx.destination);
  const resume = () => { if (audioCtx.state === 'suspended') audioCtx.resume().catch(() => {}); };
  window.addEventListener('keydown', resume);
  window.addEventListener('pointerdown', resume);
  resume();
}

// ============================== clipboard ==============================
let clipboardCache = '';

// ============================== platform bridge ==============================
window.__platform = {
  present(w, h, px) {
    blitPixels(ctx, w, h, px);
    if (++presentCount === 1) blog('first present()', w + 'x' + h);
    hideOverlay();
  },

  screenshot(w, h, px) {
    const c = document.createElement('canvas');
    blitPixels(c.getContext('2d'), w, h, px);
    const url = c.toDataURL('image/png');
    console.log('SNAP ' + url);
    if (!AUTOTEST) {
      try {
        const a = document.createElement('a');
        a.href = url;
        a.download = 'minicraft-screenshot.png';
        a.click();
      } catch { /* downloads without a gesture can be blocked */ }
    }
  },

  storageGet(k) {
    try { return localStorage.getItem(k); } catch { return null; }
  },
  storageSet(k, v) {
    try { localStorage.setItem(k, v); } catch { /* storage full or blocked */ }
  },
  storageRemove(k) {
    try { localStorage.removeItem(k); } catch { /* ignore */ }
  },

  alert(msg) {
    if (AUTOTEST) console.log('[alert] ' + msg);
    else window.alert(msg);
  },
  log(msg) { console.log('[java] ' + msg); },
  openUrl(url) {
    try { window.open(url, '_blank', 'noopener'); } catch { /* popup blocked */ }
  },

  clipboardWrite(text) {
    clipboardCache = text;
    if (navigator.clipboard?.writeText) navigator.clipboard.writeText(text).catch(() => {});
  },
  clipboardRead() { return clipboardCache; },

  now() { return performance.now(); },

  exit() {
    console.log('GAME-EXITED');
    if (!AUTOTEST) {
      $overlay.hidden = false;
      $hint.hidden = true;
      $overlay.textContent = 'game exited';
    }
    stopped = true;
  },

  setTitle(title) { document.title = title; },

  audioWrite(data) {
    ensureAudio();
    for (let i = 0; i < data.length; i++) {
      if (aWrite - aRead >= RING_SIZE) return false; // ring full; Java retries next tick
      audioRing[aWrite % RING_SIZE] = data[i] & 0xFF;
      aWrite++;
    }
    return true;
  },
  audioBuffered() { return aWrite - aRead; },
  audioSampleRate() { return audioCtx ? audioCtx.sampleRate : 44100; },

  isFullscreen() { return document.fullscreenElement === canvas; },
  toggleFullscreen() {
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
    else if (canvas.requestFullscreen) canvas.requestFullscreen().catch(() => {});
  },

  canvasFocused() { return AUTOTEST || document.activeElement === canvas; },
  isDebugEnabled() { return DEBUG; },
  autotest() { return AUTOTEST; },

  onJavaMainStarted() { javaStarted = true; blog('onJavaMainStarted'); },
  onJavaReady() {
    javaStarted = true;
    blog('onJavaReady');
    javaReadyResolve();
  },
};

// ============================== input ==============================
const downKeys = new Set();

function sendKey(code, down) {
  if (down) downKeys.add(code); else downKeys.delete(code);
  if (window.__mc) window.__mc.key(code, down);
}

window.addEventListener('keydown', e => {
  if (e.code === 'Space' || e.code === 'ArrowUp' || e.code === 'ArrowDown' ||
      e.code === 'ArrowLeft' || e.code === 'ArrowRight' || e.code === 'Tab') {
    e.preventDefault();
  }
  if (!window.__mc) return;
  sendKey(e.code, true);
  if (e.key && e.key.length === 1) window.__mc.typed(e.key);
});
window.addEventListener('keyup', e => sendKey(e.code, false));
window.addEventListener('blur', () => { for (const c of [...downKeys]) sendKey(c, false); });
// A save may have been scheduled (coalesced) but not yet written; flush it
// before the page goes away so nothing is lost.
window.addEventListener('beforeunload', () => {
  if (window.__mc) { try { window.__mc.flush(); } catch { /* ignore */ } }
});

canvas.addEventListener('mousedown', () => canvas.focus());

// Real clipboard reads need a gesture; refresh the cache on Ctrl+V / paste.
document.addEventListener('paste', e => {
  const t = e.clipboardData?.getData('text');
  if (t != null) clipboardCache = t;
});
window.addEventListener('keydown', e => {
  if ((e.ctrlKey || e.metaKey) && e.code === 'KeyV' && navigator.clipboard?.readText) {
    navigator.clipboard.readText().then(t => { clipboardCache = t; }).catch(() => {});
  }
});

// ============================== asset delivery ==============================
// Image-element decode: onload fires reliably in headless Chrome, whereas
// createImageBitmap promises can hang there under --disable-gpu.
async function decodePng(buf, rel) {
  const blobUrl = URL.createObjectURL(new Blob([buf], { type: 'image/png' }));
  try {
    const img = new Image();
    await new Promise((res, rej) => {
      img.onload = () => res();
      img.onerror = () => rej(new Error('image decode failed'));
      img.src = blobUrl;
    });
    const w = img.naturalWidth, h = img.naturalHeight;
    const c = document.createElement('canvas');
    c.width = w; c.height = h;
    const c2d = c.getContext('2d', { willReadFrequently: true });
    c2d.drawImage(img, 0, 0);
    const d = c2d.getImageData(0, 0, w, h).data;
    const argb = new Array(w * h);
    for (let i = 0, n = w * h; i < n; i++) {
      const o = i << 2;
      // canvas gives RGBA bytes; pack as Java ARGB int (matching ImageIO.getRGB).
      argb[i] = ((d[o + 3] << 24) | (d[o] << 16) | (d[o + 1] << 8) | d[o + 2]) | 0;
    }
    return { w, h, argb };
  } catch (e) {
    console.warn('PNG decode failed:', rel, e);
    return null;
  } finally {
    URL.revokeObjectURL(blobUrl);
  }
}

function loadScript(src) {
  return new Promise((res, rej) => {
    const s = document.createElement('script');
    s.src = src;
    s.onload = () => res();
    s.onerror = () => rej(new Error('failed to load ' + src));
    document.head.appendChild(s);
  });
}

function delay(ms) { return new Promise(r => setTimeout(r, ms)); }

async function fetchBuf(url) {
  const ctrl = new AbortController();
  const to = setTimeout(() => ctrl.abort(), 20000);
  try {
    const r = await fetch(url, { signal: ctrl.signal });
    if (!r.ok) throw new Error(url + ' -> HTTP ' + r.status);
    return await r.arrayBuffer();
  } finally {
    clearTimeout(to);
  }
}

// ============================== frame loop ==============================
let stopped = false;
let tickCount = 0;

function scheduleTick() {
  if (AUTOTEST) setTimeout(frameTick, 16); // virtual-time friendly
  else requestAnimationFrame(frameTick);
}

function frameTick() {
  if (stopped) return;
  tickCount++;
  if (tickCount === 1) blog('first frameTick');
  if (DEBUG && tickCount % 120 === 0) console.log('frameTick', tickCount, 'presentCount', presentCount, 'audioQueued', aWrite - aRead);
  try {
    window.__mc.tick();
  } catch (e) {
    console.error('tick threw:', e);
    stopped = true;
    fail('tick exception: ' + e);
    return;
  }
  if (AUTOTEST) autotestStep();
  if (stopped) return;
  scheduleTick();
}

// ============================== autotest ==============================
// Title -> Enter(Play) -> Enter(Generate new world) -> type world name ->
// Down x2 (hidden entries auto-skip) to "Create World!" -> Enter -> world gen -> snapshot.
let atDone = false;
const AT_SCHEDULE = [];
function atKey(code, tick) { AT_SCHEDULE.push([tick, () => sendKey(code, true)]); }
function atKeyUp(code, tick) { AT_SCHEDULE.push([tick, () => sendKey(code, false)]); }
function atTyped(ch, tick) { AT_SCHEDULE.push([tick, () => { if (window.__mc) window.__mc.typed(ch); }]); }

atKey('Enter', 120); atKeyUp('Enter', 150);          // title: Play
atKey('Enter', 210); atKeyUp('Enter', 240);          // world select: Generate new world
'World'.split('').forEach((ch, i) => atTyped(ch, 280 + i * 6)); // world name
// nameField(0) -> [nameHelp hidden] -> mode(2) -> [scoretime hidden in survival] -> create_world(4)
atKey('ArrowDown', 380); atKeyUp('ArrowDown', 385);
atKey('ArrowDown', 405); atKeyUp('ArrowDown', 410);
atKey('Enter', 450); atKeyUp('Enter', 480);          // Create World!
const AT_FINISH_TICK = 1530;

function autotestStep() {
  while (AT_SCHEDULE.length > 0 && AT_SCHEDULE[0][0] <= tickCount) {
    AT_SCHEDULE.shift()[1]();
  }
  if (tickCount === AT_FINISH_TICK) finishAutotest();

  if (DEBUG && tickCount % 120 === 0) {
    console.log('AUTOTEST frame', tickCount, 'presentCount', presentCount, 'audioQueued', aWrite - aRead);
  }
}

function finishAutotest() {
  if (atDone) return;
  atDone = true;
  stopped = true;
  console.log('AUTOTEST-DONE ticks=' + tickCount + ' presentCount=' + presentCount + ' audioQueued=' + (aWrite - aRead));
  try { console.log('SNAP ' + canvas.toDataURL('image/png')); }
  catch (e) { console.log('SNAP-FAIL ' + e); }
  document.title = 'AUTOTEST-DONE';
  const d = document.createElement('pre');
  d.id = 'autotest-result';
  d.textContent = 'AUTOTEST-DONE ticks=' + tickCount;
  document.body.appendChild(d);
}

// ============================== boot ==============================
async function boot() {
  let manifest;
  try {
    const [m] = await Promise.all([
      fetch('./manifest.json').then(r => {
        if (!r.ok) throw new Error('manifest.json -> HTTP ' + r.status);
        return r.json();
      }),
      loadScript('./game.js'),
    ]);
    manifest = m;
  } catch (e) {
    fail(e.message);
    return;
  }
  blog('manifest (' + manifest.length + ' entries) + game.js loaded');

  // TeaVM's UMD output attaches `main` to the global object when loaded as a
  // plain script (window.main = (args, callback) => ...). It does not auto-run,
  // so start it explicitly. The Java-side guard makes a double call harmless.
  if (!javaStarted && typeof globalThis.main === 'function') {
    blog('calling globalThis.main([])');
    try { globalThis.main([]); }
    catch (e) { console.error('main([]) threw:', e); }
    blog('globalThis.main([]) returned');
  } else {
    blog('skip main(): javaStarted=' + javaStarted + ' typeof main=' + typeof globalThis.main);
  }

  let readyTimeoutId = null;
  try {
    await Promise.race([
      javaReady,
      new Promise((_, rej) => {
        readyTimeoutId = setTimeout(() => rej(new Error('Java side did not signal readiness (window.main missing from game.js?)')), 15000);
      }),
    ]);
  } catch (e) {
    fail(e.message);
    return;
  } finally {
    if (readyTimeoutId !== null) clearTimeout(readyTimeoutId);
  }
  blog('java side ready; delivering assets');

  // Deliver every bundled resource as raw bytes; PNGs additionally decoded.
  window.__mc.begin(manifest.length);
  let delivered = 0, failed = 0;
  for (const rel of manifest) {
    try {
      const buf = await fetchBuf('./resources/' + encodeURI(rel));
      window.__mc.asset('/' + rel, Array.from(new Uint8Array(buf)));
      if (rel.endsWith('.png')) {
        const img = await decodePng(buf, rel);
        if (img) window.__mc.image('/' + rel, img.w, img.h, img.argb);
        else failed++;
      }
    } catch (e) {
      failed++;
      console.warn('asset failed:', rel, e);
    }
    delivered++;
    if (delivered % 25 === 0 || delivered === manifest.length) {
      blog('assets', delivered + '/' + manifest.length, failed ? ('failed=' + failed) : '');
      $overlay.textContent = `loading\u2026 ${delivered}/${manifest.length}`;
    }
  }

  blog('asset delivery complete (' + failed + ' failed); calling __mc.ready()');
  try {
    window.__mc.ready();
  } catch (e) {
    console.error('ready() threw:', e);
    fail('ready() threw: ' + e);
    return;
  }
  blog('ready() returned; starting tick loop');
  scheduleTick();
}

boot().catch(e => fail(e && e.message ? e.message : String(e)));
