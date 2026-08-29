package com.neko.service;

import com.neko.dto.UserDto;

import java.util.List;
import java.util.UUID;

public interface UserService {

    UserDto create(UserDto user);

    UserDto get(UUID id);

    List<UserDto> list();

    UserDto update(UUID id, UserDto user);

    void delete(UUID id);
}
