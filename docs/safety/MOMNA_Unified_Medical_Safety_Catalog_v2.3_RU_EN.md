# MOMNA — Unified Medical Safety Catalog v2.3 (RU / US-EN)
**28.11.2026 · Заменяет v2.2 (20.10.2026) · Product content specification, not clinical release approval**
**Статус:** v2.3 — решение Product 28.11.2026 (MOMNA-1146): L10, L11, L13 для R18, экран PM1-H, исход NOT_ANSWERED. CONTENT READY FOR DESIGN / BACKEND LOGIC APPROVED BY PRODUCT (D3, D5, D8, D10, D12, D13 — 20.10.2026) / CLINICAL RELEASE NOT APPROVED. Пороги, сочетания признаков, номера экстренных служб и маршруты помощи требуют клинического утверждения. Ни одна формулировка не является диагнозом.

---

## 0. Цель и принципы

Momna — не симптом-чекер. Каталог существует для **редкой ситуации**: женщина в опасном состоянии открывает приложение или отмечает выраженный симптом. Тогда за минимальное число касаний нужно распознать угрожающий признак, сразу показать понятное действие и не потерять ни один подтверждённый признак.

| Принцип | Значение |
|---|---|
| Ежедневные вопросы безопасности | **0** |
| Вопросы появляются | Только по входному сигналу (§2) |
| Порядок | Опасное первым |
| Остановка | Когда оставшиеся вопросы не могут изменить ни уровень, ни место обращения, ни опасное сочетание (§3, L3) |
| Повторы | Один признак — один вопрос за сессию; маршрут не открывается повторно в период паузы (L13) |
| Цель по числу вопросов | До 5 на маршрут; превышение допускается только ради вопросов EMERGENCY / SAFETY или сочетаний и явно отмечено (§6) |

**Об эмоциональном здоровье (обязательная формулировка, D5):**
> MOMNA помогает замечать изменения эмоционального состояния и находить поддержку. Приложение не проводит полноценный скрининг послеродовой депрессии и не заменяет оценку медицинским специалистом.

Заявлять о скрининге депрессии или тревоги в продукте, маркетинге и документации запрещено.

**Охват v2.2:** 121 исходный код v2.0 → **47 задаваемых фактов + 6 вычисляемых**. Число выросло на 2 относительно v2.1: добавлены признаки из списка CDC, при этом объединены два признака с одинаковым исходом. Все 121 код сохранены в трассировке (§5).

---

## 0.1. Изменения v2.2 → v2.3 (решение Product 28.11.2026, MOMNA-1146)

Каталог v2.2 не определял ответы «Не сейчас» и «Не уверена» на `F_HARM_THOUGHTS` (уровень SAFETY_ASSESSMENT): фактически они давали «ничего не найдено». v2.3 закрывает это только для R18 (входы PP2, PP18, WB1).

| # | Правило | v2.2 | v2.3 |
|---|---|---|---|
| 1 | L10 | «Не сейчас» на вопросе о вреде → `NOT_REPORTED`, H1, пауза R18 14 дней | Исход `NOT_ANSWERED` («не отвечено»): только H1, без экрана N; My Day сигнала не получает; пауза R18 — 3 дня |
| 2 | L11 | UNSURE на вопросе о вреде не давал экрана N (в L11 только EMERGENCY / URGENT) → при остальных NO «ничего не найдено» | Опрос продолжается; если выше ничего не найдено — экран PM1-H (§9.3), исход PROMPT; My Day — `post_mood_heavy` и строка о враче PM1; пауза R18 — 7 дней |
| 3 | L13 | R18 — 14 дней | По исходу: «ничего не найдено» — 14 дней; «Не сейчас» на вопросе о вреде — 3 дня; «Не уверена» → PM1-H — 7 дней; PM2 — по текущим правилам. Кнопка ER0 доступна всегда |
| 4 | §9.3 | — | Экран PM1-H: PM1 без изменений плюс текстовая ссылка на срочную помощь. PM1 для всех остальных случаев не меняется |
| 5 | §7.2 | — | Исход `NOT_ANSWERED` |

Утверждено Product на этапе разработки; клиническая проверка — перед публичным запуском, разработку не блокирует.

## 1. Изменения v2.1 → v2.2

| # | Источник | Проблема v2.1 | Решение v2.2 |
|---|---|---|---|
| 1 | Ревью | Стоп-правило могло пропустить опасное сочетание (R4: кровотечение → URGENT → боль и возможная беременность не спрашивались) | L3 учитывает уровень, место обращения и таблицу сочетаний (§4). Новый экран EP1 |
| 2 | Ревью | `D_POSTMENOPAUSAL_BLEED` исключал гормональную терапию и гистерэктомию | Эти факторы меняют текст MB1, но **никогда не отменяют** предупреждение (§5.2, §9.3) |
| 3 | Ревью | R7 без внезапной сильной головной боли и неврологических признаков | Добавлены в R7 и R17 |
| 4 | Ревью | R18: вопрос о мыслях причинить вред стоял последним, вопреки L2 | Стоит первым, с нормализующим пояснением (§9.1, Q-MH) |
| 5 | Ревью | ER0 давал 3 касания, а не 2 | Пункты ER0 срабатывают одним тапом; кнопка вызова постоянно на экране (§8) |
| 6 | Ревью | Офлайн-защита только для EMERGENCY | Офлайн работают все уровни, PM2 и ER0 (D6, §10) |
| 7 | Ревью + Annet | 5 маршрутов без привязок, M2 не подтверждён | Привязки к реальным пунктам weighted-v3; M2 подтверждён в источнике (§2.2) |
| 8 | Ревью | R18 повторялся бы при ежедневной отметке настроения | Паузы маршрутов (L13); R18 — не чаще раза в 14 дней |
| 9 | Ревью | Вычисляемые факты не знали о неполной истории календаря | Состояние UNKNOWN и критерии полноты (§5.7) |
| 10 | Ревью | `F_MOVEMENTS_ESTABLISHED` закрывался навсегда ранним «Нет» | Постоянно хранится только YES; NO истекает через 14 дней (§5.3) |
| 11 | Ревью | Выделения после полового контакта скрыты за межменструальными | Самостоятельный вход: отметка в записи кровотечения в календаре (§5.2) |
| 12 | Annet: CDC | Нет учащённого сердцебиения, сильной рвоты, истощения, неприятных выделений после родов, отёка руки, температуры при беременности | Добавлено 4 факта, 1 расширен, 2 маршрута, блок QM_SYSTEMIC (§5, §6) |
| 13 | Annet: D5 | Не было планового вопроса о самочувствии после родов | Одна необязательная карточка WB1 примерно через 4 недели после родов (§9.4) |
| 14 | Annet: D3, D10 | — | D3 с маркером `CONDITION_UNDEFINED`; D10 с исключениями для неакушерских признаков (§11) |
| 15 | **Новая находка** (сверка с weighted-v3) | 12 из 20 маршрутов не открываются от одного пункта: порог safety_score ≥ 12 недостижим | Прямой вход по состоянию «заметно» + серверный контракт (D12, §2.1, утверждено). Change request к weighted-v3 |
| 16 | **Новая находка** | В пуле Planning нет C4 (Planning использует T1–T9, у всех safety_w = 0) — решение D1 из v2.1 было невыполнимо | Новый маршрут R28 через T5 (§6) |

---

## 2. Входные сигналы

### 2.1 Правило входа из Check-in (D12 — утверждено Annet 20.10, P0)

**Находка.** В weighted-v3 маршрут открывается, когда `safety_score ≥ 12`, а один пункт даёт не больше `2 × safety_w`. Расчёт по `daily checkin weighted model v3.json` (эпик E28):

| Пункт | Маршрут | safety_w | Максимум от одного пункта | Открывается сам? |
|---|---|---|---|---|
| M3 | MENARCHE_HEADACHE | 2 | 4 | **Нет** |
| M4 | MENARCHE_DIZZINESS | 7 | 14 | Да |
| M5 | MENARCHE_BLEEDING | 6 | 12 | Да |
| C4 | CYCLE_BLEEDING | 3 | 6 | **Нет** |
| P6 | PREGNANCY_SWELLING | 7 | 14 (21 при многоплодной) | Да |
| P7 | PREGNANCY_BREATHLESSNESS | 8 | 16 | Да |
| P8 | PREGNANCY_HEADACHE | 8 | 16 | Да |
| P9 | PREGNANCY_EPIGASTRIC | 7 | 14 | Да |
| P16 | PREGNANCY_GENERAL_DISCOMFORT | 3 | 6 | **Нет** |
| PP4 | POSTPARTUM_PAIN | 4 | 8 | **Нет** |
| PP5 | POSTPARTUM_BLEEDING | 8 | 16 | Да |
| PP6 | POSTPARTUM_WOUND | 6 | 12 | Да |
| PP7 | POSTPARTUM_BREAST_DISCOMFORT | 5 | 10 | **Нет** |
| PP8 | POSTPARTUM_PELVIC_DISCOMFORT | 2 | 4 | **Нет** |
| PP14 | POSTPARTUM_GENERAL_DISCOMFORT | 2 | 4 | **Нет** |
| PM2 / PM9 | PERIMENOPAUSE_HOT_FLASHES | 3 / 2 | 6 / 4 | **Нет** |
| PM3 | PERIMENOPAUSE_UNUSUAL_CHANGE | 4 | 8 | **Нет** |
| PM12 | PERIMENOPAUSE_GENERAL_DISCOMFORT | 2 | 4 | **Нет** |
| MN3 / MN13 | MENOPAUSE_HOT_FLASHES | 2 / 2 | 5.2 / 4 | **Нет** |
| MN8 | MENOPAUSE_UROGENITAL | 3 | 9.6 | **Нет** |

Без изменения правила входа эти маршруты существуют только на бумаге: женщина отмечает «заметно», а вопросы безопасности не появляются.

**Правило v2.2 (D12, утверждено):** пункт Check-in, привязанный к маршруту, **в состоянии 2 («заметно» / «плохо») открывает свой маршрут напрямую**, минуя `safety_score ≥ 12`. Накопительный порог сохраняется как второй, дополнительный вход. Повторное открытие ограничено паузами L13, которые не действуют при новом симптоме, ухудшении или экстренном признаке. Изменение вносится change request к MOMNA-641 / MOMNA-652 (weighted-v3 → v3.1).

**Серверный контракт (обязателен для всех 28 маршрутов):**
`inputEvent → itemCode/state → applicableRoutes → factsToEvaluate → disposition → screen → action`

- `inputEvent` — сохранённый ответ Check-in, запись календаря, карточка WB1, касание в ER0.
- Одно событие может открыть несколько маршрутов; факты, уже отвеченные в сессии, переиспользуются (L5).
- Решение о следующем вопросе и завершении маршрута принимает сервер. Клиент только исполняет его; исключение — офлайн-механизм §10.
- Ни один маршрут не считается готовым без подтверждённого рабочего входа и теста реального действия пользовательницы.

### 2.2 Привязки маршрутов к пунктам Check-in (решение Annet от 20.10: существующий Check-in, без новых медицинских блоков)

