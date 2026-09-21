package com.vibeaiteam.ipocatalog.controller.ipo;

import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoRegisterRequest;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoResponse;
import com.vibeaiteam.ipocatalog.service.ipo.IpoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ipos")
@RequiredArgsConstructor
public class IpoController {

    private final IpoService ipoService;

    @PostMapping
    public ResponseEntity<IpoResponse> register(@RequestBody IpoRegisterRequest request) {
        IpoResponse response = ipoService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
