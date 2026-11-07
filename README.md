<div align="center">

# 🦊 FOXrezka

### Кино и сериалы — в одном Android-приложении

Лёгкий клиент для Rezka с поиском, каталогом и встроенным видеоплеером.

[**⬇ Скачать FOXrezka**](https://github.com/Latkovsky/rezka-client/releases/latest)
&nbsp; · &nbsp;
[Исходный код](https://github.com/Latkovsky/rezka-client)

![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
![Rust](https://img.shields.io/badge/Core-Rust-orange?logo=rust&logoColor=white)
![Версия](https://img.shields.io/badge/версия-1.0.3-blue)

</div>

---

## ✨ Возможности

- 🎬 Каталог фильмов и сериалов
- 🔎 Поиск по каталогу
- 👤 Вход в аккаунт Rezka с сохранением сессии
- 🎙️ Выбор озвучки, сезона и серии
- ▶️ Просмотр HLS-видео во встроенном плеере
- ⏯️ Перемотка на 10 секунд и сохранение позиции просмотра
- 🕘 Локальная история недавно просмотренного в профиле
- 📱 Главная подборка сериалов с главной страницы Rezka

## 📲 Установка

1. Откройте страницу [последнего релиза](https://github.com/Latkovsky/rezka-client/releases/latest).
2. Скачайте файл `FOXrezka-1.0.1-android.apk` на Android-устройство.
3. Откройте APK и подтвердите установку. Если система попросит, разрешите установку приложений из этого источника.

> **Важно:** репозиторий приватный. Скачивание доступно владельцу и приглашённым участникам GitHub.
> FOXrezka распространяется напрямую и не предназначен для Google Play.

## 🧭 Что важно знать

- Для постеров требуется доступ к `static.hdrezka.ac`. Если изображения не загружаются, причиной может быть DNS-фильтрация у провайдера.
- Видеопотоки предоставляются сторонними CDN и могут быть недоступны в некоторых сетях.
- Доступные озвучки и качество зависят от источника и аккаунта. Переключение качества доступно из плеера.
- История просмотров хранится локально на устройстве и не синхронизируется с аккаунтом Rezka.

## 🛠️ Технологии

| Часть | Технологии |
|---|---|
| Android-приложение | Kotlin, Jetpack Compose |
| Видеоплеер | AndroidX Media3 / ExoPlayer |
| Ядро | Rust, UniFFI |

## 🧑‍💻 Сборка из исходников

Понадобятся JDK 17, Rust с `rustup`, `cargo-ndk`, Android SDK (platform 34, build-tools 34, NDK 27) и Gradle 8.9.

```bash
./build.sh
```

Скрипт соберёт Rust-ядро и Android APK для `arm64-v8a` и `x86_64`. Если устройство подключено по USB с включённой отладкой, APK будет установлен автоматически.

Для ручной установки собранного debug APK:

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## ⚖️ Примечание

FOXrezka — сторонний клиент и не связан с владельцами или администрацией Rezka. Пользователь самостоятельно отвечает за соблюдение законодательства и правил сервисов в своём регионе.