Все коды пунктов сверены с weighted-v3. Вход: состояние 2, если не указано иное.

| Маршрут | Вход | Пункт weighted-v3 | Статус |
|---|---|---|---|
| R1–R9, R11–R16, R19–R23 | Legacy-привязки | M3, M4, M5, C4, P6, P7, P8, P9, P16, PP4–PP8, PP14, PM2, PM9, PM3, PM12, MN3, MN13, MN8 | Существуют |
| R10 PREGNANCY_PRETERM_LABOR | P5 «Спина / таз / тяжесть» = «заметная»; P19 = 2; ER0 | P5 (sw = 0), P19 | P5 существует; **P19 — новый пункт (D13, утверждено)** |
| R17 POSTPARTUM_PREECLAMPSIA | PP19 = «заметные»; ER0 | PP19 | **PP19 — новый пункт (D13, утверждено)**: в пуле послеродового периода не было головной боли, зрения и отёков |
| R18 POSTPARTUM_MENTAL_HEALTH | PP2 «Самочувствие эмоционально» = «тяжело эмоционально» (состояние 1, см. примечание); PP18 «Эмоциональная перегрузка» = «выраженная»; карточка WB1 = «Последнее время тяжело» | PP2, PP18 | Существуют |
| R24 MENARCHE_SEVERE_PELVIC_PAIN | **M2 «Боль или спазмы» = «заметная, мешает»** | M2 (sw = 0) | **M2 подтверждён в weighted-v3** |
| R25 GLOBAL_ALLERGIC_EMERGENCY | ER0: «Отёк лица, губ, языка или горла» | — | Один общий механизм, без пунктов в 7 пулах |
| R27 PREGNANCY_NAUSEA (новый, CDC) | P3 «Тошнота» = «заметная» | P3 (sw = 0) | Существует |
| R28 PLANNING_BODY_CHANGE (новый) | T5 «Самочувствие тела» = «заметное отклонение» | T5 (sw = 0) | Существует |

**Примечание к PP2.** Его состояния — «нормально / тяжело эмоционально / не могу понять пока». Здесь «тяжело» стоит на позиции 1, а позиция 2 означает неопределённость. Входом служит конкретное значение «тяжело эмоционально», а не номер состояния. Шкалу стоит выровнять в MOMNA-641.

**Новые пункты пула (D13, утверждено Annet 20.10; вносятся change request к MOMNA-641, weighted-v3 → v3.1):**
| Код | Период | RU | US-EN | Состояния RU / EN |
|---|---|---|---|---|
| P19 | PREGNANCY, утро и вечер | Напряжение или схватки в животе | Tightening or contractions | нет / иногда / регулярно или усиливаются · none / sometimes / regular or getting stronger |
| PP19 | POSTPARTUM_YEAR_ONE, утро | Головная боль, зрение или отёки | Headache, vision or swelling | нет / лёгкие / заметные · none / mild / noticeable |

### 2.3 Остальные входы
| Вход | Что открывает |
|---|---|
| ER0 «Мне сейчас плохо» (§8) | Экран помощи или короткий маршрут |
| Запись кровотечения в календаре | Вычисляемые факты `D_*`; отметка «после полового контакта» задаёт `F_BLEED_POSTCOITAL` |
| WB1 — одна карточка примерно через 4 недели после родов (§9.4) | R18 при ответе «Последнее время тяжело» |
| Накопительный порог `safety_score ≥ 12` | Маршрут пункта с наибольшим вкладом (как в weighted-v3) |

---

## 3. Правила нагрузки

| Код | Правило |
|---|---|
| **L1** | Без входного сигнала (§2) вопросов безопасности нет. |
| **L2** | Порядок: EMERGENCY → SAFETY_ASSESSMENT → URGENT → PROMPT → ROUTINE. |
| **L3** | **Стоп-правило v2.2.** Первый YES уровня EMERGENCY сразу открывает E/AG1, опрос прекращается. В остальных случаях опрос продолжается, пока хотя бы один оставшийся вопрос может изменить: (а) уровень; (б) экран или место обращения; (в) срабатывание сочетания из §4. Вычисляемые факты оцениваются всегда. |
| **L4** | Цель — до 5 вопросов на маршрут. Превышение допускается только для вопросов EMERGENCY / SAFETY или участвующих в сочетаниях §4 и отмечено в §6. Однородную группу одного уровня можно показать одним экраном-чеклистом QM (D14). |
| **L5** | Один факт — один вопрос за сессию; ответ переиспользуется всеми маршрутами сессии. Между сессиями YES сохраняется до изменения пользовательницей, NO и UNSURE заново не используются. Исключения со своим сроком: §5.3 (шевеления). |
| **L6** | Известное из профиля и календаря не спрашивается. При UNKNOWN (§5.7) задаётся вопрос-заменитель — только если маршрут в нём нуждается. |
| **L7** | CONTEXT спрашивается лениво — только когда он может изменить решение. |
| **L8** | Объединять признаки в один вопрос можно, только если внутри одного периода у них одинаковый исход. |
| **L9** | Все NO → без экрана «всё хорошо», только подсказка H1. |
| **L10** | «Не сейчас» завершает маршрут: оставшиеся факты получают ABSENT, найденное сохраняется. **v2.3, R18:** «Не сейчас» на `F_HARM_THOUGHTS` — исход `NOT_ANSWERED` («не отвечено»): только H1, экран N не показывается, My Day сигнала не получает, пауза R18 — 3 дня. |
| **L11** | UNSURE не прерывает поток. Экран N показывается один раз в конце, если UNSURE стоит на факте уровня EMERGENCY / URGENT и выше ничего не найдено. **v2.3, R18:** UNSURE на `F_HARM_THOUGHTS` — опрос продолжается; если выше ничего не найдено (PM2 / URGENT / EMERGENCY), в конце экран PM1-H (§9.3), исход PROMPT, экран N не показывается; My Day — сигнал `post_mood_heavy` и строка о враче PM1; пауза R18 — 7 дней. |
| **L12** | Один тап на ответ; изменить можно кнопкой «Назад». |
| **L13** | **Паузы маршрутов (D12, утверждено).** После завершённого маршрута без YES тот же маршрут не открывается повторно тем же пунктом в течение паузы. Острые маршруты (беременность, послеродовые, кровотечение, R1–R3, R28) — 24 ч. R10 и R27 — 72 ч (R27: заметная тошнота в первом триместре бывает почти ежедневно; утверждено Annet 20.10). Хронические (R19, R22, R23, R24) — 7 дней. R18 — по исходу (v2.3): «ничего не найдено» — 14 дней; «Не сейчас» на `F_HARM_THOUGHTS` — 3 дня; «Не уверена» на `F_HARM_THOUGHTS` → PM1-H — 7 дней; PM2 — по текущим правилам. **Пауза не действует:** (а) при новом симптоме — другой пункт с привязкой к маршруту перешёл в состояние 2; (б) при ухудшении — тот же пункт после паузы опускался ниже 2 и снова стал 2, либо пользовательница отмечает, что стало хуже; (в) при любом экстренном признаке и в ER0. Сохранённые YES паузой не блокируются. Сроки утверждает клиницист. |

---

## 4. Опасные сочетания (входят в стоп-правило L3)

| Код | Условие | Исход | Экран |
|---|---|---|---|
| `COMBO_PREG_PAIN` | `F_SEVERE_ABDO_PELVIC_PAIN` = YES **и** беременность возможна (`F_PREGNANCY_POSSIBLE` = YES / UNSURE, или период PLANNING) | URGENT | EP1 |
| `COMBO_PREG_PAIN_SHOCK` | `COMBO_PREG_PAIN` **и** (`F_PRESYNCOPE` = YES или `F_FAINTED` = YES) | EMERGENCY | E |
| `COMBO_BLEED_SHOCK` | Сильное кровотечение (`F_PAD_SOAK_1H` = YES или `F_BLEED_SUDDEN_INCREASE` = YES) **и** `F_PRESYNCOPE` = YES | EMERGENCY (во всех периодах) | E |
| `COMBO_PP_INFECTION` | Послеродовой период: `F_FEVER_38` = YES **и** (`F_FOUL_DISCHARGE`, `F_WOUND_PROBLEM` или `F_RAPID_WORSENING` = YES) | URGENT; кандидат в EMERGENCY — решает клиницист | U |
| `COMBO_AIRWAY` | `F_BREATHING_SEVERE` = YES **и** признак отёка (`F_LIP_FACE_SWELLING` или вход из R25) | EMERGENCY | AG1 вместо общего E |
| `COMBO_PE_BP` | Послеродовой период или беременность: гипертензивный признак (`F_SEVERE_HEADACHE`, `F_VISION_CHANGE`, `F_UPPER_ABDO_PAIN`) **и** давление в зоне EMERGENCY (BP1) | EMERGENCY | E |

**Следствие для R4, R20, R28:** после YES на сильное кровотечение (URGENT) вопросы о сильной боли и предобморочном состоянии всё равно задаются, потому что могут сработать `COMBO_PREG_PAIN` или `COMBO_BLEED_SHOCK`. Вопрос о возможной беременности задаётся только после YES на боль (в PLANNING не задаётся).

Все сочетания требуют клинического утверждения. Пока условие не утверждено, исход маркируется по D3.

---

## 5. Каталог канонических фактов

Каждая строка — один экран Q (§9.1), если не указано иное. «Заменяет» — полная трассировка кодов v2.0. Хранить: `factCode`, исходный `symptomCode` v2.0, `routeCode`, `stage`, ответ, timestamp, `sessionId`, `catalogVersion`, `ruleVersion`, `reviewStatus`, `conditionStatus`.

