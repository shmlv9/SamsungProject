const canvas = document.getElementById('frame');
const ctx = canvas.getContext('2d');
const beforeConnect = document.getElementById('beforeConnect');
const controls = document.getElementById('controls');
const wsUrl = `ws://${location.host}/ws/view`;
const img = new Image();

const toggleState = {};

function flashBtn(id) {
  const btn = document.getElementById(id);
  btn.classList.add('active');
  setTimeout(() => btn.classList.remove('active'), 200);
}

function toggleBtn(id) {
  const btn = document.getElementById(id);
  toggleState[id] = !toggleState[id];
  btn.classList.toggle('active', toggleState[id]);
}

function setBtn(id, active) {
  const btn = document.getElementById(id);
  toggleState[id] = active;
  btn.classList.toggle('active', active);
}

function sendCmd(action) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ action }));
  }
}

function showFilterDialog() {
  document.getElementById('filterOverlay').style.display = 'block';
  document.getElementById('filterDialog').style.display = 'block';
}

function hideFilterDialog() {
  document.getElementById('filterOverlay').style.display = 'none';
  document.getElementById('filterDialog').style.display = 'none';
}

function showFrame() {
  canvas.style.display = 'block';
  beforeConnect.style.display = 'none';
  controls.style.display = 'flex';
}

function showPlaceholder() {
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  canvas.width = 0;
  canvas.height = 0;
  canvas.style.display = 'none';
  beforeConnect.style.display = 'flex';
  controls.style.display = 'none';
}

const FRAME_TIMEOUT_MS = 3000;
let frameTimeout = null;

function resetFrameTimeout() {
  clearTimeout(frameTimeout);
  frameTimeout = setTimeout(showPlaceholder, FRAME_TIMEOUT_MS);
}

let ws = null;

function connectWs() {
  ws = new WebSocket(wsUrl);
  ws.binaryType = 'arraybuffer';

  ws.onopen = () => {
    clearTimeout(frameTimeout);
    showPlaceholder();
  };

  ws.onmessage = (e) => {
    if (e.data instanceof ArrayBuffer) {
      resetFrameTimeout();
      const blob = new Blob([e.data], { type: 'image/jpeg' });
      const url = URL.createObjectURL(blob);
      img.onload = () => {
        canvas.width = img.width;
        canvas.height = img.height;
        ctx.drawImage(img, 0, 0);
        URL.revokeObjectURL(url);
        showFrame();
      };
      img.src = url;
    } else {
      try {
        const msg = JSON.parse(e.data);
        if (msg.type === 'state') {
          if (msg.bg_blur !== undefined) setBtn('btnBg', msg.bg_blur);
          if (msg.filter !== undefined) setBtn('btnFilters', msg.filter !== 'none');
          if (msg.center_lock !== undefined) setBtn('btnCenter', msg.center_lock);
        }
      } catch(_) {}
    }
  };

  ws.onclose = () => {
    clearTimeout(frameTimeout);
    img.onload = null;
    img.src = '';
    showPlaceholder();
    setTimeout(connectWs, 500);
  };

  ws.onerror = () => { ws.close(); };
}

connectWs();

document.getElementById('btnRotate').onclick = () => { flashBtn('btnRotate'); sendCmd('rotate'); };
document.getElementById('btnFlip').onclick = () => { flashBtn('btnFlip'); sendCmd('flip_camera'); };
document.getElementById('btnFilters').onclick = () => { showFilterDialog(); };
document.getElementById('btnBg').onclick = () => { toggleBtn('btnBg'); sendCmd('bg_blur'); };
document.getElementById('btnCenter').onclick = () => { toggleBtn('btnCenter'); sendCmd('center_lock'); };
document.getElementById('filterOverlay').onclick = () => { hideFilterDialog(); };

document.querySelectorAll('.filter-option').forEach(opt => {
  opt.onclick = () => {
    const filter = opt.dataset.filter;
    const isActive = filter !== 'none';
    setBtn('btnFilters', isActive);
    sendCmd('filter_' + filter);
    hideFilterDialog();
  };
});
