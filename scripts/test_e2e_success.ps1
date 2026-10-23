# ==============================================================================
# SCRIPT KIỂM THỬ E2E SUCCESS — CHUẨN HÓA VÀ BẢO VỆ CHẶT CHẼ
# File: scripts/test_e2e_success.ps1
# ==============================================================================

# --- 1. THAM SỐ CẤU HÌNH ĐƠN HÀNG VÀ HỆ THỐNG ---
$TargetOrderCode = "VM001-20261020-2103"
$TargetTransferRef = "VM0015UGY"
$ExpectedOrderId = "6f83c3ec-36c3-4b6e-a199-fafd586b7992"
$ExpectedProductId = "550e8400-e29b-41d4-a716-446655440001"
$ExpectedAmount = 8000
$TxId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()

$env:PGPASSWORD = "vending_dev_password"
$env:PGCLIENTENCODING = "UTF8"
$psqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe"

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "BƯỚC 1: XÁC MINH DỮ LIỆU THỜI GIAN THỰC TRONG POSTGRESQL TRƯỚC KHI GỬI" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

# 1. Truy vấn kiểm tra tiền trạm dữ liệu đơn hàng, sản phẩm và giao dịch hiện hữu
$checkQuery = @"
SELECT 
    o.id,
    o.status,
    o.total_amount_vnd,
    o.transfer_reference,
    o.product_id,
    ROUND(EXTRACT(EPOCH FROM (o.payment_expires_at - NOW()))) AS remaining_sec,
    (SELECT COUNT(*) FROM payment_transactions WHERE provider_transaction_id = '$TxId') AS tx_exists,
    p.stock_quantity,
    p.reserved_quantity,
    (SELECT COUNT(*) FROM payment_transactions WHERE order_id = o.id) AS existing_tx_count,
    (SELECT COUNT(*) FROM dispense_commands WHERE order_id = o.id) AS existing_cmd_count,
    o.payment_expires_at,
    NOW() as current_db_time,
    ROUND(EXTRACT(EPOCH FROM NOW())) AS current_epoch
FROM orders o
JOIN products p ON o.product_id = p.id
WHERE o.public_order_code = '$TargetOrderCode';
"@

$rawResult = & $psqlPath -U vending_user -d vending_machine_dev -h localhost -t -A -F "|" -c $checkQuery

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rawResult)) {
    Write-Host "[CHẶN] Truy vấn PostgreSQL thất bại hoặc không tìm thấy đơn '$TargetOrderCode'!" -ForegroundColor Red
    Write-Host "--> DỪNG TOÀN BỘ. Tuyệt đối không gửi webhook." -ForegroundColor Red
    exit 1
}

$lines = $rawResult.Trim() -split "`r?`n"
if ($lines.Count -ne 1) {
    Write-Host "[CHẶN] Bất thường: DB trả về $($lines.Count) dòng thay vì đúng 1 dòng duy nhất!" -ForegroundColor Red
    Write-Host "--> DỪNG TOÀN BỘ. Tuyệt đối không gửi webhook." -ForegroundColor Red
    exit 1
}

$fields = $lines[0].Split('|')
if ($fields.Count -lt 14) {
    Write-Host "[CHẶN] Dữ liệu trả về từ PostgreSQL không đủ 14 cột thông tin (chỉ có $($fields.Count))!" -ForegroundColor Red
    Write-Host "--> DỪNG TOÀN BỘ. Tuyệt đối không gửi webhook." -ForegroundColor Red
    exit 1
}

$actualOrderId = $fields[0]
$orderStatus = $fields[1]
$amount = [int]$fields[2]
$actualRef = $fields[3]
$actualProductId = $fields[4]
$remainingSec = [int]$fields[5]
$txExistsCount = [int]$fields[6]
$initStock = [int]$fields[7]
$initReserved = [int]$fields[8]
$existingTxCount = [int]$fields[9]
$existingCmdCount = [int]$fields[10]
$expiresAt = $fields[11]
$dbCurrentTime = $fields[12]
$dbStartEpoch = [double]$fields[13]

# Lưu giá trị tồn kho ban đầu phục vụ cả hiển thị log và đối chiếu delta
$stockQty = $initStock
$reservedQty = $initReserved