### 5.1 Глобальные признаки
| factCode | RU | US-EN | Исход | Заменяет (v2.0) |
|---|---|---|---|---|
| `F_BREATHING_SEVERE` | Вам сейчас очень трудно дышать? | Are you having severe trouble breathing right now? | EMERGENCY | MENARCHE_DIZZINESS__SEVERE_BREATHLESSNESS, MENARCHE_BLEEDING__BREATHLESS_WITH_BLEED, CYCLE_BLEEDING__SEVERE_BREATHLESSNESS, PREGNANCY_BREATHLESSNESS__STRUGGLING_TO_BREATHE, POSTPARTUM_PAIN__BREATHLESSNESS, POSTPARTUM_GENERAL_DISCOMFORT__SEVERE_BREATHLESSNESS, PERIMENOPAUSE_HOT_FLASHES__SEVERE_BREATHLESSNESS, PERIMENOPAUSE_GENERAL_DISCOMFORT__SEVERE_BREATHLESSNESS, MENOPAUSE_HOT_FLASHES__SEVERE_BREATHLESSNESS, PP_PE_BREATHING |
| `F_CHEST_PAIN` | Есть боль в груди? | Do you have chest pain? | EMERGENCY | CYCLE_BLEEDING__CHEST_PAIN, PREGNANCY_BREATHLESSNESS__CHEST_PAIN, POSTPARTUM_PAIN__CHEST_PAIN, POSTPARTUM_GENERAL_DISCOMFORT__CHEST_PAIN, PERIMENOPAUSE_HOT_FLASHES__CHEST_PAIN, PERIMENOPAUSE_GENERAL_DISCOMFORT__CHEST_PAIN, MENOPAUSE_HOT_FLASHES__CHEST_PAIN |
| `F_FAINTED` | Вы теряли сознание? | Have you fainted? | EMERGENCY; относится к текущему эпизоду | MENARCHE_DIZZINESS__FAINTING, MENARCHE_BLEEDING__FAINT_WITH_BLEED, CYCLE_BLEEDING__FAINTING, PREGNANCY_BREATHLESSNESS__FAINTING, PREGNANCY_GENERAL_DISCOMFORT__FAINTING, POSTPARTUM_BLEEDING__FAINTING, POSTPARTUM_GENERAL_DISCOMFORT__FAINTING, PERIMENOPAUSE_HOT_FLASHES__FAINTING, PERIMENOPAUSE_GENERAL_DISCOMFORT__FAINTING, MENOPAUSE_HOT_FLASHES__FAINTING |
| `F_SEIZURE` | Был приступ судорог? | Have you had a seizure? | EMERGENCY | POSTPARTUM_GENERAL_DISCOMFORT__SEIZURES |
| `F_NEURO_DEFICIT` | Появились внезапная слабость руки, ноги или одной стороны лица, или спутанность сознания? | Do you have sudden new weakness in an arm, leg, or one side of your face, or sudden confusion? | EMERGENCY | MENARCHE_HEADACHE__NEW_WEAKNESS, MENARCHE_HEADACHE__NEW_CONFUSION |
| `F_THUNDERCLAP_HEADACHE` | Головная боль началась внезапно и сразу стала очень сильной? | Did your headache start suddenly and become extremely severe right away? | EMERGENCY | MENARCHE_HEADACHE__SUDDEN_SEVERE_HEADACHE |
| `F_AIRWAY_SWELLING` | Появились внезапный отёк языка или горла, трудности с дыханием или глотанием? | Do you have sudden tongue or throat swelling, or trouble breathing or swallowing? | EMERGENCY → AG1 | ALLERGY_AIRWAY_SWELLING |
| `F_LIP_FACE_SWELLING` | Появился внезапный отёк губ или лица? | Do you have sudden swelling of your lips or face? | URGENT → U (также при беременности, D10) · D3 | ALLERGY_LIP_SWELLING |
| `F_HARM_THOUGHTS` | Послеродовой период: Были мысли причинить вред себе или ребёнку? · Другие периоды: Были мысли причинить вред себе или другому человеку? | Postpartum: Have you had thoughts of harming yourself or your baby? · Other stages: Have you had thoughts of harming yourself or someone else? | SAFETY_ASSESSMENT → PM2 | POSTPARTUM_GENERAL_DISCOMFORT__SELF_HARM_THOUGHTS, POSTPARTUM_GENERAL_DISCOMFORT__BABY_HARM_THOUGHTS, PP_MH_SELF_HARM |
| *(не вопрос)* | — | — | Клинический протокол PM2 (§9.3) | POSTPARTUM_GENERAL_DISCOMFORT__IMMEDIATE_INTENT |
| `F_PRESYNCOPE` | Вы чувствуете, что вот-вот потеряете сознание, или не можете стоять? | Do you feel close to fainting, or are you unable to stand? | URGENT; EMERGENCY в R12 и в сочетаниях §4 | MENARCHE_DIZZINESS__CANNOT_STAND, POSTPARTUM_BLEEDING__SEVERE_WEAKNESS |
| `F_RAPID_WORSENING` | Состояние быстро ухудшается? | Are you getting worse quickly? | URGENT · D3 | POSTPARTUM_WOUND__GENERAL_DETERIORATION, POSTPARTUM_BREAST_DISCOMFORT__RAPID_WORSENING, POSTPARTUM_PELVIC_DISCOMFORT__RAPID_WORSENING, MENOPAUSE_UROGENITAL__SYSTEMIC_WORSENING |
| `F_FEVER_38` | Температура 38 °C или выше? | Is your temperature 38°C (100.4°F) or higher? | URGENT; в беременности → OB1 (D2, D3, CDC) | POSTPARTUM_WOUND__FEVER_38, POSTPARTUM_BREAST_DISCOMFORT__FEVER_38, POSTPARTUM_PELVIC_DISCOMFORT__FEVER_38, MENOPAUSE_UROGENITAL__FEVER_38 |
| `F_SEVERE_ABDO_PELVIC_PAIN` | Есть внезапная или очень сильная боль в животе или в области таза? | Do you have sudden or very severe pain in your abdomen or pelvis? | URGENT; см. сочетания §4 | CYCLE_BLEEDING__SEVERE_PELVIC_PAIN, CYCLE_BLEEDING__ONE_SIDED_PAIN (объединён, L8: тот же исход в ADULT), PREGNANCY_GENERAL_DISCOMFORT__SEVERE_ABDOMINAL_PAIN, POSTPARTUM_PAIN__SEVERE_ABDOMINAL_PAIN, POSTPARTUM_PELVIC_DISCOMFORT__SEVERE_PELVIC_PAIN, PERIMENOPAUSE_UNUSUAL_CHANGE__SEVERE_PELVIC_PAIN, MENOPAUSE_UROGENITAL__SEVERE_PELVIC_PAIN |
| `F_SUDDEN_SEVERE_PAIN` | Появилась внезапная очень сильная боль? | Do you have sudden, very severe pain? | URGENT · D3 | PERIMENOPAUSE_GENERAL_DISCOMFORT__SUDDEN_SEVERE_PAIN |
| `F_URINARY_RETENTION` | Не получается помочиться, хотя есть позыв? | Are you unable to pass urine even though you need to? | URGENT | POSTPARTUM_PELVIC_DISCOMFORT__URINARY_RETENTION, MENOPAUSE_UROGENITAL__URINARY_RETENTION |
| `F_HEART_RACING` **(новый, CDC)** | Сердце внезапно бьётся очень часто или неровно? | Is your heart suddenly racing or beating irregularly? | URGENT · D3; вместе с болью в груди, одышкой или обмороком исход EMERGENCY даёт соответствующий факт | — |
| `F_SEVERE_VOMITING` **(новый, CDC)** | Сильная рвота, и не получается удержать воду? | Are you vomiting severely and unable to keep fluids down? | URGENT · D3 | — |
| `F_EXTREME_WEAKNESS` **(новый, CDC)** | Появились необычная сильная слабость или истощение, каких раньше не было? | Do you have unusual, severe weakness or exhaustion unlike anything before? | URGENT · D3 (риск ложных срабатываний после родов — клиническая проверка) | — |

### 5.2 Кровотечение и цикл
| factCode | RU | US-EN | Исход | Заменяет (v2.0) |
|---|---|---|---|---|
| `F_PAD_SOAK_1H` | Прокладка или тампон полностью пропитывается за час или быстрее? | Are you soaking through a pad or tampon in an hour or less? | URGENT; в послеродовом периоде EMERGENCY в окне после родов, утверждённом клиницистом (вне окна URGENT); см. `COMBO_BLEED_SHOCK` | MENARCHE_BLEEDING__PAD_SOAK_HOURLY, MENARCHE_BLEEDING__PAD_SOAK_REPEAT (поглощён, D9), CYCLE_BLEEDING__HEAVY_PAD_HOURLY, POSTPARTUM_BLEEDING__PAD_SOAK_FAST, PERIMENOPAUSE_UNUSUAL_CHANGE__PAD_SOAK_FAST |
| `F_BLEED_SUDDEN_INCREASE` | Кровотечение внезапно стало намного сильнее? | Has your bleeding suddenly become much heavier? | EMERGENCY | POSTPARTUM_BLEEDING__BLEED_SUDDEN_INCREASE |
| `F_LARGE_CLOTS` | Выделяются крупные сгустки крови? | Are you passing large blood clots? | URGENT | POSTPARTUM_BLEEDING__LARGE_CLOTS |
| `F_BLEED_HEAVIER_THAN_USUAL` | Кровотечение гораздо обильнее обычного? | Is the bleeding much heavier than usual? | URGENT · D3 | PERIMENOPAUSE_UNUSUAL_CHANGE__HEAVY_BLEED |
| `F_BLEED_POSTCOITAL` | Отметка в записи кровотечения в календаре (самостоятельный вход). Вопрос задаётся только при `D_INTERMENSTRUAL` = YES и отсутствии отметки: «Замечали ли вы кровянистые выделения после полового контакта?» | Calendar bleeding-log tag (standalone entry). Fallback: Have you noticed bleeding after sex? | PROMPT | BLEED_POSTCOITAL |
| `F_PREGNANCY_POSSIBLE` | Беременность сейчас возможна? | Could you be pregnant now? | CONTEXT. Задаётся только после YES на сильную боль. В PLANNING не задаётся, считается YES | CYCLE_BLEEDING__PREGNANCY_POSSIBLE, PERIMENOPAUSE_UNUSUAL_CHANGE__PREGNANCY_POSSIBLE |
| `D_BLEED_GT7` | Календарь (§5.7). Заменитель: Кровотечение продолжается дольше 7 дней? | Calendar. Fallback: Has the bleeding lasted more than 7 days? | PROMPT | MENARCHE_BLEEDING__BLEED_GT7_DAYS, CYCLE_BLEEDING__PROLONGED_BLEED, BLEED_PROLONGED |
| `D_INTERMENSTRUAL` | Календарь (§5.7). Заменитель: Бывают ли у вас кровянистые выделения между месячными? | Calendar. Fallback: Do you have bleeding or spotting between periods? | PROMPT при повторяемости (порог — клиницист) | BLEED_INTERMENSTRUAL |
| `D_12M_NO_PERIOD` | Календарь (§5.7). Заменитель: Перед этим было не менее 12 месяцев без естественной менструации? | Calendar. Fallback: Had you gone at least 12 months without a natural period? | CONTEXT. При гормональной контрацепции или неполной истории — UNKNOWN, а не NO | PERIMENOPAUSE_UNUSUAL_CHANGE__TWELVE_MONTHS_NO_PERIOD |
| `D_POSTMENOPAUSAL_BLEED` | **YES**, если записано любое кровотечение или мажущие выделения **и** (период MENOPAUSE или `D_12M_NO_PERIOD` = YES). Гормональная терапия, гистерэктомия и любые другие факторы **не отменяют** предупреждение; они только выбирают вариант текста MB1 (§9.3). Если беременность возможна, дополнительно применяется маршрут кровотечения. Заменитель: После менопаузы были любые кровянистые выделения? | Derived (same rule). Fallback: Have you had any bleeding or spotting after menopause? | PROMPT → MB1 | PERIMENOPAUSE_UNUSUAL_CHANGE__BLEED_AFTER_GAP, MENOPAUSE_UROGENITAL__POSTMENOPAUSAL_BLEED |

