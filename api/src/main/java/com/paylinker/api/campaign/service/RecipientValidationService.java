package com.paylinker.api.campaign.service;

import com.paylinker.api.campaign.dto.response.RecipientValidationErrorItem;
import com.paylinker.api.campaign.dto.response.RecipientValidationResponse;
import com.paylinker.api.campaign.repository.UploadBatchRepository;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Service
@RequiredArgsConstructor
public class RecipientValidationService {

    private final UploadBatchRepository uploadBatchRepository;
    private final S3Client s3Client;

    @Value("${aws.s3.upload-bucket}")
    private String uploadBucket;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final List<String> REQUIRED_HEADERS = List.of("employeeno", "name", "department", "email");

    public RecipientValidationResponse getValidationResult(String campaignId, String uploadBatchId) {
        Map<String, AttributeValue> item = uploadBatchRepository
                .findByBatchIdAndCampaignId(uploadBatchId, campaignId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECIPIENT_UPLOAD_BATCH_NOT_FOUND));

        int totalRowCount = Integer.parseInt(item.get("total_row_count").n());
        int validRowCount = Integer.parseInt(item.get("valid_row_count").n());
        int errorRowCount = Integer.parseInt(item.get("error_row_count").n());
        int duplicateRowCount = Integer.parseInt(item.get("duplicate_row_count").n());
        String s3Key = item.get("s3_key").s();

        List<RecipientValidationErrorItem> errors = parseErrorsFromS3(s3Key);

        return RecipientValidationResponse.builder()
                .uploadBatchId(uploadBatchId)
                .campaignId(campaignId)
                .totalRowCount(totalRowCount)
                .validRowCount(validRowCount)
                .errorRowCount(errorRowCount)
                .duplicateRowCount(duplicateRowCount)
                .canProceed(validRowCount > 0 && errorRowCount == 0)
                .errors(errors)
                .build();
    }

    private List<RecipientValidationErrorItem> parseErrorsFromS3(String s3Key) {
        byte[] fileBytes = downloadFromS3(s3Key);
        String filename = s3Key.substring(s3Key.lastIndexOf('/') + 1).toLowerCase();
        return filename.endsWith(".csv") ? parseCsvErrors(fileBytes) : parseXlsxErrors(fileBytes);
    }

    private byte[] downloadFromS3(String s3Key) {
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(uploadBucket).key(s3Key).build());
            return response.asByteArray();
        } catch (Exception e) {
            throw new RuntimeException("S3 파일 다운로드에 실패했습니다.", e);
        }
    }

    private List<RecipientValidationErrorItem> parseCsvErrors(byte[] bytes) {
        try {
            int offset = (bytes.length >= 3
                    && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) ? 3 : 0;

            Reader reader = new InputStreamReader(
                    new ByteArrayInputStream(bytes, offset, bytes.length - offset), StandardCharsets.UTF_8);

            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .setIgnoreHeaderCase(true)
                    .build();

            try (CSVParser parser = format.parse(reader)) {
                validateHeaders(parser.getHeaderNames().stream()
                        .map(h -> h.toLowerCase().trim())
                        .toList());
                return collectCsvErrors(parser);
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("CSV 파일 파싱에 실패했습니다.", e);
        }
    }

    private List<RecipientValidationErrorItem> collectCsvErrors(CSVParser parser) {
        Set<String> seenEmployeeNos = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        List<RecipientValidationErrorItem> errors = new ArrayList<>();
        int rowNum = 1;

        for (CSVRecord record : parser) {
            rowNum++;
            String employeeNo = record.get("employeeno");
            String name = record.get("name");
            String department = record.get("department");
            String email = record.get("email");

            RecipientValidationErrorItem error = validateRow(
                    rowNum, employeeNo, name, department, email, seenEmployeeNos, seenEmails);
            if (error != null) {
                errors.add(error);
                continue;
            }
            seenEmployeeNos.add(employeeNo.toLowerCase());
            seenEmails.add(email.toLowerCase());
        }
        return errors;
    }

    private List<RecipientValidationErrorItem> parseXlsxErrors(byte[] bytes) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return List.of();

            Map<String, Integer> headerIndexMap = buildXlsxHeaderMap(headerRow);
            validateHeaders(new ArrayList<>(headerIndexMap.keySet()));
            return collectXlsxErrors(sheet, headerIndexMap);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("XLSX 파일 파싱에 실패했습니다.", e);
        }
    }

    private List<RecipientValidationErrorItem> collectXlsxErrors(Sheet sheet, Map<String, Integer> headerIndexMap) {
        DataFormatter formatter = new DataFormatter();
        Set<String> seenEmployeeNos = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        List<RecipientValidationErrorItem> errors = new ArrayList<>();

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

            int rowNum = i + 1;
            RecipientValidationErrorItem error = validateRow(
                    rowNum, employeeNo, name, department, email, seenEmployeeNos, seenEmails);
            if (error != null) {
                errors.add(error);
                continue;
            }
            seenEmployeeNos.add(employeeNo.toLowerCase());
            seenEmails.add(email.toLowerCase());
        }
        return errors;
    }

    private RecipientValidationErrorItem validateRow(int rowNum, String employeeNo, String name,
                                                     String department, String email,
                                                     Set<String> seenEmployeeNos, Set<String> seenEmails) {
        if (isBlank(employeeNo)) {
            return errorItem(rowNum, "employeeNo", "MISSING_FIELD", employeeNo, "필수 항목이 누락되었습니다.");
        }
        if (isBlank(name)) {
            return errorItem(rowNum, "name", "MISSING_FIELD", name, "필수 항목이 누락되었습니다.");
        }
        if (isBlank(department)) {
            return errorItem(rowNum, "department", "MISSING_FIELD", department, "필수 항목이 누락되었습니다.");
        }
        if (isBlank(email)) {
            return errorItem(rowNum, "email", "MISSING_FIELD", email, "필수 항목이 누락되었습니다.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return errorItem(rowNum, "email", "INVALID_EMAIL_FORMAT", email, "이메일 형식이 올바르지 않습니다.");
        }
        if (seenEmployeeNos.contains(employeeNo.toLowerCase())) {
            return errorItem(rowNum, "employeeNo", "DUPLICATE", employeeNo, "동일 사번이 파일 내에서 중복됩니다.");
        }
        if (seenEmails.contains(email.toLowerCase())) {
            return errorItem(rowNum, "email", "DUPLICATE", email, "동일 이메일이 파일 내에서 중복됩니다.");
        }
        return null;
    }

    private RecipientValidationErrorItem errorItem(int rowNum, String column, String errorType,
                                                   String rawValue, String message) {
        return RecipientValidationErrorItem.builder()
                .rowNumber(rowNum)
                .column(column)
                .errorType(errorType)
                .rawValue(rawValue == null ? "" : rawValue)
                .message(message)
                .build();
    }

    private Map<String, Integer> buildXlsxHeaderMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        DataFormatter formatter = new DataFormatter();
        for (Cell cell : headerRow) {
            String header = formatter.formatCellValue(cell).toLowerCase().trim();
            if (!header.isEmpty()) map.put(header, cell.getColumnIndex());
        }
        return map;
    }

    private void validateHeaders(List<String> headers) {
        boolean missing = REQUIRED_HEADERS.stream().anyMatch(h -> !headers.contains(h));
        if (missing) {
            throw new CustomException(ErrorCode.RECIPIENT_FILE_UNSUPPORTED);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