Write-Host "Mã đơn (Code):         $TargetOrderCode"
Write-Host "Order ID:              $actualOrderId"
Write-Host "Product ID:            $actualProductId"
Write-Host "Trạng thái đơn:        $orderStatus"
Write-Host "Số tiền:               $amount VND"
Write-Host "Transfer Reference:    $actualRef"
Write-Host "Thời điểm hết hạn:     $expiresAt"
Write-Host "Thời gian DB hiện tại: $dbCurrentTime (Epoch: $dbStartEpoch)"
Write-Host "Thời hạn còn lại:      $remainingSec giây (~$([Math]::Round($remainingSec / 60, 1)) phút)"
Write-Host "Tồn kho ban đầu:       Kho vật lý = $initStock | Giữ chỗ = $initReserved | Khả dụng = $($initStock - $initReserved)"
Write-Host "Giao dịch hiện có:     $existingTxCount bản ghi"
Write-Host "Lệnh nhả hiện có:      $existingCmdCount bản ghi"
Write-Host "Mã Tx giả lập mới:     $TxId (Trùng lặp trong DB: $txExistsCount)"

# --- 2. KIỂM TRA ĐIỀU KIỆN TIỀN TRẠM BẮT BUỘC (GUARD CHECKS) ---
$canProceed = $true

if ($actualOrderId -ne $ExpectedOrderId) {
    Write-Host "[CHẶN] Order ID không khớp! DB: $actualOrderId | Kỳ vọng: $ExpectedOrderId" -ForegroundColor Red
    $canProceed = $false
}
if ($actualProductId -ne $ExpectedProductId) {
    Write-Host "[CHẶN] Product ID không khớp! DB: $actualProductId | Kỳ vọng: $ExpectedProductId" -ForegroundColor Red
    $canProceed = $false
}
if ($orderStatus -ne "PENDING_PAYMENT") {
    Write-Host "[CHẶN] Trạng thái đơn không phải PENDING_PAYMENT (hiện tại: $orderStatus)!" -ForegroundColor Red
    $canProceed = $false
}
if ($remainingSec -le 0) {
    Write-Host "[CHẶN] Đơn hàng đã hết hạn thanh toán (quá hạn $remainingSec giây)!" -ForegroundColor Red
    $canProceed = $false
}
if ($actualRef -ne $TargetTransferRef) {
    Write-Host "[CHẶN] Transfer reference không khớp: DB là '$actualRef' nhưng kỳ vọng '$TargetTransferRef'!" -ForegroundColor Red
    $canProceed = $false
}
if ($amount -ne $ExpectedAmount) {
    Write-Host "[CHẶN] Số tiền không khớp: DB là $amount nhưng kỳ vọng $ExpectedAmount!" -ForegroundColor Red
    $canProceed = $false
}
if ($initStock -lt 1) {
    Write-Host "[CHẶN] Tồn kho vật lý ban đầu không hợp lệ (hiện tại: $initStock < 1)!" -ForegroundColor Red
    $canProceed = $false
}
if (($initStock - $initReserved) -lt 1) {
    Write-Host "[CHẶN] Hàng khả dụng không đạt yêu cầu: initStock ($initStock) - initReserved ($initReserved) = $($initStock - $initReserved) < 1!" -ForegroundColor Red
    $canProceed = $false
}
if ($txExistsCount -gt 0) {
    Write-Host "[CHẶN] Mã giao dịch $TxId đã tồn tại trong database (nguy cơ trùng lặp)!" -ForegroundColor Red
    $canProceed = $false
}
if ($existingTxCount -ne 0) {
    Write-Host "[CHẶN] Đơn hàng đã có $existingTxCount giao dịch thanh toán từ trước (không còn là đơn mới)!" -ForegroundColor Red
    $canProceed = $false
}
if ($existingCmdCount -ne 0) {
    Write-Host "[CHẶN] Đơn hàng đã có $existingCmdCount lệnh nhả hàng từ trước (không còn là đơn mới)!" -ForegroundColor Red
    $canProceed = $false
}

if (-not $canProceed) {
    Write-Host "`n======================================================================" -ForegroundColor Red
    Write-Host "ĐIỀU KIỆN TIỀN TRẠM KHÔNG ĐẠT. DỪNG LẠI HOÀN TOÀN, KHÔNG GỬI WEBHOOK!" -ForegroundColor Red
    Write-Host "======================================================================" -ForegroundColor Red
    exit 1
}

Write-Host "`n--> TẤT CẢ 11 ĐIỀU KIỆN TIỀN TRẠM ĐẠT CHUẨN! BẮT ĐẦU GỬI DUY NHẤT 1 WEBHOOK..." -ForegroundColor Green