### 5.3 Беременность и гипертензивные признаки
| factCode | RU | US-EN | Исход | Заменяет (v2.0) |
|---|---|---|---|---|
| `F_SEVERE_HEADACHE` | Есть сильная или не проходящая головная боль? | Do you have a severe or persistent headache? | URGENT; в беременности → OB1 | PREGNANCY_SWELLING__SEVERE_HEADACHE, PREGNANCY_HEADACHE__HEADACHE_SEVERE, PREGNANCY_HEADACHE__HEADACHE_PERSISTENT, PREGNANCY_EPIGASTRIC__HEADACHE_WITH_PAIN, PP_PE_HEADACHE |
| `F_VISION_CHANGE` | Появилось новое нарушение зрения — вспышки, пятна или затуманивание? | Do you have a new change in vision, such as flashes, spots, or blurring? | URGENT; в беременности → OB1 | MENARCHE_HEADACHE__VISION_CHANGE, PREGNANCY_SWELLING__VISION_CHANGE, PREGNANCY_HEADACHE__VISION_CHANGE, PREGNANCY_EPIGASTRIC__VISION_WITH_PAIN, PP_PE_VISION |
| `F_UPPER_ABDO_PAIN` | Есть сильная или не проходящая боль вверху живота или под рёбрами, особенно справа? | Do you have severe or persistent pain in your upper abdomen or under your ribs, especially on the right? | URGENT; в беременности → OB1 | PREGNANCY_SWELLING__RIGHT_UPPER_ABDOMEN, PREGNANCY_HEADACHE__RIB_PAIN, PREGNANCY_EPIGASTRIC__RIGHT_RIB_PAIN, PREGNANCY_EPIGASTRIC__SEVERE_UPPER_PAIN, PREGNANCY_EPIGASTRIC__PERSISTENT_UPPER_PAIN, PP_PE_UPPER_ABDOMEN |
| `F_SUDDEN_SWELLING` | Лицо или кисти рук внезапно заметно отекли? | Have your face or hands suddenly become noticeably swollen? | URGENT → OB1 | PREGNANCY_SWELLING__SUDDEN_FACE_SWELL, PREGNANCY_SWELLING__SUDDEN_HAND_SWELL |
| `F_BREATHLESS_SUDDEN_OR_REST` | Одышка появилась внезапно или вам трудно дышать даже в покое? | Did your shortness of breath start suddenly, or are you short of breath even at rest? | EMERGENCY (объединены по L8: оба EMERGENCY в беременности) | PREGNANCY_BREATHLESSNESS__SUDDEN_BREATHLESSNESS, PREGNANCY_BREATHLESSNESS__BREATHLESS_AT_REST |
| `F_PREG_VAGINAL_BLEED` | Есть кровотечение из влагалища? | Are you having vaginal bleeding? | URGENT → OB1 | PREGNANCY_GENERAL_DISCOMFORT__VAGINAL_BLEED |
| `F_FLUID_LEAK` | Было внезапное излитие или постоянное подтекание жидкости из влагалища? | Have you had a gush or a continuous trickle of fluid from the vagina? | URGENT → OB1, на любом сроке | PREGNANCY_GENERAL_DISCOMFORT__FLUID_LEAK, PREG_MEMBRANE_RUPTURE |
| `F_MOVEMENTS_ESTABLISHED` | Вы уже регулярно ощущаете шевеления ребёнка? | Have you already been feeling your baby move regularly? | CONTEXT. Задаётся с недели, утверждённой клиницистом. **YES хранится до конца беременности; NO истекает через 14 дней** и задаётся снова при следующем входе | PREGNANCY_GENERAL_DISCOMFORT__MOVEMENTS_PREVIOUSLY_FELT |
| `F_REDUCED_MOVEMENTS` | Шевелений стало заметно меньше, чем обычно для вашего ребёнка? | Has your baby been moving noticeably less than usual? | URGENT → OB1; только если `F_MOVEMENTS_ESTABLISHED` = YES | PREGNANCY_GENERAL_DISCOMFORT__REDUCED_MOVEMENTS |
| `F_CONTRACTIONS_PRETERM` | Появились регулярные схватки или ритмичное напряжение живота? | Are you having regular contractions or rhythmic tightening? | URGENT → OB1. Только при `D_GESTATIONAL_WEEK` < 37; если срок неизвестен — задаётся и считается < 37 | PREG_PRETERM_CONTRACTIONS |
| `F_PELVIC_PRESSURE_PRETERM` | Появились давление внизу таза или новая постоянная тупая боль в пояснице? | Do you have new pelvic pressure or a constant dull low backache? | URGENT → OB1 · D3; то же условие по сроку | PREG_PRETERM_PRESSURE |
| `D_GESTATIONAL_WEEK` | Профиль беременности | Pregnancy profile | DERIVED | — |

### 5.4 Послеродовой период (первый год)
| factCode | RU | US-EN | Исход | Заменяет (v2.0) |
|---|---|---|---|---|
| `F_ONE_LIMB` **(расширен, CDC)** | Одна рука или нога заметно отекла, болит, покраснела или болезненна при касании? | Is one arm or leg noticeably swollen, painful, red or tender? | URGENT | POSTPARTUM_PAIN__ONE_LEG_SWELL, POSTPARTUM_PAIN__ONE_LEG_PAIN |
| `F_WOUND_PROBLEM` | Из шва или раны выделяется гной, шов разошёлся или боль в ране быстро усиливается? | Is there pus from your wound or incision, has it opened, or is the wound pain getting worse quickly? | URGENT | POSTPARTUM_WOUND__WOUND_PUS, POSTPARTUM_WOUND__WOUND_OPEN, POSTPARTUM_WOUND__WOUND_PAIN_WORSE |
| `F_FOUL_DISCHARGE` **(новый, CDC)** | Появились выделения из влагалища с неприятным запахом? | Do you have vaginal discharge with an unpleasant smell? | URGENT · D3; см. `COMBO_PP_INFECTION` | — |
| `F_BREAST_RED_AREA` | Есть ограниченный болезненный красный участок на груди? | Is there a localized painful red area on your breast? | PROMPT | POSTPARTUM_BREAST_DISCOMFORT__BREAST_REDNESS |
| `F_CHILLS` | Есть озноб? | Do you have chills? | PROMPT; только если `F_FEVER_38` = NO / UNSURE | POSTPARTUM_BREAST_DISCOMFORT__CHILLS |
| `F_BP_MEASURED` | Вы измеряли артериальное давление сегодня? | Have you measured your blood pressure today? | INPUT_GATE → BP1 | PP_PE_BP_AVAILABLE |
| `F_LOW_MOOD_2W` | В последние две недели вы часто чувствовали подавленность или сильную тревогу? | During the past two weeks, have you often felt low or very anxious? | PROMPT → PM1 | PP_MH_PERSISTENT_LOW_MOOD |
| `F_ANHEDONIA_2W` | За последние две недели вы часто теряли интерес к тому, что обычно приносило радость? | During the past two weeks, have you often lost interest in things you usually enjoy? | PROMPT → PM1 | PP_MH_LOSS_OF_INTEREST |
| `F_BONDING_WORRY` | Вам трудно почувствовать связь с ребёнком, и это вас беспокоит? | Are you having difficulty feeling connected to your baby, and does this worry you? | PROMPT → PM1 | PP_MH_BONDING_DIFFICULTY |
| `D_TIME_SINCE_BIRTH` | Дата родов из профиля. Если её нет, один раз запросить дату (поле даты, не вопрос безопасности) | Birth date from profile | DERIVED | POSTPARTUM_BLEEDING__TIME_SINCE_BIRTH |

### 5.5 Менархе и менопауза
| factCode | RU | US-EN | Исход | Заменяет (v2.0) |
|---|---|---|---|---|
| `F_DIZZINESS_PERSISTENT` | Головокружение сохраняется или повторяется? | Is your dizziness continuing or coming back? | PROMPT | MENARCHE_DIZZINESS__PERSISTENT_DIZZINESS |
| `F_PERIOD_PAIN_LIMITING` | Из-за боли вы пропускаете школу или не можете заниматься обычными делами, или она не проходит после привычных способов облегчения? | Does the pain make you miss school or stop your usual activities, or does it not improve with your usual relief measures? | PROMPT → MP1 | M_PAIN_FUNCTION_LIMIT, M_PAIN_PERSISTENT |
| `F_GSM_SYMPTOMS` | Беспокоят ли сухость, раздражение или боль во влагалище, в том числе при близости? | Are you bothered by vaginal dryness, irritation or pain, including during sex? | ROUTINE → GS1 | MN_GSM_DRYNESS, MN_GSM_PAINFUL_SEX |

### 5.6 Не задаются как вопрос
| Код v2.0 | Причина |
|---|---|
| PERIMENOPAUSE_HOT_FLASHES__HOT_FLASH | Роль ENTRY: пользовательница сама отметила прилив (D9) |
| MENOPAUSE_HOT_FLASHES__HOT_FLASH | То же |

### 5.7 Вычисляемые факты: полнота и исправление
| Правило | Содержание |
|---|---|
| Значения | YES / NO / **UNKNOWN**. UNKNOWN никогда не трактуется как NO |
| UNKNOWN | История неполная: пропуски записей дольше порога (клиницист / продукт), открытый незакрытый эпизод кровотечения, гормональная контрацепция или терапия, влияющая на цикл, смена устройства или период без использования приложения |
| При UNKNOWN | Вопрос-заменитель задаётся, только если маршрут в нём нуждается (L6). В остальных случаях факт остаётся UNKNOWN |
| Исправление | Пользовательница может исправить исходные данные в календаре; сервер пересчитывает факт и решения. История изменений хранится |
| 12 месяцев без менструации (`D_12M_NO_PERIOD`) | NO возможен только при непрерывной истории без пропусков. Отсутствие записей ≠ отсутствие менструации |

---

## 6. Маршруты (28)

Вход — по §2. В скобках: E = EMERGENCY, S = SAFETY, U = URGENT, P = PROMPT, R = ROUTINE. ⚠ — превышение цели в 5 вопросов, разрешённое по L4.

