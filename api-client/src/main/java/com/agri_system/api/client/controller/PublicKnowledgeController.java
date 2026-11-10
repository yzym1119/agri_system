package com.agri_system.api.client.controller;

import com.agri_system.application.agronomy.service.PublicKnowledgeService;
import com.agri_system.domain.agronomy.repository.KnowledgeRevisionRepository.Scope;
import com.agri_system.domain.common.error.SystemErrorCode;
import com.agri_system.infrastructure.common.response.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.List;

@RestController
@RequestMapping("/api/public/agronomy/knowledge")
@RequiredArgsConstructor
public class PublicKnowledgeController {
    private final PublicKnowledgeService service;
    @ExceptionHandler({MissingServletRequestParameterException.class,MethodArgumentTypeMismatchException.class})
    public R<Void> invalidQuery(){return R.fail(SystemErrorCode.INVALID_ARGUMENT.getCode(),"请完整选择地区、作物、阶段，并核对原知识版本与分页编号");}
    @ModelAttribute public void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping("/scopes") public List<Scope> scopes(){return service.scopes();}
    @GetMapping public PublicKnowledgeService.Page page(@RequestParam String region,@RequestParam String crop,@RequestParam String growthStage,@RequestParam(required=false) Long after,@RequestParam(required=false) String keyword){return service.page(region,crop,growthStage,after,keyword);}
    @GetMapping("/{logicalKey}") public PublicKnowledgeService.Detail detail(@PathVariable String logicalKey,@RequestParam int expectedRevision,@RequestParam String region,@RequestParam String crop,@RequestParam String growthStage){return service.detail(logicalKey,expectedRevision,region,crop,growthStage);}
}
