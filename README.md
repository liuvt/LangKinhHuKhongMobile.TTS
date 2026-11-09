# Lăng Kính Hư Không Mobile TTS (`LangKinhHuKhongMobile.TTS`)

Phần mềm chỉnh sửa Video Short `9:16` (tối đa 3 phút) dạng Part kiểu **CapCut** trên Android, nổi bật với tính năng **chuyển Text thành Giọng đọc AI (Voice) kèm Sub phụ đề tự động**:

## Đáp ứng đầy đủ các tính năng theo yêu cầu
1. **Input Video MP4 tỉ lệ `9:16` ($\le 3$ phút) & Cắt ngắn Video / Tắt-Bật tiếng gốc:**
   - Giới hạn tối đa 3 phút (`180.000 ms`), hiển thị chuẩn khung dọc `9:16`.
   - **Cắt ngắn Video (`Trim Đầu / Trim Đuôi`):** Cắt bỏ phần đầu hoặc phần đuôi của Video ngay tại vạch Playhead (`trimStartMs` $\rightarrow$ `trimEndMs`) hoặc khôi phục lại độ dài gốc.
   - **Tắt / Bật Voice gốc của file Video MP4 (`muteOriginalAudio`):** Nút `🔊 / 🔇` ngay trên thanh Header và đầu Track Video để tắt hoặc bật âm thanh gốc của file MP4.
2. **Input Text chuyển thành Voices + Có Sub Phụ Đề kèm theo (`Sherpa-ONNX` + 5 Bộ Model thật):**
   - Tích hợp 5 bộ model thật từ `Voices/` (tự động sinh `tokens.txt` 161 âm vị từ `.onnx.json` qua [`PiperModelConfigParser`](app/src/main/java/vn/langkinhhukhong/tts/tts/PiperModelConfigParser.kt)):
     - 2 giọng cài sẵn miễn phí: `Nam - Minh Khang`, `Nữ - Ngọc Huyền`.
     - 7 giọng VIP mở khóa bằng quảng cáo: `Nam - Lạc Phi`, `Nam - Minh Quang`, và bộ 5 giọng `VPBank CSA v3` (`speakerId = 0..4`).
   - Tự động căn chỉnh mốc thời gian từng từ tiếng Việt (`SubtitleWord`) và hiển thị phụ đề Karaoke ngay khi chuyển Text thành Voice.
3. **Cắt file MP3 Voice thành nhiều đoạn (Part) & Kéo thả khớp với Video tùy ý:**
   - Cắt (`Split`) đoạn MP3 Voice tại vạch Playhead thành nhiều đoạn nhỏ độc lập $\rightarrow$ cả âm thanh lẫn phụ đề đi kèm tự động tách đôi tương ứng.
   - Kéo thả từng đoạn Voice đến bất kỳ vị trí nào trên Timeline để khớp với hình ảnh Video; khi xuất MP4, [`VideoExportManager`](app/src/main/java/vn/langkinhhukhong/tts/media/VideoExportManager.kt) tự động trộn đúng mốc thời gian `timelineStartMs` của từng đoạn.
4. **Nhập Chữ (`Text Overlay`) & Nhập Icon / Logo `PNG` (`Image Overlay`):**
   - Cho phép chèn nhiều lớp **Chữ (Text)** và **Icon / Logo PNG** (từ bộ nhớ máy hoặc tạo nhanh PNG trong suốt).
   - Chia dạng Part trên Timeline, hỗ trợ kéo thả thời điểm xuất hiện và cắt đôi tại Playhead.
5. **Làm mờ Chữ (`Text`) & Ảnh `PNG` (`Opacity` + `Gaussian Blur`):**
   - Khi chạm chọn bất kỳ lớp **Chữ (Text)** hoặc **Icon / Logo PNG** nào trên Khu View `9:16` hoặc trên Timeline, thanh công cụ chỉnh nhanh sẽ hiện ra gồm:
     - **Độ rõ / Làm mờ trong suốt (`Opacity`: `5%` $\rightarrow$ `100%`)**
     - **Làm mờ nhòe (`Blur`: `0dp` $\rightarrow$ `25dp`)**
     - **Kích thước (`Cỡ chữ` / `Size Logo PNG`)**
6. **Khu View `9:16` & Kéo thả vị trí bất kỳ (2D Free Drag):**
   - Trên Khu View `9:16` ([`Vertical9By16PreviewStage`](app/src/main/java/vn/langkinhhukhong/tts/ui/CapCutTtsEditorScreen.kt)), người dùng có thể dùng ngón tay **kéo thả trực tiếp đến vị trí bất kỳ (X, Y)** đối với:
     - Các lớp **Chữ (`Text Overlay`)**
     - Các lớp **Icon / Logo `PNG`**
     - Khung **Phụ đề Voice AI (`Subtitle Box`)**
