package com.wecaelum.prestemos.infrastructure.adapter.out.event;

import com.wecaelum.prestemos.domain.event.LoanEvent;
import com.wecaelum.prestemos.domain.event.UserChanged;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Evicts the cached reads once the change that made them stale is committed; a rolled-back change evicts nothing.
 * Listens to the same domain events as the live updates. {@code loansByApplicant} is cleared whole because
 * Ehcache cannot evict by key prefix (design section 10).
 */
@Component
class CacheEvictionListener {

    static final String LOAN_BY_ID = "loanById";
    static final String LOANS_BY_APPLICANT = "loansByApplicant";
    static final String USER_AUTH_BY_ID = "userAuthById";

    private final CacheManager cacheManager;

    CacheEvictionListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onLoanEvent(LoanEvent event) {
        cache(LOAN_BY_ID).evict(event.loanId().value());
        cache(LOANS_BY_APPLICANT).clear();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserChanged(UserChanged event) {
        cache(USER_AUTH_BY_ID).evict(event.userId().value());
    }

    private Cache cache(String name) {
        Cache cache = cacheManager.getCache(name);
        if (cache == null) {
            throw new IllegalStateException("Cache '" + name + "' is not configured in ehcache.xml");
        }
        return cache;
    }
}
