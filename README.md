# Видеопорт - превратите телефон в IP-камеру с AI-эффектами

Android-приложение превращает любой телефон в IP-камеру с AI-эффектами. Python-сервер транслирует видео в браузер и виртуальную камеру (Яндекс Телемост, Zoom, Teams).

## Быстрый старт

### Сервер
```bash
cd Server
pip install -r requirements.txt
python main.py
```
Откройте `http://127.0.0.1:8000` — здесь будет QR-код для подключения.

### Android
1. Скачайте APK из раздела Releases
2. Отсканируйте QR-код на странице сервера через приложение или введите IP вручную

## Возможности

- **AI-размытие фона** — ML Kit Selfie Segmentation
- **Автоцентрирование лица** — ML Kit Face Detection (обрезка по лицу)
- **Цветовые фильтры** — Холодный, тёплый, эффектный (ColorMatrix)
- **Замена фона** — своё изображение из галереи
- **Управление с ПК** — фильтры, поворот, смена камеры из браузера
- **Виртуальная камера** — pyvirtualcam в Телемост/Zoom/Teams/
- **QR-подключение** — сканируете код на странице сервера, приложение подключается само
- **Приватность** — всё работает в вашей локальной сети

### Транспорт

Видео от телефона к серверу — **UDP**. Каждый JPEG-кадр нарезается на пакеты. При потере пакета сервер отбрасывает неполный кадр и ждёт следующий — без блокировок очереди.

Команды от браузера к телефону — **HTTP polling** `GET /command` каждые 500ms. Состояние кнопок — `POST /state`.

## Команды управления

| Кнопка | Действие                |
|--------|-------------------------|
| 1      | Поворот кадра (90°)     |
| 2      | Смена камеры            |
| 3      | Выбор фильтра           |
| 4      | Размытие/замена фона    |
| 5      | Удержание лица в центре |

## Технические детали

- **minSdk**: 26
- **Ориентация**: портретная
- **ML Kit**: Selfie Segmentation + Face Detection
- **Управление**: HTTP polling `/command`, POST `/state`
- **Рассылка в браузер**: WebSocket `/ws/view` (JPEG)
- **Виртуальная камера**: pyvirtualcam

## Файлы

### Mobile/ (Java)
- `CameraService.java` — ImageAnalysis.Analyzer, цепочка обработки кадра
- `FrameProcessor.java` — ML Kit конвейер (blur, faces, filters)
- `UdpVideoSender.java` — фрагментация JPEG, UDP отправка
- `NetworkClient.java` — HTTP polling команд и отправка состояния
- `ImageUtils.java` — YUV↔NV21↔Bitmap↔JPEG конвертеры
- `StreamActivity.java` — основная Activity (камера + управление)
- `QrScannerActivity.java` — ML Kit Barcode Scanner для QR

### Server/ (Python)
- `main.py` — FastAPI (HTTP, WebSocket, UDP)
- `udp_receiver.py` — UDP, сборка фрагментов
- `virtual_cam.py` — pyvirtualcam в отдельном потоке
- `qrcode_utils.py` — генерация QR-кода, определение LAN IP
