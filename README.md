# Manhwa Translate

Ứng dụng Android (Kotlin + Jetpack Compose) dịch truyện tranh Hàn – Nhật – Trung sang tiếng Việt
ngay trên máy: **nhận diện vùng chữ → xoá chữ gốc → dịch → vẽ bản dịch vào đúng bong bóng**.

<p align="center">
  <img src="docs/demo-source.png" width="320" alt="Trang gốc" />
  <img src="docs/demo-translated.png" width="320" alt="Trang đã dịch" />
</p>

## Tính năng

- **Hai bộ nhận diện chữ**: ML Kit trên máy (mặc định) hoặc **PaddleOCR qua server** — chọn trong
  Cài đặt → Nhận diện chữ, nhập địa chỉ server Hub Serving tự chạy. Cách nhanh nhất:
  `pip install paddleocr paddlepaddle && paddleocr --server --port 8866` (hoặc dựng image
  `paddlepaddle/paddle:2.4.0` rồi `hub install deploy/hubserving/ocr_system && hub serving start -m ocr_system`).
  App gửi từng dải ảnh dạng base64, nhận `text_region` + `text` + `confidence` rồi đổi toạ độ về
  trang gốc; PaddleOCR bắt chữ trên nền có hình tốt hơn ML Kit.
- **Nhận diện chữ** bằng ML Kit (Latinh, Hàn, Trung, Nhật). Chế độ *Tự động* chạy cả bốn mô
  hình rồi chọn kết quả hợp lý nhất cho từng bong bóng. Trang hẹp được phóng to trước khi nhận
  diện (rồi thu hộp chữ về đúng tỉ lệ) để bắt được chữ nhỏ.
- **Xoá chữ gốc** bằng thuật toán inpaint riêng: ước lượng màu giấy từ viền khung chữ và màu mực
  từ các pixel xa giấy nhất, dựng mask theo màu nền + tương phản cục bộ, đóng hình thái để lấp nét
  đứt. Nền được mô hình bằng một mặt bậc hai (khớp gradient và vệt sáng tối), tô phẳng khi bong
  bóng trơn, lan màu từ pixel kề khi nền có vân, rồi hiệu chỉnh đường may để hoà vào vân giấy.
  Chế độ *Tự động* đo độ rối của giấy để chọn giữa hai cách.
- **Gộp lại một bong bóng**: OCR hay trả một bong bóng thành nhiều khối (dòng trên ngắn, dòng dưới
  dài, tên nhân vật chen trong caption). Các khối nằm sát nhau, chồng nhau theo chiều ngang và
  cách nhau bằng giấy trơn được gộp thành một vùng trước khi dịch, nên cả bong bóng dùng chung một
  cỡ chữ và bản dịch có đủ ngữ cảnh.
- **Lọc chữ hiệu ứng**: vùng một từ, chữ quá lớn, hoặc nằm trên hình vẽ bị bỏ qua thay vì xoá lem —
  trừ khi đó là cả một câu thoại (từ 3 từ và 12 ký tự trở lên) vẽ tay trên hình, những vùng đó vẫn
  được dịch.
- **Bám kiểu chữ gốc**: ước lượng in đậm, in nghiêng, chữ hoa, bề rộng chữ (condensed) và cỡ chữ
  từ chiều cao dòng gốc; hỗ trợ nhập font TTF riêng trong Cài đặt.
- **Dịch** qua 5 nhà cung cấp: ML Kit trên máy (miễn phí, offline), Google Cloud Translation,
  DeepL, Gemini, và mọi endpoint kiểu OpenAI (OpenAI, DeepSeek, Groq, OpenRouter, server nội bộ).
  Nhà cung cấp kiểu LLM dịch cả trang trong một lượt để giữ ngữ cảnh, có đánh số `[[n]]` để ghép
  lại chính xác.
- **Vẽ bản dịch**: mỗi vùng dùng đúng một cỡ chữ, chọn cỡ lớn nhất mà cả khối vẫn nằm trong khung
  gốc — kể cả phần viền chữ, và ưu tiên số dòng giống bản gốc nên bản dịch không bị vỡ thành cột chữ
  nhỏ. Tự xuống dòng theo từ (hoặc từng ký tự với tiếng Trung/Nhật), tự chọn màu chữ sáng/tối theo
  nền, có viền và bóng đổ.
