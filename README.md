# Используйте свой телефон как IP-камеру

Android-приложение для передачи видео с камеры на сервер с фильтрами и AI-эффектами (размытие фона, удержание лица в кадре) и Python-сервер для приёма видеопотока. Видео с телефона можно использовать как виртуальную камеру на компьютере (Zoom, Teams, пр.).

## Архитектура

### Android (`Mobile/`)
- **CameraX** (ImageAnalysis, Preview) — захват кадров
- **OkHttp** (WebSocket + HTTP) — отправка видео и получение команд
- **ML Kit Selfie Segmentation** — размытие фона
- **ML Kit Face Detection** — удержание лица в кадре
- **ColorMatrix** — фильтры (Cold, Warm, Effect)

### Сервер (`Server/`)
- **FastAPI** + **WebSocket** — приём и раздача потока
- **pyvirtualcam** — виртуальная камера (macOS: CMIOExtension, Windows/Linux: OBS backend)
- Веб-интерфейс управления (кнопки, фильтры) на `localhost:8000`

## Быстрый старт

### Сервер
```bash
cd Server
pip install -r requirements.txt
python main.py
```
Откройте `http://localhost:8000` в браузере.

### Android
1. Откройте `Mobile/` в Android Studio
2. Соберите и установите APK
3. Введите IP сервера и нажмите Start

В Zoom/Teams выберите камеру **OBS Virtual Camera**.

## Команды управления

| Кнопка | Действие                             |
|--------|--------------------------------------|
| 1      | Поворот кадра                        |
| 2      | Смена камеры                         |
| 3      | Выбор фильтра (Cold / Warm / Effect) |
| 4      | Размытие фона                        |
| 5      | Удержание лица в центре              |

## Технические детали

- **minSdk**: 26
- **Ориентация**: портретная
- **Формат кадров**: JPEG (quality 85)
- **FPS**: до 30
- **Доставка команд**: HTTP polling `GET /command` (500ms)
- **Синхронизация состояния**: HTTP POST `/state`
