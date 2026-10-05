package com.authvault.service.impl;

import com.authvault.entity.Wallet;
import com.authvault.entity.WalletTransaction;
import com.authvault.exception.ApiException;
import com.authvault.repository.WalletJpaRepository;
import com.authvault.repository.WalletTransactionJpaRepository;
import com.authvault.service.WalletService;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WalletServiceImpl implements WalletService {
    private final WalletJpaRepository wallets;
    private final WalletTransactionJpaRepository transactions;
    private final TransactionTemplate transaction;
    private final EntityManager entityManager;
    public WalletServiceImpl(WalletJpaRepository wallets,WalletTransactionJpaRepository transactions,
            TransactionTemplate transaction,EntityManager entityManager) {
        this.wallets=wallets;this.transactions=transactions;this.transaction=transaction;this.entityManager=entityManager;
    }
    private Wallet owned(long userId){return wallets.findByUserId(userId).orElseThrow(()->new ApiException(404,"Wallet not found"));}
    @Override public Map<String,Object> wallet(long userId){return Map.of("balance",owned(userId).getBalance(),"currency","Credits");}
    private static Map<String,Object> transaction(WalletTransaction t){Map<String,Object> out=new LinkedHashMap<>();
        out.put("id",t.getId());out.put("walletId",t.getWalletId());out.put("type",t.getType());
        out.put("amount",t.getAmount());out.put("description",t.getDescription());out.put("referenceId",t.getReferenceId());
        out.put("createdAt",t.getCreatedAt());return out;}
    @Override public List<Map<String,Object>> transactions(long userId){Wallet w=owned(userId);
        return transactions.findByWalletIdOrderByCreatedAtDescIdDesc(w.getId()).stream().map(WalletServiceImpl::transaction).toList();}
    @Override public synchronized Map<String,Object> add(long userId,Map<String,Object> request){
        String type=request==null?null:String.valueOf(request.get("type"));
        if(!"deposit".equals(type)&&!"withdrawal".equals(type))throw new ApiException(400,"Type must be one of deposit or withdrawal");
        double amount;
        try{amount=Double.parseDouble(String.valueOf(request.get("amount")));}
        catch(Exception failure){throw new ApiException(400,"Amount must be a positive number");}
        if(!Double.isFinite(amount)||amount<=0)throw new ApiException(400,"Amount must be a positive number");
        return transaction.execute(state->{
            Wallet w=owned(userId);double next=w.getBalance()+(type.equals("deposit")?amount:-amount);
            if(next<0)throw new ApiException(400,"Insufficient wallet balance");
            w.setBalance(next);wallets.saveAndFlush(w);
            WalletTransaction t=new WalletTransaction();t.setWalletId(w.getId());t.setType(type);t.setAmount(amount);
            if(request.get("description")!=null)t.setDescription(String.valueOf(request.get("description")));
            if(request.get("referenceId")!=null)t.setReferenceId(String.valueOf(request.get("referenceId")));
            t=transactions.saveAndFlush(t);entityManager.refresh(t);
            return Map.of("wallet",Map.of("balance",next,"currency","Credits"),"transaction",transaction(t));
        });
    }
}
