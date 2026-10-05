package com.authvault.service.impl;

import com.authvault.repository.DashboardReadRepository;
import com.authvault.service.DashboardService;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardServiceImpl implements DashboardService {
    private final DashboardReadRepository repository;
    public DashboardServiceImpl(DashboardReadRepository repository){this.repository=repository;}
    @Override @Transactional(readOnly=true)
    public Map<String,Object> summary(long userId){return repository.summary(userId);}
}
