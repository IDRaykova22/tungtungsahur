# Подреждане на регистрираните потребители — учебно ръководство за Java и Spring Boot

Това ръководство обяснява метода `UserService.listRegisteredUsers(String sortBy)` и свързаната с него страница в Demetra. То следва структурата на [ръководството за отчетите](reports-guide-bg.md): правила → MVC → данни → реализация → екран → проверки → приложения с код. Примерите съответстват на реализацията към 30.09.2026 г.

Функционалността вече е реализирана. Стъпките са за изучаване или повторно изграждане в учебно копие; не добавяй втори клас или втори метод със същото име в работещия проект. Изпълняваният код е в `src`, а този документ обяснява как работи.

## Съдържание

- [1. Какво прави функционалността](#1-какво-прави-функционалността)
- [2. Подготовка и стартиране](#2-подготовка-и-стартиране)
- [3. MVC и отговорности](#3-mvc-и-отговорности)
- [4. Данните за потребителя](#4-данните-за-потребителя)
- [5. Файлове и ред за реализация](#5-файлове-и-ред-за-реализация)
- [6. Съществуващият repository](#6-съществуващият-repository)
- [7. Главният метод в услугата](#7-главният-метод-в-услугата)
- [8. Пример с конкретни записи](#8-пример-с-конкретни-записи)
- [9. Как се предава резултатът](#9-как-се-предава-резултатът)
- [10. Контролерът и параметърът sort](#10-контролерът-и-параметърът-sort)
- [11. Формата, таблицата и CSS](#11-формата-таблицата-и-css)
- [12. Навигация и публичен достъп](#12-навигация-и-публичен-достъп)
- [13. Автоматични и ръчни проверки](#13-автоматични-и-ръчни-проверки)
- [14. Чести грешки](#14-чести-грешки)
- [15. Как да обясниш реализацията и да я надградиш](#15-как-да-обясниш-реализацията-и-да-я-надградиш)
- [16. Източници](#16-източници)
- [Приложение А. Пълен код на новите файлове](#приложение-а-пълен-код-на-новите-файлове)
- [Приложение Б. Промени в съществуващите файлове](#приложение-б-промени-в-съществуващите-файлове)
- [Приложение В. Проверка на документацията](#приложение-в-проверка-на-документацията)

## 1. Какво прави функционалността

Посетителят отваря **Users** от навигацията или адреса `/users`. Страницата показва имената на регистрираните потребители и датите им на регистрация. От падащия списък избира подреждане и натиска **Sort**.

### Договорени правила

| Въпрос | Реализация |
| --- | --- |
| Кои потребители се показват? | Всички записи от таблицата `users`, независимо от ролята. |
| По какво се подреждат имената? | По цялото поле `name`, във възходящ ред и без разграничаване на главни и малки букви. |
| Коя дата се използва? | `User.createdAt` — моментът на създаване на акаунта при регистрацията. |
| Какъв е редът по дата? | Най-рано регистрираните са първи. Сравнява се и часът. |
| Как се решава равенство? | При еднакви имена се използва датата; при еднакви дати се използва името. |
| Какво става без избор? | Подреждане по име. |
| Какво става при непознат избор? | Отново подреждане по име. |
| Нужен ли е вход? | Не. Страницата и връзката са публични. |
| Какво се вижда на екрана? | Име и дата на регистрация. Пароли, хешове и имейли не са колони в таблицата. |

Примерни адреси:

```text
/users
/users?sort=name
/users?sort=date
```

Първите два адреса използват еднаква подредба. Третият избира дата на регистрация. Методът връща списък; показването на списъка е отговорност на HTML шаблона.

## 2. Подготовка и стартиране

Проектът вече има Spring MVC, Spring Data JPA, Thymeleaf, Spring Security, Lombok, MySQL и H2 за тестове. Java версията в `pom.xml` е 17. За тази функционалност не се добавят зависимости или таблици.

Отвори Demetra като Maven проект в IntelliJ IDEA. Файловете трябва да са в `src`, а не в генерираната директория `target`. Ако IDE не разпознава Lombok getters, провери поддръжката за Lombok и annotation processing.

От терминал в директорията с `pom.xml`:

```bash
docker compose up -d --build
```

Отвори `http://localhost:8080/users`. При работа с Docker промяна в изходния код не обновява автоматично вече пакетирания JAR. След промяна използвай:

```bash
docker compose up -d --build --no-deps app
```

Тази команда предполага, че базата вече работи. За логове използвай `docker compose logs app --tail 100`. За подробности за директно стартиране на Java виж раздел 2 на [шаблона](reports-guide-bg.md#2-подготовка-и-стартиране).

## 3. MVC и отговорности

MVC означава **Model–View–Controller**. В проекта бизнес логиката е в Service, а четенето от базата — в Repository. Това допълва разделението на отговорностите, без да изисква допълнителни интерфейси или DTO класове за този прост списък.

```text
Браузър: GET /users?sort=date
    ↓
UserController: приема sort
    ↓
UserService: избира реда за подреждане
    ↓
UserRepository: чете подредените записи от базата
    ↓
UserService: връща List<User>
    ↓
UserController: добавя users и sort в Model
    ↓
Thymeleaf: templates/users/list.html
    ↓
Браузърът получава готов HTML
```

| Част | Отговорност |
| --- | --- |
| `User` | Представя потребител от базата. |
| `UserRepository` | Извършва заявката към базата. |
| `UserService` | Решава коя подредба да се приложи. |
| `UserController` | Приема HTTP параметъра и избира View. |
| `Model` | Пренася резултата и избрания режим към View. |
| `users/list.html` | Показва формата и таблицата. |

`User` е entity модел. Spring MVC `Model` е контейнер за данните на страницата; това са различни понятия. Контролерът не извиква repository директно, а HTML не извършва сортиране или заявки към базата.

## 4. Данните за потребителя

В съществуващия [User.java](../src/main/java/org/codingburgas/demetra/models/User.java) се използват основно:

```java
@Column(nullable = false, length = 80)
private String name;

@Column(nullable = false)
private LocalDateTime createdAt = LocalDateTime.now();
```

`name` е записаното име. То не е разделено на собствено и фамилно име, затова методът подрежда по целия текст.

`LocalDateTime` съдържа дата и час. При регистрация се създава нов `User`, полето получава началната стойност от `now()`, а `userRepository.save(user)` я записва в базата. При последващо четене JPA възстановява съхранената стойност; датата не се заменя с днешната при всяко посещение на страницата.

`@Entity` и `@Table(name = "users")` свързват класа с таблицата. `@Id` маркира първичния ключ. Lombok `@Getter` предоставя методите `getName()` и `getCreatedAt()`, които шаблонът използва чрез изразите за свойства.

`LocalDateTime` не пази часова зона. Страницата форматира записаните стойности, без автоматично преобразуване според часовата зона на браузъра.

## 5. Файлове и ред за реализация

| Файл | Промяна при добавяне на функционалността |
| --- | --- |
| `services/UserService.java` | Нов метод и imports за `Sort` и `List`. |
| `controllers/UserController.java` | Нов контролер. |
| `templates/users/list.html` | Нов HTML шаблон. |
| `templates/fragments/nav.html` | Нова връзка към `/users`. |
| `config/SecurityConfig.java` | `/users` е добавен към публичните адреси. |
| `static/css/style.css` | Стилове за формата и таблицата. |
| `UserListTests.java` | Нов тестов клас. |
| `models/User.java`, `repositories/UserRepository.java` | Използват се съществуващите файлове без промяна. |

Java пакетите са под `src/main/java/org/codingburgas/demetra`. HTML и CSS са под `src/main/resources`. Тестовете са в `src/test/java/org/codingburgas/demetra`.

За учебно възпроизвеждане следвай: провери модела и repository → добави service метода → създай контролера → създай шаблона → добави навигацията, достъпа и CSS → изпълни тестовете. Пълният код на новите файлове е в приложение А; конкретните допълнения към съществуващите са в приложение Б.

## 6. Съществуващият repository

Декларацията е:

```java
public interface UserRepository extends JpaRepository<User, Long>
```

`User` е типът на entity обекта, а `Long` е типът на неговия идентификатор. От `JpaRepository` се наследява `findAll(Sort sort)`, затова не добавяме нов метод за тази заявка в `UserRepository`.

Когато услугата извика `findAll(...)`, Spring Data JPA изпълнява заявка с указаната подредба и връща `List<User>`. Сортирането се изпълнява в базата. Няма Java цикъл, който пренарежда всички потребители след прочитането им.

Имената `name` и `createdAt` в `Sort` са **Java свойства на entity**, а не ръчно написани имена на SQL колони.

## 7. Главният метод в услугата

В [UserService.java](../src/main/java/org/codingburgas/demetra/services/UserService.java) методът е:

```java
@Transactional(readOnly = true)
public List<User> listRegisteredUsers(String sortBy) {
    Sort.Order nameOrder = Sort.Order.asc("name").ignoreCase();
    if ("date".equals(sortBy)) {
        return userRepository.findAll(Sort.by(Sort.Order.asc("createdAt"), nameOrder));
    }
    return userRepository.findAll(Sort.by(nameOrder, Sort.Order.asc("createdAt")));
}
```

### Вход и резултат

`public` позволява извикване от контролера. `String sortBy` съдържа избрания режим. `List<User>` е резултатът: списък от потребителски обекти, който може да бъде празен.

Съществуващият `@Service` върху класа го прави управляван от Spring. Конструкторът получава repository и password encoder. За новия метод е нужен repository; password encoder остава за съществуващата регистрация.

`@Transactional(readOnly = true)` задава транзакция с намерение за четене и позволява оптимизации. Не е проверка за вход и не е самостоятелна забрана за всички възможни записи. Самият метод извиква само четяща операция.

### Подреждане по име

```java
Sort.Order nameOrder = Sort.Order.asc("name").ignoreCase();
```

`Sort.Order` описва една инструкция. `asc` означава възходящ ред. `ignoreCase()` задава сравняване без разграничаване на главни и малки букви. Това не променя съхранените имена и не ги превръща в малки букви на екрана. API е описан в [официалната документация на Sort.Order](https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/domain/Sort.Order.html).

### Избор на режим

`"date".equals(sortBy)` проверява съдържанието на низа. Не използваме `==` за сравняване на текст. Поставянето на постоянния низ първо позволява сравнението да върне `false`, дори ако методът бъде извикан директно с `null`.

При `date` първият критерий е `createdAt`, а вторият — `nameOrder`. При друг вход приоритетите се разменят. Аргументите на `Sort.by(...)` се прилагат отляво надясно.

`return` приключва изпълнението, затова при една заявка се изпълнява само едно от двете извиквания на `findAll`.

## 8. Пример с конкретни записи

Следните данни са учебен пример, а не текущо съдържание на базата:

| Име | Регистрация |
| --- | --- |
| Boris | 01.09.2026 12:00 |
| Anna | 02.09.2026 09:00 |
| anna | 03.09.2026 08:00 |

При `sort=name` резултатът е `Anna → anna → Boris`. Първите две имена се сравняват еднакво при игнориране на регистъра, затова по-ранната регистрация е първа.

При `sort=date` резултатът е `Boris → Anna → anna`. Ако два записа имат точно еднакъв timestamp, името решава реда между тях.

Ако и двата критерия съвпадат, няма трети критерий по ID и взаимният ред не е гарантиран. Подредбата на смесени азбуки и знаци зависи и от правилата за сравнение на базата. Реализацията не задава отделен български азбучен компаратор.

## 9. Как се предава резултатът

`findAll` връща обектите към услугата, а услугата ги връща към контролера. Методът не отпечатва в конзолата и не произвежда HTML или JSON.

Промяната на подредбата не променя имената, датите или редовете в базата. При липса на потребители се получава празен списък, който View обработва с отделно съобщение.

Няма странициране: всички потребители се зареждат наведнъж. Това поддържа учебната реализация проста. При бъдещо изискване за голям брой записи може да се добави `Pageable`, но той не е част от текущия метод.

## 10. Контролерът и параметърът sort

Новият `UserController` има `@Controller` и получава `UserService` чрез конструктора. Това е **constructor injection**: Spring създава и свързва обектите. Полето `private final UserService userService` не се преназначава след конструиране.

```java
@GetMapping("/users")
public String list(@RequestParam(defaultValue = "name") String sort, Model model) {
    model.addAttribute("users", userService.listRegisteredUsers(sort));
    model.addAttribute("sort", sort);
    return "users/list";
}
```

`@GetMapping` свързва GET адреса с метода. `@RequestParam` чете `sort` от query string; зададената стойност по подразбиране е `name`. Този механизъм за свързване на параметри е описан в [Spring MVC: @RequestParam](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestparam.html).

Първият `addAttribute` извиква услугата и поставя резултата в `Model` под името `users`. Вторият предава избора под името `sort`, за да остане правилната опция маркирана.

`return "users/list"` е име на View, което сочи `templates/users/list.html`. Не е текст за показване и не е пренасочване. Затова класът е `@Controller`, а не `@RestController`.

## 11. Формата, таблицата и CSS

В `templates/users/list.html` формата използва `method="get"` и `th:action="@{/users}"`. Прочитането на списъка не променя данни, а изборът остава видим в URL.

`select` има `name="sort"`, съответстващо на параметъра в контролера. Стойностите на опциите са `name` и `date`. `label for="sort"` е свързан с `id="sort"` за достъпност.

| Thymeleaf израз | Роля |
| --- | --- |
| `th:selected="${sort == 'date'}"` | Маркира подредбата по дата. |
| `th:selected="${sort != 'date'}"` | Маркира подредбата по име и при непознат режим. |
| `th:each="user : ${users}"` | Създава ред за всеки потребител. |
| `th:text="${user.name}"` | Показва името чрез getter. |
| `th:if="${#lists.isEmpty(users)}"` | Показва съобщението при празен списък. |
| `th:unless="${#lists.isEmpty(users)}"` | Показва таблицата при налични записи. |
| `th:replace="~{fragments/nav :: nav}"` | Включва общата навигация. |

Датата се форматира с `#temporals.format(user.createdAt, 'dd MMM yyyy, HH:mm')`: ден, кратко име на месец, година, час и минути. `HH` е 24-часов формат; `mm` са минути, а `MM` би означавало месец. Езикът на името на месеца зависи от locale на заявката. Сортирането използва стойността от базата, а не форматирания текст.

`th:text` заменя съдържанието и екранира HTML. `${...}` чете стойности от модела, а `@{...}` създава URL. Основните атрибути са описани в [Thymeleaf 3.1](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html).

CSS класовете `.user-sort-form` и неговата `.form-group` подреждат контролите и отстоянията. `.users-table` оформя таблицата и клетките, а `.users-table-container` позволява хоризонтално превъртане. Стиловете не променят данните или подредбата.

## 12. Навигация и публичен достъп

В общата навигация е добавено:

```html
<a th:href="@{/users}">Users</a>
```

Връзката няма условие за вход. В `SecurityConfig` адресът `/users` е част от съществуващото правило с `.permitAll()`, преди общото `.anyRequest().authenticated()`.

Това означава, че списъкът се отваря и от анонимен посетител. Правилата за вход, организатори и отчети в останалата част на проекта са отделни. Методът `listRegisteredUsers` не проверява роли или текущ потребител.

## 13. Автоматични и ръчни проверки

### Автоматични тестове

```bash
mvn -Dtest=UserListTests test
```

Тестовият клас има **6 тестови изпълнения**: подредба по име с различен регистър и еднакви имена; подредба по дата с еднакви timestamps; изрично `name`; непознат избор; празен списък; анонимен достъп. Параметризираният метод се изпълнява два пъти и затова броят на изпълненията е по-голям от броя на тестовите методи.

`@SpringBootTest` зарежда приложението, `@AutoConfigureMockMvc` предоставя симулирани HTTP заявки, а `@ActiveProfiles("test")` използва тестовите настройки. Базата е отделна H2 база в паметта с име `user-list`. `@Transactional` върху тестовия клас връща промените след всяко изпълнение.

`@WithMockUser` симулира потребител само за част от тестовете; не прави страницата защитена. Последният тест няма тази анотация и проверява достъпа без вход. Проверява се и че паролният хеш не попада в HTML.

### Ръчна проверка

1. Отвори `/users` без вход и провери наличието на навигационната връзка.
2. Избери **Name (A–Z)** и натисни **Sort**.
3. Избери **Registration date (oldest first)** и сравни датите.
4. Отвори `/users?sort=unknown`: трябва да се приложи подредба по име.
5. За еднакви имена и дати използвай изолираните тестове. Не редактирай реални записи само за демонстрация.

## 14. Чести грешки

| Симптом | Причина и решение |
| --- | --- |
| Не се вижда новата връзка | Провери `nav.html`, обнови Docker образа и презареди страницата. |
| `/users` препраща към вход | Провери дали `/users` е в правилото с `permitAll()`. |
| Вижда се само текстът `users/list` | Използван е `@RestController` вместо `@Controller`. |
| `TemplateInputException` | Провери директорията `templates/users` и името `list.html`. |
| Грешка за неизвестно свойство при сортиране | Използвай Java полето `createdAt`, не SQL име като `created_at`. |
| Имената изглеждат странно подредени по регистър | Провери `.ignoreCase()` и правилата за сравнение на базата. |
| Изборът не стига до контролера | Провери `name="sort"` и стойностите `name`/`date`. |
| Месецът е на български в браузъра, но на английски в теста | Форматирането зависи от locale; това не сменя реда на данните. |
| Няма видима промяна при смяна на режима | При малко записи двата реда може естествено да съвпаднат. |

## 15. Как да обясниш реализацията и да я надградиш

За представяне можеш да кажеш: „Методът получава режим за подреждане. Услугата създава Sort с основен и допълнителен критерий. Repository извлича подредените потребители от базата. Контролерът поставя списъка в Model, а Thymeleaf го показва като таблица. Подредбата е по име или по регистрационен timestamp и страницата е публична.“

За упражнение първо обясни защо при `date` критерият за дата е първи, а при друг режим — втори. След това проследи една GET заявка от dropdown до `findAll` и обратно до `th:each`.

Възможни бъдещи разширения са избор на низходящ ред или странициране. Те не са реализирани тук. Не е нужен нов service интерфейс само за този метод.

## 16. Източници

Основен източник за конкретните правила е кодът на Demetra. Структурата следва [reports-guide-bg.md](reports-guide-bg.md). Общите механизми могат да се сверят с:

- [Spring Data: Sort.Order](https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/domain/Sort.Order.html) — посока, поле и сравняване без регистър.
- [Spring MVC: @RequestParam](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestparam.html) — параметри на заявката.
- [Spring Data JPA: Transactionality](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html) — транзакционни граници и `readOnly`.
- [Thymeleaf 3.1](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html) — показване, повторение и условни елементи.

Вторият метод е описан отделно в [ръководството за филтриране на събитията](event-creation-filter-guide-bg.md).

## Приложение А. Пълен код на новите файлове

Това са файловете, добавени при реализирането на списъка с потребители. Те вече съществуват в проекта. Кодът е извлечен от тях, за да може да се сравнява ред по ред с обясненията.

### А.1. MVC контролер

Файл: [src/main/java/org/codingburgas/demetra/controllers/UserController.java](../src/main/java/org/codingburgas/demetra/controllers/UserController.java)

```java
package org.codingburgas.demetra.controllers;

import org.codingburgas.demetra.services.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public String list(@RequestParam(defaultValue = "name") String sort, Model model) {
        model.addAttribute("users", userService.listRegisteredUsers(sort));
        model.addAttribute("sort", sort);
        return "users/list";
    }
}
```

### А.2. Thymeleaf страница

Файл: [src/main/resources/templates/users/list.html](../src/main/resources/templates/users/list.html)

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Registered Users — Demetra</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
    <link rel="icon" th:href="@{/images/favicon.svg}">
</head>
<body>
<div th:replace="~{fragments/nav :: nav}"></div>

<div class="page-container">
    <div class="page-header">
        <div class="breadcrumbs">Demetra / Users</div>
        <h1>Registered Users</h1>
    </div>

    <form method="get" th:action="@{/users}" class="user-sort-form">
        <div class="form-group">
            <label for="sort">Sort by</label>
            <select id="sort" name="sort">
                <option value="name" th:selected="${sort != 'date'}">Name (A–Z)</option>
                <option value="date" th:selected="${sort == 'date'}">Registration date (oldest first)</option>
            </select>
        </div>
        <button type="submit" class="btn">Sort</button>
    </form>

    <p th:if="${#lists.isEmpty(users)}">No users have registered yet.</p>
    <div class="users-table-container" th:unless="${#lists.isEmpty(users)}">
        <table class="users-table">
            <thead>
            <tr>
                <th scope="col">Name</th>
                <th scope="col">Registered on</th>
            </tr>
            </thead>
            <tbody>
            <tr th:each="user : ${users}">
                <td th:text="${user.name}">Name</td>
                <td th:text="${#temporals.format(user.createdAt, 'dd MMM yyyy, HH:mm')}">Registration date</td>
            </tr>
            </tbody>
        </table>
    </div>
</div>
</body>
</html>
```

### А.3. Интеграционни тестове

Файл: [src/test/java/org/codingburgas/demetra/UserListTests.java](../src/test/java/org/codingburgas/demetra/UserListTests.java)

```java
package org.codingburgas.demetra;

import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:user-list;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserListTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    @WithMockUser
    void defaultsToAlphabeticalOrderIgnoringCaseAndThenRegistrationDate() throws Exception {
        User zara = saveUser("Zara", "zara@example.com", 1);
        User laterAnna = saveUser("anna", "anna2@example.com", 3);
        User earlierAnna = saveUser("Anna", "anna1@example.com", 2);

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("users/list"))
                .andExpect(model().attribute("users", contains(earlierAnna, laterAnna, zara)))
                .andExpect(content().string(containsString("02 Jan 2026, 10:00")))
                .andExpect(content().string(not(containsString("private-password-hash"))));
    }

    @Test
    @WithMockUser
    void sortsByRegistrationDateAndThenName() throws Exception {
        User laterAnna = saveUser("Anna", "anna@example.com", 3);
        User earlierZara = saveUser("Zara", "zara@example.com", 1);
        User earlierBoris = saveUser("boris", "boris@example.com", 1);

        mockMvc.perform(get("/users").param("sort", "date"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("users", contains(earlierBoris, earlierZara, laterAnna)))
                .andExpect(content().string(containsString("value=\"date\" selected=\"selected\"")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "unknown"})
    @WithMockUser
    void usesAlphabeticalOrderForNameOrUnknownSort(String sort) throws Exception {
        User zara = saveUser("Zara", "zara@example.com", 1);
        User anna = saveUser("Anna", "anna@example.com", 2);

        mockMvc.perform(get("/users").param("sort", sort))
                .andExpect(status().isOk())
                .andExpect(model().attribute("users", contains(anna, zara)));
    }

    @Test
    @WithMockUser
    void displaysEmptyListMessage() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No users have registered yet.")));
    }

    @Test
    void anonymousCanBrowseUsers() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("users/list"))
                .andExpect(content().string(containsString("href=\"/users\">Users</a>")));
    }

    private User saveUser(String name, String email, int day) {
        User user = new User(email, "private-password-hash", name, Role.STUDENT);
        user.setCreatedAt(LocalDateTime.of(2026, 1, day, 10, 0));
        return userRepository.save(user);
    }
}
```

## Приложение Б. Промени в съществуващите файлове

Тук са само допълненията за тази функционалност. Не заменяй целия service, общата навигация, CSS или security конфигурацията с отделния фрагмент.

### Б.1. UserService.java

Файл: [UserService.java](../src/main/java/org/codingburgas/demetra/services/UserService.java).

Добавени imports:

```java
import org.springframework.data.domain.Sort;
import java.util.List;
```

Добавен метод в съществуващия клас:

```java
    @Transactional(readOnly = true)
    public List<User> listRegisteredUsers(String sortBy) {
        Sort.Order nameOrder = Sort.Order.asc("name").ignoreCase();
        if ("date".equals(sortBy)) {
            return userRepository.findAll(Sort.by(Sort.Order.asc("createdAt"), nameOrder));
        }
        return userRepository.findAll(Sort.by(nameOrder, Sort.Order.asc("createdAt")));
    }
```

### Б.2. Навигационната връзка

В [nav.html](../src/main/resources/templates/fragments/nav.html), вътре в `nav-links`, е добавен този ред:

```html
<a th:href="@{/users}">Users</a>
```

### Б.3. Публичният адрес

В [SecurityConfig.java](../src/main/java/org/codingburgas/demetra/config/SecurityConfig.java) е добавена стойността `"/users"` в съществуващото правило:

```java
.requestMatchers("/", "/register", "/login", "/users", "/css/**", "/images/**", "/favicon.ico").permitAll()
```

Останалите правила, включително достъпът до отчетите, се запазват.

### Б.4. Стиловете

В [style.css](../src/main/resources/static/css/style.css) са добавени:

```css
.user-sort-form { display: flex; align-items: end; flex-wrap: wrap; gap: 12px; margin-bottom: 24px; }
.user-sort-form .form-group { margin-bottom: 0; }
.users-table-container { overflow-x: auto; }
.users-table { width: 100%; border-collapse: collapse; background: #fff; }
.users-table th, .users-table td { padding: 12px 16px; text-align: left; border-bottom: 1px solid var(--border); }
```

## Приложение В. Проверка на документацията

Документът е сверен с текущите файлове към 30.09.2026 г. Приложение А съдържа пълни копия на трите посочени файла; методът и фрагментите в приложение Б са извлечени от текущата реализация. Проверени са относителните връзки, съдържанието и Markdown блоковете с код.

В тази задача се добавя само документация. Не са променяни Java кодът, шаблоните, тестовете, данните или ръководството за отчетите. Не е изпълняван нов тестов прогон; раздел 13 описва наличните шест тестови изпълнения и командата, с която могат да бъдат стартирани. Резултатите от бъдещ прогон се намират в `target/surefire-reports`.
