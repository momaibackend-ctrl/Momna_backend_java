package com.momna.platform.database;

import com.momna.platform.database.DatabaseContract.TransactionContract;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringTransactionAdapter implements TransactionContract {
    private final TransactionTemplate transactionTemplate;

    public SpringTransactionAdapter(
        TransactionTemplate transactionTemplate
    ) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public <T> T inTransaction(Supplier<T> block) {
        return transactionTemplate.execute(status -> block.get());
    }
}
