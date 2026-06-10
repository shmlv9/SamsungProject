import asyncio
import logging

logger = logging.getLogger(__name__)

HEADER_SIZE = 10
MAX_PARTIAL = 128


class PartialFrame:
    __slots__ = ("total", "segments")

    def __init__(self, total: int):
        self.total = total
        self.segments: dict[int, bytes] = {}


class UdpProtocol(asyncio.DatagramProtocol):
    def __init__(self, on_frame):
        self._partial: dict[int, PartialFrame] = {}
        self._on_frame = on_frame

    def datagram_received(self, data: bytes, _addr):
        if len(data) < HEADER_SIZE:
            return

        frame_id = int.from_bytes(data[0:4], "big")
        total = int.from_bytes(data[4:6], "big")
        seq = int.from_bytes(data[6:8], "big")
        length = int.from_bytes(data[8:10], "big")
        payload = data[HEADER_SIZE:HEADER_SIZE + length]

        if seq == 0 or frame_id not in self._partial:
            self._partial[frame_id] = PartialFrame(total)
            self._trim_old()

        pf = self._partial[frame_id]
        pf.segments[seq] = payload

        if len(pf.segments) == pf.total:
            jpeg = b"".join(pf.segments[i] for i in range(pf.total))
            del self._partial[frame_id]
            self._on_frame(jpeg)

    def _trim_old(self):
        if len(self._partial) > MAX_PARTIAL:
            worst = min(self._partial.keys(), key=lambda k: len(self._partial[k].segments))
            del self._partial[worst]
