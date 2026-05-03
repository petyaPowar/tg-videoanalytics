# tg-videoanalytics

Telegram-бот для агрегации статистики просмотров видео с YouTube и RuTube.

## Требования

- Java 21+
- PostgreSQL 14+
- Maven (или используйте прилагаемый `./mvnw`)

## Настройка

1. Скопируйте `.env.example` в `.env` и заполните переменные:

```
BOT_TOKEN=        # токен от @BotFather
BOT_USERNAME=     # username бота без @
YOUTUBE_API_KEY=  # ключ YouTube Data API v3
ALLOWED_USER_IDS= # Telegram user ID через запятую
DB_URL=           # jdbc:postgresql://localhost:5432/videoanalytics
DB_USER=          # пользователь БД
DB_PASSWORD=      # пароль БД
MAX_VIDEOS=0      # максимум видео (0 — без ограничений)
```

### Получение BOT_TOKEN

1. Откройте Telegram, найдите [@BotFather](https://t.me/BotFather)
2. Отправьте `/newbot`, задайте имя и username
3. Скопируйте токен вида `123456789:AAF...`

### Получение YOUTUBE_API_KEY

1. Перейдите в [Google Cloud Console](https://console.cloud.google.com/)
2. Создайте проект (или выберите существующий)
3. Перейдите в **APIs & Services → Library**, найдите и включите **YouTube Data API v3**
4. Перейдите в **APIs & Services → Credentials → Create Credentials → API Key**
5. Скопируйте ключ и вставьте в `.env`

> Квота YouTube Data API v3 — 10 000 единиц в сутки (один запрос статистики = 1 единица). При превышении бот вернёт понятное сообщение: «Квота YouTube API исчерпана, попробуйте завтра» и сохранит последние известные данные.

## Схема базы данных

Схема применяется автоматически при первом запуске (`schema.sql`).

| Таблица / объект | Назначение |
|---|---|
| `videos` | Основная таблица: URL, платформа, название, просмотры, статус доступности |
| `view_stats` | История замеров: (video_id, view_count, recorded_at) — одна запись на каждый успешный запрос |
| `stats_summary` | VIEW для агрегированной статистики (total / youtube / rutube по count и views) |

**Поля таблицы `videos`:**

| Поле | Тип | Описание |
|---|---|---|
| id | BIGSERIAL | Первичный ключ |
| url | TEXT UNIQUE | Оригинальная ссылка |
| platform | VARCHAR | YOUTUBE / RUTUBE |
| video_id | VARCHAR | ID видео на платформе |
| title | VARCHAR | Название видео |
| view_count | BIGINT | Последнее известное число просмотров |
| last_updated | TIMESTAMP | Время последнего успешного обновления |
| last_error | TEXT | Текст ошибки при недоступности платформы |
| is_available | BOOLEAN | Флаг доступности |
| added_by | BIGINT | Telegram user ID добавившего |
| created_at | TIMESTAMP | Дата добавления |

## Устранение проблем

| Ошибка | Причина | Решение |
|---|---|---|
| `Network unreachable` при сборке | Нет доступа к Maven Central | `network: host` уже настроен в docker-compose |
| «Квота YouTube API исчерпана» | Дневной лимит 10 000 ед. исчерпан | Подождать до полуночи по PT; квота сбрасывается ежедневно |
| Бот не отвечает | Telegram заблокирован на сервере | Настроить VPN/прокси на уровне хоста или роутера |
| Бот игнорирует сообщения | userId не в `ALLOWED_USER_IDS` | Добавить свой Telegram ID в `.env` |

---

2. Создайте базу данных PostgreSQL:

```sql
CREATE DATABASE videoanalytics;
CREATE USER app WITH PASSWORD 'secret';
GRANT ALL PRIVILEGES ON DATABASE videoanalytics TO app;
```

Схема применяется автоматически при первом запуске.

## Запуск

```bash
./mvnw package -q
java -jar target/app.jar
```

## Поддерживаемые форматы URL

| Платформа | Форматы |
|-----------|---------|
| YouTube   | `https://youtube.com/watch?v=ID` |
|           | `https://youtu.be/ID` |
|           | `https://youtube.com/shorts/ID` |
| RuTube    | `https://rutube.ru/video/ID/` |
|           | `https://rutube.ru/shorts/ID/` |

## Команды бота

| Команда | Описание |
|---------|----------|
| `/start` | Главное меню |
| `/add <url>` | Добавить видео по ссылке |
| `/list` | Список всех видео |
| `/stats` | Статистика просмотров |
| `/refresh` | Обновить данные для всех видео |

Все команды также доступны через кнопки меню.

## Docker

Для запуска в Docker нужны только Docker и Docker Compose — Java и PostgreSQL устанавливать отдельно не нужно.

1. Скопируйте `.env.example` в `.env` и заполните `BOT_TOKEN`, `BOT_USERNAME`, `YOUTUBE_API_KEY`, `ALLOWED_USER_IDS`. Поля `DB_*` можно оставить без изменений — docker-compose подставит их автоматически.

2. Запустите:

```bash
docker compose up --build -d
```

Контейнеры `db` (PostgreSQL) и `app` (бот) поднимутся автоматически. Схема БД применяется при первом старте приложения. Данные сохраняются в volume `pgdata`.

3. Просмотр логов:

```bash
docker compose logs -f app
```

4. Остановка:

```bash
docker compose down
```
