package com.agri_system.infrastructure.catalog.query;

import com.agri_system.domain.catalog.query.PublicCatalogQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PublicCatalogQueryImpl implements PublicCatalogQuery {
    private final JdbcTemplate jdbc;
    private static final String PRODUCT=" FROM product p WHERE p.is_online=1 AND p.status=1 AND p.deleted=0 AND p.name IS NOT NULL AND TRIM(p.name)<>''";
    public List<StoreView> stores(Long after){
        var args=new ArrayList<Object>();String cursor="";if(after!=null){cursor=" AND id>?";args.add(after);}
        return jdbc.query("SELECT id,name,phone,address FROM store WHERE client_visible=1 AND status=1 AND deleted=0"
                +" AND name IS NOT NULL AND TRIM(name)<>'' AND phone IS NOT NULL AND TRIM(phone)<>'' AND address IS NOT NULL AND TRIM(address)<>''"
                +cursor+" ORDER BY id LIMIT 21",(r,n)->new StoreView(Long.toString(r.getLong("id")),r.getString("name"),r.getString("phone"),r.getString("address")),args.toArray());
    }
    public List<ProductSummary> products(Long after,String keyword){
        var args=new ArrayList<Object>();String filter="";
        if(after!=null){filter+=" AND p.id>?";args.add(after);}
        if(keyword!=null&&!keyword.isBlank()){
            String literal="%"+keyword.strip().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            filter+=" AND (p.name LIKE ? ESCAPE '!' OR p.code LIKE ? ESCAPE '!')";args.add(literal);args.add(literal);
        }
        return jdbc.query("SELECT p.id,p.name,p.specification,p.manufacturer,p.image_url"+PRODUCT+filter+" ORDER BY p.id LIMIT 21",
                (r,n)->new ProductSummary(Long.toString(r.getLong("id")),r.getString("name"),r.getString("specification"),r.getString("manufacturer"),r.getString("image_url")),args.toArray());
    }
    public Optional<ProductView> product(Long id){
        return jdbc.query("SELECT p.id,p.name,p.specification,p.manufacturer,p.registration_no,p.image_url,p.detail_text,p.storage_conditions,p.shelf_life_days"+PRODUCT+" AND p.id=?",
                (r,n)->new ProductView(Long.toString(r.getLong("id")),r.getString("name"),r.getString("specification"),r.getString("manufacturer"),r.getString("registration_no"),r.getString("image_url"),r.getString("detail_text"),r.getString("storage_conditions"),r.getObject("shelf_life_days",Integer.class)),id).stream().findFirst();
    }
}
