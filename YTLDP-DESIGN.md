# Desain Integrasi yt-dlp — AB Download Manager

Branch: `feat/ytdlp`. Target: aplikasi desktop (JVM). Kode `androidMain` TIDAK disentuh.

## 1. Ringkasan arsitektur existing (hasil eksplorasi)

- Modul engine: `downloader:core` (Kotlin Multiplatform: `jvm("desktop")` + android). HTTP engine sendiri
  (OkHttp), antrian via `ir.amirab.downloader.queue.QueueManager`.
- UI: `shared:app` (KMP: commonMain/androidMain/desktopMain), Compose Multiplatform + Decompose
  (navigasi/komponen), DI memakai **Koin** (`KoinComponent` + `inject`).
- Settings: `shared:config` (KotlinSerializationDataStore + skema `schemakt`);
  model di `shared/app/.../storage/appsettings/` (`IAppSettingsModel`, `BaseAppSettingsDefinition`,
  `DefaultAppSettings`). UI settings: `BaseSettingsComponent.configurables: StateFlow<List<ConfigurableGroup>>`;
  implementasi desktop di `DesktopSettingsComponent` per `SettingSection` (Appearance, DownloadEngine, ...).
- i18n: `shared:resources` — string dari `locales/en_US.properties` digenerate jadi `Res.string.*`
  (task `propertiesToKotlinTask`); terjemahan via Crowdin. Locale `id_ID` tersedia.
- Alur "add download": toolbar action (`createNewDownloadAction` di
  `shared/action/CommonActionFactories.kt`) → `EnterNewURLDialogManager.openEnterNewURLWindow()`
  → dialog/page `AddDownloadComponent` (Decompose) → `QueueManager` / `NewDownloadInputs`.
- Java toolchain: **25** (`gradle.properties: jvm.toolchain=25`), Gradle wrapper 9.8.0.

## 2. Keputusan desain

Modul Gradle **baru** `downloader:ytdlp` (JVM-only, plugin `myPlugins.kotlin` = `kotlin("jvm")`,
toolchain 25), package `ir.amirab.downloader.ytdlp`. Alasan: fitur murni JVM
(`ProcessBuilder`), tidak boleh bocor ke android; modul ringan sehingga unit test cepat tanpa
build full desktop app. Didaftarkan di `settings.gradle.kts`.

UI ditaruh di `shared:app` `commonMain` (mengikuti pola existing, Compose Multiplatform),
dengan kode yang menyentuh proses hanya lewat API modul `ytdlp` (yang memang JVM-only —
konsumsi dari commonMain dimungkinkan karena modulnya JVM; androidMain tidak mereferensikannya).

## 3. Komponen modul `downloader:ytdlp`

```
ir.amirab.downloader.ytdlp
├── YtDlpSettings.kt        data class: binaryPath: String?, autoDownloadBinary: Boolean,
│                           defaultFormat: String ("best"), maxPlaylistItems: Int
├── YtDlpVideoInfo.kt       @Serializable data class hasil --dump-json:
│                           YtDlpVideoInfo(id, title, duration, uploader, webpageUrl, formats: List<YtDlpFormat>)
│                           YtDlpFormat(formatId, ext, resolution?, width?, height?, fps?, filesize?,
│                                       filesizeApprox?, vcodec?, acodec?, formatNote?, url?, httpHeaders?)
│                           (semua field opsional/toleran: coerceInputValues, ignoreUnknownKeys)
├── YtDlpProgress.kt        data class YtDlpProgress(percent, downloadedBytes, totalBytes,
│                           totalBytesIsApprox, speedBps, etaSeconds, state)
├── YtDlpProgressParser.kt  parse baris stderr/stdout:
│                           `[download]  12.3% of ~50.00MiB at 1.20MiB/s ETA 00:36`
│                           → YtDlpProgress; juga baris `[download] 100% ...`, `Destination: ...`,
│                           `[Merger]`, error `[ERROR]`.
├── YtDlpBinaryManager.kt   resolusi binary:
│                           1. `settings.binaryPath` (bila ada & executable)
│                           2. `PATH` (`where`/`which yt-dlp`)
│                           3. folder data aplikasi (`<appData>/ytdlp/yt-dlp[.exe]`)
│                           `getVersion(): String?` via `--version`.
│                           `ensureBinary(autoDownload)`: bila tak ditemukan & user setuju →
│                           unduh dari GitHub releases resmi (`yt-dlp/yt-dlp`, tag `latest`)
│                           sesuai OS/arch (asset map, lihat §5), chmod +x di Unix.
├── YtDlpClient.kt          JVM process wrapper:
│                           - `fetchInfo(url): YtDlpVideoInfo` →
│                             `yt-dlp --dump-json --no-playlist --no-warnings --no-check-certificate? (tidak) <url>`
│                           - `fetchPlaylist(url, maxItems): List<YtDlpPlaylistEntry>` →
│                             `--flat-playlist --dump-json` (tiap baris = 1 JSON)
│                           - `download(url, format, outputTemplate, extraArgs): Flow<YtDlpProgress>` →
│                             `--newline --progress` diparse per baris; cancel via coroutine cancellation
│                             (destroy process).
└── YtDlpFormatSelector.kt  `defaultFormat(info)`: pilih format progresif terbaik
                            (vcodec != "none" && acodec != "none", height terbesar),
                            fallback `"best"`.
```

