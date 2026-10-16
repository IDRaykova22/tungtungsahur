# Модул „Отчети“ — учебно ръководство за Java и Spring Boot

> **Актуализация.** Филтърът вече е „От дата“ / „До дата“ (`ReportPeriod.between`, до 2 години) с избор „Групирай по“ (`ReportGrouping`: месец, категория, статус, организатор), а `MonthlyReportRow` и `CategoryReportRow` са заменени с общия `ReportGroupRow`. Описанието по-долу за „1, 6 или 12 месеца“ се отнася за първата версия; актуалният код е в `src`.

Това ръководство обяснява как да създадеш модула сама в съществуващия проект Demetra. Първо прочети правилата и архитектурата, след това изпълнявай стъпките в посочения ред. В приложение А е включен пълният код на новите файлове, а в приложение Б — промените в съществуващите файлове. Кодът в `src` е изпълняваната реализация; примерите в документацията обясняват тази реализация.

## Съдържание

1. [Какво прави модулът](#1-какво-прави-модулът)
2. [Подготовка и стартиране](#2-подготовка-и-стартиране)
3. [MVC и отговорности](#3-mvc-и-отговорности)
4. [Данни и периоди](#4-данни-и-периоди)
5. [Създаване на пакетите и файловете](#5-създаване-на-пакетите-и-файловете)
6. [Календарният период](#6-календарният-период)
7. [Моделите на отчета](#7-моделите-на-отчета)
8. [Заявката към базата](#8-заявката-към-базата)
9. [Часовникът и услугата](#9-часовникът-и-услугата)
10. [Формата и контролерът](#10-формата-и-контролерът)
11. [Екранът, календарът и CSS](#11-екранът-календарът-и-css)
12. [Навигацията и достъпът](#12-навигацията-и-достъпът)
13. [Автоматични и ръчни проверки](#13-автоматични-и-ръчни-проверки)
14. [Чести грешки](#14-чести-грешки)
15. [Как да обясниш реализацията и да я надградиш](#15-как-да-обясниш-реализацията-и-да-я-надградиш)
16. [Официални източници](#16-официални-източници)

## 1. Какво прави модулът

Организаторът отваря „Отчети“ от навигацията или „Отчети за събитията“ от страницата със събития. Избира дата в календар и продължителност: **1, 6 или 12 месеца**. Натиска „Покажи отчет“.

Екранът показва:

- началната и крайната дата на включените календарни месеци;
- общ брой събития и потвърдени записвания;
- обща изчислена сума;
- разбивка по месеци, включително месеци с нулеви стойности;
- разбивка по категории;
- списък на събитията с категория, статус, дата на създаване, дата на провеждане, цена, потвърдени записвания и изчислена сума.

При отваряне без параметри се показва текущият месец. Примерен адрес:

```text
/reports?date=2026-09-15&months=6
```

Избраната дата определя **крайния месец**. Денят не ограничава отчета: избор на 01.08 или 20.08 включва целия август, когато датата не е в бъдещето.

### Договорените бизнес правила

| Въпрос | Реализация |
| --- | --- |
| Кои дати определят периода? | `Event.createdAt`: датата на създаване, както при съществуващия филтър. |
| Какво означава 6 месеца? | Избраният краен месец и петте предходни. |
| Какво означава 12 месеца? | Избраният краен месец и единадесетте предходни. |
| Колко назад може да се гледа? | Последните 12 календарни месеца, включително текущия; целият отчет трябва да е вътре в този прозорец. |
| Може ли бъдеща дата? | Не, включително бъдещ ден в текущия месец. |
| Кои събития участват? | `PUBLISHED` и `CLOSED`; `DRAFT` и `CANCELLED` се изключват. |
| Кои записвания участват в сумата? | Само текущите `CONFIRMED`. |
| Чии събития участват? | На всички организатори — общ отчет за приложението. |
| Кой има достъп? | Само потребители с роля `ORGANIZER`. |
| Каква е валутата? | USD, съобразно вече използвания знак `$` в приложението. |

**Изчислената сума не е доказателство за получено плащане.** Няма таблица за плащания. За събитие с цена 12,35 и две потвърдени записвания сумата е `12,35 × 2 = 24,70`. Изчакващите и отказаните записвания добавят нула.

Отчетът е справка за създадени през периода събития и **текущото състояние** на записванията им. Записване, направено след отчетния месец, също участва, ако сега е потвърдено. Ако по-късно бъде отказано или цената бъде променена, повторното отваряне на стар отчет може да покаже друга сума. Това е следствие от наличния модел, който не пази история на плащанията и цената при записването.

Текущият месец е обозначен като незавършен. Старите месеци имат пълни календарни граници, но данните за тях не са заключени исторически снимки.

## 2. Подготовка и стартиране

Проектът вече има необходимите зависимости в `pom.xml`: Spring Boot 4.1.1, Spring MVC, Spring Data JPA, Spring Security, Thymeleaf, Lombok, MySQL и H2 за тестове. Зададената Java версия е 17. Модулът не добавя нови Maven зависимости и не изисква промяна на таблиците.

В IntelliJ IDEA отвори папката на проекта като Maven проект. Избери инсталиран JDK 17 или по-нов съвместим JDK. Ако IntelliJ не разпознава генерираните от Lombok getters, провери поддръжката за Lombok и настройката **Enable annotation processing** в настройките на компилатора. Maven вече има конфигурация за Lombok в `pom.xml`.

Отвори терминал в папката, която съдържа `pom.xml`.

### Стартиране с Docker

```bash
docker compose up -d --build
```

После отвори `http://localhost:8080/events`. Docker Compose създава MySQL и приложението. Базата използва порт 3307 на компютъра, а приложението — 8080. Паролите в Compose са локални учебни настройки.

След промяна на кода контейнерът се обновява с:

```bash
docker compose up -d --build app
```

За логове:

```bash
docker compose logs app --tail 100
```

### Стартиране на Java директно, с MySQL от Docker

Не стартирай две приложения на порт 8080 едновременно. Ако контейнерът `app` работи, можеш да използваш друг порт за Java процеса:

```powershell
docker compose up -d db
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3307/demetra_springboot?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC'
$env:SPRING_DATASOURCE_USERNAME = 'demetra_springboot'
$env:SPRING_DATASOURCE_PASSWORD = 'demetra_springboot_password'
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

Тези настройки са за Windows PowerShell. В IntelliJ можеш да въведеш същите три променливи в **Run Configuration → Environment variables**, а `--server.port=8081` — в **Program arguments**. Директният процес и контейнерът ще четат същата локална база.

За автоматичните тестове **не е нужен Docker**:

```bash
mvn test
```

Тестовият профил използва H2 в паметта. `mvn -DskipTests package` създава изпълним JAR в `target`, но пропуска тестовете. За проверка и пакетиране използвай `mvn verify`.

## 3. MVC и отговорности

MVC разделя Model, View и Controller. В Spring приложението допълнително разделяме бизнес логиката и достъпа до базата в Service и Repository. Те са част от организацията на модела, а не четвърта и пета буква в MVC.

```text
Браузър: GET /reports?date=2026-09-15&months=6
    ↓
Spring Security: проверява ролята
    ↓
ReportController: приема параметрите и подготвя Model
    ↓
ReportService: валидира периода и изчислява отчета
    ↓
ReportRepository: изпълнява заявка към events и registrations
    ↓
EventReportProjection: резултати от базата
    ↓
ReportService: създава EventReport и неговите редове
    ↓
ReportController: model.addAttribute("report", report)
    ↓
Thymeleaf: templates/reports/index.html
    ↓
Браузърът получава готов HTML
```

| Слой | Файлове | Отговорност |
| --- | --- | --- |
| Entity Model | съществуващите `Event`, `Registration`, `User` | Съответстват на записите в базата. |
| Модел за отчета / DTO | `models/reports/*` | Пренася само данните, нужни за справката. |
| Repository | `ReportRepository`, `EventReportProjection` | Чете от базата и връща обобщен ред за всяко събитие. |
| Service | `ReportService`, `ReportPeriod` | Прилага бизнес правилата, периодите, групиранията и сумите. |
| Controller | `ReportController`, `ReportFilterForm` | Обработва HTTP заявката, формата, грешките и избора на View. |
| View | `reports/index.html`, CSS | Показва и форматира готовите данни. |
| Configuration | `TimeConfig`, промяна в `SecurityConfig` | Осигурява часовник и права за достъп. |

Контролерът не извиква repository и не умножава цени. HTML не прави заявки и не брои регистрации. Repository не решава кой потребител може да отвори страницата. Така всяка част има конкретна отговорност.

`org.codingburgas.demetra.DemetraApplication` е в основния пакет. Spring открива класовете с подходящите анотации в подпакетите му. Ако поставиш клас извън тази структура, той може да не бъде открит автоматично.

## 4. Данни и периоди

Съществуващият `Event` има `id`, `title`, `category`, `status`, `createdAt`, `startsAt`, `price` и `organizer`. `Registration` има връзка към събитие и потребител и статус `CONFIRMED`, `WAITLISTED` или `CANCELLED`.

`createdAt` и `startsAt` имат различни значения. Събитие, създадено през септември за провеждане през декември, участва в септемврийския отчет. Непопълнена дата на провеждане не изключва събитието.

Използваме:

- `LocalDate` за ден от календарната форма;
- `YearMonth` за календарен месец без конкретен ден;
- `LocalDateTime` за сравнение с датата и часа на създаване в базата;
- `BigDecimal` за десетичните изчисления на сумите.

При текуща дата 15.09.2026 допустимият общ прозорец е 01.10.2025–30.09.2026:

| Избрана дата | Продължителност | Резултат |
| --- | --- | --- |
| 15.09.2026 | 1 | 01.09.2026–30.09.2026 |
| 15.09.2026 | 6 | 01.04.2026–30.09.2026 |
| 15.09.2026 | 12 | 01.10.2025–30.09.2026 |
| 10.03.2026 | 6 | 01.10.2025–31.03.2026 |
| 10.02.2026 | 6 | Грешка: началото би било през септември 2025. |
| 01.08.2026 | 12 | Грешка: началото би било през септември 2025. |
| 16.09.2026 | 1 | Грешка: избраният ден е в бъдещето. |

Следователно 12-месечният отчет може да завършва само в текущия месец. Това предотвратява скрито излизане извън последните 12 календарни месеца.

## 5. Създаване на пакетите и файловете

В IntelliJ натисни с десен бутон върху основния Java пакет → **New → Package**. Създай `models.reports` и `repositories.projections`. В съществуващите `config`, `services`, `controllers` и `controllers.forms` добави посочените класове. За интерфейсите избери **New → Java Class → Interface**.

```text
src/main/java/org/codingburgas/demetra/
├── config/TimeConfig.java
├── models/reports/
│   ├── ReportPeriod.java
│   ├── EventReportRow.java
│   ├── MonthlyReportRow.java
│   ├── CategoryReportRow.java
│   └── EventReport.java
├── repositories/
│   ├── ReportRepository.java
│   └── projections/EventReportProjection.java
├── services/ReportService.java
├── controllers/forms/ReportFilterForm.java
└── controllers/ReportController.java

src/main/resources/templates/reports/index.html
src/test/java/org/codingburgas/demetra/ReportPeriodTests.java
src/test/java/org/codingburgas/demetra/ReportFlowTests.java
```

Не създавай тези файлове в `target`: това е генерирана папка, която Maven може да изтрие при `clean`. Java името на публичния клас трябва да съвпада с името на файла. Първият ред `package` трябва да отговаря на директорията.

Редът за работа е: период → DTO модели → projection → repository → Clock → service → form → controller → HTML → CSS → security/navigation → tests. За всеки файл използвай пълния код от приложение А. След като всички Java класове са създадени, изпълни `mvn -DskipTests compile`, за да провериш синтаксиса и imports.

## 6. Календарният период

`ReportPeriod` е обикновен Java клас с бизнес правила. Няма `@Entity`, защото не записваме периодите в отделна таблица. Класът е `final`, полетата са `final`, а конструкторът е `private`: създаването минава през `from(...)`, за да не може да се пропусне проверката.

```java
YearMonth last = YearMonth.from(date);
YearMonth first = last.minusMonths(months - 1L);
YearMonth earliest = YearMonth.from(today).minusMonths(11);
```

За шест месеца изваждаме **пет**, защото последният месец също се брои. `1L` означава `long`; `minusMonths` приема такъв тип. `Set.of(1, 6, 12)` е множеството допустими продължителности.

Проверките се извършват преди заявка към базата: позволена продължителност, налична дата, дата до днес и начална граница в прозореца.

### Защо краят е изключващ

В базата търсим:

```text
createdAt >= 2026-09-01 00:00:00
createdAt <  2026-10-01 00:00:00
```

Това е интервал `[начало, край)`: включено начало и изключен край. Ако използваме `<= 30.09 23:59:59`, бихме изпуснали записи с части от секундата след `23:59:59`. Ако включим `01.10 00:00`, ще преброим събитие от следващия месец.

`YearMonth.atEndOfMonth()` намира правилния последен ден: 28, 29, 30 или 31. Не пишем ръчно правила за февруари. За показване на екрана използваме `getEndDate()`, а за заявката — `getEndExclusive()`.

## 7. Моделите на отчета

DTO означава **Data Transfer Object**: обект, който пренася данни между частите на приложението. В модула DTO класовете нямат setters и не променят базата.

| Клас | Съдържание |
| --- | --- |
| `EventReportRow` | Един ред за събитие, неговата цена, брой потвърдени записвания и изчислена сума. |
| `MonthlyReportRow` | Месец, български етикет, брой събития, записвания и сума. |
| `CategoryReportRow` | Категория, брой събития, записвания и сума. |
| `EventReport` | Период, общи показатели и трите списъка. |

Lombok `@Getter` генерира методи като `getTitle()` и `getEstimatedAmount()`. `@AllArgsConstructor` генерира конструктор с параметър за всяко поле. За `boolean currentMonthIncluded` getter е `isCurrentMonthIncluded()`. Ако не използваш Lombok в бъдещ проект, можеш да напишеш тези методи и конструктори ръчно.

`EventReport` има собствен конструктор: извежда броя събития от `events.size()` и използва `List.copyOf(...)`, за да не се изменят списъците след създаване на отчета.

Нито един DTO не връща `User`, парола, email на участник или мързеливо зареждана JPA връзка. Това е особено полезно, защото проектът има `spring.jpa.open-in-view=false`: HTML трябва да може да се генерира след приключване на транзакцията.

## 8. Заявката към базата

Създай първо `EventReportProjection`, после `ReportRepository`. Projection е интерфейс с getters за избраните стойности. Alias-ите в заявката трябва да съответстват на имената им: `confirmedCount` → `getConfirmedCount()`.

`ReportRepository extends Repository<Event, Long>` означава repository върху `Event` с идентификатор `Long`. Разширяваме минималния интерфейс `Repository`, защото този модул има само операция за четене. Spring Data създава реализацията автоматично. Не пишем `new ReportRepository(...)` и не пишем клас `ReportRepositoryImpl`.

`@Query` задава JPQL/HQL заявка. Имената са Java класове и полета — `Event`, `Registration`, `createdAt` — а не имената на SQL таблиците и колоните. Hibernate превежда заявката към SQL за избраната база.

```java
left join Registration r on r.event = e and r.status = :registrationStatus
```

Тази връзка събира само потвърдените записвания към събитието. `LEFT JOIN` запазва и събитие без записвания. При `INNER JOIN` то би изчезнало. Условието за статуса е в `ON`, за да останат редовете без потвърдени регистрации.

```java
count(r.id) as confirmedCount
```

Броим `r.id`, а не `count(*)`: за събитие без регистрации `r.id` е `null`, затова резултатът е нула. `GROUP BY` обединява редовете на едно събитие. Всички избрани неагрегирани полета са изброени в `GROUP BY`, за да работи заявката и с MySQL при строг режим на групиране.

```java
where e.createdAt >= :start and e.createdAt < :end and e.status in :statuses
```

`@Param("start")` свързва Java параметъра с `:start` в заявката. Параметрите не се залепват като текст в SQL. Сортирането е по `createdAt`, след това по `id`, така че резултатът е предвидим и при еднакви дати.

Заявката връща всички нужни данни с едно изпълнение. Не правим отделна заявка за потвърдените записвания на всяко събитие. Това избягва проблем от вида „една заявка за списъка + N заявки за N събития“.

Не е нужна нова таблица `reports`: отчетът се изчислява от съществуващите данни. Когато имаш изискване за съхранен исторически отчет, това би било друга функционалност с отделен модел.

## 9. Часовникът и услугата

### TimeConfig

`@Configuration` означава конфигурационен клас. `@Bean` върху `clock()` казва на Spring да създаде и управлява обект `Clock`, който може да се подава в конструктори.

`Clock.systemDefaultZone()` използва часовата зона на Java процеса, също както съществуващото `LocalDateTime.now()` в entities. Тестовете заменят часовника с фиксиран. Така не започват да се провалят, когато реалният календар премине в друг месец.

Часовата зона на контейнера може да се различава от тази на компютъра. `serverTimezone=UTC` в JDBC URL е настройка на връзката към MySQL; не задава часовата зона на `Clock`. За последователни календарни отчети използвай една и съща зона за процесите, например чрез `TZ=Europe/Sofia` в средата при бъдеща конфигурация. Този модул не преобразува вече записаните `LocalDateTime` стойности в друга зона.

### ReportService

`@Service` маркира класа като услуга, управлявана от Spring. Конструкторът получава repository и Clock. Това е **constructor injection**: Spring подава зависимостите. Един конструктор не изисква `@Autowired`.

`@Transactional(readOnly = true)` върху `generate` създава транзакционна граница за четене. Това описва намерението и позволява оптимизации; не трябва да го приемаш като самостоятелна гаранция, че никога не може да бъде извършен запис.

Алгоритъмът на `generate(date, months)` е:

1. Прочети текущата дата от Clock.
2. Валидирай и създай `ReportPeriod`.
3. Прочети редовете от repository за целия период, с позволените статуси.
4. Превърни всеки projection в `EventReportRow`.
5. Групирай редовете по месец на създаване.
6. Обходи всички календарни месеци, дори когато няма редове.
7. Групирай редовете по категория.
8. Сумирай потвърдените записвания и изчислените суми.
9. Върни готов `EventReport`.

### BigDecimal и закръгляване

Съществуващият `Event.price` е `double`. За модула го превръщаме веднъж в `BigDecimal`:

```java
BigDecimal price = BigDecimal.valueOf(source.getPrice())
        .setScale(2, RoundingMode.HALF_UP);
BigDecimal amount = price.multiply(BigDecimal.valueOf(source.getConfirmedCount()));
```

`valueOf` използва десетичното представяне на стойността. `new BigDecimal(double)` може да внесе дълга двоична апроксимация. `setScale(2, HALF_UP)` задава два знака след десетичната запетая и закръглява 2,345 до 2,35. Първо закръгляме единичната цена, после умножаваме, така че показаната цена и сумата да са съгласувани.

`BigDecimal` е неизменяем: `add` и `multiply` връщат нов обект. Затова не можеш да извикаш `total.add(value)` и да очакваш `total` да се промени без присвояване.

### Групиране и нулеви месеци

`Collectors.groupingBy(...)` прави карта `ключ → списък от редове`. Например месец април → всички априлски събития. `getOrDefault(month, List.of())` връща празен списък, ако за месеца няма събития. Цикълът върху всички месеци добавя нулев ред и в този случай.

За общата сума `reduce(ZERO_AMOUNT, BigDecimal::add)` започва от `0.00` и добавя всяка стойност. За брой записвания използваме `mapToLong(...).sum()`.

Категории с `null` или само интервали стават „Без категория“. При останалите премахваме външните интервали. Различен регистър, например `Concert` и `concert`, остава различна категория, защото проектът няма отделна таблица или enum за категориите. `TreeMap` дава предвидима подредба по текстовия ключ.

## 10. Формата и контролерът

### ReportFilterForm

Това е Java обект за входните данни. `date` е `LocalDate`, а `months` е `int` със стойност по подразбиране 1. Тук setters са необходими, за да може Spring да попълни параметрите.

```java
@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
private LocalDate date;
```

Тази анотация указва входен формат `yyyy-MM-dd`, например `2026-09-15`. Браузърът може да показва датата на местния език, но изпраща ISO стойност.

### ReportController

`@Controller` означава MVC контролер. `@GetMapping("/reports")` обработва GET заявка. `@ModelAttribute("filter")` попълва формата от параметрите и я поставя в модела под името `filter`.

`BindingResult` трябва да е **веднага след формата** в параметрите на метода. Той пази грешки при преобразуване, например `date=hello` или `months=abc`.

Контролерът първо добавя границите за календарното поле. При грешка в типовете връща същия екран, разбираемо съобщение и HTTP 400. Ако датата липсва или е празна, използва днешната дата. След това извиква услугата. При бизнес грешка като период извън прозореца също показва съобщение с HTTP 400, без резултатен отчет.

```java
model.addAttribute("report", reportService.generate(filter.getDate(), filter.getMonths()));
return "reports/index";
```

`Model` е карта с данни за View. Низът `reports/index` е име на шаблон: Spring намира `src/main/resources/templates/reports/index.html`. Не връщаме файлов абсолютен път и не пишем `.html` в името.

Използваме `@Controller`, защото искаме HTML страница. `@RestController` би изпратил резултата като HTTP съдържание вместо да избере Thymeleaf View.

Филтрирането е GET, защото чете данни и не създава запис. URL може да се запази като bookmark. Не добавяме POST endpoint за тази справка.

## 11. Екранът, календарът и CSS

Създай директория `reports` в `src/main/resources/templates`, а в нея `index.html`. В `<head>` добави UTF-8, viewport, заглавие, общия CSS и favicon. `lang="bg"` определя езика на страницата. Навигацията се включва чрез съществуващия Thymeleaf fragment.

```html
<form method="get" th:action="@{/reports}" th:object="${filter}">
    <input type="date" id="date" name="date" th:value="${filter.date}"
           th:min="${minDate}" th:max="${maxDate}" required>
    <select id="months" name="months" th:field="*{months}">
        <option value="1">1 месец</option>
        <option value="6">6 месеца</option>
        <option value="12">12 месеца</option>
    </select>
    <button type="submit">Покажи отчет</button>
</form>
```

Това е съкратен пример; пълният достъпен шаблон е в приложение А. `input type="date"` интегрира календарния избор на браузъра. Няма външна JavaScript библиотека или CDN. Външният вид на календара зависи от браузъра.

`name` трябва да съответства на Java полето. Без `name="date"` стойността няма да стигне до `ReportFilterForm.date`. `label for="date"` сочи `id="date"` и прави формата удобна за клавиатура и екранни четци.

`min` и `max` ограничават датите в браузъра. Началната календарна граница е общата за последните 12 месеца. При 6 или 12 месеца допълнителната проверка за целия период се прави от услугата. Ограниченията на HTML не заместват проверките в Java, защото URL може да се напише ръчно.

### Основни Thymeleaf атрибути

| Атрибут | Значение в модула |
| --- | --- |
| `th:action="@{/reports}"` | Генерира адреса за изпращане на формата. |
| `th:object="${filter}"` | Задава обекта, към който се свързват полетата. |
| `th:field="*{months}"` | Свързва избора с полето `months` и запазва избраната стойност. |
| `th:text="${report.eventCount}"` | Показва готова стойност от модела. |
| `th:each="row : ${report.monthlyRows}"` | Повтаря HTML ред за всеки месец. |
| `th:if="${report != null}"` | Показва резултатите само при валиден отчет. |
| `th:unless` | Показва елемент, когато условието е невярно. |
| `th:replace="~{fragments/nav :: nav}"` | Включва общата навигация. |
| `th:href="@{/events/{id}(id=${row.id})}"` | Генерира връзка към конкретното събитие. |

`${...}` чете от модела; `*{...}` чете от обекта, зададен с `th:object`; `@{...}` е израз за URL. `report.eventCount` използва Java метода `getEventCount()`.

`#temporals.format(...)` форматира дати, а `#numbers.formatDecimal(...)` — числа. Шаблонът само форматира: всички сборове вече са пресметнати от услугата.

За заглавията и категориите използваме `th:text`, който екранира HTML. Не го заменяй с `th:utext` за потребителски текст — заглавие като `<script>...` трябва да се покаже като текст.

### CSS

Добавените класове в общия `style.css` имат следните роли:

- `.report-summary`: три карти в CSS Grid;
- `.report-section`: разстояние между секциите;
- `.report-table`: общ външен вид на таблиците;
- `.report-table-container`: хоризонтално превъртане за широки таблици;
- `.report-events-table`: минимална ширина за подробния списък;
- `.report-value`: големи числа в обобщението;
- `.report-entry`: отстояние на бутона от заглавието.

При ширина до 720px картите стават една под друга. Навигацията може да преминава на нов ред. Таблицата със събития се превърта хоризонтално, за да не отрязва колоните. Използваме съществуващите цветове и класове `card`, `btn`, `alert` и `event-meta`.

## 12. Навигацията и достъпът

В `SecurityConfig`, преди `.anyRequest().authenticated()`, добави:

```java
.requestMatchers("/reports", "/reports/**").hasRole("ORGANIZER")
```

`hasRole("ORGANIZER")` проверява authority `ROLE_ORGANIZER`. В `hasRole` не добавяй ръчно префикса `ROLE_`. Редът на правилата има значение: по-специфичните се поставят преди общото правило.

В `templates/fragments/nav.html` добави:

```html
<a sec:authorize="hasRole('ORGANIZER')" th:href="@{/reports}" class="nav-cta">Отчети</a>
```

В заглавната част на `templates/events/list.html` добави:

```html
<a sec:authorize="hasRole('ORGANIZER')" th:href="@{/reports}"
   class="btn report-entry">Отчети за събитията</a>
```

Съществуващите шаблони вече декларират пространството `xmlns:sec` и проектът има Thymeleaf Security extras. Скриването на бутона е интерфейсно правило. **Истинската защита е в Spring Security**: студент, който напише `/reports` ръчно, получава HTTP 403; анонимен посетител се препраща към вход.

Публичните страници със събития и потребители запазват съществуващия си достъп. Не изключваме CSRF защитата. GET справката не променя данни.

## 13. Автоматични и ръчни проверки

### Автоматични тестове

Създай двата тестови файла от приложение А в `src/test/java/org/codingburgas/demetra`.

```bash
mvn -Dtest=ReportPeriodTests,ReportFlowTests test
```

За всички тестове, включително старите функционалности:

```bash
mvn test
```

`ReportPeriodTests` проверява календарните правила без стартиране на Spring: 1/6/12 месеца, смяна на година, високосен февруари, 31-дневен месец, невалидни продължителности, бъдещи и прекалено стари дати.

`ReportFlowTests` стартира Spring, реалните repositories, H2, Security и Thymeleaf. `MockMvc` изпълнява HTTP заявки в тестова среда. `@WithMockUser(roles = "ORGANIZER")` симулира организатор за проверка на достъпа.

`@TestConfiguration` създава фиксиран Clock. `@Primary` кара Spring да го предпочете пред нормалния Clock само в този тестов контекст. Датата е 15.09.2026 независимо от реалния ден на изпълнение.

Интеграционните тестове използват собствена H2 база `reports`. Почистването в `@BeforeEach` е в ред регистрации → събития → потребители заради външните ключове. **Не изпълнявай това почистване върху реалната база.** Тестовете нямат обща `@Transactional` върху класа, така че HTML се рендерира и без отворена тестова транзакция. Това проверява съвместимостта с `open-in-view=false`.

Проверяват се:

- точно включено начало и изключен край, включително последната микросекунда;
- филтриране по `createdAt`, независимо от `startsAt`;
- включване на приключили и изключване на отменени/чернови събития;
- събития без участници и без дата на провеждане;
- потвърдени срещу изчакващи и отказани записвания;
- безплатни събития, десетични суми и закръгляване;
- съгласуваност на общата сума с разбивките;
- текущо състояние при последващ отказ;
- събития от различни организатори;
- празен отчет и всички нулеви месеци;
- валиден HTML, съобщения за грешки, бутони според ролята и екраниране на текст.

Добавени са **34 тестови изпълнения**: 12 за периода и 22 за целия поток. При изпълнението всички минават без грешки. Резултатите на Maven се записват в `target/surefire-reports`. H2 проверката не замества напълно проверката върху MySQL; затова модулът се проверява и в локалния Docker стек.

### Ръчна проверка

1. Стартирай приложението и влез като организатор. Ако нямаш такъв акаунт, създай го от съществуващата регистрационна форма с роля Organizer.
2. Отвори `/events` и натисни „Отчети за събитията“.
3. Провери, че е показан текущият месец.
4. Отвори календара, избери дата в текущия месец, избери 6 месеца и натисни „Покажи отчет“.
5. Провери, че началото е първият ден на месеца пет месеца по-рано и таблицата има шест реда.
6. Избери 12 месеца и провери, че таблицата има дванадесет реда.
7. Избери стар краен месец с 12-месечна продължителност: трябва да видиш грешка, защото началото излиза извън прозореца.
8. Избери минал месец с 1-месечна продължителност и сравни списъка със съществуващия филтър по създаване в `/events`. Имай предвид, че списъкът със събития включва само `PUBLISHED`, а отчетът включва и `CLOSED`.
9. За платено събитие сравни сумата с цената по броя потвърдени записвания. Провери също безплатно събитие и събитие без записвания.
10. Излез от профила и отвори `/reports`: трябва да стигнеш до вход. Влез като студент: директният адрес трябва да връща 403 и бутоните да липсват.
11. Стесни прозореца или използвай мобилен изглед: картите трябва да са една под друга, а широката таблица да се превърта.

Не променяй датите на реални събития само за демонстрация. Автоматичните тестове вече създават изолирани записи за историческите граници.

## 14. Чести грешки

| Симптом | Причина и решение |
| --- | --- |
| `cannot find symbol get...` | Провери Lombok/annotation processing и imports; изпълни Maven compile. |
| `No qualifying bean of type Clock` | Липсва `TimeConfig` или е извън основния пакет. |
| `TemplateInputException` | Провери пътя `templates/reports/index.html` и връщаното име `reports/index`. |
| Виждаш само текста `reports/index` | Използван е `@RestController` вместо `@Controller`. |
| Платено събитие показва нула | Няма `CONFIRMED` регистрации или събитието е безплатно. Наличието само на `WAITLISTED` не увеличава сумата. |
| Стар месец с 12 месеца дава грешка | Правилно: целият период трябва да е в последните 12 календарни месеца. |
| Датата на провеждане не съвпада с периода | Отчетът използва датата на създаване, съгласно избраните правила. |
| Събитие без участници изчезва | Провери дали заявката остава `LEFT JOIN` и условието за регистрацията е в `ON`. |
| Първо събитие на следващия месец се включва | Краят на заявката трябва да е `< :end`, а не `<=`. |
| `LazyInitializationException` | Не предавай JPA връзки към View; използвай готовите projection/DTO стойности. |
| Студент вижда отчет с ръчно въведен URL | Липсва правилото в `SecurityConfig`; скриването на бутона не е достатъчно. |
| Кодът е променен, но екранът е стар | Обнови Docker образа с `docker compose up -d --build app` и презареди браузъра. |
| `Communications link failure` | MySQL не е стартиран или URL/портът са грешни. Docker хост портът е 3307. |
| `Port 8080 already in use` | Контейнерът и директният Java процес са на един порт; използвай 8081 за втория. |
| HTTP 400 след ръчно променен URL | Провери ISO датата и дали `months` е точно 1, 6 или 12. |

## 15. Как да обясниш реализацията и да я надградиш

За представяне можеш да обясниш: „Модулът изчислява справка за събития, създадени в избран календарен период. Контролерът приема филтъра, услугата валидира периода и агрегира данните, repository извлича редовете с една заявка, а Thymeleaf показва готовите резултати. Достъпът се контролира от Spring Security. Данните за суми са изчислени от потвърдените записвания, понеже проектът няма плащания.“

За самостоятелна работа опитай първо малки промени:

1. Добави колоната „Безплатно/платено“ в таблицата, като използваш вече готовата цена.
2. Добави нов обобщен показател в DTO и изчисли стойността в Service.
3. Напиши тест с конкретни данни и очакван резултат за новия показател.
4. Добави филтър по категория: ново поле във формата, бизнес проверка в Service, параметър в Repository и поле в HTML.

За бъдещ дипломен проект с реални плащания ще са нужни нови бизнес правила и модел: `Payment` с точна `BigDecimal` сума, валута, статус, дата на плащане, връзка към регистрация и данни за възстановявания. Тогава трябва да решиш дали финансовият отчет се групира по дата на плащане или дата на събитие. Историческата цена и платената сума трябва да се пазят при операцията, а не да се вземат от текущата цена на събитието. Тези промени не са част от настоящия модул.

При много данни следва да се разгледат индекси върху датата/статуса на събитията и връзката/статуса на регистрациите, pagination на подробния списък и агрегиране на обобщенията в базата. Текущият модул ограничава времето до 12 месеца, но зарежда всички подходящи събития в този период в паметта. Не добавя нови индекси или миграции.

Не въвеждай излишна service interface с една-единствена реализация само за да изглежда архитектурата по-сложна. В този проект конкретната услуга с constructor injection е достатъчна. Interface е полезен, когато действително имаш различни реализации или нужда от отделен договор.

## 16. Официални източници

Обясненията на общите механизми могат да се сверят с официалната документация:

- [Spring MVC и достъп до модела в Thymeleaf](https://www.thymeleaf.org/doc/articles/springmvcaccessdata.html) — роли на контролера, Model и View.
- [Spring Data JPA: Projections](https://docs.spring.io/spring-data/jpa/reference/repositories/projections.html) — интерфейси за връщане на избрани стойности.
- [Spring Data JPA: Transactionality](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html) — транзакции и `readOnly`.
- [Thymeleaf 3.1: Using Thymeleaf](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html) — `th:text`, `th:each`, условни елементи и форматиране.
- [Thymeleaf + Spring](https://www.thymeleaf.org/doc/tutorials/3.1/thymeleafspring.html) — свързване на форми с Java обекти.

Специфичните правила за периодите, статуса и изчислените суми са решения за този проект и са описани в раздел 1.

## Приложение А. Пълен код на новите файлове

Следва кодът на всеки нов файл. Създай файла на посочения път и постави съдържанието му. Това е снимка на реализацията към създаване на документацията; при бъдещи промени обновявай и тези примери.

### А.1. Bean за часовника

Файл: [src/main/java/org/codingburgas/demetra/config/TimeConfig.java](../src/main/java/org/codingburgas/demetra/config/TimeConfig.java)

```java
package org.codingburgas.demetra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
```

### А.2. Валидиран календарен период

Файл: [src/main/java/org/codingburgas/demetra/models/reports/ReportPeriod.java](../src/main/java/org/codingburgas/demetra/models/reports/ReportPeriod.java)

```java
package org.codingburgas.demetra.models.reports;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Set;

/** A calendar interval with an inclusive start and an exclusive end. */
@Getter
public final class ReportPeriod {
    private final YearMonth firstMonth;
    private final YearMonth lastMonth;
    private final int months;

    private ReportPeriod(YearMonth firstMonth, YearMonth lastMonth, int months) {
        this.firstMonth = firstMonth;
        this.lastMonth = lastMonth;
        this.months = months;
    }

    public static ReportPeriod from(LocalDate date, int months, LocalDate today) {
        if (!Set.of(1, 6, 12).contains(months)) {
            throw new IllegalArgumentException("Изберете период от 1, 6 или 12 месеца.");
        }
        if (date == null || date.isAfter(today)) {
            throw new IllegalArgumentException("Изберете дата, която не е в бъдещето.");
        }
        YearMonth last = YearMonth.from(date);
        YearMonth first = last.minusMonths(months - 1L);
        YearMonth earliest = YearMonth.from(today).minusMonths(11);
        if (first.isBefore(earliest)) {
            throw new IllegalArgumentException("Целият период трябва да е в последните 12 календарни месеца. "
                    + "За избраната продължителност крайният месец трябва да е между "
                    + earliest.plusMonths(months - 1L) + " и " + YearMonth.from(today) + ".");
        }
        return new ReportPeriod(first, last, months);
    }

    public LocalDate getStartDate() {
        return firstMonth.atDay(1);
    }

    public LocalDate getEndDate() {
        return lastMonth.atEndOfMonth();
    }

    public LocalDateTime getStartInclusive() {
        return getStartDate().atStartOfDay();
    }

    public LocalDateTime getEndExclusive() {
        return lastMonth.plusMonths(1).atDay(1).atStartOfDay();
    }
}
```

### А.3. Ред за събитие

Файл: [src/main/java/org/codingburgas/demetra/models/reports/EventReportRow.java](../src/main/java/org/codingburgas/demetra/models/reports/EventReportRow.java)

```java
package org.codingburgas.demetra.models.reports;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.codingburgas.demetra.models.EventStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public final class EventReportRow {
    private final Long id;
    private final String title;
    private final String category;
    private final EventStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime startsAt;
    private final BigDecimal price;
    private final long confirmedCount;
    private final BigDecimal estimatedAmount;

    public String getStatusLabel() {
        return status == EventStatus.CLOSED ? "Приключило" : "Публикувано";
    }
}
```

### А.4. Ред за месец

Файл: [src/main/java/org/codingburgas/demetra/models/reports/MonthlyReportRow.java](../src/main/java/org/codingburgas/demetra/models/reports/MonthlyReportRow.java)

```java
package org.codingburgas.demetra.models.reports;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.YearMonth;

@Getter
@AllArgsConstructor
public final class MonthlyReportRow {
    private final YearMonth month;
    private final String label;
    private final long eventCount;
    private final long confirmedCount;
    private final BigDecimal estimatedAmount;
}
```

### А.5. Ред за категория

Файл: [src/main/java/org/codingburgas/demetra/models/reports/CategoryReportRow.java](../src/main/java/org/codingburgas/demetra/models/reports/CategoryReportRow.java)

```java
package org.codingburgas.demetra.models.reports;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public final class CategoryReportRow {
    private final String category;
    private final long eventCount;
    private final long confirmedCount;
    private final BigDecimal estimatedAmount;
}
```

### А.6. Целият отчет

Файл: [src/main/java/org/codingburgas/demetra/models/reports/EventReport.java](../src/main/java/org/codingburgas/demetra/models/reports/EventReport.java)

```java
package org.codingburgas.demetra.models.reports;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
public final class EventReport {
    private final ReportPeriod period;
    private final long eventCount;
    private final long confirmedCount;
    private final BigDecimal estimatedAmount;
    private final boolean currentMonthIncluded;
    private final List<EventReportRow> events;
    private final List<MonthlyReportRow> monthlyRows;
    private final List<CategoryReportRow> categoryRows;

    public EventReport(ReportPeriod period, long confirmedCount, BigDecimal estimatedAmount,
                       boolean currentMonthIncluded, List<EventReportRow> events,
                       List<MonthlyReportRow> monthlyRows, List<CategoryReportRow> categoryRows) {
        this.period = period;
        this.eventCount = events.size();
        this.confirmedCount = confirmedCount;
        this.estimatedAmount = estimatedAmount;
        this.currentMonthIncluded = currentMonthIncluded;
        this.events = List.copyOf(events);
        this.monthlyRows = List.copyOf(monthlyRows);
        this.categoryRows = List.copyOf(categoryRows);
    }
}
```

### А.7. Projection на резултата от базата

Файл: [src/main/java/org/codingburgas/demetra/repositories/projections/EventReportProjection.java](../src/main/java/org/codingburgas/demetra/repositories/projections/EventReportProjection.java)

```java
package org.codingburgas.demetra.repositories.projections;

import org.codingburgas.demetra.models.EventStatus;

import java.time.LocalDateTime;

/** Only the scalar values needed by the report; no lazy entity relationships. */
public interface EventReportProjection {
    Long getId();
    String getTitle();
    String getCategory();
    EventStatus getStatus();
    LocalDateTime getCreatedAt();
    LocalDateTime getStartsAt();
    double getPrice();
    long getConfirmedCount();
}
```

### А.8. Repository и заявка

Файл: [src/main/java/org/codingburgas/demetra/repositories/ReportRepository.java](../src/main/java/org/codingburgas/demetra/repositories/ReportRepository.java)

```java
package org.codingburgas.demetra.repositories;

import org.codingburgas.demetra.models.Event;
import org.codingburgas.demetra.models.EventStatus;
import org.codingburgas.demetra.models.RegistrationStatus;
import org.codingburgas.demetra.repositories.projections.EventReportProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** Read-only repository for the reporting module. */
public interface ReportRepository extends Repository<Event, Long> {
    @Query("""
            select e.id as id, e.title as title, e.category as category, e.status as status,
                   e.createdAt as createdAt, e.startsAt as startsAt, e.price as price,
                   count(r.id) as confirmedCount
            from Event e
            left join Registration r on r.event = e and r.status = :registrationStatus
            where e.createdAt >= :start and e.createdAt < :end and e.status in :statuses
            group by e.id, e.title, e.category, e.status, e.createdAt, e.startsAt, e.price
            order by e.createdAt asc, e.id asc
            """)
    List<EventReportProjection> findEventReportRows(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("statuses") Collection<EventStatus> statuses,
            @Param("registrationStatus") RegistrationStatus registrationStatus);
}
```

### А.9. Бизнес логика

Файл: [src/main/java/org/codingburgas/demetra/services/ReportService.java](../src/main/java/org/codingburgas/demetra/services/ReportService.java)

```java
package org.codingburgas.demetra.services;

import org.codingburgas.demetra.models.EventStatus;
import org.codingburgas.demetra.models.RegistrationStatus;
import org.codingburgas.demetra.models.reports.CategoryReportRow;
import org.codingburgas.demetra.models.reports.EventReport;
import org.codingburgas.demetra.models.reports.EventReportRow;
import org.codingburgas.demetra.models.reports.MonthlyReportRow;
import org.codingburgas.demetra.models.reports.ReportPeriod;
import org.codingburgas.demetra.repositories.ReportRepository;
import org.codingburgas.demetra.repositories.projections.EventReportProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class ReportService {
    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.00");
    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("bg"));

    private final ReportRepository reportRepository;
    private final Clock clock;

    public ReportService(ReportRepository reportRepository, Clock clock) {
        this.reportRepository = reportRepository;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDate earliestDate() {
        return YearMonth.from(today()).minusMonths(11).atDay(1);
    }

    @Transactional(readOnly = true)
    public EventReport generate(LocalDate date, int months) {
        LocalDate today = today();
        ReportPeriod period = ReportPeriod.from(date, months, today);
        List<EventReportRow> events = reportRepository.findEventReportRows(
                        period.getStartInclusive(), period.getEndExclusive(),
                        List.of(EventStatus.PUBLISHED, EventStatus.CLOSED), RegistrationStatus.CONFIRMED)
                .stream().map(this::toReportRow).toList();

        Map<YearMonth, List<EventReportRow>> byMonth = events.stream().collect(
                Collectors.groupingBy(row -> YearMonth.from(row.getCreatedAt())));
        List<MonthlyReportRow> monthlyRows = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            YearMonth month = period.getFirstMonth().plusMonths(i);
            List<EventReportRow> rows = byMonth.getOrDefault(month, List.of());
            monthlyRows.add(new MonthlyReportRow(month, month.format(MONTH_LABEL), rows.size(),
                    confirmedCount(rows), estimatedAmount(rows)));
        }

        Map<String, List<EventReportRow>> byCategory = events.stream().collect(
                Collectors.groupingBy(EventReportRow::getCategory, TreeMap::new, Collectors.toList()));
        List<CategoryReportRow> categoryRows = byCategory.entrySet().stream()
                .map(entry -> new CategoryReportRow(entry.getKey(), entry.getValue().size(),
                        confirmedCount(entry.getValue()), estimatedAmount(entry.getValue())))
                .toList();

        return new EventReport(period, confirmedCount(events), estimatedAmount(events),
                period.getLastMonth().equals(YearMonth.from(today)), events, monthlyRows, categoryRows);
    }

    private EventReportRow toReportRow(EventReportProjection source) {
        // The existing entity stores double. Convert once, then calculate using decimal arithmetic.
        BigDecimal price = BigDecimal.valueOf(source.getPrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = price.multiply(BigDecimal.valueOf(source.getConfirmedCount()));
        String category = source.getCategory() == null || source.getCategory().isBlank()
                ? "Без категория" : source.getCategory().trim();
        return new EventReportRow(source.getId(), source.getTitle(), category, source.getStatus(),
                source.getCreatedAt(), source.getStartsAt(), price, source.getConfirmedCount(), amount);
    }

    private long confirmedCount(List<EventReportRow> rows) {
        return rows.stream().mapToLong(EventReportRow::getConfirmedCount).sum();
    }

    private BigDecimal estimatedAmount(List<EventReportRow> rows) {
        return rows.stream().map(EventReportRow::getEstimatedAmount).reduce(ZERO_AMOUNT, BigDecimal::add);
    }
}
```

### А.10. Входни параметри

Файл: [src/main/java/org/codingburgas/demetra/controllers/forms/ReportFilterForm.java](../src/main/java/org/codingburgas/demetra/controllers/forms/ReportFilterForm.java)

```java
package org.codingburgas.demetra.controllers.forms;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class ReportFilterForm {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;
    private int months = 1;
}
```

### А.11. MVC контролер

Файл: [src/main/java/org/codingburgas/demetra/controllers/ReportController.java](../src/main/java/org/codingburgas/demetra/controllers/ReportController.java)

```java
package org.codingburgas.demetra.controllers;

import jakarta.servlet.http.HttpServletResponse;
import org.codingburgas.demetra.controllers.forms.ReportFilterForm;
import org.codingburgas.demetra.services.ReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/reports")
    public String report(@ModelAttribute("filter") ReportFilterForm filter, BindingResult bindingResult,
                         Model model, HttpServletResponse response) {
        model.addAttribute("minDate", reportService.earliestDate());
        model.addAttribute("maxDate", reportService.today());
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("error", "Въведете валидна дата и период от 1, 6 или 12 месеца.");
            return "reports/index";
        }
        if (filter.getDate() == null) {
            filter.setDate(reportService.today());
        }
        try {
            model.addAttribute("report", reportService.generate(filter.getDate(), filter.getMonths()));
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("error", ex.getMessage());
        }
        return "reports/index";
    }
}
```

### А.12. Пълен Thymeleaf екран

Файл: [src/main/resources/templates/reports/index.html](../src/main/resources/templates/reports/index.html)

```html
<!DOCTYPE html>
<html lang="bg" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Отчети — Demetra</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
    <link rel="icon" th:href="@{/images/favicon.svg}">
</head>
<body>
<div th:replace="~{fragments/nav :: nav}"></div>
<main class="page-container">
    <header class="page-header">
        <div class="breadcrumbs"><a th:href="@{/events}">Demetra / Събития</a> / Отчети</div>
        <h1>Отчети за събитията</h1>
        <p class="event-meta">Общ отчет за всички организатори, по дата на създаване на събитието.</p>
    </header>

    <section class="card report-section" aria-labelledby="filter-heading">
        <h2 id="filter-heading">Избор на период</h2>
        <form method="get" th:action="@{/reports}" th:object="${filter}" class="event-filter-form">
            <div class="form-group">
                <label for="date">Дата в крайния месец</label>
                <input type="date" id="date" name="date" th:value="${filter.date}"
                       th:min="${minDate}" th:max="${maxDate}" required aria-describedby="period-help">
            </div>
            <div class="form-group">
                <label for="months">Продължителност</label>
                <select id="months" name="months" th:field="*{months}">
                    <option value="1">1 месец</option>
                    <option value="6">6 месеца</option>
                    <option value="12">12 месеца</option>
                </select>
            </div>
            <button type="submit" class="btn">Покажи отчет</button>
            <a th:href="@{/reports}" class="btn btn-secondary">Текущ месец</a>
        </form>
        <p id="period-help" class="event-meta">
            Избраната дата определя крайния месец. Включват се целият месец и предходните 5 или 11 месеца.
            Целият период трябва да попада в последните 12 календарни месеца, включително текущия.
            За 12 месеца изберете дата в текущия месец.
        </p>
        <div th:if="${error}" class="alert alert-error" role="alert" th:text="${error}">Грешка</div>
    </section>

    <div th:if="${report != null}">
        <section class="report-section" aria-labelledby="summary-heading">
            <h2 id="summary-heading">Обобщение</h2>
            <p class="report-period">
                <strong th:text="${#temporals.format(report.period.startDate, 'dd.MM.yyyy')}">01.09.2026</strong>
                – <strong th:text="${#temporals.format(report.period.endDate, 'dd.MM.yyyy')}">30.09.2026</strong>
            </p>
            <p class="event-meta" th:if="${report.currentMonthIncluded}">
                Текущият месец още не е приключил. Данните отразяват наличните записи към момента на справката.
            </p>
            <div class="report-summary">
                <div class="card"><div class="sidebar-label">Брой събития</div><div class="report-value" th:text="${report.eventCount}">0</div></div>
                <div class="card"><div class="sidebar-label">Потвърдени записвания</div><div class="report-value" th:text="${report.confirmedCount}">0</div></div>
                <div class="card"><div class="sidebar-label">Изчислена сума (USD)</div><div class="report-value" th:text="${#numbers.formatDecimal(report.estimatedAmount, 1, 'WHITESPACE', 2, 'COMMA')} + ' $'">0,00 $</div></div>
            </div>
            <p class="event-meta">
                Сумата е цената на всяко събитие × текущия брой потвърдени записвания.
                Няма регистрирани реални плащания. Изчакващи и отказани записвания не участват.
                Включват се публикувани и приключили събития; чернови и отменени събития се изключват.
                Валутата USD следва съществуващите цени в приложението.
            </p>
        </section>

        <section class="card report-section" aria-labelledby="monthly-heading">
            <h2 id="monthly-heading">По месеци</h2>
            <div class="report-table-container">
                <table class="report-table">
                    <caption class="event-meta">Включени са и месеците без събития.</caption>
                    <thead><tr><th scope="col">Месец</th><th scope="col">Събития</th><th scope="col">Потвърдени записвания</th><th scope="col">Изчислена сума (USD)</th></tr></thead>
                    <tbody><tr th:each="row : ${report.monthlyRows}">
                        <th scope="row" th:text="${row.label}">Месец</th>
                        <td th:text="${row.eventCount}">0</td>
                        <td th:text="${row.confirmedCount}">0</td>
                        <td th:text="${#numbers.formatDecimal(row.estimatedAmount, 1, 'WHITESPACE', 2, 'COMMA')} + ' $'">0,00 $</td>
                    </tr></tbody>
                </table>
            </div>
        </section>

        <div th:if="${#lists.isEmpty(report.events)}" class="card no-events report-section">
            Няма публикувани или приключили събития, създадени през избрания период.
        </div>

        <section th:unless="${#lists.isEmpty(report.categoryRows)}" class="card report-section" aria-labelledby="category-heading">
            <h2 id="category-heading">По категории</h2>
            <div class="report-table-container">
                <table class="report-table">
                    <thead><tr><th scope="col">Категория</th><th scope="col">Събития</th><th scope="col">Потвърдени записвания</th><th scope="col">Изчислена сума (USD)</th></tr></thead>
                    <tbody><tr th:each="row : ${report.categoryRows}">
                        <th scope="row" th:text="${row.category}">Категория</th>
                        <td th:text="${row.eventCount}">0</td>
                        <td th:text="${row.confirmedCount}">0</td>
                        <td th:text="${#numbers.formatDecimal(row.estimatedAmount, 1, 'WHITESPACE', 2, 'COMMA')} + ' $'">0,00 $</td>
                    </tr></tbody>
                </table>
            </div>
        </section>

        <section th:unless="${#lists.isEmpty(report.events)}" class="card report-section" aria-labelledby="events-heading">
            <h2 id="events-heading">Включени събития</h2>
            <div class="report-table-container">
                <table class="report-table report-events-table">
                    <thead><tr><th scope="col">Събитие</th><th scope="col">Категория</th><th scope="col">Статус</th><th scope="col">Създадено на</th><th scope="col">Провеждане</th><th scope="col">Цена (USD)</th><th scope="col">Потвърдени</th><th scope="col">Изчислена сума (USD)</th></tr></thead>
                    <tbody><tr th:each="row : ${report.events}">
                        <th scope="row"><a th:href="@{/events/{id}(id=${row.id})}" th:text="${row.title}">Събитие</a></th>
                        <td th:text="${row.category}">Категория</td>
                        <td th:text="${row.statusLabel}">Публикувано</td>
                        <td th:text="${#temporals.format(row.createdAt, 'dd.MM.yyyy HH:mm')}">Дата</td>
                        <td th:text="${row.startsAt != null ? #temporals.format(row.startsAt, 'dd.MM.yyyy HH:mm') : 'Не е определено'}">Дата</td>
                        <td th:text="${#numbers.formatDecimal(row.price, 1, 'WHITESPACE', 2, 'COMMA')} + ' $'">0,00 $</td>
                        <td th:text="${row.confirmedCount}">0</td>
                        <td th:text="${#numbers.formatDecimal(row.estimatedAmount, 1, 'WHITESPACE', 2, 'COMMA')} + ' $'">0,00 $</td>
                    </tr></tbody>
                </table>
            </div>
        </section>
    </div>
</main>
</body>
</html>
```

### А.13. Тестове за календарните правила

Файл: [src/test/java/org/codingburgas/demetra/ReportPeriodTests.java](../src/test/java/org/codingburgas/demetra/ReportPeriodTests.java)

```java
package org.codingburgas.demetra;

import org.codingburgas.demetra.models.reports.ReportPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportPeriodTests {
    private final LocalDate today = LocalDate.of(2026, 9, 15);

    @ParameterizedTest
    @CsvSource({"1, 2026-09-01", "6, 2026-04-01", "12, 2025-10-01"})
    void includesWholeMonthsAndCrossesYearBoundary(int months, String expectedStart) {
        ReportPeriod period = ReportPeriod.from(today, months, today);
        assertEquals(LocalDate.parse(expectedStart).atStartOfDay(), period.getStartInclusive());
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), period.getEndExclusive());
        assertEquals(LocalDate.of(2026, 9, 30), period.getEndDate());
    }

    @Test
    void leapFebruaryAndThirtyOneDayMonthHaveCorrectExclusiveEnds() {
        ReportPeriod february = ReportPeriod.from(LocalDate.of(2024, 2, 10), 1, LocalDate.of(2024, 3, 15));
        assertEquals(LocalDate.of(2024, 2, 29), february.getEndDate());
        assertEquals(LocalDateTime.of(2024, 3, 1, 0, 0), february.getEndExclusive());
        ReportPeriod march = ReportPeriod.from(LocalDate.of(2024, 3, 1), 1, LocalDate.of(2024, 3, 15));
        assertEquals(LocalDate.of(2024, 3, 31), march.getEndDate());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 2, 5, 13, Integer.MAX_VALUE})
    void rejectsUnsupportedDuration(int months) {
        assertThrows(IllegalArgumentException.class, () -> ReportPeriod.from(today, months, today));
    }

    @Test
    void rejectsFutureMissingAndOutOfWindowDates() {
        assertThrows(IllegalArgumentException.class, () -> ReportPeriod.from(today.plusDays(1), 1, today));
        assertThrows(IllegalArgumentException.class, () -> ReportPeriod.from(null, 1, today));
        assertThrows(IllegalArgumentException.class,
                () -> ReportPeriod.from(LocalDate.of(2025, 9, 30), 1, today));
        assertThrows(IllegalArgumentException.class,
                () -> ReportPeriod.from(LocalDate.of(2026, 2, 1), 6, today));
        assertThrows(IllegalArgumentException.class,
                () -> ReportPeriod.from(LocalDate.of(2026, 8, 1), 12, today));
    }

    @Test
    void acceptsEarliestPossibleEndMonthForSixMonths() {
        ReportPeriod period = ReportPeriod.from(LocalDate.of(2026, 3, 10), 6, today);
        assertEquals(LocalDate.of(2025, 10, 1), period.getStartDate());
        assertEquals(LocalDate.of(2026, 3, 31), period.getEndDate());
    }
}
```

### А.14. Интеграционни тестове

Файл: [src/test/java/org/codingburgas/demetra/ReportFlowTests.java](../src/test/java/org/codingburgas/demetra/ReportFlowTests.java)

```java
package org.codingburgas.demetra;

import org.codingburgas.demetra.models.Event;
import org.codingburgas.demetra.models.EventStatus;
import org.codingburgas.demetra.models.Registration;
import org.codingburgas.demetra.models.RegistrationStatus;
import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.models.reports.EventReport;
import org.codingburgas.demetra.models.reports.EventReportRow;
import org.codingburgas.demetra.repositories.EventRepository;
import org.codingburgas.demetra.repositories.RegistrationRepository;
import org.codingburgas.demetra.repositories.UserRepository;
import org.codingburgas.demetra.services.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:reports;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReportFlowTests.FixedTime.class)
class ReportFlowTests {
    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock reportTestClock() {
            return Clock.fixed(Instant.parse("2026-09-15T09:00:00Z"), ZoneId.of("Europe/Sofia"));
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ReportService reportService;
    @Autowired private EventRepository eventRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private UserRepository userRepository;
    private User organizer;
    private int participantNumber;

    @BeforeEach
    void setUp() {
        registrationRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
        organizer = userRepository.save(new User("reports@example.com", "test-hash", "Organizer", Role.ORGANIZER));
        participantNumber = 0;
    }

    @ParameterizedTest
    @CsvSource({"1, 2026-09-01T00:00:00", "6, 2026-04-01T00:00:00", "12, 2025-10-01T00:00:00"})
    void queryRespectsExactBoundariesAndUsesCreationRatherThanScheduledDate(int months, String firstDate) {
        LocalDateTime start = LocalDateTime.parse(firstDate);
        LocalDateTime end = LocalDateTime.of(2026, 10, 1, 0, 0);
        saveEvent("Before", start.minusSeconds(1), "Concert", 10, EventStatus.PUBLISHED);
        Event first = saveEvent("First", start, "Concert", 10, EventStatus.PUBLISHED);
        Event last = saveEvent("Last", end.minusNanos(1000), "Concert", 10, EventStatus.CLOSED);
        saveEvent("After", end, "Concert", 10, EventStatus.PUBLISHED);
        first.setStartsAt(start.minusYears(1));
        eventRepository.save(first);
        last.setStartsAt(null);
        eventRepository.save(last);

        EventReport report = reportService.generate(LocalDate.of(2026, 9, 15), months);
        assertEquals(List.of(first.getId(), last.getId()), report.getEvents().stream().map(EventReportRow::getId).toList());
        assertEquals(months, report.getMonthlyRows().size());
        assertEquals(2, report.getEventCount());
        assertEquals(0, report.getConfirmedCount());
        assertEquals(new BigDecimal("0.00"), report.getEstimatedAmount());
        assertTrue(report.isCurrentMonthIncluded());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void aggregatesConfirmedRegistrationsCategoriesAmountsAndRendersWithoutOpenSessionInView() throws Exception {
        Event concert = saveEvent("Autumn concert", LocalDateTime.of(2026, 4, 10, 12, 0), "Concert", 12.35, EventStatus.PUBLISHED);
        addRegistration(concert, RegistrationStatus.CONFIRMED);
        addRegistration(concert, RegistrationStatus.CONFIRMED);
        addRegistration(concert, RegistrationStatus.WAITLISTED);
        addRegistration(concert, RegistrationStatus.CANCELLED);
        Event recital = saveEvent("Recital", LocalDateTime.of(2026, 6, 2, 12, 0), "Concert", 0.10, EventStatus.CLOSED);
        addRegistration(recital, RegistrationStatus.CONFIRMED);
        Event free = saveEvent("Free workshop", LocalDateTime.of(2026, 9, 1, 12, 0), " ", 0, EventStatus.PUBLISHED);
        free.setStartsAt(null);
        eventRepository.save(free);
        addRegistration(free, RegistrationStatus.CONFIRMED);
        saveEvent("No participants", LocalDateTime.of(2026, 5, 1, 12, 0), null, 25, EventStatus.PUBLISHED);
        Event cancelled = saveEvent("Cancelled event", LocalDateTime.of(2026, 7, 1, 12, 0), "Concert", 100, EventStatus.CANCELLED);
        addRegistration(cancelled, RegistrationStatus.CONFIRMED);
        saveEvent("Draft event", LocalDateTime.of(2026, 8, 1, 12, 0), "Concert", 100, EventStatus.DRAFT);

        EventReport report = reportService.generate(LocalDate.of(2026, 9, 15), 6);
        assertEquals(4, report.getEventCount());
        assertEquals(4, report.getConfirmedCount());
        assertEquals(new BigDecimal("24.80"), report.getEstimatedAmount());
        assertEquals(2, report.getCategoryRows().size());
        assertEquals(2, report.getCategoryRows().get(0).getEventCount());
        assertEquals(new BigDecimal("24.80"), report.getCategoryRows().get(0).getEstimatedAmount());
        assertEquals("Без категория", report.getCategoryRows().get(1).getCategory());
        assertEquals(0, report.getMonthlyRows().get(3).getEventCount());
        assertEquals(new BigDecimal("0.00"), report.getMonthlyRows().get(3).getEstimatedAmount());
        assertEquals(report.getEstimatedAmount(), report.getMonthlyRows().stream()
                .map(row -> row.getEstimatedAmount()).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(report.getEstimatedAmount(), report.getCategoryRows().stream()
                .map(row -> row.getEstimatedAmount()).reduce(BigDecimal.ZERO, BigDecimal::add));

        mockMvc.perform(get("/reports").param("date", "2026-09-15").param("months", "6"))
                .andExpect(status().isOk()).andExpect(view().name("reports/index"))
                .andExpect(content().string(containsString("24,80 $")))
                .andExpect(content().string(containsString("Autumn concert")))
                .andExpect(content().string(containsString("Не е определено")))
                .andExpect(content().string(containsString("Няма регистрирани реални плащания")))
                .andExpect(content().string(not(containsString("Cancelled event"))))
                .andExpect(content().string(not(containsString("Draft event"))));
    }

    @Test
    void roundsUnitPriceBeforeMultiplyingAndUsesCurrentRegistrationStatus() {
        Event event = saveEvent("Rounded price", LocalDateTime.of(2026, 9, 1, 0, 0), "Concert", 2.345, EventStatus.PUBLISHED);
        Registration registration = addRegistration(event, RegistrationStatus.CONFIRMED);
        addRegistration(event, RegistrationStatus.CONFIRMED);
        assertEquals(new BigDecimal("4.70"), reportService.generate(LocalDate.of(2026, 9, 1), 1).getEstimatedAmount());
        registration.setStatus(RegistrationStatus.CANCELLED);
        registrationRepository.save(registration);
        assertEquals(new BigDecimal("2.35"), reportService.generate(LocalDate.of(2026, 9, 1), 1).getEstimatedAmount());
    }

    @Test
    void includesEventsFromEveryOrganizer() {
        Event event = saveEvent("Other organizer", LocalDateTime.of(2026, 9, 1, 0, 0), "Concert", 0, EventStatus.PUBLISHED);
        User other = userRepository.save(new User("other@example.com", "test-hash", "Other", Role.ORGANIZER));
        event.setOrganizer(other);
        eventRepository.save(event);
        assertEquals(1, reportService.generate(LocalDate.of(2026, 9, 1), 1).getEventCount());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void defaultReportUsesCurrentMonthAndShowsZeroesWhenEmpty() throws Exception {
        mockMvc.perform(get("/reports"))
                .andExpect(status().isOk()).andExpect(view().name("reports/index"))
                .andExpect(content().string(containsString("2026-09-15")))
                .andExpect(content().string(containsString("Няма публикувани или приключили събития")));
        EventReport report = reportService.generate(LocalDate.of(2026, 9, 15), 12);
        assertEquals(12, report.getMonthlyRows().size());
        assertEquals(0, report.getEventCount());
        assertTrue(report.getCategoryRows().isEmpty());
        assertEquals(new BigDecimal("0.00"), report.getEstimatedAmount());
    }

    @Test
    void previousMonthIsCompleteAndIndependentOfSelectedDay() {
        saveEvent("End of August", LocalDateTime.of(2026, 8, 31, 23, 59), "Concert", 0, EventStatus.PUBLISHED);
        EventReport report = reportService.generate(LocalDate.of(2026, 8, 1), 1);
        assertEquals(1, report.getEventCount());
        assertFalse(report.isCurrentMonthIncluded());
    }

    @ParameterizedTest
    @CsvSource({"2026-13-01, 1", "not-a-date, 6", "2026-09-15, abc", "2026-09-15, 0",
            "2026-09-15, 2", "2026-09-15, -1", "2026-09-15, 13", "2026-09-16, 1",
            "2025-09-30, 1", "2026-02-01, 6", "2026-08-01, 12"})
    @WithMockUser(roles = "ORGANIZER")
    void invalidFiltersReturnHelpfulErrorWithoutReport(String date, String months) throws Exception {
        mockMvc.perform(get("/reports").param("date", date).param("months", months))
                .andExpect(status().isBadRequest()).andExpect(view().name("reports/index"))
                .andExpect(model().attributeExists("error"))
                .andExpect(model().attributeDoesNotExist("report"));
    }

    @Test
    void anonymousMustLogIn() throws Exception {
        mockMvc.perform(get("/reports")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/events")).andExpect(content().string(not(containsString("href=\"/reports\""))));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentsCannotOpenReportOrSeeReportButton() throws Exception {
        mockMvc.perform(get("/reports")).andExpect(status().isForbidden());
        mockMvc.perform(get("/events")).andExpect(content().string(not(containsString("href=\"/reports\""))));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void organizersSeeReportButtonAndTitlesAreHtmlEscaped() throws Exception {
        saveEvent("<script>alert('x')</script>", LocalDateTime.of(2026, 9, 1, 0, 0), "Concert", 0, EventStatus.PUBLISHED);
        mockMvc.perform(get("/events")).andExpect(content().string(containsString("href=\"/reports\"")));
        mockMvc.perform(get("/reports"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>"))));
    }

    private Event saveEvent(String title, LocalDateTime createdAt, String category, double price, EventStatus status) {
        Event event = new Event();
        event.setTitle(title);
        event.setCreatedAt(createdAt);
        event.setStartsAt(createdAt.plusMonths(3));
        event.setCategory(category);
        event.setPrice(price);
        event.setCapacity(100);
        event.setStatus(status);
        event.setOrganizer(organizer);
        return eventRepository.save(event);
    }

    private Registration addRegistration(Event event, RegistrationStatus status) {
        User user = userRepository.save(new User("participant" + (++participantNumber) + "@example.com",
                "test-hash", "Participant", Role.STUDENT));
        return registrationRepository.save(new Registration(user, event, status));
    }
}
```

## Приложение Б. Промени в съществуващите файлове

Пълният краен вариант на четирите променени файла е включен за сравнение. При прилагане в различен проект запази собствената му логика и добави само описаните в раздели 11 и 12 промени.

### Б.1. SecurityConfig.java

Файл: [src/main/java/org/codingburgas/demetra/config/SecurityConfig.java](../src/main/java/org/codingburgas/demetra/config/SecurityConfig.java)

```java
package org.codingburgas.demetra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/register", "/login", "/users", "/css/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/events/new").hasRole("ORGANIZER")
                        .requestMatchers("/reports", "/reports/**").hasRole("ORGANIZER")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/events", "/events/*").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/events", false)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/events?loggedOut")
                        .permitAll()
                );

        return http.build();
    }
}
```

### Б.2. nav.html

Файл: [src/main/resources/templates/fragments/nav.html](../src/main/resources/templates/fragments/nav.html)

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
<body>
<header th:fragment="nav" class="site-nav">
    <a class="brand" href="/events">
        <span class="brand-icon">D</span>
        <span class="brand-name">Demetra</span>
    </a>
    <nav class="nav-links">
        <a href="/events">Events</a>
        <a th:href="@{/users}">Users</a>
        <span sec:authorize="hasRole('ORGANIZER')">
            <a href="/events/new">Host an event</a>
        </span>
        <a sec:authorize="hasRole('ORGANIZER')" th:href="@{/reports}" class="nav-cta">Отчети</a>
        <span sec:authorize="isAuthenticated()" class="nav-user">
            <span sec:authentication="name">user@example.com</span>
            <form method="post" th:action="@{/logout}" class="inline-form">
                <button type="submit" class="link-button">Sign out</button>
            </form>
        </span>
        <span sec:authorize="isAnonymous()">
            <a href="/login">Sign in</a>
            <a href="/register" class="nav-cta">Register</a>
        </span>
    </nav>
</header>
</body>
</html>
```

### Б.3. list.html

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

### Б.4. style.css

Файл: [src/main/resources/static/css/style.css](../src/main/resources/static/css/style.css)

```css
:root {
  --navy: #162a43;
  --navy-light: #2d4a7a;
  --gold: rgb(167, 154, 14);
  --cream: #fdfbf7;
  --muted: #5c6e85;
  --danger: #e53e3e;
  --success: #38a169;
  --border: rgba(22, 42, 67, 0.15);
}

* { box-sizing: border-box; }

body {
  margin: 0;
  font-family: "Inter", system-ui, sans-serif;
  background: var(--cream);
  color: var(--navy);
}

a { color: inherit; text-decoration: none; }

.site-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 32px;
  background: var(--navy);
  color: var(--cream);
}

.brand { display: flex; align-items: center; gap: 8px; font-weight: 800; font-size: 1.1rem; }
.brand-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 28px; height: 28px; border-radius: 6px;
  background: var(--gold); color: var(--navy); font-weight: 900;
}

.nav-links { display: flex; align-items: center; gap: 20px; }
.nav-links a { color: var(--cream); opacity: 0.9; }
.nav-links a:hover { opacity: 1; text-decoration: underline; }
.nav-cta {
  background: var(--gold); color: var(--navy) !important;
  padding: 6px 14px; border-radius: 6px; font-weight: 700;
}
.nav-user { display: flex; align-items: center; gap: 12px; font-size: 0.9rem; opacity: 0.9; }

.inline-form { display: inline; }
.link-button {
  background: none; border: none; color: var(--cream); opacity: 0.85;
  cursor: pointer; font: inherit; text-decoration: underline; padding: 0;
}

.page-container { max-width: 1100px; margin: 0 auto; padding: 32px 24px 64px; }
.narrow-container { max-width: 440px; margin: 48px auto; padding: 0 24px; }

.page-header { margin-bottom: 28px; }
.page-header h1 { font-size: 1.9rem; margin: 4px 0 0; }
.breadcrumbs { font-size: 0.85rem; color: var(--muted); }

.card {
  background: #fff; border: 1px solid var(--border); border-radius: 10px;
  padding: 24px;
}

.form-group { margin-bottom: 16px; display: flex; flex-direction: column; gap: 6px; }
.form-group label { font-weight: 600; font-size: 0.9rem; }
.form-group input, .form-group textarea, .form-group select {
  padding: 10px 12px; border: 1px solid var(--border); border-radius: 6px; font: inherit;
}
.form-row { display: flex; gap: 16px; }
.form-row .form-group { flex: 1; }

.btn {
  display: inline-block; padding: 10px 20px; border-radius: 6px; border: none;
  background: var(--navy); color: var(--cream); font-weight: 700; cursor: pointer; font: inherit;
}
.btn:hover { background: var(--navy-light); }
.btn-secondary { background: transparent; color: var(--navy); border: 1px solid var(--border); }
.btn-danger { background: var(--danger); }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }

.alert { padding: 12px 16px; border-radius: 6px; margin-bottom: 16px; font-size: 0.9rem; }
.alert-error { background: rgba(229, 62, 62, 0.1); color: var(--danger); border: 1px solid rgba(229,62,62,0.3); }
.alert-success { background: rgba(56, 161, 105, 0.1); color: var(--success); border: 1px solid rgba(56,161,105,0.3); }

.auth-card { text-align: left; }
.auth-card h2 { margin-top: 0; }
.role-toggle { display: flex; gap: 8px; margin-bottom: 20px; }
.role-toggle label {
  flex: 1; text-align: center; padding: 10px; border: 1px solid var(--border); border-radius: 6px;
  cursor: pointer; font-weight: 600; font-size: 0.9rem;
}
.role-toggle input { display: none; }
.role-toggle input:checked + span { color: var(--gold); }
.auth-footer { margin-top: 18px; font-size: 0.9rem; color: var(--muted); }

.events-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 20px; margin-top: 20px;
}
.event-card {
  background: #fff; border: 1px solid var(--border); border-radius: 10px; overflow: hidden;
  display: flex; flex-direction: column;
}
.event-card-image {
  height: 100px; background: linear-gradient(135deg, var(--navy) 0%, var(--navy-light) 100%);
  display: flex; align-items: center; justify-content: space-between; padding: 12px; color: var(--cream);
}
.event-card-body { padding: 16px; display: flex; flex-direction: column; gap: 6px; flex: 1; }
.event-card-body h3 { margin: 0; font-size: 1.1rem; }
.event-meta { font-size: 0.85rem; color: var(--muted); }
.event-card-footer { margin-top: auto; display: flex; justify-content: space-between; align-items: center; padding-top: 8px; }
.badge {
  display: inline-block; padding: 3px 10px; border-radius: 999px; font-size: 0.75rem; font-weight: 700;
}
.badge-open { background: rgba(56,161,105,0.15); color: var(--success); }
.badge-full { background: rgba(229,62,62,0.15); color: var(--danger); }
.badge-registered { background: rgba(167,154,14,0.18); color: var(--gold); }
.badge-waitlisted { background: rgba(22,42,67,0.1); color: var(--navy); }

.no-events { text-align: center; padding: 60px 20px; color: var(--muted); }

.event-filter-form { display: flex; align-items: end; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.event-filter-form .form-group { margin-bottom: 0; }
.report-entry { margin-top: 16px; }
.report-section { margin-bottom: 24px; }
.report-section h2 { margin-top: 0; font-size: 1.2rem; }
.report-summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.report-value { margin-top: 8px; font-size: 1.8rem; font-weight: 800; overflow-wrap: anywhere; }
.report-table-container { overflow-x: auto; }
.report-table { width: 100%; border-collapse: collapse; }
.report-table th, .report-table td { padding: 12px; text-align: left; border-bottom: 1px solid var(--border); }
.report-table thead { background: var(--cream); }
.report-table caption { text-align: left; padding-bottom: 12px; }
.report-table a { text-decoration: underline; }
.report-events-table { min-width: 920px; }
.report-period { font-size: 1.1rem; }
.event-card-image { flex-wrap: wrap; gap: 8px; }

.user-sort-form { display: flex; align-items: end; flex-wrap: wrap; gap: 12px; margin-bottom: 24px; }
.user-sort-form .form-group { margin-bottom: 0; }
.users-table-container { overflow-x: auto; }
.users-table { width: 100%; border-collapse: collapse; background: #fff; }
.users-table th, .users-table td { padding: 12px 16px; text-align: left; border-bottom: 1px solid var(--border); }

.event-detail-hero {
  background: linear-gradient(135deg, var(--navy) 0%, var(--navy-light) 100%);
  color: var(--cream); padding: 32px; border-radius: 10px; margin-bottom: 24px;
}
.event-detail-hero .tag { font-size: 0.8rem; opacity: 0.8; text-transform: uppercase; letter-spacing: 0.05em; }
.event-detail-hero h1 { margin: 8px 0; }
.event-detail-meta { display: flex; gap: 20px; flex-wrap: wrap; font-size: 0.9rem; opacity: 0.9; }

.event-detail-body { display: grid; grid-template-columns: 2fr 1fr; gap: 24px; }
.sidebar-card { margin-bottom: 16px; }
.sidebar-label { font-size: 0.75rem; text-transform: uppercase; color: var(--muted); font-weight: 700; }
.sidebar-price { font-size: 1.8rem; font-weight: 800; margin: 4px 0 12px; }
.cap-row { display: flex; justify-content: space-between; font-size: 0.9rem; padding: 4px 0; }

@media (max-width: 720px) {
  .site-nav, .nav-links { flex-wrap: wrap; gap: 12px; }
  .site-nav { padding: 16px 20px; }
  .nav-user { flex-wrap: wrap; overflow-wrap: anywhere; }
  .report-summary { grid-template-columns: 1fr; }
  .event-detail-body { grid-template-columns: 1fr; }
  .form-row { flex-direction: column; }
}
```

## Приложение В. Резултати от проверката

`mvn test`: **63 тестови изпълнения, 0 failures, 0 errors, 0 skipped**. От тях 34 са нови за отчетите. Docker образът е успешно компилиран и пакетиран с Java 17.

Допълнително услугата и Thymeleaf екранът са изпълнени срещу локалния MySQL с реалните съществуващи събития за 1, 6 и 12 месеца. И трите заявки връщат HTTP 200 и правилния брой месечни редове. Проверката не създава и не променя събития, потребители или регистрации; използва симулирана роля на организатор в MockMvc. В момента на проверката резултатите са 3 събития, 2 потвърдени записвания и изчислена сума 10,00 USD. Тези стойности са пример от състоянието на локалната база и могат да се променят при следваща употреба.

Генерираният от MySQL шестмесечен HTML е прегледан визуално в Chrome при стандартен прозорец и ширина 390px. При тесния екран навигацията и формата се подреждат на няколко реда, без отрязване на съдържанието. Визуалният преглед е на временно копие на рендерирания HTML; филтрирането и правата са проверени отделно през Spring MVC тестовете. Локалният Docker контейнер `app` е обновен с реализацията; `/reports` в работещото приложение препраща анонимния посетител към вход.
