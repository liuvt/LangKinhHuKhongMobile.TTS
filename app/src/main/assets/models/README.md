# Danh sách 5 Bộ Giọng Đọc AI Piper/VITS Tiếng Việt (`Sherpa-ONNX`)

Toàn bộ 5 bộ model trong thư mục `Voices/` đã được tự động sinh `tokens.txt` (161 ký hiệu âm vị từ `phoneme_id_map` trong `.onnx.json`) và đồng bộ vào `app/src/main/assets/models/`:

1. **Giọng mặc định #1 (`Nam - Minh Khang`):**
   - `app/src/main/assets/models/minhkhang/minhkhang.onnx`
   - `app/src/main/assets/models/minhkhang/minhkhang.onnx.json`
   - `app/src/main/assets/models/minhkhang/tokens.txt`

2. **Giọng mặc định #2 (`Nữ - Ngọc Huyền`):**
   - `app/src/main/assets/models/ngochuyenupdate/ngochuyennew(update).onnx`
   - `app/src/main/assets/models/ngochuyenupdate/ngochuyennew(update).onnx.json`
   - `app/src/main/assets/models/ngochuyenupdate/tokens.txt`

3. **Giọng VIP #3 (`Nam - Lạc Phi`):**
   - `app/src/main/assets/models/lacphi/lacphi.onnx`
   - `app/src/main/assets/models/lacphi/lacphi.onnx.json`
   - `app/src/main/assets/models/lacphi/tokens.txt`

4. **Giọng VIP #4 (`Nam - Minh Quang`):**
   - `app/src/main/assets/models/minhquang/minhquang.onnx`
   - `app/src/main/assets/models/minhquang/minhquang.onnx.json`
   - `app/src/main/assets/models/minhquang/tokens.txt`

5. **Bộ Đa Giọng VIP #5 (`VPBank CSA v3` - 5 Speakers `speakerId = 0..4`):**
   - `app/src/main/assets/models/VPBank/vi_VN-csa-voice-piper-v3-medium.onnx`
   - `app/src/main/assets/models/VPBank/vi_VN-csa-voice-piper-v3-medium.onnx.json`
   - `app/src/main/assets/models/VPBank/tokens.txt`
   - Hỗ trợ 5 giọng đọc độc lập (`Speaker #0` đến `Speaker #4`) dùng chung 1 file `.onnx` duy nhất.