# ==============================================================================
# BƯỚC 2: GỬI DUY NHẤT 1 WEBHOOK SEPAY GIẢ LẬP (BẢO VỆ NGOẠI LỆ HTTP)
# ==============================================================================
$webhookPayload = @{
    id              = [long]$TxId
    gateway         = "MBBank"
    transactionDate = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    accountNumber   = "DEMO123456"
    subAccount      = $null
    transferType    = "in"
    transferAmount  = $amount
    code            = $actualRef
    content         = "Thanh toan don $actualRef"
    description     = "Thanh toan don $actualRef"
    referenceCode   = "MB$TxId"
} | ConvertTo-Json

$headers = @{
    "Authorization" = "Apikey test-sepay-secret-key"
}

$webhookExecuted = $false
$webhookHttpOk = $false
$webhookResponse = $null

try {
    $webhookExecuted = $true
    $webhookResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/webhooks/payments/sepay" `
        -Method Post `
        -Headers $headers `
        -ContentType "application/json; charset=utf-8" `
        -Body $webhookPayload `
        -TimeoutSec 10

    $webhookHttpOk = $true
    Write-Host "`n[HTTP SUCCESS] Webhook phản hồi thành công:" -ForegroundColor Green
    $webhookResponse | ConvertTo-Json
}
catch {
    Write-Host "`n[HTTP EXCEPTION] Gửi Webhook phát sinh lỗi hoặc Timeout!" -ForegroundColor Red
    if ($_.Exception.Response) {
        $statusCode = [int]$_.Exception.Response.StatusCode
        $statusDesc = $_.Exception.Response.StatusDescription
        Write-Host "HTTP Status: $statusCode ($statusDesc)" -ForegroundColor Red
        try {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $errorBody = $reader.ReadToEnd()
            Write-Host "Response Error Body: $errorBody" -ForegroundColor Red
        }
        catch {
            Write-Host "Không thể đọc error body stream: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    else {
        Write-Host "Lỗi mạng hoặc Timeout: $($_.Exception.Message)" -ForegroundColor Red
    }
    Write-Host "--> DỪNG THAO TÁC GỬI LẠI. Chuyển sang bước hậu kiểm database để xác định trạng thái thực tế..." -ForegroundColor Yellow
}

# ==============================================================================
# BƯỚC 3: HẬU KIỂM TOÀN DIỆN DỮ LIỆU THỰC TẾ TRONG POSTGRESQL & SIMULATOR
# ==============================================================================
Write-Host "`n======================================================================" -ForegroundColor Cyan
Write-Host "BƯỚC 3: HẬU KIỂM DỮ LIỆU THỰC TẾ TRONG POSTGRESQL & SIMULATOR" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

$dbQueryErrorOccurred = $false

Write-Host "`n--- 1. BẢNG ORDERS ---" -ForegroundColor Yellow
& $psqlPath -U vending_user -d vending_machine_dev -h localhost -c `
    "SELECT id, public_order_code, status, paid_at, dispensing_started_at, completed_at FROM orders WHERE id = '$actualOrderId';"
if ($LASTEXITCODE -ne 0) { $dbQueryErrorOccurred = $true; Write-Host "[LỖI SQL] Truy vấn bảng orders thất bại!" -ForegroundColor Red }

Write-Host "`n--- 2. BẢNG PAYMENT_TRANSACTIONS ---" -ForegroundColor Yellow
& $psqlPath -U vending_user -d vending_machine_dev -h localhost -c `
    "SELECT provider_transaction_id, amount_vnd, verification_status, transaction_status, received_at FROM payment_transactions WHERE order_id = '$actualOrderId' OR provider_transaction_id = '$TxId';"
if ($LASTEXITCODE -ne 0) { $dbQueryErrorOccurred = $true; Write-Host "[LỖI SQL] Truy vấn bảng payment_transactions thất bại!" -ForegroundColor Red }

Write-Host "`n--- 3. BẢNG DISPENSE_COMMANDS ---" -ForegroundColor Yellow
& $psqlPath -U vending_user -d vending_machine_dev -h localhost -c `
    "SELECT command_id, slot_number, status, attempt_count, failure_code, published_at, finished_at FROM dispense_commands WHERE order_id = '$actualOrderId';"
if ($LASTEXITCODE -ne 0) { $dbQueryErrorOccurred = $true; Write-Host "[LỖI SQL] Truy vấn bảng dispense_commands thất bại!" -ForegroundColor Red }

Write-Host "`n--- 4. BẢNG DEVICE_EVENTS (SỰ KIỆN TỪ THIẾT BỊ MÔ PHỎNG) ---" -ForegroundColor Yellow
& $psqlPath -U vending_user -d vending_machine_dev -h localhost -c `
    "SELECT event_id, command_id, event_type, slot_number, payload, created_at FROM device_events WHERE command_id IN (SELECT command_id FROM dispense_commands WHERE order_id = '$actualOrderId') ORDER BY created_at ASC;"
if ($LASTEXITCODE -ne 0) { $dbQueryErrorOccurred = $true; Write-Host "[LỖI SQL] Truy vấn bảng device_events thất bại!" -ForegroundColor Red }

Write-Host "`n--- 5. BẢNG PRODUCTS (TỒN KHO CỦA ĐÚNG SẢN PHẨM NÀY) ---" -ForegroundColor Yellow
& $psqlPath -U vending_user -d vending_machine_dev -h localhost -c `
    "SELECT id, slot_number, name, stock_quantity, reserved_quantity, (stock_quantity - reserved_quantity) AS available_stock FROM products WHERE id = '$actualProductId';"
if ($LASTEXITCODE -ne 0) { $dbQueryErrorOccurred = $true; Write-Host "[LỖI SQL] Truy vấn bảng products thất bại!" -ForegroundColor Red }

Write-Host "`n--- 6. KIỂM TRA PHÁT HIỆN THAO TÁC ĐỒNG THỜI TRÊN CÙNG SẢN PHẨM ---" -ForegroundColor Yellow
& $psqlPath -U vending_user -d vending_machine_dev -h localhost -c `
    "SELECT id, public_order_code, status, updated_at FROM orders WHERE product_id = '$actualProductId' AND id != '$actualOrderId' AND updated_at >= to_timestamp($dbStartEpoch);"
if ($LASTEXITCODE -ne 0) { $dbQueryErrorOccurred = $true; Write-Host "[LỖI SQL] Truy vấn kiểm tra đơn hàng đồng thời thất bại!" -ForegroundColor Red }

Write-Host "`n--- 7. TRẠNG THÁI SIMULATOR ADAPTER ---" -ForegroundColor Yellow
try {
    $simStatus = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/simulation/device/status" -Method Get
    $simStatus | ConvertTo-Json -Depth 3
}
catch {
    Write-Host "[CẢNH BÁO] Không thể đọc trạng thái simulator adapter: $($_.Exception.Message)" -ForegroundColor Red
}

# ==============================================================================
# BƯỚC 4: TỰ ĐỘNG ĐÁNH GIÁ 7 TIÊU CHÍ NGHIỆM THU E2E SUCCESS DỰA TRÊN DATABASE
# ==============================================================================
Write-Host "`n======================================================================" -ForegroundColor Cyan
Write-Host "BƯỚC 4: KẾT LUẬN NGHIỆM THU E2E SUCCESS DỰA TRÊN DỮ LIỆU ĐỐI CHIẾU" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

$evalQuery = @"
SELECT 
    o.status AS order_status,
    (SELECT COUNT(*) FROM payment_transactions WHERE order_id = o.id) AS tx_total_count,
    (SELECT COUNT(*) FROM payment_transactions WHERE order_id = o.id AND provider_transaction_id = '$TxId') AS tx_matches_txid_count,
    CASE 
        WHEN (SELECT COUNT(*) FROM payment_transactions WHERE order_id = o.id) = 0 THEN 'NONE'
        WHEN (SELECT COUNT(*) FROM payment_transactions WHERE order_id = o.id) = 1 
             THEN (SELECT verification_status FROM payment_transactions WHERE order_id = o.id)
        ELSE 'MULTIPLE'
    END AS tx_status,
    (SELECT COUNT(*) FROM dispense_commands WHERE order_id = o.id) AS cmd_total_count,
    CASE 
        WHEN (SELECT COUNT(*) FROM dispense_commands WHERE order_id = o.id) = 0 THEN 'NONE'
        WHEN (SELECT COUNT(*) FROM dispense_commands WHERE order_id = o.id) = 1 
             THEN (SELECT status FROM dispense_commands WHERE order_id = o.id)
        ELSE 'MULTIPLE'
    END AS cmd_status,
    (SELECT COUNT(*) FROM dispense_commands WHERE order_id = o.id AND status != 'COMPLETED') AS cmd_non_completed_count,
    p.stock_quantity,
    p.reserved_quantity,
    (SELECT COUNT(*) FROM orders WHERE product_id = p.id AND id != o.id AND updated_at >= to_timestamp($dbStartEpoch)) AS concurrent_modified_count
FROM orders o
JOIN products p ON o.product_id = p.id
WHERE o.id = '$actualOrderId';
"@

$evalRaw = & $psqlPath -U vending_user -d vending_machine_dev -h localhost -t -A -F "|" -c $evalQuery

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($evalRaw) -or $dbQueryErrorOccurred) {
    Write-Host "----------------------------------------------------------------------" -ForegroundColor Red
    Write-Host "[KẾT LUẬN CUỐI CÙNG: E2E CHƯA ĐẠT / CẦN ĐIỀU TRA]" -ForegroundColor Red
    Write-Host "Lý do: Lệnh truy vấn PostgreSQL thất bại hoặc có lỗi xảy ra trong quá trình đọc cơ sở dữ liệu!" -ForegroundColor Red
    Write-Host "----------------------------------------------------------------------" -ForegroundColor Red
    exit 1
}

