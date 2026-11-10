package com.agri_system.application.catalog.service;
import com.agri_system.domain.catalog.query.PublicCatalogQuery;
import com.agri_system.domain.product.model.ProductUnit;
import com.agri_system.domain.product.service.QuantityGranularityPolicy;
import com.agri_system.application.product.service.ProductPricingService;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.application.product.dto.vo.PriceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
@Service
@RequiredArgsConstructor
public class ClientCatalogService {
    private final PublicCatalogQuery query;
    private final ProductUnitService units;
    private final ProductPricingService prices;
    public record Stores(List<PublicCatalogQuery.StoreView> rows,String nextAfter) {}
    public record Products(List<PublicCatalogQuery.ProductSummary> rows,String nextAfter) {}
    public record SaleUnit(String unitName,String baseQuantityPerUnit) {}
    public record ReferencePrice(String productId,String unitName,String unitPrice,String state) {}
    public record Detail(PublicCatalogQuery.ProductView product,String baseUnitName,Boolean allowFractionalBaseQuantity,List<SaleUnit> saleUnits,ReferencePrice referencePrice) {}
    public Stores stores(Long after){cursor(after);var raw=query.stores(after);var rows=List.copyOf(raw.subList(0,Math.min(20,raw.size())));return new Stores(rows,raw.size()>20?rows.get(19).storeId():null);}
    public Products products(Long after,String keyword){cursor(after);String search=keyword==null||keyword.isBlank()?null:keyword.strip();if(keyword!=null&&(keyword.length()>100||keyword.chars().anyMatch(Character::isISOControl)))throw new IllegalArgumentException("商品搜索内容无效");
        var raw=query.products(after,search);var rows=raw.subList(0,Math.min(20,raw.size())).stream().map(p->new PublicCatalogQuery.ProductSummary(p.productId(),p.name(),p.specification(),p.manufacturer(),image(p.imageUrl()))).toList();return new Products(rows,raw.size()>20?rows.get(19).productId():null);}
    public Detail product(Long id,String selectedUnit){
        positive(id);if(selectedUnit!=null&&(selectedUnit.isBlank()||selectedUnit.length()>50||selectedUnit.chars().anyMatch(Character::isISOControl)))throw new IllegalArgumentException("销售单位无效");
        var raw=query.product(id).orElseThrow(ClientCatalogService::unavailable);if(!id.toString().equals(raw.productId()))throw unavailable();
        List<ProductUnit> configured=List.of();String baseName=null;Boolean fractional=null;List<SaleUnit> sale=List.of();
        try{
            configured=units.getUnits(id);var bases=configured.stream().filter(u->Integer.valueOf(1).equals(u.getStatus())&&Integer.valueOf(1).equals(u.getIsBaseUnit())&&BigDecimal.ONE.compareTo(u.getBaseQuantityPerUnit()==null?BigDecimal.ZERO:u.getBaseQuantityPerUnit())==0).toList();
            if(configured.size()>100||bases.size()!=1||bases.get(0).getId()==null||!id.equals(bases.get(0).getProductId())||bases.get(0).getUnitName()==null||bases.get(0).getUnitName().isBlank())throw new IllegalStateException("单位待核对");
            var allowed=configured.stream().filter(u->Integer.valueOf(1).equals(u.getStatus())&&Integer.valueOf(1).equals(u.getAllowSale())).toList();
            if(allowed.stream().anyMatch(u->u.getId()==null||!id.equals(u.getProductId())||u.getUnitName()==null||u.getUnitName().isBlank()||u.getBaseQuantityPerUnit()==null||u.getBaseQuantityPerUnit().signum()<=0)||allowed.stream().map(ProductUnit::getUnitName).distinct().count()!=allowed.size())throw new IllegalStateException("单位待核对");
            baseName=bases.get(0).getUnitName();fractional=QuantityGranularityPolicy.allowsFractionalBaseQuantity(baseName);sale=allowed.stream().map(u->new SaleUnit(u.getUnitName(),u.getBaseQuantityPerUnit().stripTrailingZeros().toPlainString())).toList();
        }catch(IllegalArgumentException|IllegalStateException unknown){configured=List.of();}
        var price=new ReferencePrice(id.toString(),selectedUnit,null,"CONFIRM_WITH_STORE");
        if(baseName!=null&&!sale.isEmpty()){
            if(selectedUnit!=null&&sale.stream().noneMatch(u->u.unitName().equals(selectedUnit)))throw new IllegalArgumentException("该商品未公开此销售单位");
            try{var quote=prices.quote(new PriceContext(id,null,"PUBLIC_CATALOG",1,null,selectedUnit,null));
                String quotedName=quote==null?null:quote.saleUnit();var source=configured.stream().filter(u->u.getId()!=null&&quote!=null&&u.getId().equals(quote.saleUnitId())&&u.getUnitName().equals(quotedName)&&Integer.valueOf(1).equals(u.getAllowSale())).toList();
                if(quote!=null&&id.equals(quote.productId())&&source.size()==1&&(selectedUnit==null||selectedUnit.equals(quotedName))&&"RETAIL".equals(quote.ruleVersion())&&quote.suggestedUnitPrice()!=null&&quote.suggestedUnitPrice().signum()>0)
                    price=new ReferencePrice(id.toString(),quotedName,quote.suggestedUnitPrice().setScale(2,RoundingMode.UNNECESSARY).toPlainString(),"AVAILABLE");
            }catch(IllegalArgumentException|IllegalStateException|ArithmeticException unavailablePrice){ /* 配置未知不猜价格。 */ }
        }
        if(!query.product(id).filter(raw::equals).isPresent())throw unavailable();
        var product=new PublicCatalogQuery.ProductView(raw.productId(),raw.name(),raw.specification(),raw.manufacturer(),raw.registrationNo(),image(raw.imageUrl()),raw.detailText(),raw.storageConditions(),raw.shelfLifeDays()!=null&&raw.shelfLifeDays()>0?raw.shelfLifeDays():null);
        return new Detail(product,baseName,fractional,sale,price);
    }
    private static String image(String value){if(value==null||value.length()>2048)return null;try{var uri=new URI(value);return "https".equalsIgnoreCase(uri.getScheme())&&uri.getHost()!=null&&uri.getRawUserInfo()==null&&uri.getFragment()==null&&(uri.getPort()==-1||uri.getPort()>0&&uri.getPort()<=65535)?value:null;}catch(URISyntaxException invalid){return null;}}
    private static void cursor(Long value){if(value!=null)positive(value);}
    private static void positive(Long value){if(value==null||value<=0)throw new IllegalArgumentException("查询编号无效");}
    private static IllegalArgumentException unavailable(){return new IllegalArgumentException("商品资料未公开、已下架或已变化，请重新查询");}
}
