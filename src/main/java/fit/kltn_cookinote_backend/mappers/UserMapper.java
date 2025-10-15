/*
 * @ (#) UserMapper.java    1.0    23/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.mappers;/*
 * @description:
 * @author: Bao Thong
 * @date: 23/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.UserDto;
import fit.kltn_cookinote_backend.dtos.request.UserDetailDto;
import fit.kltn_cookinote_backend.entities.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "role", expression = "java(user.getRole().name())")
    @Mapping(source = "username", target = "username")
    UserDto toDto(User user);

    @Mapping(source = "username", target = "username")
    UserDetailDto toDetailDto(User user);
}