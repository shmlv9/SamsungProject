import asyncio
import json
import logging
from contextlib import asynccontextmanager
from io import BytesIO
from pathlib import Path

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Request
from fastapi.responses import Response, HTMLResponse
from fastapi.staticfiles import StaticFiles
from PIL import Image

from qrcode_utils import generate_qr_base64, get_lan_ip
from udp_receiver import UdpProtocol
from virtual_cam import VirtualCamera

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)

frame_buffer: bytes | None = None
viewers: set[WebSocket] = set()
command_queue: list[str] = []
virtual_cam = VirtualCamera()
lan_ip: str | None = None
lan_qr: str | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global lan_ip, lan_qr
    lan_ip = get_lan_ip()
    lan_qr = generate_qr_base64(f"http://{lan_ip}:8000")
    virtual_cam.start(lambda: frame_buffer)

    def on_udp_frame(jpeg: bytes):
        global frame_buffer
        frame_buffer = jpeg
        asyncio.get_event_loop().create_task(broadcast(jpeg))

    loop = asyncio.get_event_loop()
    udp_transport, _ = await loop.create_datagram_endpoint(
        lambda: UdpProtocol(on_udp_frame),
        local_addr=("0.0.0.0", 8001),
    )
    logger.info("UDP receiver listening on port 8001")

    yield

    udp_transport.close()
    virtual_cam.stop()


app = FastAPI(title="IP Camera Server", lifespan=lifespan)
app.mount("/static", StaticFiles(directory="static"), name="static")


def _mirror_jpeg(data: bytes) -> bytes:
    img = Image.open(BytesIO(data))
    img = img.transpose(Image.FLIP_LEFT_RIGHT)
    buf = BytesIO()
    img.save(buf, "JPEG")
    return buf.getvalue()


async def broadcast(data: bytes):
    global viewers
    if not viewers:
        return

    mirror = current_state.get("mirror", False)
    if mirror:
        data = _mirror_jpeg(data)

    dead: set[WebSocket] = set()

    async def send(v: WebSocket):
        try:
            await v.send_bytes(data)
        except Exception:
            dead.add(v)

    await asyncio.gather(*[send(v) for v in viewers], return_exceptions=True)
    viewers -= dead


@app.post("/upload")
async def upload_frame(request: Request):
    global frame_buffer
    body = await request.body()
    if not body:
        return {"status": "error", "size": 0}
    frame_buffer = body
    await broadcast(frame_buffer)
    return {"status": "ok", "size": len(frame_buffer)}


@app.websocket("/ws/view")
async def ws_view(websocket: WebSocket):
    await websocket.accept()
    viewers.add(websocket)
    if frame_buffer:
        try:
            await websocket.send_bytes(frame_buffer)
        except Exception:
            viewers.discard(websocket)
            return
    try:
        while True:
            text = await websocket.receive_text()
            command_queue.append(text)
    except WebSocketDisconnect:
        viewers.discard(websocket)


current_state: dict = {}

@app.post("/state")
async def update_state(request: Request):
    global current_state, viewers
    body = await request.json()
    current_state = body
    text = json.dumps(body)
    if viewers:
        dead: set[WebSocket] = set()

        async def send(v: WebSocket):
            try:
                await v.send_text(text)
            except Exception:
                dead.add(v)

        await asyncio.gather(*[send(v) for v in viewers], return_exceptions=True)
        viewers -= dead
    return {"status": "ok"}

@app.get("/command")
async def get_command():
    global command_queue
    if command_queue:
        return {"command": command_queue.pop(0)}
    return {"command": None}


@app.get("/ping")
async def ping():
    return {"status": "ok"}


@app.get("/preview")
async def preview():
    if frame_buffer is None:
        return Response(status_code=204)
    return Response(content=frame_buffer, media_type="image/jpeg")


@app.get("/")
async def index():
    html = (Path(__file__).parent / "templates" / "index.html").read_text()
    html = html.replace("{{LAN_IP}}", lan_ip or "127.0.0.1")
    html = html.replace("{{QR_CODE}}", lan_qr or "")
    return HTMLResponse(html)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
