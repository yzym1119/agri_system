package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.system.dict.dto.DictDataCreateCommand;
import com.agri_system.application.system.dict.dto.DictDataManageVO;
import com.agri_system.application.system.dict.dto.DictDataUpdateCommand;
import com.agri_system.application.system.dict.service.DictApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dict")
@Tag(name = "字典管理", description = "后台字典数据管理")
@RequiredArgsConstructor
public class DictAdminController {

    private final DictApplicationService dictApplicationService;

    @GetMapping("/data")
    @Operation(summary = "获取字典数据列表（管理端）")
    @SaCheckPermission("dict:list")
    public ResponseEntity<List<DictDataManageVO>> listData(@RequestParam String typeCode) {
        return ResponseEntity.ok(dictApplicationService.listDataForManage(typeCode));
    }

    @PostMapping("/data")
    @Operation(summary = "新增字典数据")
    @SaCheckPermission("dict:save")
    public ResponseEntity<Long> create(@Valid @RequestBody DictDataCreateCommand command) {
        return ResponseEntity.ok(dictApplicationService.createData(command));
    }

    @PutMapping("/data")
    @Operation(summary = "修改字典数据")
    @SaCheckPermission("dict:save")
    public ResponseEntity<Void> update(@Valid @RequestBody DictDataUpdateCommand command) {
        dictApplicationService.updateData(command);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/data")
    @Operation(summary = "删除字典数据")
    @SaCheckPermission("dict:delete")
    public ResponseEntity<Void> delete(@RequestParam Long id, @RequestParam String typeCode) {
        dictApplicationService.deleteData(id, typeCode);
        return ResponseEntity.ok().build();
    }
}