| # | routeCode | Вход | Вопросы по порядку | После YES / вычисляется / сочетания | Пауза |
|---|---|---|---|---|---|
| R1 | MENARCHE_HEADACHE | M3 = 2 | F_THUNDERCLAP_HEADACHE (E), F_NEURO_DEFICIT (E), F_VISION_CHANGE (U) | — | 24 ч |
| R2 | MENARCHE_DIZZINESS | M4 = 2 | F_FAINTED (E), F_BREATHING_SEVERE (E), F_PRESYNCOPE (U), F_DIZZINESS_PERSISTENT (P) | — | 24 ч |
| R3 | MENARCHE_BLEEDING | M5 = 2 | F_FAINTED (E), F_BREATHING_SEVERE (E), F_PAD_SOAK_1H (U), F_PRESYNCOPE (U) | D_BLEED_GT7; `COMBO_BLEED_SHOCK` | 24 ч |
| R4 | CYCLE_BLEEDING | C4 = 2 (ADULT) | F_CHEST_PAIN (E), F_BREATHING_SEVERE (E), F_FAINTED (E), F_PAD_SOAK_1H (U), F_SEVERE_ABDO_PELVIC_PAIN (U), F_PRESYNCOPE (U) ⚠ | После YES на боль: F_PREGNANCY_POSSIBLE. D_BLEED_GT7, D_INTERMENSTRUAL, F_BLEED_POSTCOITAL. `COMBO_PREG_PAIN`, `COMBO_PREG_PAIN_SHOCK`, `COMBO_BLEED_SHOCK` | 24 ч |
| R5 | PREGNANCY_SWELLING | P6 = 2 | F_SUDDEN_SWELLING, F_SEVERE_HEADACHE, F_VISION_CHANGE, F_UPPER_ABDO_PAIN (все U → OB1) | Остановка на первом YES | 24 ч |
| R6 | PREGNANCY_BREATHLESSNESS | P7 = 2 | F_BREATHING_SEVERE (E), F_CHEST_PAIN (E), F_FAINTED (E), F_BREATHLESS_SUDDEN_OR_REST (E), F_HEART_RACING (U) | — | 24 ч |
| R7 | PREGNANCY_HEADACHE | P8 = 2 | **F_THUNDERCLAP_HEADACHE (E), F_NEURO_DEFICIT (E)**, F_SEVERE_HEADACHE (U), F_VISION_CHANGE (U), F_UPPER_ABDO_PAIN (U) | — | 24 ч |
| R8 | PREGNANCY_EPIGASTRIC | P9 = 2 | F_UPPER_ABDO_PAIN, F_SEVERE_HEADACHE, F_VISION_CHANGE (все U → OB1) | — | 24 ч |
| R9 | PREGNANCY_GENERAL_DISCOMFORT | P16 = 2 | F_FAINTED (E), F_PREG_VAGINAL_BLEED (U), F_FLUID_LEAK (U), F_SEVERE_ABDO_PELVIC_PAIN (U), F_REDUCED_MOVEMENTS (U), **QM_SYSTEMIC** (U) ⚠ | F_MOVEMENTS_ESTABLISHED (§5.3) | 24 ч |
| R10 | PREGNANCY_PRETERM_LABOR | P5 = 2; P19 = 2; ER0 | F_FLUID_LEAK (U), F_CONTRACTIONS_PRETERM (U), F_PELVIC_PRESSURE_PRETERM (U) | D_GESTATIONAL_WEEK; при ≥ 37 нед. — только F_FLUID_LEAK | 72 ч |
| R11 | POSTPARTUM_PAIN | PP4 = 2 | F_CHEST_PAIN (E), F_BREATHING_SEVERE (E), F_SEVERE_ABDO_PELVIC_PAIN (U), F_ONE_LIMB (U) | — | 24 ч |
| R12 | POSTPARTUM_BLEEDING | PP5 = 2 | F_FAINTED (E), F_BLEED_SUDDEN_INCREASE (E), F_PAD_SOAK_1H (E в окне), F_PRESYNCOPE (E), F_LARGE_CLOTS (U) | D_TIME_SINCE_BIRTH | 24 ч |
| R13 | POSTPARTUM_WOUND | PP6 = 2 | F_FEVER_38 (U), F_WOUND_PROBLEM (U), F_FOUL_DISCHARGE (U), F_RAPID_WORSENING (U) | `COMBO_PP_INFECTION` | 24 ч |
| R14 | POSTPARTUM_BREAST_DISCOMFORT | PP7 = 2 | F_RAPID_WORSENING (U), F_FEVER_38 (U), F_BREAST_RED_AREA (P) | F_CHILLS при температуре NO / UNSURE | 24 ч |
| R15 | POSTPARTUM_PELVIC_DISCOMFORT | PP8 = 2 | F_URINARY_RETENTION (U), F_SEVERE_ABDO_PELVIC_PAIN (U), F_FEVER_38 (U), F_FOUL_DISCHARGE (U), F_RAPID_WORSENING (U) | `COMBO_PP_INFECTION` | 24 ч |
| R16 | POSTPARTUM_GENERAL_DISCOMFORT | PP14 = 2 | F_BREATHING_SEVERE (E), F_CHEST_PAIN (E), F_FAINTED (E), F_SEIZURE (E), F_HARM_THOUGHTS (S), **QM_SYSTEMIC** (U) ⚠ | — | 24 ч |
| R17 | POSTPARTUM_PREECLAMPSIA | PP19 = 2; ER0 | F_BREATHING_SEVERE (E), **F_THUNDERCLAP_HEADACHE (E), F_NEURO_DEFICIT (E)**, F_SEVERE_HEADACHE (U), F_VISION_CHANGE (U), F_UPPER_ABDO_PAIN (U), F_BP_MEASURED → BP1 ⚠ | `COMBO_PE_BP`; BP1 задаётся и после URGENT | 24 ч |
| R18 | POSTPARTUM_MENTAL_HEALTH | PP2 = «тяжело эмоционально»; PP18 = 2; WB1 = «тяжело» | **F_HARM_THOUGHTS (S) первым**, F_LOW_MOOD_2W (P), F_ANHEDONIA_2W (P), F_BONDING_WORRY (P) | После первого P остальные P не задаются | 14 дней |
| R19 | PERIMENOPAUSE_HOT_FLASHES | PM2 = 2 или PM9 = 2 | F_CHEST_PAIN (E), F_BREATHING_SEVERE (E), F_FAINTED (E) | — | 7 дней |
| R20 | PERIMENOPAUSE_UNUSUAL_CHANGE | PM3 = 2 | F_PAD_SOAK_1H (U), F_BLEED_HEAVIER_THAN_USUAL (U), F_SEVERE_ABDO_PELVIC_PAIN (U), F_PRESYNCOPE (U) | После YES на боль: F_PREGNANCY_POSSIBLE. D_12M_NO_PERIOD, D_POSTMENOPAUSAL_BLEED → MB1, D_BLEED_GT7, D_INTERMENSTRUAL, F_BLEED_POSTCOITAL. Сочетания как в R4 | 24 ч |
| R21 | PERIMENOPAUSE_GENERAL_DISCOMFORT | PM12 = 2 | F_CHEST_PAIN (E), F_BREATHING_SEVERE (E), F_FAINTED (E), F_SUDDEN_SEVERE_PAIN (U) | — | 24 ч |
| R22 | MENOPAUSE_HOT_FLASHES | MN3 = 2 или MN13 = 2 | F_CHEST_PAIN (E), F_BREATHING_SEVERE (E), F_FAINTED (E) | — | 7 дней |
| R23 | MENOPAUSE_UROGENITAL | MN8 = 2 | F_URINARY_RETENTION (U), F_SEVERE_ABDO_PELVIC_PAIN (U), F_FEVER_38 (U), F_RAPID_WORSENING (U) | D_POSTMENOPAUSAL_BLEED → MB1; F_GSM_SYMPTOMS (R) — только если ничего не найдено | 7 дней |
| R24 | MENARCHE_SEVERE_PELVIC_PAIN | **M2 = 2** | F_PERIOD_PAIN_LIMITING (P) | — | 7 дней |
| R25 | GLOBAL_ALLERGIC_EMERGENCY | ER0: «Отёк лица, губ, языка или горла» | F_AIRWAY_SWELLING (E), F_LIP_FACE_SWELLING (U) | — | — |
| R26 | GLOBAL_EMERGENCY_QUICK_CHECK | ER0 (§8) | Экран одного касания | — | — |
| R27 | PREGNANCY_NAUSEA **(новый, CDC)** | P3 = 2 | F_FAINTED (E), F_SEVERE_VOMITING (U → OB1) | — | 72 ч |
| R28 | PLANNING_BODY_CHANGE **(новый)** | T5 = 2 | F_CHEST_PAIN (E), F_BREATHING_SEVERE (E), F_FAINTED (E), F_PAD_SOAK_1H (U), F_SEVERE_ABDO_PELVIC_PAIN (U), F_PRESYNCOPE (U) ⚠ | Беременность считается возможной (D1); `COMBO_PREG_PAIN`, `COMBO_PREG_PAIN_SHOCK`, `COMBO_BLEED_SHOCK` | 24 ч |

**QM_SYSTEMIC** — один экран-чеклист (§9.1): `F_FEVER_38`, `F_HEART_RACING`, `F_SEVERE_VOMITING`, `F_PRESYNCOPE`, `F_EXTREME_WEAKNESS`. Каждый отмеченный пункт — отдельный факт YES. Уже отвеченные в сессии факты не показываются.

**Счёт:** 20 legacy (22 привязки) + 5 из Addendum (R10, R17, R18, R24, R25) + ER0 (R26) + 2 новых (R27, R28) = **28**.

---

## 7. Исходы и маршрутизация экранов

### 7.1 Ответы
| Значение | Смысл |
|---|---|
| `YES` | Подтверждён только этот признак |
| `NO` | Этот признак не отмечен; не означает «риска нет» |
| `UNSURE` | Неопределённость сохраняется |
| `ABSENT` | Не задан или не отвечен |
| `UNKNOWN` | Только для вычисляемых фактов: данных недостаточно (§5.7) |

### 7.2 Исходы по приоритету
| Исход | Смысл | Экран |
|---|---|---|
| `EMERGENCY` | Экстренная помощь сейчас | E; при отёке дыхательных путей AG1 |
| `SAFETY_ASSESSMENT` | Немедленная оценка безопасности; в EMERGENCY только по утверждённому протоколу | PM2 |
| `URGENT` | Оценка без промедления | По §7.3 |
| `PROMPT` | Своевременная запись | P или профильный: MB1, MP1, PM1 |
| `ROUTINE` | Плановое обсуждение | R / GS1 |
| `UNKNOWN` | Данных недостаточно | N (L11) |
| `NOT_REPORTED` | Все заданные признаки NO | Экрана нет, только H1 |
| `NOT_ANSWERED` | v2.3, только R18: вопрос о вреде пропущен («Не сейчас», L10) | Экрана нет, только H1 |

### 7.3 Место обращения при URGENT (D10, утверждено)
| Ситуация | Экран |
|---|---|
| Беременность: гипертензивные признаки (головная боль, зрение, отёки, боль под рёбрами) | OB1 |
| Беременность: кровотечение, подтекание жидкости, снижение шевелений, схватки до 37 недель | OB1 |
| Беременность: сильная боль в животе, температура, рвота без возможности пить, учащённое сердцебиение, сильная слабость | OB1, если нет более высокого исхода |
| Возможная беременность + сильная боль (`COMBO_PREG_PAIN`) | EP1 |
| Отёк губ или лица без нарушения дыхания (в любом периоде) | U, не OB1 |
| Остальные URGENT вне беременности | U |
| Любое EMERGENCY | E / AG1 — всегда в приоритете |

### 7.4 Метки v2.0 → v2.2
| Метка v2.0 | v2.2 |
|---|---|
| `PROMPT`, `PROMPT_EVALUATION` | `PROMPT` |
| `IMMEDIATE_ASSESSMENT` | `SAFETY_ASSESSMENT` |
| `CONDITIONAL_PROMPT` | `PROMPT` + `condition` |
| `ROUTINE_OR_PROMPT` | `ROUTINE` |
| `*_CONTEXT` | Базовый уровень + `condition`; если условие не определено — D3 |
| `CONTEXT`, `INPUT_GATE` | Роли фактов |
| `ATTENTION` | Только визуальный стиль |

