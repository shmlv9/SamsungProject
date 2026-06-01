import io
import logging
import sys
import threading
import time
from typing import Callable, Optional

import numpy as np
from PIL import Image

logger = logging.getLogger(__name__)

FPS = 30
FRAME_INTERVAL = 1.0 / FPS
DEFAULT_W = 640
DEFAULT_H = 480


class VirtualCamera:
    WARN_DELAY = 3.0

    def __init__(self):
        self._thread: Optional[threading.Thread] = None
        self._running = False
        self._cam = None
        self._available = False
        self._start_ts = 0.0

    @property
    def available(self) -> bool:
        return self._available

    def start(self, get_frame: Callable[[], Optional[bytes]]):
        self._running = True
        self._start_ts = time.monotonic()
        self._thread = threading.Thread(
            target=self._run, args=(get_frame,), daemon=True, name="virtual-cam"
        )
        self._thread.start()

    def stop(self):
        self._running = False
        if self._thread:
            self._thread.join(timeout=3)
            self._thread = None
        self._close()

    def _close(self):
        if self._cam is not None:
            try:
                self._cam.close()
            except Exception:
                pass
            self._cam = None

    def _ensure_cam(self, w: int, h: int):
        import pyvirtualcam

        if self._cam is not None and self._cam.width == w and self._cam.height == h:
            return True
        self._close()
        try:
            kwargs = {"width": w, "height": h, "fps": FPS}
            if sys.platform != "darwin":
                kwargs["backend"] = "obs"
            self._cam = pyvirtualcam.Camera(**kwargs)
            self._available = True
            logger.info(f"Virtual camera started: {w}x{h} @ {FPS}fps")
            return True
        except RuntimeError as e:
            self._available = False
            if time.monotonic() - self._start_ts > self.WARN_DELAY:
                logger.warning(
                    f"Virtual camera unavailable: {e}. "
                    "Open OBS Studio → Tools → Virtual Camera → Start, "
                    "then restart."
                )
            return False

    def _run(self, get_frame: Callable[[], Optional[bytes]]):
        w, h = DEFAULT_W, DEFAULT_H

        while self._running:
            t0 = time.perf_counter()
            data = get_frame()
            if data is not None:
                try:
                    img = Image.open(io.BytesIO(data))
                    if img.mode != "RGB":
                        img = img.convert("RGB")
                    w, h = img.width, img.height
                    frame_rgb = np.asarray(img)
                except Exception:
                    self._sleep(FRAME_INTERVAL)
                    continue
            else:
                frame_rgb = np.zeros((h, w, 3), np.uint8)

            if not self._ensure_cam(w, h):
                self._sleep(FRAME_INTERVAL)
                continue

            try:
                self._cam.send(frame_rgb)
            except Exception:
                self._close()
                self._available = False

            elapsed = time.perf_counter() - t0
            remaining = FRAME_INTERVAL - elapsed
            if remaining > 0:
                self._sleep(remaining)

    def _sleep(self, seconds: float):
        if self._running:
            time.sleep(seconds)
