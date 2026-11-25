package com.wecaelum.prestemos.application.port.out;

import com.wecaelum.prestemos.application.model.PageQuery;
import com.wecaelum.prestemos.application.model.PageResult;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.User;
import com.wecaelum.prestemos.domain.model.UserId;
import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);

    PageResult<User> findAll(Role roleOrNull, Boolean activeOrNull, PageQuery query);
}