---

## 8. ER0 — «Мне сейчас плохо» (все периоды)

**Вход.** Постоянная кнопка на My Day, в Календаре и в шапке Check-in. Работает без авторизации повторно и без сети.
RU **«Мне сейчас плохо»** · EN **“I feel very unwell now”**

**Путь до помощи — 2 касания:** открыть ER0 → коснуться пункта → экран помощи. Либо открыть ER0 → кнопка вызова.

| Элемент | RU | US-EN |
|---|---|---|
| Кнопка вверху экрана (всегда видна) | Позвонить в экстренную службу | Call emergency services |
| Title | Что происходит прямо сейчас? | What's happening right now? |
| Supporting | Выберите то, что подходит больше всего. | Choose what fits best. |
| Нижний вариант | Ничего из этого | None of these |

Кнопка вызова открывает набор только при подтверждённом региональном номере, иначе — инструкцию без вымышленного номера.

**Пункты (каждый — одно касание, сразу ведёт к решению):**
| RU | US-EN | Факт / маршрут |
|---|---|---|
| Очень трудно дышать | Severe trouble breathing | F_BREATHING_SEVERE |
| Боль в груди | Chest pain | F_CHEST_PAIN |
| Теряла сознание | I fainted | F_FAINTED |
| Судороги | Seizure | F_SEIZURE |
| Внезапная слабость руки, ноги или половины лица, или спутанность | Sudden weakness in an arm, leg or one side of the face, or confusion | F_NEURO_DEFICIT |
| Внезапная очень сильная головная боль | Sudden, extremely severe headache | F_THUNDERCLAP_HEADACHE |
| Отёк лица, губ, языка или горла | Swelling of the face, lips, tongue or throat | R25 (1–2 вопроса) |
| Сердце бьётся очень часто или неровно | Heart racing or beating irregularly | F_HEART_RACING |
| Кровотечение: прокладка пропитывается за час или быстрее | Bleeding: soaking a pad in an hour or less | F_PAD_SOAK_1H |
| Мысли причинить вред себе или другому (после родов: «себе или ребёнку») | Thoughts of harming myself or someone else (postpartum: “myself or my baby”) | F_HARM_THOUGHTS |

**Дополнительно по периоду (до 3):**
| Период | RU | US-EN | Факт |
|---|---|---|---|
| PREGNANCY | Кровотечение из влагалища | Vaginal bleeding | F_PREG_VAGINAL_BLEED |
| PREGNANCY | Излилась или подтекает жидкость | Fluid gushing or leaking | F_FLUID_LEAK |
| PREGNANCY (если шевеления установлены) | Ребёнок шевелится заметно меньше | Baby is moving noticeably less | F_REDUCED_MOVEMENTS |
| POSTPARTUM | Кровотечение внезапно усилилось | Bleeding suddenly got much heavier | F_BLEED_SUDDEN_INCREASE |
| POSTPARTUM | Одна рука или нога отекла или болит | One arm or leg is swollen or painful | F_ONE_LIMB |
| POSTPARTUM | Головная боль, нарушение зрения или отёки | Headache, vision changes or swelling | R17 |

**Поведение.** Касание записывает YES для этого факта и сразу открывает экран по §7. Остальные пункты — ABSENT, не NO. «Ничего из этого» записывает NO для показанных пунктов и предлагает выбрать симптом в Check-in; экрана «всё хорошо» нет. После закрытия экрана помощи можно вернуться в ER0 и отметить ещё один пункт; повторные отметки добавляются (экран M).

---

## 9. Экраны

### 9.1 Вопросы

**Q — один признак**
| Элемент | RU | US-EN |
|---|---|---|
| Заголовок | Уточним важный признак | Let's check an important sign |
| Supporting | Ответьте только о том, что вы действительно замечаете. | Answer based on what you actually notice. |
| Вопрос | `{fact.ru}` | `{fact.en}` |
| Ответы | Да · Нет · Не уверена | Yes · No · Not sure |
| Навигация | Назад | Back |
| Secondary | Не сейчас | Not now |
| Подпись | Можно изменить ответ позже. | You can change your answer later. |

**Q-MH — вариант supporting для первого вопроса R18 и для F_HARM_THOUGHTS в R16**
RU: **«Мы задаём этот вопрос всем, кто отмечает тяжёлое состояние. Ответ видите только вы.»**
EN: **“We ask everyone who reports a hard time. Only you can see your answer.”**
Утверждение «видите только вы» допустимо, только если оно технически верно (без Couple Mode, без передачи по умолчанию). Иначе используется только первое предложение.

**QM — чеклист однородной группы (D14)**
| Элемент | RU | US-EN |
|---|---|---|
| Заголовок | Уточним важные признаки | Let's check a few important signs |
| Supporting | Отметьте всё, что есть сейчас. | Select everything you have right now. |
| Пункты QM_SYSTEMIC | Температура 38 °C или выше · Сердце бьётся очень часто или неровно · Сильная рвота, не получается удержать воду · Чувство, что вот-вот потеряете сознание · Необычная сильная слабость или истощение | Temperature 38°C (100.4°F) or higher · Heart racing or irregular · Severe vomiting, can't keep fluids down · Feeling close to fainting · Unusual, severe weakness or exhaustion |
| Primary (активна при ≥ 1 отметке) | Продолжить | Continue |
| Кнопки | Ничего из этого · Не уверена | None of these · Not sure |
| Secondary | Не сейчас | Not now |

Отмеченный пункт — YES. «Продолжить» при отмеченных пунктах — неотмеченные получают NO (пользовательница видела список). «Ничего из этого» — NO для всех. «Не уверена» — UNSURE для всех. «Не сейчас» — ABSENT.

**H1 — подсказка** (строка, не экран)
RU **«Если станет хуже, нажмите «Мне сейчас плохо».»** · EN **“If things get worse, tap “I feel very unwell now.””**

### 9.2 Базовые экраны

**N — недостаточно информации**
| Элемент | RU | US-EN |
|---|---|---|
| Title | Пока недостаточно информации | We don't have enough information yet |
| Body | Мы не можем оценить этот признак по вашему ответу. Если симптомы выраженные, быстро усиливаются или вызывают опасения, обратитесь за медицинской помощью. | We can't assess this sign from your answer. If symptoms are severe, rapidly worsening, or concerning, seek medical care. |
| Primary | Уточнить ответ | Review my answer |
| Secondary | Продолжить | Continue |

**E — экстренное предупреждение**
| Элемент | RU | US-EN |
|---|---|---|
| Label | Экстренная ситуация | Emergency |
| Title | Обратитесь за экстренной помощью сейчас | Seek emergency medical care now |
| Body | Ваш ответ указывает на симптом, который может требовать немедленной помощи. Позвоните в местную экстренную службу или обратитесь в ближайшее отделение экстренной помощи. Не ждите рекомендаций в приложении. | Your answer describes a symptom that may need immediate care. Call your local emergency number or go to the nearest emergency department. Do not wait for advice in the app. |
| Primary | Позвонить в экстренную службу | Call emergency services |
| Secondary | Я поняла | I understand |

**U — срочная оценка**
| Элемент | RU | US-EN |
|---|---|---|
| Label | Нужна срочная оценка | Urgent assessment needed |
| Title | Свяжитесь с медицинской службой без промедления | Contact a medical professional without delay |
| Body | Ваш ответ заслуживает срочной медицинской оценки. Свяжитесь с лечащим врачом или подходящей медицинской службой сейчас. Если появятся признаки экстренной ситуации, обратитесь за экстренной помощью. | Your answer calls for urgent medical assessment. Contact your clinician or an appropriate medical service now. If emergency warning signs appear, seek emergency care. |
| Primary | Понятно | Got it |
| Secondary | Посмотреть признаки экстренности | View emergency warning signs |

**P — своевременная оценка**
| Элемент | RU | US-EN |
|---|---|---|
| Label | Стоит обсудить с врачом | Worth discussing with a clinician |
| Title | Запланируйте медицинскую оценку | Arrange a medical assessment |
| Body | Этот симптом стоит обсудить с медицинским специалистом. Он не означает определённый диагноз. Если самочувствие резко ухудшается, не ждите планового приёма. | This symptom is worth discussing with a clinician. It does not establish a diagnosis. If your condition worsens suddenly, do not wait for a routine appointment. |
| Primary | Понятно | Got it |
| Secondary | Посмотреть симптомы | Review symptoms |

**R — плановая поддержка**
| Элемент | RU | US-EN |
|---|---|---|
| Title | Ваши симптомы заслуживают внимания | Your symptoms deserve attention |
| Body | Вы можете обсудить их с медицинским специалистом на плановом приёме. | You can discuss them with a clinician at a routine appointment. |
| Primary | Понятно | Got it |

**M — несколько предупреждений**
| Элемент | RU | US-EN |
|---|---|---|
| Title | Есть несколько важных признаков | There are several important signs |
| Body | Сначала показано самое срочное предупреждение. Другие отмеченные симптомы сохранены и не игнорируются. | The most urgent notice is shown first. Your other reported symptoms are still recorded and have not been ignored. |
| Secondary | Посмотреть остальные признаки | See other signs |

**ERR — проблема соединения**
| Элемент | RU | US-EN |
|---|---|---|
| Title | Не удалось обновить проверку | We couldn't update your safety check |
| Body | Сейчас нельзя подтвердить результат. Ранее показанное срочное предупреждение остаётся актуальным до получения новых данных. При серьёзных симптомах обращайтесь за медицинской помощью без ожидания приложения. | We can't confirm an updated result right now. A previously displayed urgent warning remains visible until new data is available. If you have serious symptoms, seek medical care without waiting for the app. |
| Primary | Повторить | Try again |
| Secondary | Закрыть | Close |

### 9.3 Специальные экраны

**EP1 — сильная боль при возможной беременности (новый).** Триггер: `COMBO_PREG_PAIN`.
RU: Label **«Нужна срочная оценка»**; Title **«Свяжитесь с врачом сейчас»**; Body **«Сильная боль в животе или тазу при возможной беременности требует срочной медицинской оценки, особенно вместе с кровотечением. Обратитесь к врачу или в приёмное отделение сейчас и сообщите, что беременность возможна. Если появятся сильная слабость, головокружение или обморок, вызовите экстренную помощь.»**; Primary **«Понятно»**; Secondary **«Посмотреть признаки экстренности»**.
EN: **“Urgent assessment needed”**; **“Contact a medical professional now”**; **“Severe abdominal or pelvic pain when pregnancy is possible needs urgent medical assessment, especially with bleeding. Contact a clinician or go to an emergency department now and tell them you could be pregnant. If you feel very weak, dizzy or faint, call emergency services.”**; **“Got it”**; **“View emergency warning signs”**.
Без названия диагноза.

