package com.paylinker.api.campaign.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paylinker.api.campaign.dto.response.RecipientUploadResponse;
import com.paylinker.api.campaign.dto.response.RecipientValidationErrorItem;
import com.paylinker.api.campaign.repository.CampaignRecipientRepository;
import com.paylinker.api.campaign.repository.CampaignRepository;
import com.paylinker.api.campaign.repository.UploadBatchRepository;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
public class CampaignRecipientService {

    private final CampaignRepository campaignRepository;
    private final UploadBatchRepository uploadBatchRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.upload-bucket}")
    private String uploadBucket;

    @Value("${paylinker.campaign-limit.max-recipients}")
    private int maxRecipients;

    @Value("${paylinker.upload-batch.file-storage-key}")
    private String fileStorageKey;

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final List<String> REQUIRED_HEADERS = List.of("employeeno", "name", "department", "email");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public RecipientUploadResponse uploadRecipients(String campaignId, MultipartFile file,
                                                    String uploadType, String adminId) {
        // 캠페인 존재 및 소유자 검증
        Map<String, AttributeValue> campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND));
        String createdBy = campaign.getOrDefault("admin_id", AttributeValue.fromS("")).s();
        if (!adminId.equals(createdBy)) {
            throw new CustomException(ErrorCode.CAMPAIGN_FORBIDDEN);
        }

        // 파일 확장자 검증
        String filename = file.getOriginalFilename();
        boolean isCsv = filename != null && filename.toLowerCase().endsWith(".csv");
        boolean isXlsx = filename != null && filename.toLowerCase().endsWith(".xlsx");
        if (!isCsv && !isXlsx) {
            throw new CustomException(ErrorCode.RECIPIENT_FILE_UNSUPPORTED);
        }

        // 파일 크기 검증
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new CustomException(ErrorCode.RECIPIENT_FILE_TOO_LARGE);
        }

        String resolvedUploadType = (uploadType == null || uploadType.isBlank()) ? "FULL_REPLACE" : uploadType.trim();

        // 파일 파싱 (valid row 목록 + error 상세 목록 동시 수집)
        byte[] fileBytes = readFileBytes(file);
        ParseResult result = isCsv ? parseCsv(fileBytes) : parseXlsx(fileBytes);

        // 최대 수신자 수 검증
        if (result.totalRowCount() > maxRecipients) {
            throw new CustomException(ErrorCode.RECIPIENT_CAMPAIGN_LIMIT_EXCEEDED);
        }

        // S3 키 사전 결정
        String uploadBatchId = "ub_" + UUID.randomUUID();
        String fileS3Key = fileStorageKey + "/" + campaignId + "/" + uploadBatchId + "/" + filename;
        String errorsS3Key = fileStorageKey + "/" + campaignId + "/" + uploadBatchId + "/validation-errors.json";

        // ① DynamoDB 먼저 저장 (orphan 파일 방지)
        if ("FULL_REPLACE".equals(resolvedUploadType)) {
            campaignRecipientRepository.deleteAllByCampaignId(campaignId);
        }
        campaignRecipientRepository.saveAll(toRecipientItems(result.validRows(), campaignId, uploadBatchId));
        uploadBatchRepository.save(uploadBatchId, campaignId,
                result.totalRowCount(), result.validRowCount(), result.errorRowCount(), result.duplicateRowCount(),
                resolvedUploadType, fileS3Key, errorsS3Key);

        // ② S3 업로드 (DynamoDB 성공 후)
        uploadToS3(fileBytes, fileS3Key);
        uploadErrorsJsonToS3(result.errorItems(), errorsS3Key);

        return RecipientUploadResponse.builder()
                .uploadBatchId(uploadBatchId)
                .campaignId(campaignId)
                .totalRowCount(result.totalRowCount())
                .validRowCount(result.validRowCount())
                .errorRowCount(result.errorRowCount())
                .duplicateRowCount(result.duplicateRowCount())
                .build();
    }

    // ── 파일 파싱 ─────────────────────────────────────────────────────────────

    private byte[] readFileBytes(MultipartFile file) {
        try {
            return file.getInputStream().readAllBytes();
        } catch (Exception e) {
            throw new CustomException(ErrorCode.RECIPIENT_FILE_PARSE_FAILED);
        }
    }

    private ParseResult parseCsv(byte[] bytes) {
        try {
            int offset = (bytes.length >= 3
                    && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) ? 3 : 0;
            Reader reader = new InputStreamReader(
                    new ByteArrayInputStream(bytes, offset, bytes.length - offset), StandardCharsets.UTF_8);
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreHeaderCase(true).build();
            try (CSVParser parser = format.parse(reader)) {
                validateHeaders(parser.getHeaderNames().stream().map(h -> h.toLowerCase().trim()).toList());
                return collectCsvRows(parser);
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.RECIPIENT_FILE_PARSE_FAILED);
        }
    }

    private ParseResult collectCsvRows(CSVParser parser) {
        Set<String> seenEmployeeNos = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        List<RecipientRow> validRows = new ArrayList<>();
        List<RecipientValidationErrorItem> errorItems = new ArrayList<>();
        int total = 0, duplicates = 0;

        for (CSVRecord record : parser) {
            total++;
            int rowNum = (int) record.getRecordNumber() + 1;
            String employeeNo = record.get("employeeno");
            String name = record.get("name");
            String department = record.get("department");
            String email = record.get("email");

            RecipientValidationErrorItem error = validateRow(
                    rowNum, employeeNo, name, department, email, seenEmployeeNos, seenEmails);
            if (error != null) {
                errorItems.add(error);
                if ("DUPLICATE".equals(error.errorType())) duplicates++;
                continue;
            }
            seenEmployeeNos.add(employeeNo.toLowerCase());
            seenEmails.add(email.toLowerCase());
            validRows.add(new RecipientRow(employeeNo, name, department, email));
        }
        int errors = errorItems.size() - duplicates;
        return new ParseResult(total, validRows.size(), errors, duplicates, validRows, errorItems);
    }

    private ParseResult parseXlsx(byte[] bytes) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return ParseResult.empty();
            Map<String, Integer> headerIndexMap = buildXlsxHeaderMap(headerRow);
            validateHeaders(new ArrayList<>(headerIndexMap.keySet()));
            return collectXlsxRows(sheet, headerIndexMap);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.RECIPIENT_FILE_PARSE_FAILED);
        }
    }

    private ParseResult collectXlsxRows(Sheet sheet, Map<String, Integer> headerIndexMap) {
        DataFormatter formatter = new DataFormatter();
        Set<String> seenEmployeeNos = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        List<RecipientRow> validRows = new ArrayList<>();
        List<RecipientValidationErrorItem> errorItems = new ArrayList<>();
        int total = 0, duplicates = 0;

        int empCol = headerIndexMap.get("employeeno");
        int nameCol = headerIndexMap.get("name");
        int deptCol = headerIndexMap.get("department");
        int emailCol = headerIndexMap.get("email");

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String employeeNo = formatter.formatCellValue(row.getCell(empCol)).trim();
            String name = formatter.formatCellValue(row.getCell(nameCol)).trim();
            String department = formatter.formatCellValue(row.getCell(deptCol)).trim();
            String email = formatter.formatCellValue(row.getCell(emailCol)).trim();
            if (employeeNo.isEmpty() && name.isEmpty() && department.isEmpty() && email.isEmpty()) continue;

            total++;
            int rowNum = i + 1;
            RecipientValidationErrorItem error = validateRow(
                    rowNum, employeeNo, name, department, email, seenEmployeeNos, seenEmails);
            if (error != null) {
                errorItems.add(error);
                if ("DUPLICATE".equals(error.errorType())) duplicates++;
                continue;
            }
            seenEmployeeNos.add(employeeNo.toLowerCase());
            seenEmails.add(email.toLowerCase());
            validRows.add(new RecipientRow(employeeNo, name, department, email));
        }
        int errors = errorItems.size() - duplicates;
        return new ParseResult(total, validRows.size(), errors, duplicates, validRows, errorItems);
    }

    // ── 검증 ──────────────────────────────────────────────────────────────────

    private RecipientValidationErrorItem validateRow(int rowNum, String employeeNo, String name,
                                                     String department, String email,
                                                     Set<String> seenEmployeeNos, Set<String> seenEmails) {
        if (isBlank(employeeNo)) return errorItem(rowNum, "employeeNo", "MISSING_FIELD", employeeNo, "필수 항목이 누락되었습니다.");
        if (isBlank(name))       return errorItem(rowNum, "name",       "MISSING_FIELD", name,       "필수 항목이 누락되었습니다.");
        if (isBlank(department)) return errorItem(rowNum, "department", "MISSING_FIELD", department, "필수 항목이 누락되었습니다.");
        if (isBlank(email))      return errorItem(rowNum, "email",      "MISSING_FIELD", email,      "필수 항목이 누락되었습니다.");
        if (!EMAIL_PATTERN.matcher(email).matches())
            return errorItem(rowNum, "email", "INVALID_EMAIL_FORMAT", email, "이메일 형식이 올바르지 않습니다.");
        if (seenEmployeeNos.contains(employeeNo.toLowerCase()))
            return errorItem(rowNum, "employeeNo", "DUPLICATE", employeeNo, "동일 사번이 파일 내에서 중복됩니다.");
        if (seenEmails.contains(email.toLowerCase()))
            return errorItem(rowNum, "email", "DUPLICATE", email, "동일 이메일이 파일 내에서 중복됩니다.");
        return null;
    }

    private void validateHeaders(List<String> headers) {
        if (REQUIRED_HEADERS.stream().anyMatch(h -> !headers.contains(h)))
            throw new CustomException(ErrorCode.RECIPIENT_FILE_UNSUPPORTED);
    }

    // ── S3 ────────────────────────────────────────────────────────────────────

    private void uploadToS3(byte[] fileBytes, String s3Key) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(uploadBucket).key(s3Key)
                            .contentLength((long) fileBytes.length).build(),
                    RequestBody.fromBytes(fileBytes));
        } catch (Exception e) {
            throw new CustomException(ErrorCode.RECIPIENT_S3_UPLOAD_FAILED);
        }
    }

    private void uploadErrorsJsonToS3(List<RecipientValidationErrorItem> errorItems, String s3Key) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(errorItems);
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(uploadBucket).key(s3Key)
                            .contentType("application/json").contentLength((long) json.length).build(),
                    RequestBody.fromBytes(json));
        } catch (Exception e) {
            throw new CustomException(ErrorCode.RECIPIENT_S3_UPLOAD_FAILED);
        }
    }

    // ── DynamoDB 변환 ──────────────────────────────────────────────────────────

    private List<Map<String, AttributeValue>> toRecipientItems(List<RecipientRow> rows,
                                                                String campaignId, String uploadBatchId) {
        String now = Instant.now().toString();
        return rows.stream()
                .map(r -> Map.of(
                        "campaign_id",    AttributeValue.fromS(campaignId),
                        "employee_no",    AttributeValue.fromS(r.employeeNo()),
                        "name",           AttributeValue.fromS(r.name()),
                        "department",     AttributeValue.fromS(r.department()),
                        "email",          AttributeValue.fromS(r.email()),
                        "upload_batch_id",AttributeValue.fromS(uploadBatchId),
                        "created_at",     AttributeValue.fromS(now)
                ))
                .toList();
    }

    // ── 유틸 ──────────────────────────────────────────────────────────────────

    private Map<String, Integer> buildXlsxHeaderMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        DataFormatter formatter = new DataFormatter();
        for (Cell cell : headerRow) {
            String header = formatter.formatCellValue(cell).toLowerCase().trim();
            if (!header.isEmpty()) map.put(header, cell.getColumnIndex());
        }
        return map;
    }

    private RecipientValidationErrorItem errorItem(int rowNum, String column, String errorType,
                                                   String rawValue, String message) {
        return RecipientValidationErrorItem.builder()
                .rowNumber(rowNum).column(column).errorType(errorType)
                .rawValue(rawValue == null ? "" : rawValue).message(message).build();
    }

    private boolean isBlank(String v) { return v == null || v.isBlank(); }

    // ── 내부 타입 ──────────────────────────────────────────────────────────────

    private record RecipientRow(String employeeNo, String name, String department, String email) {}

    private record ParseResult(int totalRowCount, int validRowCount, int errorRowCount, int duplicateRowCount,
                               List<RecipientRow> validRows, List<RecipientValidationErrorItem> errorItems) {
        static ParseResult empty() { return new ParseResult(0, 0, 0, 0, List.of(), List.of()); }
    }
}
