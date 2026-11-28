# Manhwa Translate

Dịch truyện tranh Hàn — Nhật — Trung sang tiếng Việt **ngay trên điện thoại**, chạy hoàn toàn trên máy:
nhận diện chữ → xoá chữ gốc → dịch → vẽ lại bản dịch vào đúng bong bóng.

<p align="center">
  <img src="docs/demo-source.png" width="300" alt="Trang gốc" />
  <img src="docs/demo-translated.png" width="300" alt="Trang đã dịch" />
</p>

## Giới thiệu

Manhwa Translate là ứng dụng Android mã nguồn mở, dùng **Jetpack Compose + Kotlin**. Thay vì phải
chụp màn hình rồi dịch bằng mắt, bạn mở một trang truyện và ứng dụng tự động:

- **Nhận diện vùng chữ** bằng ML Kit (Latinh, Hàn, Trung, Nhật) hoặc PaddleOCR qua server.
- **Xoá chữ gốc** bằng thuật toán inpaint riêng, giữ nguyên nền, vân và gradient của bong bóng.
- **Dịch** với bộ nguồn miễn phí tích hợp sẵn (Google Dịch, MyMemory, ML Kit) hoặc các provider
  nâng cao (Google Cloud, DeepL, Gemini, OpenAI-compatible).
- **Vẽ lại bản dịch** khớp cỡ chữ, kiểu chữ và bong bóng gốc; bản dịch có viền và bóng đổ.

Kết quả là một trang đã dịch trông tự nhiên như bản gốc.

## Tính năng nổi bật

| | |
| --- | --- |
| 🧠 **Nhận diện chữ** | Tự động chọn mô hình phù hợp, bắt cả chữ nhỏ trên ảnh nén. |
| 🎨 **Xoá & vá nền** | Inpaint thông minh, giữ vân giấy, gradient và màu nền gốc. |
| 🌐 **Dịch linh hoạt** | Miễn phí không cần key, hoặc dùng API key cho chất lượng cao hơn. |
| 🤖 **AI offline** | Chạy model GGUF (llama.cpp) ngay trên máy, không cần mạng. |
| 📖 **Đọc webtoon** | Trình đọc web tích hợp, chặn quảng cáo, dịch ảnh trực tiếp trên trang. |
| ✍️ **Tuỳ chỉnh** | Nhập font riêng, chỉnh cỡ chữ, kiểu chữ, màu và bóng. |

## Cài đặt

Tải APK từ **Releases** (chọn `app-arm64-v8a-debug.apk` cho hầu hết điện thoại) rồi cài đặt.

## Build từ mã nguồn

Yêu cầu: JDK 17+, Android SDK 35.

```bash
export ANDROID_HOME=$HOME/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug     # APK: app/build/outputs/apk/debug/
./gradlew :core:test             # chạy unit test thuật toán
./gradlew :demo:run              # chạy pipeline trên trang mẫu
```

## Kiến trúc

```
core/   Thuật toán thuần JVM (không phụ thuộc Android): inpaint, layout, ghép vùng, pipeline
app/    Android: OCR, các provider dịch, Compose UI, trình đọc web, chặn quảng cáo
demo/   Harness Java2D chạy pipeline trên trang mẫu, xuất ảnh PNG để kiểm tra
```

Toàn bộ phần khó (mask, inpaint, layout, ghép vùng) nằm trong module JVM thuần nên chạy được
unit test và trên desktop.
