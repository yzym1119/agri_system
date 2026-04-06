package com.agri_system.api.admin.controller;

import com.agri_system.application.system.dict.dto.DictDataVO;
import com.agri_system.application.system.dict.service.DictApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/dict")
@Tag(name = "公开字典接口", description = "提供下拉选项数据")
@RequiredArgsConstructor
public class DictPublicController {

    private final DictApplicationService dictApplicationService;

    @GetMapping("/{typeCode}")
    @Operation(summary = "根据字典编码获取选项列表")
    public ResponseEntity<List<DictDataVO>> listByCode(@PathVariable String typeCode) {
        return ResponseEntity.ok(dictApplicationService.listByCode(typeCode));
    }
}






