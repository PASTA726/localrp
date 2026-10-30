# THIRD_PARTY_NOTICES — LocalRP

LocalRP projesinin kendi kodu **MIT** lisanslıdır (bkz. `LICENSE`).
Uygulama, üçüncü taraf bileşenlere bağımlıdır; bildirimler aşağıdadır.

> Doğrulama notu: Bu liste, projedeki `README.md`/`ARCHITECTURE.md`'de
> bildirilen bilgiye dayanır. Kesin lisans metinleri, her bileşenin kendi
> kaynağındaki LICENSE/NOTICE dosyasıdır; dağıtım öncesi bunlar taranarak
> onaylanmalıdır.

## APK içinde paketlenen bileşenler

### 1. `com.arm:ai-chat:0.1.0`
- **Amaç:** llama.cpp tabanlı, cihaz-içi çıkarım katmanı (GGUF yükleme, prompt gönderme, token akışı).
- **Lisans:** Apache-2.0 (kaynak: `ARCHITECTURE.md`, upstream `github.com/arm/ai-chat`).
- **Not:** Tüm çalışma cihazda, çevrimdışıdır; kütüphane telemetri yapmaz.

### 2. `kotlinx-coroutines-android:1.10.2`
- **Amaç:** model yükleme ve üretim işlemleri için coroutine akışı (IO/Main dispatcher ayrımı).
- **Lisans:** Apache-2.0 — metin: kotlinx.coroutines deposu (`LICENSE`).

## Yalnızca test classpath'i (APK'ya girmez)

- `junit:junit:4.13.2` — JUnit 4 lisansı (EPL); kesin metin bileşenin kaynağındadır.
- `org.json:json:20240303` — kalıcılık codec testlerini JVM'de koşturmak için; JVM'de `org.json` yokluğunu kapatır. Lisans metni bileşen kaynağındadır (`stleary/JSON-java`).

## Katalogdaki modeller (kullanıcı indirdiğinde)

Uygulama içindeki model öneri merkezi şu Hugging Face modellerini listeler;
her modelin kendi lisansı indirme kartındaki HF sayfasından doğrulanabilir:

- Qwen 2.5 Instruct (Apache-2.0 ailesi)
- Llama 3.2 Instruct (Meta Llama Community License)
- Gemma 2 (Gemma Terms of Use)
- Phi-3.5 Mini (MIT)
- TinyLlama Chat (Apache-2.0)

Uygulaya modeller dahil edilmez; kullanıcı yalnızca kendi onayıyla indirir.
