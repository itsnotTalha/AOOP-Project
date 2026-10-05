package com.authvault.repository;

import jakarta.persistence.EntityManager;
import java.util.*;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

/** Aggregate reporting queries; parameters are bound, and callers supply only fixed SQL. */
@Repository
public class AdminReadRepository {
    private final EntityManager manager;
    public AdminReadRepository(EntityManager manager) { this.manager = manager; }
    @SuppressWarnings("unchecked")
    public List<Map<String,Object>> rows(String sql, Object... parameters) {
        var query = manager.createNativeQuery(sql).unwrap(NativeQuery.class);
        for (int i=0;i<parameters.length;i++) query.setParameter(i+1, parameters[i]);
        query.setTupleTransformer((values, aliases) -> {
            Map<String,Object> row = new LinkedHashMap<>();
            for (int i=0;i<values.length;i++) row.put(aliases[i], values[i]);
            return row;
        });
        return query.getResultList();
    }
    public Map<String,Object> one(String sql,Object... parameters) { return rows(sql,parameters).getFirst(); }
}
