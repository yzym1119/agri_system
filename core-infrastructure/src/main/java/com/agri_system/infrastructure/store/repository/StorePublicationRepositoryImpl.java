package com.agri_system.infrastructure.store.repository;
import com.agri_system.domain.store.repository.StorePublicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
@Repository
@RequiredArgsConstructor
public class StorePublicationRepositoryImpl implements StorePublicationRepository {
    private final JdbcTemplate jdbc;
    public Optional<State> find(Long id,boolean lock){
        return jdbc.query("SELECT id,name,phone,address,status,deleted,client_visible,client_publication_version FROM store WHERE id=?"+(lock?" FOR UPDATE":""),
                (r,n)->new State(r.getLong("id"),r.getString("name"),r.getString("phone"),r.getString("address"),r.getObject("status",Integer.class),r.getObject("deleted",Integer.class),r.getObject("client_visible",Integer.class),r.getObject("client_publication_version",Long.class)),id).stream().findFirst();
    }
    public boolean setVisible(Long id,Long expectedVersion,int visible){
        return jdbc.update("UPDATE store SET client_visible=?,client_publication_version=client_publication_version+1 WHERE id=? AND client_publication_version=? AND deleted=0",visible,id,expectedVersion)==1;
    }
}