$evalLines = $evalRaw.Trim() -split "`r?`n"
if ($evalLines.Count -ne 1) {
    Write-Host "----------------------------------------------------------------------" -ForegroundColor Red
    Write-Host "[KẾT LUẬN CUỐI CÙNG: E2E CHƯA ĐẠT / CẦN ĐIỀU TRA]" -ForegroundColor Red
    Write-Host "Lý do: Truy vấn đánh giá trả về $($evalLines.Count) dòng thay vì đúng 1 dòng duy nhất!" -ForegroundColor Red
    Write-Host "----------------------------------------------------------------------" -ForegroundColor Red
    exit 1
}

$ev = $evalLines[0].Split('|')
if ($ev.Count -ne 10) {
    Write-Host "----------------------------------------------------------------------" -ForegroundColor Red
    Write-Host "[KẾT LUẬN CUỐI CÙNG: E2E CHƯA ĐẠT / CẦN ĐIỀU TRA]" -ForegroundColor Red
    Write-Host "Lý do: Dữ liệu phân tích không đủ 10 trường thông tin (chỉ nhận được $($ev.Count) trường)!" -ForegroundColor Red
    Write-Host "----------------------------------------------------------------------" -ForegroundColor Red
    exit 1
}

$finalOrderStatus = $ev[0]
$finalTxTotalCount = [int]$ev[1]
$finalTxMatchesTxIdCount = [int]$ev[2]
$finalTxStatus = $ev[3]
$finalCmdTotalCount = [int]$ev[4]
$finalCmdStatus = $ev[5]
$finalCmdNonCompletedCount = [int]$ev[6]
$finalStock = [int]$ev[7]
$finalReserved = [int]$ev[8]
$concurrentModifiedCount = [int]$ev[9]