- **Ảnh webtoon dài, không giới hạn chiều cao**: trang được cắt thành các dải chồng lấn để OCR, sau
  đó ghép lại và khử trùng lặp vùng chữ ở mép dải. Ở tầng pipeline, trang rất cao còn được cắt tiếp
  thành các dải lớn (mặc định 6 000 px) tại đúng hàng trống giữa các khung, xử lý từng dải rồi ghép
  — nhờ vậy không chạm giới hạn bitmap/canvas của Android và RAM không phình theo chiều dài trang.
- **Sửa bản dịch**: chạm vào khung chữ để sửa tay hoặc dịch lại riêng vùng đó; mọi thay đổi được
  vẽ lại và lưu.
- **Chia sẻ / lưu ảnh**: nhận ảnh từ ứng dụng khác qua menu Chia sẻ, xuất PNG vào thư viện ảnh.
- **Trình đọc web**: thanh địa chỉ luôn hiện (có nút Dán và nút Mở), bảng cài đặt riêng cho web
  (bật/tắt chặn quảng cáo, chọn từng bộ lọc, chế độ đọc, chế độ máy tính, giữ sáng màn hình, xoá
  cookie, xoá bản dịch, mở bằng trình duyệt ngoài) và dịch ảnh ngay trên trang.
- **Chặn quảng cáo bằng bộ lọc thật**: APK nhúng sẵn ba bộ lọc mã nguồn mở — EasyList, EasyPrivacy
  và danh sách của Peter Lowe — đã được `tools/build_filters.py` rút gọn thành tên miền + CSS ẩn
  quảng cáo (≈97 000 tên miền, 18 000 selector, 1,9 MB nén). Mỗi bộ bật/tắt được trong Cài đặt web;
  script trên trang dọn thêm khung quảng cáo, tường cookie và link ẩn trong suốt.

## Kiến trúc

```
core/   Kotlin/JVM thuần, không phụ thuộc Android — toàn bộ thuật toán
  geometry/   RectI
  image/      ArgbImage, Colors
  scan/       ImageTiler (cắt dải), RegionMerger (khử trùng lặp, nối đoạn bị cắt), SfxFilter
  erase/      TextMaskBuilder (giấy + mực + mask), BackgroundSurface (mặt bậc hai), InkAnalyzer,
              Morphology, TextEraser (solid/diffusion/auto)
  layout/     TextLayoutEngine (tự co cỡ chữ, ngắt dòng theo từ và CJK), FontEstimator, FontSpec
  render/     RenderStyle, PageCompositor (interface vẽ theo nền tảng)
  translate/  TranslationProvider, TranslationService (chia lô, khử trùng, retry)
  pipeline/   TranslationPipeline (detect → erase → translate → render)
app/    Android: ML Kit OCR, các provider dịch, Canvas renderer, Compose UI, DataStore,
        phông chữ nhập tay, trình đọc web (WebView + WebImageBridge), chặn quảng cáo (AdBlocker)
demo/   Harness Java2D chạy đúng pipeline trên một trang manhwa tổng hợp, xuất ảnh PNG để kiểm tra
```

Toàn bộ phần khó (mask, inpaint, layout, ghép vùng) nằm trong module JVM thuần nên chạy được
unit test và chạy được trên desktop. Module `demo` dùng chính `TextLayoutEngine`,
`TextMaskBuilder`, `TextEraser` của app để dựng ảnh minh chứng.

## Build

Yêu cầu: JDK 17+, Android SDK 35.

```bash
export ANDROID_HOME=$HOME/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug      # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:test              # unit test thuật toán
./gradlew :demo:run               # chạy pipeline trên trang mẫu, ghi ảnh vào ./out
```

`assembleDebug` tạo APK riêng cho từng kiến trúc CPU (`app-arm64-v8a-debug.apk` ~50 MB dùng cho
hầu hết điện thoại) và một APK universal (~128 MB). CI trên GitHub cũng build và đính kèm APK.

Chạy thử đúng pipeline trên một trang manhwa thật (cần file ảnh và file JSON hộp chữ OCR, xem
`testdata/ocr.py`; thư mục này không được commit vì là ảnh truyện có bản quyền):

```bash
./gradlew :demo:realPage -Pargs="testdata/page_scan.jpg testdata/page_scan.json out/real"
```