**BP1 — ввод давления.** Триггер: `F_BP_MEASURED` = YES.
RU: заголовок **«Укажите последнее измерение давления»**; supporting **«Введите оба числа с прибора. Если вы не уверены в значениях, не придумывайте их.»**; поля **«Верхнее (систолическое), мм рт. ст.»**, **«Нижнее (диастолическое), мм рт. ст.»**; Primary **«Сохранить измерение»**; Secondary **«Не помню значения»**.
EN: **“Enter your most recent blood pressure reading”**; **“Enter both numbers from your monitor. If you're unsure, don't guess.”**; **“Systolic (top number), mmHg”**, **“Diastolic (bottom number), mmHg”**; **“Save reading”**; **“I don't remember”**.
Пропуск → UNKNOWN. Предложение Addendum (требует утверждения): ≥ 160 **или** ≥ 110 → EMERGENCY; ≥ 140 **или** ≥ 90 → URGENT. Расчёт на сервере (офлайн — по D6).

**PM1 — эмоциональная поддержка после родов.** Триггер: YES на `F_LOW_MOOD_2W` / `F_ANHEDONIA_2W` / `F_BONDING_WORRY` без более высокого исхода.
RU: Overline **«Эмоциональное здоровье»**; Title **«Не нужно оставаться с этим одной»**; Body **«Подавленность, тревога и трудности после родов заслуживают поддержки. Можно обратиться за помощью уже сейчас — не обязательно ждать две недели.»**; Primary **«Понятно»**; Secondary **«Что обсудить с врачом»**.
EN: **“Emotional wellbeing”**; **“You don't have to manage this alone”**; **“Low mood, anxiety and difficulties after birth deserve support. You can ask for help now — you don't have to wait two weeks.”**; **“Got it”**; **“What to discuss with a clinician”**.
Подпись (обязательна, §0): RU **«MOMNA не проводит скрининг депрессии и не заменяет оценку специалиста.»** · EN **“MOMNA does not screen for depression and does not replace assessment by a professional.”**
Валидированный опросник — только отдельным утверждённым и лицензированным маршрутом. В Couple Mode ничего не передаётся.

**PM1-H — PM1 при «Не уверена» на вопросе о вреде (v2.3).** Триггер: R18, `F_HARM_THOUGHTS` = UNSURE, выше ничего не найдено (L11). Тексты, кнопки и подпись — как у PM1, без изменений. Внизу текстовая ссылка: RU **«Сейчас вам небезопасно? Как получить срочную помощь»** · EN **“Not feeling safe right now? How to get urgent help”**. Ведёт туда же, куда кнопка PM2 «Как получить срочную помощь». Исход PROMPT. PM1 для остальных случаев не меняется.

**PM2 — безопасность сейчас.** Триггер: `F_HARM_THOUGHTS` = YES (любой период).
RU: Label **«Важное предупреждение»**; Title **«Сейчас важна ваша безопасность»**; Body (после родов) **«Мы рекомендуем немедленно связаться с медицинским специалистом или кризисной службой для оценки ситуации. Если есть опасность, что вы можете причинить вред себе или ребёнку прямо сейчас, вызовите экстренную помощь.»**; Body (другие периоды) — то же с **«себе или другому человеку»**; Primary **«Понятно»**; Secondary **«Как получить срочную помощь»**.
EN: **“Important safety notice”**; **“Your safety matters right now”**; postpartum: **“Please contact a clinician or crisis service immediately for an assessment. If you might harm yourself or your baby right now, call emergency services.”**; other stages: **“…harm yourself or someone else…”**; **“Got it”**; **“How to get urgent help”**.
**Протокол (заменяет `IMMEDIATE_INTENT`, release gate):** переход в EMERGENCY — только через отдельный клинически утверждённый путь, который отличает навязчивые нежелательные мысли от намерения. Один вопрос Да/Нет его не заменяет. «Как получить срочную помощь» показывает только проверенные кризисные службы региона.

**OB1 — акушерская оценка.** Триггер: URGENT в беременности по §7.3.
RU: Label **«Нужна срочная оценка»**; Title **«Обратитесь в акушерскую службу сейчас»**; Body **«Эти признаки могут требовать срочного осмотра во время беременности. Немедленно свяжитесь с акушерской службой или обратитесь в родильное отделение.»**; Primary **«Понятно»**; Secondary **«Посмотреть тревожные признаки»**.
EN: **“Urgent pregnancy assessment”**; **“Contact your maternity care team now”**; **“These symptoms may need urgent pregnancy assessment. Contact your maternity care team immediately or go to your maternity assessment unit.”**; **“Got it”**; **“View warning signs”**.

**MB1 — кровотечение после менопаузы.** Триггер: `D_POSTMENOPAUSAL_BLEED` = YES. Предупреждение никогда не отменяется.
RU: Label **«Нужен осмотр гинеколога»**; Title **«Кровотечение после менопаузы требует оценки»**; Body **«Даже небольшие кровянистые выделения после менопаузы требуют своевременной оценки гинеколога. Свяжитесь с врачом в ближайшие дни. При сильном кровотечении, обмороке или выраженной слабости обратитесь за экстренной помощью.»**; Primary **«Понятно»**; Secondary **«Посмотреть тревожные признаки»**.
EN: **“Gynecological assessment needed”**; **“Bleeding after menopause needs evaluation”**; **“Even light bleeding or spotting after menopause needs prompt gynecological evaluation. Contact your clinician within the next few days. Seek emergency care for heavy bleeding, fainting or marked weakness.”**; **“Got it”**; **“View warning signs”**.
Дополнительная строка по контексту (не отменяет основной текст):
| Контекст | RU | US-EN |
|---|---|---|
| Гормональная терапия | Если вы принимаете гормональную терапию, неожиданное кровотечение тоже стоит обсудить с врачом. | If you use hormone therapy, unexpected bleeding is still worth discussing with your clinician. |
| Гистерэктомия | Кровянистые выделения после удаления матки тоже требуют осмотра. | Bleeding after a hysterectomy also needs to be checked. |

**MP1 — сильная менструальная боль (менархе).** Триггер: `F_PERIOD_PAIN_LIMITING` = YES.
RU: Title **«Сильную менструальную боль не нужно терпеть»**; Body **«Сильную боль при месячных, которая мешает школе или обычной жизни, не нужно просто терпеть. Стоит обсудить её с врачом.»**; Primary **«Понятно»**; Secondary **«Вернуться к симптомам»**.
EN: **“Severe period pain deserves attention”**; **“Severe period pain that keeps you from school or daily activities is worth a medical check. You don't have to just put up with it.”**; **“Got it”**; **“Back to symptoms”**.

**AG1 — аллергическая экстренность.** Триггер: `F_AIRWAY_SWELLING` = YES.
RU: Label **«Экстренная ситуация»**; Title **«Вызовите экстренную помощь сейчас»**; Body **«Внезапный отёк горла или языка с затруднением дыхания может угрожать жизни. Немедленно вызовите экстренную помощь.»**; Primary **«Позвонить в экстренную службу»**; Secondary **«Я поняла»**.
EN: **“Emergency”**; **“Call emergency services now”**; **“Sudden throat or tongue swelling with breathing difficulty can be life-threatening. Call emergency services now.”**; **“Call emergency services”**; **“I understand”**.

**PR1 — длительные изменения цикла в молодом возрасте.** Триггер: серверное правило `PR_EARLY_MENOPAUSE_REVIEW` по полной (§5.7) истории цикла и возрасту, после исключения беременности, гормональных и анатомических факторов. Вопроса нет.
RU: Label **«Стоит обсудить с гинекологом»**; Title **«Обратите внимание на изменения цикла»**; Body **«В вашем возрасте длительные изменения цикла стоит обсудить с гинекологом. Для выяснения причины могут потребоваться анализы и оценка других факторов.»**; Primary **«Понятно»**; Secondary **«Посмотреть историю цикла»**.
EN: **“Worth discussing with a gynecologist”**; **“Review changes in your cycle”**; **“Long changes in your cycle at your age are worth discussing with a gynecologist. Tests and assessment of other causes may be needed.”**; **“Got it”**; **“View cycle history”**.

**GS1 — сухость и дискомфорт в менопаузе.** Триггер: `F_GSM_SYMPTOMS` = YES без острых признаков.
RU: Title **«Есть способы облегчить эти симптомы»**; Body **«Сухость и болезненность после менопаузы распространены, и существуют способы облегчения. Обсудите симптомы с медицинским специалистом.»**; Primary **«Понятно»**; Secondary **«Посмотреть симптомы»**.
EN: **“There are ways to relieve these symptoms”**; **“Vaginal dryness and pain after menopause are common, and treatments are available. Discuss your symptoms with a clinician.”**; **“Got it”**; **“Review symptoms”**.

### 9.4 WB1 — карточка эмоционального самочувствия после родов (D5, утверждено)

**Когда:** один раз, примерно через 4 недели после родов (`D_TIME_SINCE_BIRTH` ≈ 28 дней; окно показа задаёт продукт), на My Day. Без напоминаний и повторов. Не показывается, если R18 уже проходился за последние 14 дней.

| Элемент | RU | US-EN |
|---|---|---|
| Overline | MOMNA · Эмоциональное здоровье | MOMNA · Emotional wellbeing |
| Title | Как вы чувствуете себя эмоционально в последнее время? | How have you been feeling emotionally lately? |
| Body | После родов бывает непросто. Если вам нужна поддержка, мы поможем понять, куда обратиться. | Things can be hard after birth. If you'd like support, we'll help you find where to turn. |
| Вариант 1 | В целом хорошо | Mostly good |
| Вариант 2 | Последнее время тяжело | It's been hard lately |
| Вариант 3 | Не хочу отвечать | I'd rather not say |

| Ответ | Поведение |
|---|---|
| «В целом хорошо» | Больше ничего не спрашивать; карточка закрывается навсегда |
| «Последнее время тяжело» | Открыть R18 (первым — F_HARM_THOUGHTS с Q-MH) |
| «Не хочу отвечать» / закрыть | Закрыть без повторного показа |

Это приглашение к поддержке, **не скрининг** (§0). Маршрут R18 по отметкам PP2 / PP18 доступен независимо от карточки.

### 9.5 Контракт CTA
| Действие | Поведение |
|---|---|
| `CALL_EMERGENCY` | Набор номера только при подтверждённом регионе, иначе текстовая инструкция |
| `ACKNOWLEDGE` | Закрывает экран, **не** очищает сигнал |
| `REVIEW_ANSWER` | Возврат к точному `factCode` |
| `VIEW_SIGNS` | Признаки из утверждённого справочника |
| `CONTACT_MATERNITY` | Локальный канал при подтверждённых данных, иначе инструкция |
| `OPEN_ER0` | Открывает ER0 |

У всех CTA есть состояния loading и unavailable, а также accessibility label. Медицинских рекомендаций от ИИ нет.

---

## 10. Офлайн (D6, расширено)

