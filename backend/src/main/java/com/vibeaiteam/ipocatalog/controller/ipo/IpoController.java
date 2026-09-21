package com.vibeaiteam.ipocatalog.controller.ipo;

import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoImportResponse;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoRegisterRequest;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoResponse;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoUpdateRequest;
import com.vibeaiteam.ipocatalog.service.ipo.IpoCsvImportService;
import com.vibeaiteam.ipocatalog.service.ipo.IpoService;
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

import java.util.List;

// IPO(공모주) 기준정보에 대한 등록/조회/수정/비활성화/CSV 일괄등록 API를 제공한다.
@RestController
@RequestMapping("/ipos")
@RequiredArgsConstructor
public class IpoController {

    private final IpoService ipoService;
    private final IpoCsvImportService ipoCsvImportService;

    @GetMapping
    public ResponseEntity<List<IpoResponse>> list() {
        List<IpoResponse> responses = ipoService.list();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<IpoResponse> getById(@PathVariable Long id) {
        IpoResponse response = ipoService.getById(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<IpoResponse> update(@PathVariable Long id,
                                              @RequestBody IpoUpdateRequest request) {
        IpoResponse response = ipoService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        ipoService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<IpoResponse> register(@RequestBody IpoRegisterRequest request) {
        IpoResponse response = ipoService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IpoImportResponse> importCsv(@RequestParam("file") MultipartFile file) {
        IpoImportResponse response = ipoCsvImportService.importCsv(file);
        return ResponseEntity.ok(response);
    }
}
