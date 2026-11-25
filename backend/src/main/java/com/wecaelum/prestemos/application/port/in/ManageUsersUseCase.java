package com.wecaelum.prestemos.application.port.in;

import com.wecaelum.prestemos.application.model.PageQuery;
import com.wecaelum.prestemos.application.model.PageResult;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.User;
import com.wecaelum.prestemos.domain.model.UserId;

public interface ManageUsersUseCase {

    User create(CreateUserCommand command);

    User update(UpdateUserCommand command);

    void deactivate(UserId target, UserId actingAdmin);

    User get(UserId id);

    PageResult<User> list(Role roleOrNull, Boolean activeOrNull, PageQuery query);
}
