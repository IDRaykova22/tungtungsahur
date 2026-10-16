# Филтриране на събития по дата на създаване — учебно ръководство за Java и Spring Boot

Това ръководство обяснява метода `EventService.filterPublishedEvents(YearMonth month, int week)` и използването му в страницата `/events`. Структурата следва [ръководството за отчетите](reports-guide-bg.md): правила → MVC → данни → период → repository → service → екран → проверки → приложения с код. Съдържанието съответства на реализацията към 30.09.2026 г.

**Филтрирането и подреждането използват `Event.createdAt` — кога събитието е създадено.** `startsAt` описва кога ще се проведе и не определя резултата от филтъра.

Кодът вече е в проекта. Стъпките са за обучение и повторно изграждане в учебно копие. Не създавай втори `EventController` или втори handler за същия GET адрес. Пълните примери в приложенията са снимка на настоящия код; при бъдещи промени ги сверявай с `src`.

## Съдържание

- [1. Какво прави функционалността](#1-какво-прави-функционалността)
- [2. Подготовка и стартиране](#2-подготовка-и-стартиране)
- [3. MVC и отговорности](#3-mvc-и-отговорности)
- [4. Данните и двата вида дата](#4-данните-и-двата-вида-дата)
- [5. Файлове и ред за реализация](#5-файлове-и-ред-за-реализация)
- [6. Месецът и седмиците](#6-месецът-и-седмиците)
- [7. Защо краят е изключващ](#7-защо-краят-е-изключващ)
- [8. Заявката към базата](#8-заявката-към-базата)
- [9. Главният метод в услугата](#9-главният-метод-в-услугата)
- [10. Контролерът и входните параметри](#10-контролерът-и-входните-параметри)
- [11. Екранът, формата и CSS](#11-екранът-формата-и-css)
- [12. Навигация и достъп](#12-навигация-и-достъп)
- [13. Автоматични и ръчни проверки](#13-автоматични-и-ръчни-проверки)
- [14. Чести грешки](#14-чести-грешки)
- [15. Как да обясниш реализацията и да я надградиш](#15-как-да-обясниш-реализацията-и-да-я-надградиш)
- [16. Източници](#16-източници)
- [Приложение А. Пълен код на новия тестов файл](#приложение-а-пълен-код-на-новия-тестов-файл)
- [Приложение Б. Промени в съществуващите файлове](#приложение-б-промени-в-съществуващите-файлове)
- [Приложение В. Проверка на документацията](#приложение-в-проверка-на-документацията)

## 1. Какво прави функционалността

Посетителят отваря **Events**, избира **Created in month**, по желание избира **Creation week** и натиска **Filter events**. Показват се публикуваните събития, създадени през избрания период, подредени от най-рано създаденото към най-късно създаденото.

Примерен адрес:

```text
/events?month=2026-09&week=3
```

Той включва събития, създадени от 15 до 21 септември 2026 г. включително. Ако събитие е създадено на 21 септември за провеждане на 9 октомври, то влиза в този септемврийски резултат.

### Договорени правила

| Въпрос | Реализация |
| --- | --- |
| Кое поле определя периода и реда? | `Event.createdAt`. |
| Кои статуси се включват? | Само `PUBLISHED`. |
| Чии събития се включват? | На всички организатори; няма филтър по текущ потребител. |
| Какъв е редът? | По дата и час на създаване във възходящ ред. |
| Какво означава седмица? | Групи от дни в месеца: 1–7, 8–14, 15–21, 22–28, 29–края. |
| Какво означава `week=0`? | Всички седмици в избрания месец. |
| Какво става без месец и без седмица? | Всички публикувани събития, подредени по създаване. |
| Нужен ли е вход? | Не. GET страницата е публична. |
| Какво става без дата на провеждане? | Събитието може да участва, защото има `createdAt`; показва се `Date TBA`. |

Текущата форма избира **месец и седмица**. Денят, часът и минутите участват в сравнението и подредбата на timestamps; няма отделни контроли за филтър по конкретен час или ден.

За разлика от модула „Отчети“, тук няма ограничение до последните 12 месеца и няма забрана за бъдещ месец. Ако няма подходящи записи, резултатът е празен. Отчетите включват и `CLOSED`, а този списък включва само `PUBLISHED`.

## 2. Подготовка и стартиране

Използват се наличните зависимости на проекта: Java 17, Spring MVC, Spring Data JPA, Thymeleaf, Spring Security, Lombok, MySQL и H2 за тестовете. Не се добавят таблици, външен календар или JavaScript библиотека.

Отвори проекта като Maven проект в IntelliJ IDEA. Работи в `src/main/java`, `src/main/resources` и `src/test/java`. Директорията `target` съдържа генерирани файлове.

От директорията с `pom.xml`:

```bash
docker compose up -d --build
```

Отвори `http://localhost:8080/events`. След промяна на кода, при вече работеща база, обнови приложението с:

```bash
docker compose up -d --build --no-deps app
```

За диагностични съобщения използвай `docker compose logs app --tail 100`. Автоматичните тестове използват H2 и не изискват стартиран Docker. Подробностите за директно стартиране на Java с локалния MySQL са в раздел 2 на [шаблона](reports-guide-bg.md#2-подготовка-и-стартиране).

## 3. MVC и отговорности

```text
Браузър: GET /events?month=2026-09&week=3
    ↓
EventController: преобразува параметрите и извиква услугата
    ↓
EventService: изчислява начало и изключващ край
    ↓
EventRepository: WHERE по createdAt и ORDER BY createdAt
    ↓
EventService: връща List<Event>
    ↓
EventController: добавя events, month и week в Model
    ↓
Thymeleaf: templates/events/list.html
    ↓
Браузърът показва картите
```

| Слой | Отговорност |
| --- | --- |
| Entity Model — `Event` | Пази полетата на събитието, включително двата timestamps. |
| Repository — `EventRepository` | Извлича и подрежда записите в базата. |
| Service — `EventService` | Проверява допустимата седмица и изчислява периода. |
| Controller — `EventController` | Обработва HTTP входа, грешките и данните за View. |
| View — `events/list.html` | Показва формата, избраните стойности и резултатите. |

Service и Repository разделят логиката и достъпа до данни в Spring приложението. Не са допълнителни букви в MVC. Контролерът не изчислява границите на седмицата, а HTML не решава дали дадено събитие попада в периода.

## 4. Данните и двата вида дата

Съществуващият [Event.java](../src/main/java/org/codingburgas/demetra/models/Event.java) съдържа:

```java
private LocalDateTime startsAt;

@Column(nullable = false)
private LocalDateTime createdAt = LocalDateTime.now();
```

| Поле | Значение | Роля тук |
| --- | --- | --- |
| `createdAt` | Момент на създаване на записа за събитието. | Филтриране, подреждане и надпис `Created:`. |
| `startsAt` | Планиран момент на провеждане. Може да е `null`. | Информация с надпис `Scheduled:` или `Date TBA`. |

При създаване на нов обект `Event` полето `createdAt` получава стойност от `LocalDateTime.now()`. `createEvent(...)` задава организатора и статуса и записва обекта. При четене се използва съхраненият timestamp, а не моментът на отваряне на страницата.

`YearMonth` представя месец за конкретна година, например септември 2026. `LocalDateTime` съдържа и ден, и час, затова е подходящ за границите и сравнението с entity полето. `List<Event>` е списъкът с резултатите.

`LocalDateTime` не съдържа часова зона. Филтърът сравнява стойностите така, както са записани; показването не ги преобразува автоматично към часовата зона на посетителя. Процесът в Docker и този в IDE могат да използват различни зони при `now()`.

## 5. Файлове и ред за реализация

| Файл | Какво е добавено или променено |
| --- | --- |
| `repositories/EventRepository.java` | Метод за период по `createdAt` и подредба по `createdAt` за целия списък. |
| `services/EventService.java` | `filterPublishedEvents` и използване на новата подредба в `listPublishedEvents`. |
| `controllers/EventController.java` | Параметри `month` и `week`, извикване на услугата, обработка на невалидния вход, стойности за View. |
| `templates/events/list.html` | Форма, съобщение при празен период, показване на датата на създаване и етикети. |
| `static/css/style.css` | Подреждане и пренасяне на контролите и датите. |
| `EventFilterTests.java` | Нов тестов клас за периода и целия поток. |
| `models/Event.java`, `config/SecurityConfig.java` | Използват се съществуващото поле и публичният GET достъп. |

Java файловете са под `src/main/java/org/codingburgas/demetra`, шаблоните и CSS — под `src/main/resources`, а тестовете — под `src/test/java/org/codingburgas/demetra`.

Редът за учебно изграждане е: провери `Event.createdAt` → добави repository методите → добави service метода → обнови съществуващия handler `/events` → обнови шаблона и CSS → провери тестовете. Новият тестов файл е в приложение А. Кодът за съществуващите файлове е в приложение Б.

## 6. Месецът и седмиците

В Java `week=0` означава целия избран месец. Останалите стойности са:

| week | Начален ден | Последен включен ден |
| --- | --- | --- |
| 1 | 1 | 7 |
| 2 | 8 | 14 |
| 3 | 15 | 21 |
| 4 | 22 | 28 |
| 5 | 29, ако съществува | 29, 30 или 31 според месеца |

Това не са календарни седмици понеделник–неделя. Първата група винаги започва на първо число, независимо от деня на седмицата.

Месечните граници се изчисляват с:

```java
LocalDateTime start = month.atDay(1).atStartOfDay();
LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
```

`atDay(1)` създава дата за първия ден, а `atStartOfDay()` добавя час `00:00`. `plusMonths(1)` връща следващия месец и обработва автоматично смяната на годината. Тези операции са част от [Java YearMonth API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/YearMonth.html).

Примерни граници:

| Избор | Начало, включено | Край, изключен |
| --- | --- | --- |
| 2026-09, всички седмици | 01.09.2026 00:00 | 01.10.2026 00:00 |
| 2026-09, седмица 3 | 15.09.2026 00:00 | 22.09.2026 00:00 |
| 2026-09, седмица 5 | 29.09.2026 00:00 | 01.10.2026 00:00 |
| 2024-02, седмица 5 | 29.02.2024 00:00 | 01.03.2024 00:00 |
| 2026-02, седмица 5 | Няма дни | Празен списък |
| 2026-12, всички седмици | 01.12.2026 00:00 | 01.01.2027 00:00 |

## 7. Защо краят е изключващ

Заявката използва интервал `[start, end)`:

```text
createdAt >= start
createdAt < end
```

При седмица 3 на септември запис от `15.09 00:00` се включва. Запис от `21.09 23:59:59` също се включва. Запис от `22.09 00:00` вече е извън периода.

Това включва и стойности с части от секундата преди `end`. Не се налага да гадаем последния възможен timestamp на деня. Условието `<= end` би включило първия момент от следващия период и би било грешно за тази реализация.

Периодът филтрира записите, а подредбата сравнява целия `createdAt`. Събития, създадени на една дата в `09:00`, `09:30` и `18:20`, се връщат в този ред. Няма нужда от отделно сортиране по час.

## 8. Заявката към базата

В `EventRepository` е деклариран:

```java
List<Event> findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
        EventStatus status, LocalDateTime start, LocalDateTime end);
```

Spring Data JPA извежда заявка от името на метода. Декларацията изглежда дълга, защото описва условията и подредбата без ръчен SQL.

| Част от името | Значение |
| --- | --- |
| `findByStatus` | Статусът е равен на подадения параметър. |
| `AndCreatedAtGreaterThanEqual` | `createdAt >= start`. |
| `AndCreatedAtLessThan` | `createdAt < end`. |
| `OrderByCreatedAtAsc` | Възходящ ред по `createdAt`. |

Това използване на ключови думи е описано в [Spring Data JPA: Query Methods](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html). Името сочи Java свойството `createdAt`, а не името на SQL колоната.

Услугата подава `EventStatus.PUBLISHED`. Затова `DRAFT`, `CANCELLED` и `CLOSED` не участват. Не се подава организатор или текущ потребител, така че резултатът е общ за публикуваните събития.

При липса на филтър се използва:

```java
List<Event> findByStatusOrderByCreatedAtAsc(EventStatus status);
```

Така и след **Clear filters** редът остава по създаване. При точно еднакви `createdAt` стойности няма допълнителен критерий по ID и взаимният ред не е определен. Списъкът не е странициран.

## 9. Главният метод в услугата

Методът е в съществуващия `EventService`. Той има `@Transactional(readOnly = true)` и връща `List<Event>`. Пълният точен код е в приложение Б.

### Проверка на входа

```java
if (week < 0 || week > 5 || (month == null && week != 0)) {
    throw new IllegalArgumentException("Choose a month and a week from 1 to 5, or all weeks.");
}
```

`||` означава „или“, а `&&` — „и“. Отхвърля се седмица извън 0–5 или избрана конкретна седмица без месец. `week=0` и `month=null` са валидни: означават липса на филтър.

`throw` прекратява нормалното изпълнение и предава грешката към контролера. Услугата не избира HTTP статус; това е работа на уеб слоя.

### Без избран месец

```java
if (month == null) {
    return listPublishedEvents();
}
```

Използва се съществуващият помощен метод, който вече подрежда по `createdAt`. `return` приключва метода и не се изчисляват месечни граници.

### Избран месец и седмица

След създаване на месечните `start` и `end`, при `week > 0` се изпълнява:

```java
start = start.plusWeeks(week - 1);
```

За първата седмица добавяме нула седмици; за третата — две, тоест 14 дни. Обектите за дата и час са неизменяеми: `plusWeeks` връща нова стойност, която трябва да се присвои обратно на `start`.

```java
if (!start.isBefore(end)) {
    return List.of();
}
```

Ако началото вече е равно на или след края на месеца, няма валидни дни. Това се случва за седмица 5 през февруари с 28 дни. `!` отрича условието, а `List.of()` връща празен списък. В този случай не се прави заявка за събития.

```java
LocalDateTime weekEnd = start.plusWeeks(1);
if (weekEnd.isBefore(end)) {
    end = weekEnd;
}
```

Краят става по-ранната от двете стойности: край на седемдневната група или край на месеца. За седмица 5 на септември не се допуска включване на октомврийски записи.

Накрая услугата извиква repository с `PUBLISHED`, `start` и `end`. Базата извършва филтрирането и сортирането; не се зареждат всички събития за последващ Java цикъл.

## 10. Контролерът и входните параметри

Обновява се съществуващият метод с `@GetMapping("/events")` в `EventController`:

```java
@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month
@RequestParam(defaultValue = "0") int week
```

Това са два параметъра от сигнатурата, показани отделно за обяснение. `@RequestParam` чете стойностите от URL. `required = false` позволява месецът да липсва. `@DateTimeFormat` задава формат като `2026-09`, а `week` по подразбиране е 0. Механизмът за преобразуване и незадължителни параметри е описан в [Spring MVC: @RequestParam](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestparam.html).

| Адрес | Резултат |
| --- | --- |
| `/events` | Всички публикувани събития, по създаване. |
| `/events?month=2026-09` | Целият септември 2026. |
| `/events?month=2026-09&week=3` | Създадени през дни 15–21. |
| `/events?week=1` | HTTP 400: липсва месец за седмицата. |
| `/events?month=2026-13` | HTTP 400: невалиден месец. |
| `/events?month=2026-09&week=abc` | HTTP 400: не може да се преобразува в `int`. |
| `/events?month=2026-09&week=6` | HTTP 400: седмица извън позволените. |

Грешките при преобразуване се обработват от Spring преди нормалното изпълнение на handler метода. `IllegalArgumentException` от услугата се превежда чрез `ResponseStatusException(HttpStatus.BAD_REQUEST, ...)` в HTTP 400. Няма специална форма за показване на тези грешки в самата страница; използва се стандартната обработка на грешки на приложението.

Контролерът поставя `events`, `month` и `week` в `Model`, след което връща `events/list`. Съществуващият код създава и `confirmedCounts` за броя записвания. За влязъл потребител добавя `myStatuses`, за да се покажат неговите регистрации. Това не определя кои събития минават през филтъра и не изисква вход за разглеждане.

Прочитането на филтрираните събития е една repository заявка. Съществуващите броячи извикват допълнително `confirmedCount` за всяко показано събитие, затова цялата страница не използва само една SQL заявка.

## 11. Екранът, формата и CSS

Формата в `templates/events/list.html` използва GET и адрес `/events`. Полето `input type="month"` изпраща стойност `yyyy-MM`, независимо как браузърът визуално показва месеца. `required` изисква месец при подаване през формата; липсата на филтър се избира чрез **Clear filters**.

`name="month"` и `name="week"` съответстват на Java параметрите. `label for` сочи `id` на съответния контрол. `th:value="${month}"` и `th:selected="${week == ...}"` запазват избора след заявката.

Шаблонът обхожда вече готовия списък с `th:each="event : ${events}"`. Новият ред за създаването е:

```html
<div class="event-meta" th:text="'Created: ' + ${#temporals.format(event.createdAt, 'dd MMM yyyy, HH:mm')}">Creation date and time</div>
```

`dd` е ден, `MMM` — кратко име на месец, `yyyy` — година, `HH` — час от 00 до 23, а `mm` — минути. Locale може да промени езика на месеца. Показването до минута не намалява точността на timestamp, използван за сравнение в базата.

`Scheduled:` показва отделно `startsAt`, когато има такава стойност. При `null` се показва `Date TBA`. Събитие без планиран час може да присъства в резултата по създаване.

При празен филтриран списък се показва `No published events were created in this period.`. Без филтър празният списък има общото съобщение, че няма публикувани събития.

Използват се `th:text` за екраниран текст, `th:if`/`th:unless` за условни елементи и `th:replace` за общата навигация. Общото поведение на тези атрибути е описано в [Thymeleaf 3.1](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html).

`.event-filter-form` подрежда контролите с flex, отстояния и пренасяне. Правилото за `.form-group` премахва излишен долен margin. Допълнението към `.event-card-image` позволява пренасяне на по-дългия надпис с дата и час.

## 12. Навигация и достъп

Използва се съществуващата връзка **Events**. Не е добавена нова страница или нова връзка специално за филтъра. В `SecurityConfig` съществуващото правило разрешава GET `/events` и `/events/*` за всички посетители.

Входът и ролята `ORGANIZER` остават нужни за други действия, например създаване на събитие и отваряне на отчетите. Те не се изискват за този филтър.

Бутонът „Отчети за събитията“, който присъства в актуалния шаблон, е част от отделната функционалност за отчети. Той е запазен в пълния пример в приложение Б, за да не се загуби при учебно сравнение на файловете.

## 13. Автоматични и ръчни проверки

### Автоматични тестове

```bash
mvn -Dtest=EventFilterTests test
```

В `EventFilterTests` има **16 тестови изпълнения**:

| Проверка | Брой изпълнения |
| --- | --- |
| Месец, часове, `createdAt` срещу `startsAt`, изключване на чернова | 1 |
| Всяка от петте седмици | 5 |
| Последна седмица във високосен февруари, април и декември | 3 |
| Пета седмица през февруари с 28 дни | 1 |
| Невалидни параметри | 5 |
| Изчистване на филтъра, ред по създаване и липсващ `startsAt` | 1 |

`@SpringBootTest` зарежда приложението; `MockMvc` изпълнява HTTP заявките през MVC и рендерира Thymeleaf. Профилът е `test`, а базата е отделна H2 база `event-filter`. `@Transactional` върху тестовия клас връща промените след всеки тест.

Тестовите записи имат фиксирани creation timestamps. Планираните дати нарочно се различават; при някои записи са в обратен ред или липсват. Така тестът би открил връщане към неправилното поле `startsAt`. Параметризираните тестове проверяват включено начало и изключен край. Няма вход с реален акаунт: заявките проверяват публичния достъп.

### Ръчна проверка

1. Отвори `/events` без вход. Провери текстовете **Created in month** и **Creation week**.
2. Намери събитие и прочети стойността след **Created:**.
3. Избери съответния месец и **All weeks**. Събитието трябва да присъства, ако е публикувано.
4. Избери групата дни за неговата дата на създаване. Например 21-во число е седмица 3, а 22-ро — седмица 4.
5. Сравни `Created:` и `Scheduled:`. Планиран месец, различен от месеца на създаване, не променя принадлежността към резултата.
6. Натисни **Clear filters**. Всички публикувани събития се връщат и остават подредени по създаване.
7. Избери февруари 2026, седмица 5: резултатът трябва да е празен.
8. За различни часове и исторически граници използвай автоматичните тестове, без да редактираш реални timestamps само за демонстрация.

## 14. Чести грешки

| Симптом | Причина и решение |
| --- | --- |
| Събитие за октомври се вижда в септември | Правилно е, ако `Created:` е през септември. |
| След Clear filters редът пак е по провеждане | Провери дали `listPublishedEvents` използва `OrderByCreatedAtAsc`. |
| Събития без планирана дата изчезват | Провери дали заявката не е останала по `startsAt`. |
| Влиза първият ден на следващия месец | Краят трябва да е `< end`, а не `<= end`. |
| Пета седмица на февруари дава мартенски записи | Липсва проверката дали изчисленото начало е преди края на месеца. |
| Седмица 1 започва на осмо число | Трябва да се добавят `week - 1` седмици. |
| Седмицата не започва в понеделник | Това е договорената група от дни, а не ISO календарна седмица. |
| Невалиден URL връща HTTP 400 | Провери формата `yyyy-MM`, седмица 0–5 и наличието на месец за конкретна седмица. |
| Отчетът съдържа повече събития от филтъра | Отчетът включва и `CLOSED`, филтърът — само `PUBLISHED`. |
| Промените не се виждат | Обнови Docker образа и презареди браузъра. |
| Часът в Docker се различава от този в IDE | Сравни часовите зони на Java процесите; `LocalDateTime` не извършва автоматично преобразуване. |

## 15. Как да обясниш реализацията и да я надградиш

За представяне можеш да кажеш: „Контролерът приема месец и номер на седмица. Услугата превръща избора в интервал с включено начало и изключен край. Repository връща публикуваните събития, чието createdAt е в интервала, подредени по същото поле. Thymeleaf показва резултатите, датата на създаване и отделно планираната дата. Филтърът е публичен и не променя записи.“

За упражнение пресметни на хартия границите за седмица 3 на септември и седмица 5 на февруари във високосна и невисокосна година. След това ги сравни с кода и тестовете.

Възможни бъдещи разширения са низходящ ред, отделен филтър по ден или час и странициране. При много събития може да се оптимизира и съществуващото четене на броячите за записвания. Тези разширения не са част от настоящата реализация. Допълнителен DTO за периода или service интерфейс не е необходим за текущия кратък алгоритъм.

## 16. Източници

Правилата за седмиците, публичния достъп и включените статуси са правила на Demetra и се сверяват с `src`. Структурата на документа следва [reports-guide-bg.md](reports-guide-bg.md). Официални източници за използваните механизми:

- [Java 17: YearMonth](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/YearMonth.html) — месечни операции и създаване на дати.
- [Spring Data JPA: Query Methods](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html) — генериране на заявки от имената на методите.
- [Spring MVC: @RequestParam](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestparam.html) — преобразуване на параметри от URL.
- [Spring Data JPA: Transactionality](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html) — транзакции за четене.
- [Thymeleaf 3.1](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html) — форматиране и визуализиране на резултатите.

Първият метод е описан отделно в [ръководството за подреждане на потребителите](users-guide-bg.md).

## Приложение А. Пълен код на новия тестов файл

При добавянето на филтъра бизнес класовете вече съществуват; новият файл е тестовият клас. Следва текущото му съдържание.

### А.1. Тестове за филтъра по създаване

Файл: [src/test/java/org/codingburgas/demetra/EventFilterTests.java](../src/test/java/org/codingburgas/demetra/EventFilterTests.java)

```java
package org.codingburgas.demetra;

import org.codingburgas.demetra.models.Event;
import org.codingburgas.demetra.models.EventStatus;
import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.repositories.EventRepository;
import org.codingburgas.demetra.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:event-filter;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EventFilterTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    private User organizer;

    @BeforeEach
    void setUp() {
        organizer = userRepository.save(new User("filter@example.com", "test-hash", "Organizer", Role.ORGANIZER));
    }

    @Test
    void filtersByCreationMonthAndSortsByCreationTimeRegardlessOfScheduledDate() throws Exception {
        Event previous = saveEvent("Previous month", LocalDateTime.of(2025, 12, 31, 23, 59));
        previous.setStartsAt(LocalDateTime.of(2026, 1, 10, 12, 0));
        Event evening = saveEvent("Evening", LocalDateTime.of(2026, 1, 10, 18, 30));
        Event morning = saveEvent("Morning", LocalDateTime.of(2026, 1, 10, 9, 0));
        evening.setStartsAt(LocalDateTime.of(2026, 1, 10, 8, 0));
        morning.setStartsAt(LocalDateTime.of(2026, 1, 10, 20, 0));
        Event first = saveEvent("First", LocalDateTime.of(2026, 1, 1, 0, 0));
        first.setStartsAt(null);
        Event last = saveEvent("Last", LocalDateTime.of(2026, 1, 31, 23, 59, 59));
        saveEvent("Next month", LocalDateTime.of(2026, 2, 1, 0, 0));
        Event draft = saveEvent("Draft", LocalDateTime.of(2026, 1, 2, 12, 0));
        draft.setStatus(EventStatus.DRAFT);

        mockMvc.perform(get("/events").param("month", "2026-01"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("events", contains(first, morning, evening, last)))
                .andExpect(content().string(containsString("Created: 10 Jan 2026, 09:00")))
                .andExpect(content().string(containsString("Created: 10 Jan 2026, 18:30")))
                .andExpect(content().string(containsString("value=\"2026-01\"")));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void includesOnlyTheSelectedWeek(int week) throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0).plusWeeks(week - 1);
        LocalDateTime end = week == 5 ? LocalDateTime.of(2026, 2, 1, 0, 0) : start.plusWeeks(1);
        saveEvent("Before", start.minusSeconds(1));
        Event last = saveEvent("Last", end.minusSeconds(1));
        Event first = saveEvent("First", start);
        saveEvent("After", end);

        mockMvc.perform(get("/events").param("month", "2026-01").param("week", String.valueOf(week)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("events", contains(first, last)))
                .andExpect(content().string(containsString("value=\"" + week + "\" selected=\"selected\"")));
    }

    @ParameterizedTest
    @CsvSource({"2024-02, 29", "2026-04, 30", "2026-12, 31"})
    void lastWeekStopsAtTheEndOfTheMonth(String monthText, int lastDay) throws Exception {
        YearMonth month = YearMonth.parse(monthText);
        Event last = saveEvent("Last day", month.atDay(lastDay).atTime(23, 59));
        saveEvent("Next month", month.plusMonths(1).atDay(1).atStartOfDay());

        mockMvc.perform(get("/events").param("month", monthText).param("week", "5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("events", contains(last)));
    }

    @Test
    void fifthWeekOfFebruaryWith28DaysIsEmpty() throws Exception {
        saveEvent("March", LocalDateTime.of(2026, 3, 1, 0, 0));

        mockMvc.perform(get("/events").param("month", "2026-02").param("week", "5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("events", empty()))
                .andExpect(content().string(containsString("No published events were created in this period.")));
    }

    @ParameterizedTest
    @CsvSource({"2026-13, 0", "2026-01, -1", "2026-01, 6", "2026-01, abc", ", 1"})
    void rejectsInvalidFilters(String month, String week) throws Exception {
        mockMvc.perform(get("/events").param("month", month == null ? "" : month).param("week", week))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clearingFiltersSortsByCreationTimeAndIncludesEventsWithoutScheduledDates() throws Exception {
        Event later = saveEvent("Created later", LocalDateTime.of(2026, 2, 1, 12, 0));
        later.setStartsAt(LocalDateTime.of(2026, 3, 1, 12, 0));
        Event earlier = saveEvent("Created earlier", LocalDateTime.of(2026, 1, 1, 12, 0));
        earlier.setStartsAt(LocalDateTime.of(2026, 4, 1, 12, 0));
        Event undated = saveEvent("Date to be announced", LocalDateTime.of(2026, 1, 15, 12, 0));
        undated.setStartsAt(null);

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("events", contains(earlier, undated, later)))
                .andExpect(content().string(containsString("Date TBA")));
    }

    private Event saveEvent(String title, LocalDateTime createdAt) {
        Event event = new Event();
        event.setTitle(title);
        event.setCreatedAt(createdAt);
        event.setStartsAt(createdAt.plusMonths(3));
        event.setCapacity(10);
        event.setOrganizer(organizer);
        return eventRepository.save(event);
    }
}
```

## Приложение Б. Промени в съществуващите файлове

За repository и HTML е показан целият текущ файл. За големите service и controller класове са показани необходимите imports и съответните методи; останалите методи за създаване, преглед и записване за събитие се запазват.

### Б.1. EventRepository.java

Файл: [src/main/java/org/codingburgas/demetra/repositories/EventRepository.java](../src/main/java/org/codingburgas/demetra/repositories/EventRepository.java)

```java
package org.codingburgas.demetra.repositories;

import org.codingburgas.demetra.models.Event;
import org.codingburgas.demetra.models.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByStatusOrderByCreatedAtAsc(EventStatus status);

    List<Event> findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            EventStatus status, LocalDateTime start, LocalDateTime end);
}
```

### Б.2. EventService.java

Файл: [EventService.java](../src/main/java/org/codingburgas/demetra/services/EventService.java).

Допълнителни imports:

```java
import java.time.LocalDateTime;
import java.time.YearMonth;
```

Съществуващият помощен метод вече подрежда по създаване:

```java
    public List<Event> listPublishedEvents() {
        return eventRepository.findByStatusOrderByCreatedAtAsc(EventStatus.PUBLISHED);
    }
```

Новият метод за филтриране:

```java
    @Transactional(readOnly = true)
    public List<Event> filterPublishedEvents(YearMonth month, int week) {
        if (week < 0 || week > 5 || (month == null && week != 0)) {
            throw new IllegalArgumentException("Choose a month and a week from 1 to 5, or all weeks.");
        }
        if (month == null) {
            return listPublishedEvents();
        }

        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();

        if (week > 0) {
            start = start.plusWeeks(week - 1);
            if (!start.isBefore(end)) {
                return List.of();
            }
            LocalDateTime weekEnd = start.plusWeeks(1);
            if (weekEnd.isBefore(end)) {
                end = weekEnd;
            }
        }

        return eventRepository.findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                EventStatus.PUBLISHED, start, end);
    }
```

### Б.3. EventController.java

Файл: [EventController.java](../src/main/java/org/codingburgas/demetra/controllers/EventController.java).

Добавени imports:

```java
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import java.time.YearMonth;
```

Обновеният handler, включително съществуващите броячи и статуси за страницата:

```java
    @GetMapping("/events")
    public String list(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                       @RequestParam(defaultValue = "0") int week,
                       Model model, @AuthenticationPrincipal AppUserPrincipal principal) {
        List<Event> events;
        try {
            events = eventService.filterPublishedEvents(month, week);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        Map<Long, Long> confirmedCounts = events.stream()
                .collect(java.util.stream.Collectors.toMap(Event::getId, eventService::confirmedCount));
        model.addAttribute("events", events);
        model.addAttribute("confirmedCounts", confirmedCounts);
        model.addAttribute("month", month);
        model.addAttribute("week", week);

        if (principal != null) {
            User user = principal.getUser();
            Map<Long, String> myStatuses = eventService.myRegistrations(user).stream()
                    .filter(r -> r.getStatus() != org.codingburgas.demetra.models.RegistrationStatus.CANCELLED)
                    .collect(java.util.stream.Collectors.toMap(r -> r.getEvent().getId(), r -> r.getStatus().name()));
            model.addAttribute("myStatuses", myStatuses);
        }

        return "events/list";
    }
```

### Б.4. Актуалният HTML шаблон

Файл: [src/main/resources/templates/events/list.html](../src/main/resources/templates/events/list.html)

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Events — Demetra</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
    <link rel="icon" th:href="@{/images/favicon.svg}">
</head>
<body>
<div th:replace="~{fragments/nav :: nav}"></div>

<div class="page-container">
    <div class="page-header">
        <div class="breadcrumbs">Demetra / Events</div>
        <h1>Musical Events</h1>
        <a sec:authorize="hasRole('ORGANIZER')" th:href="@{/reports}" class="btn report-entry">Отчети за събитията</a>
    </div>

    <form method="get" th:action="@{/events}" class="event-filter-form">
        <div class="form-group">
            <label for="month">Created in month</label>
            <input type="month" id="month" name="month" th:value="${month}" required>
        </div>
        <div class="form-group">
            <label for="week">Creation week</label>
            <select id="week" name="week">
                <option value="0" th:selected="${week == 0}">All weeks</option>
                <option value="1" th:selected="${week == 1}">Week 1 (days 1–7)</option>
                <option value="2" th:selected="${week == 2}">Week 2 (days 8–14)</option>
                <option value="3" th:selected="${week == 3}">Week 3 (days 15–21)</option>
                <option value="4" th:selected="${week == 4}">Week 4 (days 22–28)</option>
                <option value="5" th:selected="${week == 5}">Week 5 (days 29–end)</option>
            </select>
        </div>
        <button type="submit" class="btn">Filter events</button>
        <a th:href="@{/events}" class="btn btn-secondary">Clear filters</a>
    </form>
    <p class="event-meta">Events are filtered and ordered by creation date and time, oldest first.</p>

    <div th:if="${#lists.isEmpty(events)}" class="no-events">
        <span th:text="${month != null} ? 'No published events were created in this period.' : 'No events have been published yet.'">No events</span>
        <div sec:authorize="hasRole('ORGANIZER')" style="margin-top: 12px;">
            <a th:href="@{/events/new}" class="btn">Host an event</a>
        </div>
    </div>

    <div class="events-grid" th:unless="${#lists.isEmpty(events)}">
        <article class="event-card" th:each="event : ${events}">
            <div class="event-card-image">
                <span th:text="${event.category ?: 'Event'}">Category</span>
                <span th:text="'Scheduled: ' + ${#temporals.format(event.startsAt, 'dd MMM yyyy, HH:mm')}" th:if="${event.startsAt}">Scheduled date and time</span>
                <span th:unless="${event.startsAt}">Date TBA</span>
            </div>
            <div class="event-card-body">
                <h3 th:text="${event.title}">Title</h3>
                <div class="event-meta" th:text="'Created: ' + ${#temporals.format(event.createdAt, 'dd MMM yyyy, HH:mm')}">Creation date and time</div>
                <div class="event-meta" th:text="${event.location ?: 'Location TBA'}">Location</div>
                <div class="event-card-footer">
                    <span class="badge"
                          th:classappend="${confirmedCounts.get(event.id) >= event.capacity} ? 'badge-full' : 'badge-open'"
                          th:text="${confirmedCounts.get(event.id) >= event.capacity} ? 'Full' : 'Open'">Open</span>
                    <span th:text="${event.price > 0} ? '$' + ${#numbers.formatDecimal(event.price, 1, 2)} : 'Free'">Free</span>
                </div>
                <div class="event-meta" th:text="${confirmedCounts.get(event.id)} + ' / ' + ${event.capacity} + ' registered'">0 / 0</div>
                <span class="badge badge-registered" th:if="${myStatuses != null and myStatuses.get(event.id) == 'CONFIRMED'}">You're registered</span>
                <span class="badge badge-waitlisted" th:if="${myStatuses != null and myStatuses.get(event.id) == 'WAITLISTED'}">Waitlisted</span>
                <a th:href="@{'/events/' + ${event.id}}" class="btn" style="text-align:center;">View event</a>
            </div>
        </article>
    </div>
</div>
</body>
</html>
```

В този пълен шаблон присъства и съществуващият бутон към отчетите. Той не е част от филтрирането; не премахвай свързаната функционалност при работа по филтъра.

### Б.5. CSS допълнения

В [style.css](../src/main/resources/static/css/style.css) се използват следните допълнения:

```css
.event-filter-form { display: flex; align-items: end; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.event-filter-form .form-group { margin-bottom: 0; }
.event-card-image { flex-wrap: wrap; gap: 8px; }
```

### Б.6. Съществуващият публичен GET достъп

В [SecurityConfig.java](../src/main/java/org/codingburgas/demetra/config/SecurityConfig.java) вече има:

```java
.requestMatchers(org.springframework.http.HttpMethod.GET, "/events", "/events/*").permitAll()
```

За филтъра не е добавено ново изискване за вход или роля.

## Приложение В. Проверка на документацията

Документът е сверен с кода към 30.09.2026 г. Приложенията съдържат текущия тестов файл, repository, HTML и точните service/controller методи. Проверени са връзките, Markdown блоковете и съответствието на имената `createdAt`, `month`, `week` с реализацията.

В тази задача се добавя само документация. Не са променяни кодът на приложението, данните или ръководството за отчетите. Не е изпълняван нов тестов прогон; раздел 13 описва наличните 16 тестови изпълнения, а не нов резултат от тях. За нова проверка използвай посочената Maven команда и резултатите в `target/surefire-reports`.
