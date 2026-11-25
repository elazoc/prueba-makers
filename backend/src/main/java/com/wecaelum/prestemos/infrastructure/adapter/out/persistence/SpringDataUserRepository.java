package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;

import com.wecaelum.prestemos.domain.model.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select u from UserJpaEntity u where (:role is null or u.role = :role) "
            + "and (:active is null or u.active = :active)")
    Page<UserJpaEntity> search(@Param("role") Role role, @Param("active") Boolean active, Pageable pageable);
}
