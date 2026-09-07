# 🎵 SonicSnag

Android-застосунок для пошуку, прослуховування та завантаження музики з YouTube у форматі mp3.
Написаний нативно на **Kotlin + Jetpack Compose**, без сторонніх API-ключів чи бекенду.

Автор: **reeperetter**

## Можливості

- 🔍 Пошук треків за назвою або виконавцем (10 / 25 / 100 / 500 результатів)
- ▶️ Реальне прослуховування прямо в застосунку (через ExoPlayer)
- ✅ Вибір кількох треків одночасно ("Все" / "Скинути")
- ⬇️ Завантаження обраних треків у форматі **mp3** (192 kbps, стерео)
- 📁 Автоматичне збереження у публічну папку **"Завантаження"** пристрою через MediaStore
  (жодних дозволів на Android 10+)
- 🎨 Тепла кольорова гама, темний екран завантаження застосунку

## Технології

| Компонент | Призначення |
|---|---|
| Kotlin + Jetpack Compose | UI та вся логіка застосунку |
| [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) | пошук і отримання посилань на аудіо-потоки з YouTube (без офіційного API) |
| OkHttp | мережеві запити (і для екстрактора, і для завантаження файлів) |
| ExoPlayer (Media3) | відтворення аудіо прямо в застосунку |
| [ffmpeg-kit (maintained fork)](https://github.com/ffmpegkit-maintained/ffmpeg-kit) | конвертація завантаженого аудіо в mp3 |
| MediaStore API | збереження файлів у публічну папку "Завантаження" |
| GitHub Actions + Gradle | автоматична збірка `.apk` без Android Studio |

> ⚠️ **Про ffmpeg-kit:** оригінальний `com.arthenica:ffmpeg-kit-*` архівовано в квітні 2025 (ліцензійний
> спір). Проєкт використовує активно підтримуваний форк `dev.ffmpegkit-maintained` з тим самим API —
> код у `DownloadManager.kt` звертається до тих самих класів `com.arthenica.ffmpegkit.*`, змінилась
> лише координата залежності в Gradle.

## Структура проєкту

```
.
├── settings.gradle.kts       # реєстрація модулів, репозиторії (google, mavenCentral, jitpack)
├── build.gradle.kts          # версії плагінів (AGP, Kotlin)
├── app/
│   ├── build.gradle.kts      # залежності, підпис, назва вихідного .apk
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/reeperetter/sonicsnag/
│       │   ├── MainActivity.kt          # весь UI (Jetpack Compose)
│       │   ├── MusicRepository.kt       # пошук + вибір аудіо-потоку
│       │   ├── NewPipeDownloaderImpl.kt # HTTP-клієнт для NewPipeExtractor (на базі OkHttp)
│       │   ├── DownloadManager.kt       # завантаження -> конвертація в mp3 -> MediaStore
│       │   ├── SearchResult.kt          # модель одного результату пошуку
│       │   └── NetworkConstants.kt      # спільний User-Agent для екстрактора й плеєра
│       └── res/
│           ├── values/ (colors, themes, strings)
│           └── mipmap-*/ (іконка застосунку, усі щільності)
└── .github/workflows/build.yml   # збірка через Gradle (без Android Studio)
```

## Збірка

Повністю автоматизована через **GitHub Actions** — досить запушити зміни в `main`/`master`, і в
розділі **Actions** з'явиться готовий `.apk` як артефакт збірки з назвою `SonicSnag`.

Наразі збирається **debug**-версія — вона автоматично підписується стандартним відлагоджувальним
ключем Android (не потребує жодних секретів). Коли знадобиться релізний підпис (для консистентних
оновлень з однаковим ключем) — у `app/build.gradle.kts` вже підготовлено `signingConfigs`, лишається
додати 4 секрети в GitHub (`ANDROID_SIGNING_KEY`, `ANDROID_KEY_ALIAS`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_PASSWORD`) і повернути крок `assembleRelease` у `build.yml`.

### Локальна збірка (за потреби)

```bash
./gradlew assembleDebug
```

Готовий `.apk` з'явиться в `app/build/outputs/apk/debug/SonicSnag-debug.apk`.

## Встановлення на телефон і попередження Google Play Protect

При встановленні APK не з Play Store, Android/Google Play Protect майже завжди показує
попередження на кшталт "Це може завдати шкоди пристрою" — це стандартна поведінка для **будь-якого**
застосунку, який не пройшов сканування через офіційний Play Store, і жодні налаштування самого коду
застосунку не можуть це прибрати повністю.

Щоб встановити:
1. При появі попередження натисніть **"Додатково"** (Details / More details)
2. Натисніть **"Установити попри це"** (Install anyway)

Якщо попередження дратує і хочеться його прибрати зовсім (на свій ризик, лише для власних
перевірених збірок):
- Play Маркет → ваш профіль → **Play Protect** → шестерня налаштувань → вимкнути
  **"Сканувати додатки за допомогою Play Protect"**

## Дозволи

- `INTERNET` — пошук і завантаження з YouTube
- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` (лише Android 9 і старіші) — на Android 10+
  файли зберігаються через MediaStore без потреби в цих дозволах

## Відомі обмеження

- Прослуховування і завантаження залежать від `NewPipeExtractor` — якщо YouTube змінить щось у
  внутрішньому API (трапляється кілька разів на рік), може знадобитись оновити версію бібліотеки
  в `app/build.gradle.kts` до найновішої (перевірити на
  [GitHub-релізах NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor/releases)).
- Підтримка Android 9 і старіших для завантаження реалізована спрощено (без runtime-запиту дозволу
  на запис) — основні цільові пристрої проєкту (Android 11+) цього не потребують.

## Автор

**reeperetter**