`YtDlpClient` menerima `binaryPathProvider: () -> String` + `CoroutineDispatcher` (IO) agar
mudah di-test/di-mock. Timeout default untuk `fetchInfo`: 60 dtk.

## 4. Integrasi UI (shared:app, desktop)

- **Entry point**: action baru `createYtDlpDownloadAction()` di `CommonActionFactories.kt`
  (ikon `MyIcons.download`, string `Res.string.ytdlp_download`), dipasang di toolbar/menu
  desktop dekat "New Download" (desktop `main.kt` actions). Membuka `YtDlpDialogManager`.
- **Dialog** `shared/app/.../pages/ytdlp/`:
  - `YtDlpDownloadComponent` (Decompose `BaseComponent`, KoinComponent): state
    `url → fetchState(Loading/Info/Error) → selectedFormat → outputFolder → downloadState(Progress/Done/Error)`.
  - UI Compose: field URL + tombol "Ambil info" → judul/durasi + tabel format
    (format_id, resolusi, ext, ukuran, note) → radio pilih (default best/progresif terbaik) →
    pilih folder (pakai pola `LocationOptions` existing bila memungkinkan) → tombol Download.
    Progress bar + teks `12.3% • 1.2 MiB/s • ETA 00:36`, tombol Batal.
  - v1: progress di dialog + notifikasi selesai (via `DownloadSystem` notification API bila ada);
    pendaftaran ke `QueueManager` utama ditunda (antrian ABDM memakai engine HTTP sendiri,
    tidak bisa menampung proses eksternal tanpa refactor besar) — dicatat di keterbatasan.
- **Settings**: section baru `SettingSection.YtDlp` di `DesktopSettingsComponent`:
  `ConfigurableGroup` berisi: path binary (text + tombol Browse + tombol Test→ tampilkan versi),
  toggle `autoDownloadBinary`, info versi terdeteksi. Nilai disimpan di `IAppSettingsModel`
  (tambah properti `ytdlpBinaryPath: String?`, `ytdlpAutoDownloadBinary: Boolean` +
  definisi skema + default di `PlatformDefaultSettings`).
- **i18n**: tambah key di `en_US.properties` + `id_ID.properties`
  (`ytdlp_download`, `ytdlp_fetch_info`, `ytdlp_choose_format`, ...). Key lain ikut Crowdin.

## 5. Asset binary resmi yt-dlp (github.com/yt-dlp/yt-dlp releases, tag `latest`)

| OS | arch | asset |
|---|---|---|
| Windows | x64 | `yt-dlp.exe` |
| Windows | arm64 | `yt-dlp_arm64.exe` |
| Linux | x64 | `yt-dlp` |
| Linux | aarch64 | `yt-dlp_linux_aarch64` |
| macOS | x64 | `yt-dlp_macos` |
| macOS | arm64 | `yt-dlp_macos_arm64` |

URL: `https://github.com/yt-dlp/yt-dlp/releases/latest/download/<asset>`.
Deteksi OS via `System.getProperty("os.name")`, arch via `os.arch`
(x86_64/amd64→x64, aarch64/arm64→arm64). Fallback bila kombinasi tak dikenal: error jelas.

## 6. Batasan v1 (jujur)

- Download yt-dlp berjalan di luar engine ABDM: tanpa multi-koneksi ABDM, tanpa masuk antrian/
  speed-limit global; progress hanya di dialog.
- Playlist: fetch daftar sederhana (N pertama), unduh satu-per-satu berurutan via client yang sama.
- Merge format (video+audio terpisah) butuh ffmpeg di PATH — yt-dlp akan error jelas bila tak ada;
  v1 memilih format progresif (sudah digabung) sebagai default.
- Auto-download binary butuh koneksi ke github.com.
