package com.agri_system.api.client.controller;
import com.agri_system.application.catalog.service.ClientCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class ClientCatalogController {
    private final ClientCatalogService service;
    @ModelAttribute public void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping("/stores") public ClientCatalogService.Stores stores(@RequestParam(required=false) Long after){return service.stores(after);}
    @GetMapping("/catalog/products") public ClientCatalogService.Products products(@RequestParam(required=false) Long after,@RequestParam(required=false) String keyword){return service.products(after,keyword);}
    @GetMapping("/catalog/products/{productId}") public ClientCatalogService.Detail product(@PathVariable Long productId,@RequestParam(required=false) String unit){return service.product(productId,unit);}
}