Kết quả ghi ra `out/real/*-source.png`, `*-erased.png`, `*-translated.png` kèm số đo.

## Cài đặt

1. Mở APK, cấp quyền lưu ảnh nếu máy chạy Android 9 trở xuống.
2. Vào **Cài đặt → Nhà cung cấp dịch**. Mặc định là ML Kit trên máy: miễn phí, chỉ cần mạng
   lần đầu để tải gói ngôn ngữ (~30 MB). Muốn chất lượng cao hơn thì nhập API key Google Cloud,
   DeepL, Gemini hoặc OpenAI.
3. Chọn **Chữ trên ảnh** (Hàn/Nhật/Trung/Latinh) nếu truyện chỉ có một ngôn ngữ — nhận diện sẽ
   nhanh hơn nhiều so với chế độ Tự động.
4. Về trang chủ, chọn ảnh hoặc chụp ảnh. Ứng dụng xử lý rồi mở trình xem.

## Đo chất lượng trên trang thật

Bộ test chạy trên ba trang manhwa thật (1080×14419, 720×13718, 720×5498) qua đúng pipeline của
app, đo độ lệch chuẩn trong từng khung chữ trước và sau khi xoá:

| Trang | Vùng chữ | Xử lý | Bỏ qua (hiệu ứng) | Lệch chuẩn trước → sau | Vùng phẳng hẳn | Pixel đổi ngoài khung | Thời gian |
| --- | --- | --- | --- | --- | --- | --- | --- |
| page_scan (chụp, nền có vân) | 68 | 53 | 8 | 78.0 → 4.2 | 53/53 | 0 | 1.5 s |
| page_bw (đen trắng dài) | 23 | 20 | 1 | 87.8 → 4.1 | 20/20 | 0 | 0.9 s |
| page_color (màu) | 10 | 8 | 0 | 92.0 → 3.9 | 8/8 | 0 | 0.6 s |

Trang 14 419 px và 13 718 px ở trên được cắt thành 3 dải mỗi trang; số vùng xử lý vẫn đúng bằng
khi chạy một lượt, nên việc cắt dải không làm mất hay nhân đôi vùng chữ.

Cùng lượt chạy đo thêm hai thứ: mực chữ còn sót trong các khung (162 844 → 778 pixel ở trang chụp,
102 114 → 58 ở trang đen trắng, 24 349 → 0 ở trang màu) và số vùng có chữ vẽ tràn ra ngoài khung
(0/53, 0/20, 0/8).

"Pixel đổi ngoài khung" = số pixel bị sửa nằm ngoài mọi hộp chữ đã nhận — bằng 0 nghĩa là thuật
toán không đụng tới phần hình vẽ còn lại của trang.

Trên trang mẫu tổng hợp (caption, bốn bong bóng thoại kể cả bong bóng nền đen, và một khung chữ
dài) `:demo:run` đếm số pixel "mực" trong từng khung chữ — pixel lệch quá 60 đơn vị độ sáng so với
màu nền của chính khung đó, nên bong bóng nền đen cũng được đo đúng:

```
#0 mực 1187 -> 0    #1 mực 5310 -> 0    #2 mực 4018 -> 0    #4 mực 6502 -> 0
#5 mực 3241 -> 0    #6 mực 768  -> 0    #7 mực 8317 -> 0
Tổng: 29343 -> 0
```

## Giới hạn đã biết

- ML Kit không có bounding box theo từng ký tự; vùng nhận diện là theo dòng/khối. Bong bóng có
  chữ xoay nghiêng hoặc chữ lồng trong hình vẽ phức tạp có thể bị xoá lem.
- ML Kit không dịch tốt văn phong truyện; nên dùng provider LLM nếu cần chất lượng.
- Bản dịch dài hơn nhiều so với gốc sẽ thu nhỏ cỡ chữ; tăng "Mở rộng khung chữ" trong Cài đặt.
- Chưa hỗ trợ chữ dọc (manga Nhật) — hiện vẽ ngang.
- Chưa chạy thử trên máy thật: toàn bộ số đo ở trên đến từ unit test và harness JVM. Chất lượng
  OCR của ML Kit, hành vi WebView và việc chặn quảng cáo cần kiểm chứng trên điện thoại.
