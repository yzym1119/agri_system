package com.agri_system.application.order.service;

import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.inventory.dto.query.InventoryQuery;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.order.dto.vo.PosCustomerVO;
import com.agri_system.application.product.dto.query.ProductQuery;
import com.agri_system.application.product.dto.vo.CategoryTreeVO;
import com.agri_system.application.product.dto.vo.ProductDetailVO;
import com.agri_system.application.product.service.ProductApplicationService;
import com.agri_system.application.productpackage.dto.query.ProductPackageQuery;
import com.agri_system.application.productpackage.dto.vo.ProductPackageDetailVO;
import com.agri_system.application.productpackage.service.ProductPackageApplicationService;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.inventory.vo.InventoryPageVO;
import com.mybatisflex.core.paginate.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/** POS选货所需只读投影；管理档案与库存写权限不随收款权限授予。 */
@Service
@RequiredArgsConstructor
public class PosCatalogApplicationService {
    private final ProductApplicationService products;
    private final ProductPackageApplicationService packages;
    private final InventoryApplicationService inventory;
    private final CustomerRepository customerRepository;

    public Page<ProductDetailVO> products(int number,int size) {
        return products(number, size, null);
    }

    /** 服务端关键词搜索：命中编码/名称/条码，避免移动端一次性拉全量后本地过滤。 */
    public Page<ProductDetailVO> products(int number,int size,String keyword) {
        validatePage(number,size);
        if (keyword != null && !keyword.trim().isEmpty()) {
            var source=products.queryActiveByKeyword(keyword.trim(),number,size);
            var result=new Page<ProductDetailVO>();result.setPageNumber(source.getPageNumber());
            result.setPageSize(source.getPageSize());result.setTotalRow(source.getTotalRow());
            result.setRecords(source.getRecords().stream().map(this::toCatalogProduct).toList());
            return result;
        }
        var query=new ProductQuery();query.setStatus(1);query.setPageNum(number);query.setPageSize(size);
        var source=products.queryProducts(query);
        var result=new Page<ProductDetailVO>();result.setPageNumber(source.getPageNumber());
        result.setPageSize(source.getPageSize());result.setTotalRow(source.getTotalRow());
        result.setRecords(source.getRecords().stream().map(this::toCatalogProduct).toList());return result;
    }

    private ProductDetailVO toCatalogProduct(ProductDetailVO p) {
        var sale=new ProductDetailVO();sale.setId(p.getId());sale.setCode(p.getCode());sale.setName(p.getName());
        sale.setCategoryId(p.getCategoryId());sale.setSpecification(p.getSpecification());sale.setManufacturer(p.getManufacturer());
        sale.setMainUnit(p.getMainUnit());sale.setBarcode(p.getBarcode());sale.setImageUrl(p.getImageUrl());
        sale.setSuggestedRetailPrice(p.getSuggestedRetailPrice());sale.setPricingUnit(p.getPricingUnit());
        sale.setBaseUnitName(p.getBaseUnitName());sale.setAllowFractionalBaseQuantity(p.getAllowFractionalBaseQuantity());
        sale.setAllowSpecialFund(p.getAllowSpecialFund());sale.setUnits(p.getUnits());sale.setStatus(p.getStatus());
        return sale;
    }

    public List<CategoryTreeVO> categories() {return products.getCategoryTree();}

    /** 收银选客户：按姓名/编码/手机号模糊匹配正常客户，只返回开单与配送所需字段。 */
    public List<PosCustomerVO> customers(String keyword,int limit) {
        return customerRepository.findActiveByKeyword(keyword,limit).stream().map(c->new PosCustomerVO(
                c.getId(),c.getCode(),c.getName(),c.getPhone(),c.getAddress(),
                c.getType(),c.getVipLevel(),c.isVip()&&!c.isVipExpired())).toList();
    }

    public Page<ProductPackageDetailVO> packages(Long storeId,int number,int size) {
        validatePage(number,size);var query=new ProductPackageQuery();query.setStatus(1);
        query.setStoreId(scopedStore(storeId));query.setPageNum(number);query.setPageSize(size);
        return packages.queryPackages(query);
    }

    public Page<InventoryPageVO> inventory(Long storeId,int number,int size) {
        validatePage(number,size);var query=new InventoryQuery();query.setStoreId(scopedStore(storeId));
        query.setPageNum(number);query.setPageSize(size);return inventory.queryInventoryPage(query);
    }

    private Long scopedStore(Long requested) {
        if(CurrentUser.staffId()==null) throw new IllegalArgumentException("仅员工可读取收银目录");
        Long own=CurrentUser.storeId();
        if(!CurrentUser.isAdmin() && requested!=null && !requested.equals(own)) throw new IllegalArgumentException("不能读取其他门店库存");
        return requested==null?own:requested;
    }

    private void validatePage(int number,int size) {
        if(number<1 || size<1 || size>10000) throw new IllegalArgumentException("收银查询分页超出范围");
    }
}