| Правило | Содержание |
|---|---|
| Локальная копия | Клиент хранит подписанную копию действующей `catalogVersion`: факты, маршруты, сочетания, тексты всех экранов, проверенные номера и кризисные службы региона |
| Что работает офлайн | ER0, все маршруты, все уровни (E, AG1, PM2, U, OB1, EP1, P), BP1 |
| Статус решения | Офлайн-решение помечено `provisional`, показывается сразу, журналируется |
| Синхронизация | Сервер пересчитывает. Он может **добавить** предупреждение, но не может убрать показанное без изменения ответа пользовательницей |
| Устаревшая копия | Если копия старше допустимого срока — ER0 и экраны E / PM2 работают, остальные маршруты показывают ERR + H1 |
| Это единственное исключение | Вне офлайна клиент не вычисляет срочность |

---

## 11. Решения

| # | Решение | Статус |
|---|---|---|
| D1 | Planning: C4 в пуле Planning нет → вход через T5 (R28); беременность считается возможной без вопроса | Предложено, подтвердить |
| D2 | Температура ≥ 38 °C после родов — URGENT во всех маршрутах; в беременности — OB1 | Предварительно, клиническое утверждение |
| D3 | Признак известен, условие не определено → уровень не понижается; маркер `CONDITION_UNDEFINED`, в журнале причина и версия правила; после утверждения добавляется условие. Применяется только к перечисленным в каталоге признакам, не к неизвестным симптомам. Пользовательница видит обычную рекомендацию | **Утверждено (Annet, 20.10)** |
| D4 | `IMMEDIATE_INTENT` — не вопрос, а протокол PM2 | Поддержано ревью |
| D5 | Одна необязательная карточка WB1 примерно через 4 недели; заявлять о скрининге запрещено | **Утверждено (Annet, 20.10)** |
| D6 | Офлайн для всех уровней (§10) | Расширено по ревью |
| D7 | Один тап на ответ | Поддержано ревью |
| D8 | Стоп-правило: остановка на первом **достаточном для безопасного действия** результате (L3, §4). EMERGENCY — сразу экран помощи; при URGENT / PROMPT продолжаются только вопросы, способные изменить срочность, место обращения или выявить сочетание; ABSENT, UNKNOWN и UNSURE не считаются отрицательными; лимит в 5 вопросов не основание скрыть проверку сочетания | **Утверждено (Annet, 20.10, P0)** |
| D9 | `PAD_SOAK_REPEAT` поглощён `F_PAD_SOAK_1H` (тот же исход); `HOT_FLASH` не спрашивается | Клиническая проверка смысла повторного обильного кровотечения |
| D10 | Акушерские срочные признаки → OB1; неакушерские → U; EMERGENCY всегда в приоритете (§7.3) | **Утверждено (Annet, 20.10)** |
| D11 | Заменено сочетаниями `COMBO_PREG_PAIN` / `COMBO_PREG_PAIN_SHOCK` и экраном EP1 | Клиническое определение срочности |
| D12 | Прямой вход по состоянию 2 пункта, привязанного к маршруту, минуя `safety_score ≥ 12`; паузы L13 (24 ч острые, 7–14 дней хронические), не действуют при новом симптоме, ухудшении или экстренном признаке. Change request к weighted-v3 (MOMNA-641 / 652) | **Утверждено (Annet, 20.10, P0)** |
| D13 | Новые пункты Check-in: P19 «Напряжение или схватки в животе» (→ R10), PP19 «Головная боль, зрение или отёки» (→ R17), шкала 0/1/2. Change request к MOMNA-641 | **Утверждено (Annet, 20.10)** |
| D14 | Экран-чеклист QM для однородных групп; сейчас только QM_SYSTEMIC | Предложено |
| D15 | ER0 одним касанием вместо множественного выбора | Исправлено по ревью |
| — | Пять маршрутов через существующий Check-in; M2 подтверждён по weighted-v3; признаки CDC в v2.2 | **Утверждено (Annet, 20.10)** |

---

## 12. Календарь

Календарь показывает медицинское уведомление только по подтверждённому серверному решению (офлайн — по §10). Он не вычисляет диагнозы. Серверные правила используют историю календаря как вычисляемые факты `D_*` с учётом полноты (§5.7).

| Применимость | Сигналы | Поведение |
|---|---|---|
| ВСЕ | Боль в груди, тяжёлое нарушение дыхания, обморок, судороги, внезапная слабость или спутанность, учащённое сердцебиение | Экстренный или срочный экран; постоянное предупреждение |
| ВСЕ | Отёк лица, губ, языка или горла | R25 / AG1 |
| ВСЕ | Мысли о вреде себе или другому | PM2, конфиденциально, без Couple Mode |
| ВСЕ | Кнопка ER0 в календаре | §8 |
| ВСЕ с циклом | Запись кровотечения с отметкой «после полового контакта» | `F_BLEED_POSTCOITAL` → P |
| MENARCHE | Опасная головная боль, головокружение, кровотечение, боль, мешающая школе | Предупреждение у дня; формулировки по возрасту |
| ADULT / PLANNING | Сильное кровотечение, `D_INTERMENSTRUAL`, `D_BLEED_GT7`, боль при возможной беременности | Отметка эпизода, EP1 / P |
| PREGNANCY | Гипертензивные признаки, дыхание, кровотечение, жидкость, шевеления, схватки до 37 нед., рвота, температура | OB1 или E с учётом срока |
| POSTPARTUM | Кровотечение, гипертензивные признаки, тромбоз (рука или нога), инфекции, неприятные выделения, кризис настроения | Первый год после родов; CTA с проверенной локацией |
| PERIMENOPAUSE | Необычное кровотечение, `D_POSTMENOPAUSAL_BLEED`, экстренные признаки под видом прилива, PR1 | Напоминание до отметки об оценке |
| MENOPAUSE | Любое кровотечение (MB1, без отмены), задержка мочи, температура, экстренные признаки под видом прилива | Без рутинного закрытия |

---

## 13. Покрытие 7 периодов

| Период | Маршруты | Глобальные |
|---|---|---|
| Menarche | R1, R2, R3, R24 | R25, ER0 |
| Adult | R4 | R25, ER0 |
| Planning | R28 | R25, ER0 |
| Pregnancy | R5–R10, R27 | R25, ER0 + 3 пункта |
| Postpartum (первый год) | R11–R18, WB1 | R25, ER0 + 3 пункта |
| Perimenopause | R19, R20, R21, PR1 | R25, ER0 |
| Menopause | R22, R23 | R25, ER0 |

---

## 14. Приёмка QA

1. **Трассировка:** все 121 код v2.0 ровно один раз в §5. Пропуск или дубль — провал.
2. **Маршруты:** 28/28; у каждого есть вход по §2.2, сверенный с weighted-v3.
3. **Достижимость (D12):** для каждого из 22 legacy-пунктов и для M2, P3, P5, P19, PP2, PP18, PP19, T5 состояние 2 (для PP2 — «тяжело эмоционально») открывает маршрут при safety_score < 12. Для каждого из 28 маршрутов есть тест реального действия пользовательницы и проверка ответа сервера по всей цепочке контракта §2.1.
4. **Паузы L13:** повторный вход в период паузы не открывает маршрут; новый симптом, ухудшение, экстренный признак и ER0 паузу снимают; R18 не чаще раза в 14 дней без ухудшения.
5. **Стоп-правило L3 / D8:** тесты проверяют не только итоговый уровень, но и место обращения, отсутствие преждевременной остановки и отсутствие повторных вопросов. E на первом вопросе → один вопрос и экран E. В R4, R20, R28 после YES на кровотечение задаются вопросы о боли и предобморочном состоянии. BP1 в R17 задаётся после URGENT.
6. **Сочетания §4** (включая `COMBO_AIRWAY`): golden tests на каждое сочетание и каждый его компонент (YES / NO / UNSURE / ABSENT).
7. **MB1:** гормональная терапия и гистерэктомия не отменяют MB1, а добавляют строку контекста.
8. **R18 и WB1:** F_HARM_THOUGHTS первым; WB1 показывается один раз; все три ответа WB1 ведут себя по §9.4; формулировки о скрининге отсутствуют.
9. **Шевеления:** YES сохраняется; NO истекает через 14 дней.
10. **Вычисляемые факты:** UNKNOWN при неполной истории; UNKNOWN ≠ NO; исправление данных пересчитывает решения.
11. **ER0:** 2 касания до экрана помощи; кнопка вызова видна всегда; повторные отметки добавляются.
12. **QM:** отметки → YES; «Продолжить» → неотмеченные NO; «Ничего из этого» → NO; «Не уверена» → UNSURE; уже отвеченные факты скрыты.
13. **Офлайн:** все уровни работают; синхронизация не убирает показанные предупреждения.
14. **Маршрутизация URGENT:** таблица §7.3, включая отёк губ при беременности → U.
15. **Privacy:** данные R18, PM1, PM2, WB1 не передаются в Couple Mode.
16. **Accessibility и локализация:** динамический шрифт, screen reader, контраст; RU и US-EN без пустых строк.
17. **Версия:** коды и тексты совпадают с `catalogVersion`; `CONDITION_UNDEFINED` журналируется.

---

## 15. Клинические release gates

- Исходы, пороги и сочетания: D2, D3 (все `CONDITION_UNDEFINED`), D9, D11, §4, окно `F_PAD_SOAK_1H` после родов, порог `D_INTERMENSTRUAL`, неделя для `F_MOVEMENTS_ESTABLISHED`, пороги BP1.
- Новые признаки CDC: `F_HEART_RACING`, `F_SEVERE_VOMITING`, `F_EXTREME_WEAKNESS` (риск ложных срабатываний после родов), `F_FOUL_DISCHARGE`.
- Все объединения по L8, включая новое `CYCLE_BLEEDING__ONE_SIDED_PAIN` → `F_SEVERE_ABDO_PELVIC_PAIN`.
- Паузы L13 и критерии полноты §5.7.
- Протокол PM2.
- Номера экстренных и кризисных служб по странам.
- EPDS / PHQ-9 / GAD-7 не включены: нужен отдельный лицензированный маршрут.
- Локализация, юридические требования, accessibility; сверка с серверным контрактом и golden tests.

---

## 16. Источники и версии

1. v2.3 (28.11.2026): решение Product по R18, MOMNA-1146. v2.2 (20.10.2026). v2.1 (20.10.2026); v2.0 — все коды сохранены в трассировке §5.
2. Qira MOMNA-E28: `daily checkin weighted model v3.json` — 99 пунктов, safety_w, привязки; источник §2.
3. Qira: `MOMNA_Qira_Safety_Addendum_2026-10-16.md` (v2.1); MOMNA-652 (каталог маршрутов), MOMNA-641 (пул и веса), MOMNA-640 (правила Check-in), MOMNA-669, CORE-BE-12.
4. Исполнение: MOMNA-1142 (дизайн), MOMNA-1143 (backend), MOMNA-1144 (frontend).
5. Внешние материалы: CDC urgent maternal warning signs; ACOG (послеродовые предупреждающие признаки, скрининг депрессии и тревоги); NICE NG88 и рекомендации по боли и кровотечению в ранней беременности; NHS (кровотечение после менопаузы); ESHRE POI. Ссылка на материалы не означает медицинского допуска.

**Политика версий:** после утверждения фиксируется матрица `factCode → content → route → entry → condition → disposition → screen → CTA → source → test`. Новые находки оформляются change request с новой `catalogVersion`.
