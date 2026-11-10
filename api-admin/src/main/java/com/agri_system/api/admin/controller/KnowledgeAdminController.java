package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.agri_system.application.agronomy.service.KnowledgeRevisionService;
import com.agri_system.application.auth.util.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/agronomy/knowledge")
@SaCheckPermission("agronomy:knowledge:query")
@RequiredArgsConstructor
public class KnowledgeAdminController {
    private final KnowledgeRevisionService service;
    public record Decision(@NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String expectedVersion,@NotBlank @Size(max=500) String reason) {}
    @ModelAttribute public void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping public KnowledgeRevisionService.Page page(@RequestParam(required=false) Long storeId,@RequestParam(required=false) Long after){return service.page(CurrentUser.staffId(),storeId,after);}
    @GetMapping("/{logicalKey}/revisions/{revision}") public KnowledgeRevisionService.Detail detail(@PathVariable String logicalKey,@PathVariable int revision){return service.detail(CurrentUser.staffId(),logicalKey,revision);}
    @GetMapping("/submissions/{requestKey}") public KnowledgeRevisionService.Submission submission(@PathVariable String requestKey){return service.submission(CurrentUser.staffId(),requestKey);}
    @PostMapping
    @SaCheckPermission(value={"agronomy:knowledge:query","agronomy:knowledge:write"},mode=SaMode.AND)
    public KnowledgeRevisionService.Detail create(@RequestBody KnowledgeRevisionService.Draft command){return service.create(CurrentUser.staffId(),null,command);}
    @PostMapping("/{logicalKey}/revisions")
    @SaCheckPermission(value={"agronomy:knowledge:query","agronomy:knowledge:write"},mode=SaMode.AND)
    public KnowledgeRevisionService.Detail revision(@PathVariable String logicalKey,@RequestBody KnowledgeRevisionService.Draft command){return service.create(CurrentUser.staffId(),logicalKey,command);}
    @PostMapping("/{logicalKey}/revisions/{revision}/review")
    @SaCheckPermission(value={"agronomy:knowledge:query","agronomy:knowledge:review"},mode=SaMode.AND)
    public KnowledgeRevisionService.Detail review(@PathVariable String logicalKey,@PathVariable int revision,@Valid @RequestBody Decision command){return service.change(CurrentUser.staffId(),logicalKey,revision,command.expectedVersion(),"REVIEW",command.reason());}
    @PostMapping("/{logicalKey}/revisions/{revision}/publish")
    @SaCheckPermission(value={"agronomy:knowledge:query","agronomy:knowledge:publish"},mode=SaMode.AND)
    public KnowledgeRevisionService.Detail publish(@PathVariable String logicalKey,@PathVariable int revision,@Valid @RequestBody Decision command){return service.change(CurrentUser.staffId(),logicalKey,revision,command.expectedVersion(),"PUBLISH",command.reason());}
    @PostMapping("/{logicalKey}/revisions/{revision}/withdraw")
    @SaCheckPermission(value={"agronomy:knowledge:query","agronomy:knowledge:publish"},mode=SaMode.AND)
    public KnowledgeRevisionService.Detail withdraw(@PathVariable String logicalKey,@PathVariable int revision,@Valid @RequestBody Decision command){return service.change(CurrentUser.staffId(),logicalKey,revision,command.expectedVersion(),"WITHDRAW",command.reason());}
}
