package com.vaultchain.service;

import java.util.List;
import java.util.Map;

public interface WalletService {
    Map<String,Object> wallet(long userId);
    List<Map<String,Object>> transactions(long userId);
    Map<String,Object> add(long userId,Map<String,Object> request);
}
