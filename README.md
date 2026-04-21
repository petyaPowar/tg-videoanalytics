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
