package com.manhwatranslate.core.model

/** Where the lettering boxes come from. */
enum class OcrProviderType(
    val label: String,
    val hint: String,
) {
    ML_KIT(
        label = "ML Kit trên máy",
        hint = "Chạy offline, không cần server. Nhận diện theo dòng chữ, đôi khi tách một bong bóng thành nhiều khối.",
    ),
    PADDLE_OCR(
        label = "PaddleOCR (server)",
        hint = "Gửi ảnh tới server PaddleOCR Hub Serving bạn tự chạy (Docker). Bắt chữ tốt hơn trên nền có hình, cần mạng tới server.",
    ),
}
