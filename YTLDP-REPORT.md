# Laporan Integrasi yt-dlp — AB Download Manager

Branch: `feat/ytdlp` (belum di-push; tidak ada remote fork).
Tanggal: 2026-10-14.

## Ringkasan

Fitur "Download Video (yt-dlp)" ditambahkan sebagai integrasi binary eksternal yt-dlp,
khusus desktop (JVM). `androidMain` tidak disentuh sama sekali; perilaku existing tidak diubah
(semua perubahan bersifat aditif).

## File yang dibuat

Modul Gradle baru `downloader:ytdlp` (JVM-only, Kotlin 2.4.20, toolchain 25),
package `ir.amirab.downloader.ytdlp`:
- `YtDlpVideoInfo.kt` — data class hasil `--dump-json` (toleran: unknown keys diabaikan,
  semua field opsional) + `YtDlpPlaylistEntry` untuk `--flat-playlist`.
- `YtDlpProgress.kt` — data class progress (percent, bytes, speed, ETA, state).
- `YtDlpProgressParser.kt` — parser baris `[download] …% of … at …/s ETA …`,
  baris `100%`, `Destination:`, `has already been downloaded`, `[Merger]`, postprocess.
- `YtDlpBinaryManager.kt` — resolusi binary (settings → PATH → folder data aplikasi),
  `--version`, auto-download binary resmi dari GitHub releases sesuai OS/arch
  (Windows x64/arm64, Linux x64/arm64, macOS x64/arm64).
- `YtDlpClient.kt` — `fetchInfo()`, `fetchPlaylist()`, `download(): Flow<YtDlpProgress>`
  (cancel = destroy process).
- `YtDlpFormatSelector.kt` — default = format progresif terbaik, fallback `"best"`.
- `YtDlpSettings.kt` / `YtDlpSettingsStore.kt` — settings + penyimpanan JSON di
  `<appData>/ytdlp/settings.json` dengan StateFlow.
- Test (4 file, 25 test) + resource `src/test/resources/ytdlp/sample-video.json`
  (contoh JSON realistis ala output yt-dlp asli).

UI:
- `shared/app` commonMain: `pagemanager/YtDlpDialogManager.kt` (interface),
  `action/CommonActionFactories.kt` += `createYtDlpDownloadAction()`.
- `shared/app` desktopMain: `pages/ytdlp/YtDlpDownloadComponent.kt` (Decompose + Koin,
  state machine URL → fetch → pilih format → download),
  `pages/ytdlp/YtDlpDownloadDialogContent.kt` (Compose UI dialog).
- `desktop/app`: `pages/ytdlp/YtDlpDownloadWindow.kt` (jendela dialog),
  `AppComponent` implement `YtDlpDialogManager` (SlotNavigation),
  `ui/Ui.kt` += window, `actions/main.kt` += `ytdlpDownloadAction`,
  `pages/home/HomeComponent.kt` += item di menu File,
  `pages/settings/YtDlpSettingsConfigurables.kt` + section `SettingSection.YtDlp`
  di `DesktopSettingsComponent` (path binary, toggle auto-download, info versi),
  `di/Di.kt` += `single { YtDlpSettingsStore(...) }`.
- i18n: 19 key baru di `en_US.properties` + `id_ID.properties` (`ytdlp_*`).

## File yang diubah

`settings.gradle.kts` (+modul), `downloader/ytdlp/build.gradle.kts` (baru),
`shared/app/build.gradle.kts` (desktopMain dep), `desktop/app/build.gradle.kts` (dep),
`desktop/app/.../AppComponent.kt`, `actions/main.kt`, `di/Di.kt`,
`pages/home/HomeComponent.kt`, `pages/settings/DesktopSettingsComponent.kt`,
`ui/Ui.kt`, `shared/app/.../action/CommonActionFactories.kt`,
`en_US.properties`, `id_ID.properties`. Dokumen: `YTLDP-DESIGN.md`, `YTLDP-REPORT.md` (ini).

## Cara build & test

Modul inti (ringan, tanpa Android SDK):
```bash
./gradlew :downloader:ytdlp:test      # butuh JDK 25 + network ke Maven Central
```
Full desktop app: `./gradlew :desktop:app:run` (butuh Android SDK + Compose artifacts;
belum dicoba di VM ini — lihat "Status build").

## Status build / verifikasi

- ✅ Modul `downloader:ytdlp` **terkompilasi bersih** (kotlinc 2.4.20 langsung, karena
  Gradle daemon tidak bisa jalan di VM ini — RAM hanya ~637 MB bebas, daemon langsung mati
  "broken pipe"; JDK 25 Temurin di-install manual di `/opt/jdk-25`, Gradle 9.8.0 manual di
  `/opt/gradle`, proxy egress dikonfigurasi di `~/.gradle/gradle.properties`).
- ✅ **25/25 unit test lolos** (JUnit 5 via junit-platform-console-standalone):
  parsing JSON dump-json, parsing 8 varian baris progress, format selector,
  asset-name per OS/arch, binary resolution, settings store round-trip.
- ✅ Smoke test runtime: `YtDlpClient` melawan fake binary `yt-dlp` (shell script) —
  `fetchInfo` parse JSON OK, `download` emit progress → DONE dengan destination terbawa,
  `YtDlpBinaryManager` resolve via PATH OK, `--version` OK.
- ⚠️ Kode UI (Compose/Decompose di `shared/app` + `desktop/app`) **ditulis mengikuti pola
  existing dan semua API yang dipakai diverifikasi manual ada** (`rememberChild`,
  `CustomWindow`, `MyTextFieldWithIcons`, `ActionButton`, `RadioButton`, `Configurable`,
  dsb.), tetapi **belum terkompilasi** — build modul tersebut butuh Android SDK +
  artefak Compose Multiplatform yang tidak tersedia di VM ini. Kemungkinan butuh
  penyesuaian kecil saat build pertama di mesin dev.

## Keterbatasan v1

1. Download yt-dlp berjalan di luar engine ABDM: tanpa multi-koneksi, tanpa masuk
   antrian/queue, tanpa speed-limit global; progress hanya di dialog.
2. Format default = progresif terbaik (tanpa ffmpeg); format video-only+audio-only
   butuh ffmpeg di PATH (yt-dlp akan error dengan jelas).
3. Playlist: baru API client (`fetchPlaylist`); UI daftar pilih belum ada.
4. Settings "Test binary" berupa baris read-only versi terdeteksi (tanpa tombol Browse
   file-picker); path binary diisi manual sebagai teks.
5. Auto-download binary memakai `URL.openStream()` Java tanpa proxy env — di jaringan
   yang butuh proxy, unduhan perlu proxy dikonfigurasi di JVM.

## Langkah manual yang tersisa

1. Di mesin dev (dengan Android SDK + GUI): `./gradlew :desktop:app:run`, buka
   File → "Download Video (yt-dlp)", tes fetch info + download video sungguhan.
2. Buka Settings → yt-dlp: pastikan section muncul, path & toggle tersimpan
   (file `<appData>/ytdlp/settings.json`).
3. Pertimbangkan terjemahan key `ytdlp_*` via Crowdin untuk locale selain en/id.
4. (Opsional) Daftarkan download yt-dlp ke `QueueManager` bila diinginkan — butuh
   refactor karena antrian ABDM memakai engine HTTP internal.
