package com.paylinker.api.campaign.service;

import com.paylinker.api.campaign.dto.response.RecipientUploadResponse;
import com.paylinker.api.campaign.repository.UploadBatchRepository;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
public class CampaignRecipientService {

    private final UploadBatchRepository uploadBatchRepository;
    private final S3Client s3Client;

    @Value("${aws.s3.upload-bucket}")
    private String uploadBucket;

    @Value("${paylinker.campaign-limit.max-recipients}")
    private int maxRecipients;

    @Value("${paylinker.upload-batch.file-storage-key}")
    private String fileStorageKey;

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final List<String> REQUIRED_HEADERS = List.of("employeeno", "name", "department", "email");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public RecipientUploadResponse uploadRecipients(String campaignId, MultipartFile file, String uploadType) {
        String filename = file.getOriginalFilename();
        boolean isCsv = filename != null && filename.toLowerCase().endsWith(".csv");
        boolean isXlsx = filename != null && filename.toLowerCase().endsWith(".xlsx");

        if (!isCsv && !isXlsx) {
            throw new CustomException(ErrorCode.RECIPIENT_FILE_UNSUPPORTED);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new CustomException(ErrorCode.RECIPIENT_FILE_TOO_LARGE);
        }

        String resolvedUploadType = (uploadType == null || uploadType.isBlank()) ? "FULL_REPLACE" : uploadType.trim();

        ParseResult result = isCsv ? parseCsv(file) : parseXlsx(file);

        if (result.totalRowCount() > maxRecipients) {
            throw new CustomException(ErrorCode.RECIPIENT_CAMPAIGN_LIMIT_EXCEEDED);
        }

        String uploadBatchId = "ub_" + UUID.randomUUID();
        String s3Key = fileStorageKey + "/" + campaignId + "/" + uploadBatchId + "/" + filename;
        uploadToS3(file, s3Key);

        uploadBatchRepository.save(uploadBatchId, campaignId,
                result.totalRowCount(), result.validRowCount(), result.errorRowCount(), result.duplicateRowCount(),
                resolvedUploadType, s3Key);

        return RecipientUploadResponse.builder()
                .uploadBatchId(uploadBatchId)
                .campaignId(campaignId)
                .totalRowCount(result.totalRowCount())
                .validRowCount(result.validRowCount())
                .errorRowCount(result.errorRowCount())
                .duplicateRowCount(result.duplicateRowCount())
                .build();
    }

    private void uploadToS3(MultipartFile file, String s3Key) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(uploadBucket)
                            .key(s3Key)
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromBytes(file.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("S3 파일 업로드에 실패했습니다.", e);
        }
    }

    private ParseResult parseCsv(MultipartFile file) {
        try (InputStream raw = file.getInputStream()) {
            byte[] bytes = raw.readAllBytes();
            // UTF-8 BOM 제거
            int offset = (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) ? 3 : 0;

            Reader reader = new InputStreamReader(
                    new java.io.ByteArrayInputStream(bytes, offset, bytes.length - offset), StandardCharsets.UTF_8);

            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .setIgnoreHeaderCase(true)
                    .build();

            try (CSVParser parser = format.parse(reader)) {
                Map<String, Integer> headerMap = new HashMap<>();
                parser.getHeaderNames().forEach(h -> headerMap.put(h.toLowerCase().trim(), 0));
                validateHeaders(headerMap);
                return countCsvRows(parser);
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("CSV 파일 파싱에 실패했습니다.", e);
        }
    }

    private ParseResult countCsvRows(CSVParser parser) {
        Set<String> seenEmployeeNos = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        int total = 0, errors = 0, duplicates = 0;

        for (CSVRecord record : parser) {
            total++;
            String employeeNo = record.get("employeeno");
            String name = record.get("name");
            String department = record.get("department");
            String email = record.get("email");

            if (isBlank(employeeNo) || isBlank(name) || isBlank(department) || isBlank(email)
                    || !EMAIL_PATTERN.matcher(email).matches()) {
                errors++;
                continue;
            }

            String empKey = employeeNo.toLowerCase();
            String emailKey = email.toLowerCase();
            if (seenEmployeeNos.contains(empKey) || seenEmails.contains(emailKey)) {
                duplicates++;
                continue;
            }
            seenEmployeeNos.add(empKey);
            seenEmails.add(emailKey);
        }
        return new ParseResult(total, total - errors - duplicates, errors, duplicates);
    }

    private ParseResult parseXlsx(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return new ParseResult(0, 0, 0, 0);
            }

            Map<String, Integer> headerIndexMap = buildXlsxHeaderMap(headerRow);
            validateHeaders(headerIndexMap);
            return countXlsxRows(sheet, headerIndexMap);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("XLSX 파일 파싱에 실패했습니다.", e);
        }
    }

    private Map<String, Integer> buildXlsxHeaderMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        DataFormatter formatter = new DataFormatter();
        for (Cell cell : headerRow) {
            String header = formatter.formatCellValue(cell).toLowerCase().trim();
            if (!header.isEmpty()) {
                map.put(header, cell.getColumnIndex());
            }
        }
        return map;
    }

    private ParseResult countXlsxRows(Sheet sheet, Map<String, Integer> headerIndexMap) {
        DataFormatter formatter = new DataFormatter();
        Set<String> seenEmployeeNos = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        int total = 0, errors = 0, duplicates = 0;

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

            // 모든 셀이 비어있으면 빈 행으로 간주하고 건너뜀
            if (employeeNo.isEmpty() && name.isEmpty() && department.isEmpty() && email.isEmpty()) {
                continue;
            }

            total++;

            if (isBlank(employeeNo) || isBlank(name) || isBlank(department) || isBlank(email)
                    || !EMAIL_PATTERN.matcher(email).matches()) {
                errors++;
                continue;
            }

            String empKey = employeeNo.toLowerCase();
            String emailKey = email.toLowerCase();
            if (seenEmployeeNos.contains(empKey) || seenEmails.contains(emailKey)) {
                duplicates++;
                continue;
            }
            seenEmployeeNos.add(empKey);
            seenEmails.add(emailKey);
        }
        return new ParseResult(total, total - errors - duplicates, errors, duplicates);
    }

    private void validateHeaders(Map<String, Integer> headerMap) {
        boolean missing = REQUIRED_HEADERS.stream().anyMatch(h -> !headerMap.containsKey(h));
        if (missing) {
            throw new CustomException(ErrorCode.RECIPIENT_FILE_UNSUPPORTED);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ParseResult(int totalRowCount, int validRowCount, int errorRowCount, int duplicateRowCount) {}
}
