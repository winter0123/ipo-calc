package com.vibeaiteam.ipocatalog.controller.ipo;

import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoImportResponse;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoRegisterRequest;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoResponse;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoUpdateRequest;
import com.vibeaiteam.ipocatalog.service.ipo.IpoCsvImportService;
import com.vibeaiteam.ipocatalog.service.ipo.IpoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// IPO(공모주) 기준정보에 대한 등록/조회/수정/비활성화/CSV 일괄등록 API를 제공한다.
@RestController
@RequestMapping("/ipos")
@RequiredArgsConstructor
public class IpoController {

  private final IpoService ipoService;
  private final IpoCsvImportService ipoCsvImportService;

  // 비활성화되지 않은(삭제되지 않은) IPO 목록을 최신 기준·생성일 내림차순으로 조회해 반환한다.
  @GetMapping
  public ResponseEntity<List<IpoResponse>> list() {
    List<IpoResponse> responses = ipoService.list();
    return ResponseEntity.ok(responses);
  }

  // IPO ID로 단건 IPO 정보를 조회한다. 존재하지 않거나 비활성화되었으면 404를 반환한다.
  @GetMapping("/{id}")
  public ResponseEntity<IpoResponse> getById(@PathVariable Long id) {
    IpoResponse response = ipoService.getById(id);
    return ResponseEntity.ok(response);
  }

  // IPO ID로 해당 IPO의 회사명/공모명 등을 수정하고, 수정된 IPO 정보를 반환한다. 존재하지 않으면 404를 반환한다.
  @PatchMapping("/{id}")
  public ResponseEntity<IpoResponse> update(
      @PathVariable Long id, @RequestBody IpoUpdateRequest request) {
    IpoResponse response = ipoService.update(id, request);
    return ResponseEntity.ok(response);
  }

  // IPO ID로 해당 IPO를 비활성화 상태로 전환하고, 204 No Content를 반환한다. 존재하지 않으면 404를 반환한다.
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deactivate(@PathVariable Long id) {
    ipoService.deactivate(id);
    return ResponseEntity.noContent().build();
  }

  // IPO 등록 요청을 받아 새 IPO를 생성하고, 201 Created와 함께 생성된 IPO 정보를 반환한다.
  @PostMapping
  public ResponseEntity<IpoResponse> register(@RequestBody IpoRegisterRequest request) {
    IpoResponse response = ipoService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  // multipart로 업로드된 CSV 파일을 파싱해 IPO를 일괄 등록하고, 처리 결과(등록/실패 건수 등)를 반환한다.
  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<IpoImportResponse> importCsv(@RequestParam("file") MultipartFile file) {
    IpoImportResponse response = ipoCsvImportService.importCsv(file);
    return ResponseEntity.ok(response);
  }
}