$expectedFinalStock = $initStock - 1
$expectedFinalReserved = $initReserved - 1

# Đánh giá 7 tiêu chí nghiệm thu chặt chẽ (Delta Tồn kho):
$crit1_OrderCompleted = ($finalOrderStatus -eq "COMPLETED")
$crit2_SingleTxMatched = ($finalTxTotalCount -eq 1 -and $finalTxMatchesTxIdCount -eq 1)
$crit3_TxVerified = ($finalTxStatus -eq "VERIFIED")
$crit4_SingleCmd = ($finalCmdTotalCount -eq 1)
$crit5_CmdCompleted = ($finalCmdStatus -eq "COMPLETED")
$crit6_NoFailedCmd = ($finalCmdNonCompletedCount -eq 0)
$crit7_InventoryDelta = ($finalStock -eq $expectedFinalStock -and $finalReserved -eq $expectedFinalReserved)

$allPassed = $crit1_OrderCompleted -and $crit2_SingleTxMatched -and $crit3_TxVerified -and `
    $crit4_SingleCmd -and $crit5_CmdCompleted -and $crit6_NoFailedCmd -and `
    $crit7_InventoryDelta -and (-not $dbQueryErrorOccurred)

Write-Host "Chi tiết đối chiếu 7 tiêu chí nghiệm thu:"
Write-Host " 1. Trạng thái đơn hàng:         $finalOrderStatus $(if ($crit1_OrderCompleted) {'[ĐẠT]'} else {'[KHÔNG ĐẠT - Kỳ vọng: COMPLETED]'})" -ForegroundColor $(if ($crit1_OrderCompleted) { 'Green' } else { 'Red' })
Write-Host " 2. Giao dịch duy nhất khớp Tx:  Tổng=$finalTxTotalCount, Khớp Tx=$finalTxMatchesTxIdCount $(if ($crit2_SingleTxMatched) {'[ĐẠT]'} else {'[BẤT THƯỜNG - Không đúng 1 giao dịch hợp lệ]'})" -ForegroundColor $(if ($crit2_SingleTxMatched) { 'Green' } else { 'Red' })
Write-Host " 3. Xác minh thanh toán:         $finalTxStatus $(if ($crit3_TxVerified) {'[ĐẠT]'} else {'[KHÔNG ĐẠT - Kỳ vọng: VERIFIED]'})" -ForegroundColor $(if ($crit3_TxVerified) { 'Green' } else { 'Red' })
Write-Host " 4. Số lệnh nhả hàng:            $finalCmdTotalCount $(if ($crit4_SingleCmd) {'[ĐẠT]'} else {'[BẤT THƯỜNG - Có nhiều hơn 1 lệnh nhả hàng]'})" -ForegroundColor $(if ($crit4_SingleCmd) { 'Green' } else { 'Red' })
Write-Host " 5. Trạng thái lệnh nhả:         $finalCmdStatus $(if ($crit5_CmdCompleted) {'[ĐẠT]'} else {'[KHÔNG ĐẠT - Kỳ vọng: COMPLETED]'})" -ForegroundColor $(if ($crit5_CmdCompleted) { 'Green' } else { 'Red' })
Write-Host " 6. Lệnh lỗi/chưa xong:          $finalCmdNonCompletedCount $(if ($crit6_NoFailedCmd) {'[ĐẠT]'} else {'[CẢNH BÁO - Có lệnh lỗi/treo]'})" -ForegroundColor $(if ($crit6_NoFailedCmd) { 'Green' } else { 'Red' })
Write-Host " 7. Tồn kho sau xuất hàng (Delta): Kho=$finalStock (ban đầu: $initStock -> giảm 1 = $expectedFinalStock), Giữ chỗ=$finalReserved (ban đầu: $initReserved -> giảm 1 = $expectedFinalReserved) $(if ($crit7_InventoryDelta) {'[ĐẠT]'} else {"[CẦN ĐIỀU TRA - Kỳ vọng: Kho=$expectedFinalStock, Giữ chỗ=$expectedFinalReserved]"})" -ForegroundColor $(if ($crit7_InventoryDelta) { 'Green' } else { 'Red' })

