package com.vibeaiteam.ipocatalog.service.ipo;

import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoImportFailure;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoImportResponse;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoRegisterRequest;
import com.vibeaiteam.ipocatalog.exception.IpoConflictException;
import com.vibeaiteam.ipocatalog.exception.IpoFileTooLargeException;
import com.vibeaiteam.ipocatalog.exception.IpoInvalidCsvException;
import com.vibeaiteam.ipocatalog.exception.IpoValidationException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * IPO CSV 일괄 등록 서비스. commons-csv(RFC 4180)로 파싱하고, 행 단위 기존 IpoService.register() 검증 로직을 재사용해 저장한다. 한
 * 행의 실패가 나머지에 영향 없도록 행마다 개별 트랜잭션으로 처리한다(AC-018~AC-020).
 */
@Service
@RequiredArgsConstructor
public class IpoCsvImportService {

  private static final long MAX_CSV_SIZE_BYTES = 5L * 1024 * 1024;

  private static final List<String> REQUIRED_HEADERS =
      List.of("companyName", "ipoName", "subscriptionStartDate", "subscriptionEndDate");

  private final IpoService ipoService;

  public IpoImportResponse importCsv(MultipartFile file) {
    validateFile(file);
    List<CSVRecord> records = parseRecords(file);
    Map<String, Integer> headerIndex = buildHeaderIndex(records.get(0));
    validateRequiredHeaders(headerIndex);

    int successCount = 0;
    List<IpoImportFailure> failures = new ArrayList<>();
    for (int i = 1; i < records.size(); i++) {
      CSVRecord record = records.get(i);
      int rowNumber = i + 1;
      try {
        IpoRegisterRequest request = toRegisterRequest(headerIndex, record);
        ipoService.register(request);
        successCount++;
      } catch (IpoValidationException | IpoConflictException | IpoInvalidCsvException ex) {
        failures.add(new IpoImportFailure(rowNumber, ex.getMessage()));
      } catch (RuntimeException ex) {
        failures.add(new IpoImportFailure(rowNumber, "처리 중 오류가 발생했습니다."));
      }
    }

    return IpoImportResponse.builder()
        .successCount(successCount)
        .failureCount(failures.size())
        .failures(failures)
        .build();
  }

  private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IpoInvalidCsvException("CSV 파일이 비어 있습니다.");
    }
    if (file.getSize() > MAX_CSV_SIZE_BYTES) {
      throw new IpoFileTooLargeException("CSV 파일은 최대 5MB까지 업로드할 수 있습니다.");
    }
  }

  private List<CSVRecord> parseRecords(MultipartFile file) {
    CSVFormat format = CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(true).setTrim(true).get();
    try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
        CSVParser parser = format.parse(reader)) {
      List<CSVRecord> records = parser.getRecords();
      if (records.isEmpty()) {
        throw new IpoInvalidCsvException("CSV 파일이 비어 있습니다.");
      }
      return records;
    } catch (IpoInvalidCsvException ex) {
      throw ex;
    } catch (IOException | UncheckedIOException ex) {
      throw new IpoInvalidCsvException("CSV 파일을 읽을 수 없습니다.");
    }
  }

  private Map<String, Integer> buildHeaderIndex(CSVRecord header) {
    Map<String, Integer> headerIndex = new LinkedHashMap<>();
    for (int i = 0; i < header.size(); i++) {
      headerIndex.putIfAbsent(header.get(i), i);
    }
    return headerIndex;
  }

  private void validateRequiredHeaders(Map<String, Integer> headerIndex) {
    List<String> missing =
        REQUIRED_HEADERS.stream().filter(header -> !headerIndex.containsKey(header)).toList();
    if (!missing.isEmpty()) {
      throw new IpoInvalidCsvException("필수 헤더가 누락되었습니다: " + String.join(", ", missing));
    }
  }

  private IpoRegisterRequest toRegisterRequest(Map<String, Integer> headerIndex, CSVRecord record) {
    String lowerBandPrice = get(headerIndex, record, "lowerBandPrice");
    String upperBandPrice = get(headerIndex, record, "upperBandPrice");
    String fixedPrice = get(headerIndex, record, "fixedPrice");
    String subscriptionStartDate = get(headerIndex, record, "subscriptionStartDate");
    String subscriptionEndDate = get(headerIndex, record, "subscriptionEndDate");
    String listingDate = get(headerIndex, record, "listingDate");
    String offeringShareCount = get(headerIndex, record, "offeringShareCount");

    return IpoRegisterRequest.builder()
        .companyName(get(headerIndex, record, "companyName"))
        .ipoName(get(headerIndex, record, "ipoName"))
        .stockCode(get(headerIndex, record, "stockCode"))
        .roundLabel(get(headerIndex, record, "roundLabel"))
        .lowerBandPrice(parseOptionalLong("lowerBandPrice", lowerBandPrice))
        .upperBandPrice(parseOptionalLong("upperBandPrice", upperBandPrice))
        .fixedPrice(parseOptionalLong("fixedPrice", fixedPrice))
        .subscriptionStartDate(parseRequiredDate("subscriptionStartDate", subscriptionStartDate))
        .subscriptionEndDate(parseRequiredDate("subscriptionEndDate", subscriptionEndDate))
        .listingDate(parseOptionalDate("listingDate", listingDate))
        .offeringShareCount(parseOptionalLong("offeringShareCount", offeringShareCount))
        .build();
  }

  private String get(Map<String, Integer> headerIndex, CSVRecord record, String header) {
    Integer index = headerIndex.get(header);
    if (index == null || index >= record.size()) {
      return null;
    }
    String value = record.get(index);
    return (value == null || value.isBlank()) ? null : value;
  }

  private Long parseOptionalLong(String fieldName, String raw) {
    if (raw == null) {
      return null;
    }
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException ex) {
      throw new IpoValidationException(fieldName + " 값이 숫자 형식이 아닙니다.");
    }
  }

  private LocalDate parseRequiredDate(String fieldName, String raw) {
    if (raw == null) {
      throw new IpoValidationException(fieldName + "는 필수입니다.");
    }
    try {
      return LocalDate.parse(raw.trim());
    } catch (DateTimeParseException ex) {
      throw new IpoValidationException(fieldName + " 값이 YYYY-MM-DD 형식이 아닙니다.");
    }
  }

  private LocalDate parseOptionalDate(String fieldName, String raw) {
    if (raw == null) {
      return null;
    }
    try {
      return LocalDate.parse(raw.trim());
    } catch (DateTimeParseException ex) {
      throw new IpoValidationException(fieldName + " 값이 YYYY-MM-DD 형식이 아닙니다.");
    }
  }
}
