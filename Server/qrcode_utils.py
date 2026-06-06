import base64
import socket
from io import BytesIO

import qrcode


def get_lan_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


def generate_qr_base64(data: str) -> str:
    img = qrcode.make(data)
    buf = BytesIO()
    img.save(buf, "PNG")
    return base64.b64encode(buf.getvalue()).decode()
