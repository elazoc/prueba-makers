package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({LoanPersistenceAdapter.class, UserPersistenceAdapter.class})
class LoanPersistenceAdapterH2Test extends LoanPersistenceAdapterContract {}
