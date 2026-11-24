# Налаштування середовища

Сервіс можна запустити повністю в Docker або локально з PostgreSQL у контейнері. Для першої перевірки зручніший Docker: він не залежить від локальних версій Java та Maven.

## Варіант 1. Застосунок і PostgreSQL у Docker

### Що потрібно

- Docker Desktop із запущеним Linux engine;
- Docker Compose версії 2;
- вільні порти `8080` і `5433`.

Перевірити Docker:

```powershell
docker version
docker compose version
```

З кореня проєкту запустити стек:

```powershell
docker compose up -d --build
```

Перший build може тривати кілька хвилин, оскільки Docker завантажує образи й Maven-залежності. Перевірити стан контейнерів:

```powershell
docker compose ps
```

Обидва сервіси мають перейти у стан `healthy`. Додаткова перевірка:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Очікувана відповідь містить `"status":"UP"`.

Після запуску доступні:

- API та Swagger UI: `http://localhost:8080/swagger-ui.html`;
- OpenAPI: `http://localhost:8080/v3/api-docs`;
- PostgreSQL із хост-машини: `localhost:5433`;
- база, користувач і пароль для локального стенда: `wallet`.

Подивитися логи застосунку:

```powershell
docker compose logs -f app
```

Зупинити контейнери, зберігши дані PostgreSQL:

```powershell
docker compose down
```

Повністю очистити локальну базу та знову застосувати міграції Flyway:

```powershell
docker compose down -v
docker compose up -d --build
```

Команда з `-v` видаляє Docker volume разом із локальними даними.

## Варіант 2. Локальний запуск застосунку

### Що потрібно

- JDK 21;
- Maven 3.9 або новіший;
- Docker Desktop для PostgreSQL;
- вільні порти `8080` і `5433`.

Перевірити Java та Maven:

```powershell
java -version
mvn -version
```

Обидві команди мають використовувати Java 21. Якщо `java` або `mvn` не знайдено, потрібно додати їхні каталоги `bin` до змінної `PATH` і відкрити новий PowerShell.

Запустити лише PostgreSQL:

```powershell
docker compose up -d postgres
```

Потім запустити застосунок:

```powershell
mvn spring-boot:run
```

Локальні параметри підключення вже задані в `application.properties`:

```text
jdbc:postgresql://localhost:5433/wallet
username: wallet
password: wallet
```

Ці значення призначені лише для локальної розробки. У зовнішньому середовищі їх потрібно передати змінними:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://db-host:5432/wallet"
$env:SPRING_DATASOURCE_USERNAME = "wallet_app"
$env:SPRING_DATASOURCE_PASSWORD = "change-me"
mvn spring-boot:run
```

Паролі та локальні `.env`-файли не слід додавати до Git.

## Запуск тестів

Інтеграційні тести використовують Testcontainers, тому Docker Desktop має працювати:

```powershell
mvn test
```

Тестовий PostgreSQL створюється автоматично. Окремо готувати схему або тестову базу не потрібно.

## Перевірка API

Після старту відкрити Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

Або створити гаманець із PowerShell:

```powershell
$body = @{ openingBalance = 1000.00 } | ConvertTo-Json
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/v1/wallets `
  -ContentType "application/json" `
  -Body $body
```

## Типові проблеми

### Docker API недоступне

```text
failed to connect to the docker API
```

Запустити Docker Desktop і дочекатися стану `Engine running`. Для цього проєкту потрібні Linux containers.

### Порт уже зайнятий

Якщо зайнятий `8080` або `5433`, зупинити процес, який слухає порт, або змінити відповідний mapping у `docker-compose.yml`.

### PostgreSQL відхиляє пароль

Docker volume міг зберегти старі облікові дані. Для чистого локального запуску видалити volume:

```powershell
docker compose down -v
docker compose up -d --build
```

### Flyway повідомляє про невідповідність міграцій

Не редагувати міграцію, яка вже була застосована. Для нової зміни створити наступний файл `V{номер}__опис.sql`. Локальну тестову базу можна перебудувати командою `docker compose down -v`.