Write-Host "`nKiểm tra phát hiện giao dịch đồng thời trên cùng sản phẩm:"
if ($concurrentModifiedCount -eq 0) {
    Write-Host " - Thao tác đồng thời:           0 đơn khác thay đổi [ĐẠT - Môi trường không có nhiễu đồng thời]" -ForegroundColor Green
} else {
    Write-Host " - Thao tác đồng thời:           $concurrentModifiedCount đơn khác thay đổi [CẢNH BÁO - Có thao tác đồng thời trên cùng sản phẩm]" -ForegroundColor Yellow
    Write-Host "   (GIỚI HẠN: Kiểm tra delta tồn kho chỉ an toàn tuyệt đối khi không có giao dịch song song trên cùng sản phẩm!)" -ForegroundColor Yellow
}

Write-Host "----------------------------------------------------------------------"
if ($allPassed) {
    Write-Host "[KẾT LUẬN CUỐI CÙNG: E2E SUCCESS — MÔ PHỎNG ĐẠT]" -ForegroundColor Green
    Write-Host "Hệ thống đã nhận webhook, đối soát thanh toán, nhả hàng qua thiết bị mô phỏng (SimulatedDeviceAdapter) và khấu trừ kho theo delta chính xác!" -ForegroundColor Green
    Write-Host "(LƯU Ý 1: Đây là kết quả xác minh trên bộ mô phỏng phần mềm, chưa xác minh trên phần cứng vật lý ESP32/động cơ thật)." -ForegroundColor Yellow
    Write-Host "(LƯU Ý 2: Kiểm tra delta tồn kho giả định không có đơn hàng song song nào khác can thiệp vào cùng sản phẩm trong suốt quá trình chạy test)." -ForegroundColor Yellow
}
else {
    Write-Host "[KẾT LUẬN CUỐI CÙNG: E2E CHƯA ĐẠT / CẦN ĐIỀU TRA]" -ForegroundColor Red
    Write-Host "Một hoặc nhiều tiêu chí nghiệp vụ chưa thỏa mãn. Vui lòng kiểm tra log backend chi tiết!" -ForegroundColor Red
}
Write-Host "----------------------------------------------------------------------"
