package com.neko.serviceImpl;

import com.neko.dto.UserDto;
import com.neko.entity.User;
import com.neko.exceptions.ErrorCode.ErrorCode;
import com.neko.exceptions.UserTaskReminderException;
import com.neko.repositories.UserRepository;
import com.neko.service.UserService;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final ModelMapper mapper;
    private final Clock clock;

    public UserServiceImpl(UserRepository userRepository, ModelMapper mapper, Clock clock) {
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UserDto create(UserDto user) {
        if (userRepository.existsByUserName(user.getUserName())) {
            throw new UserTaskReminderException(ErrorCode.DUPLICATE_USER_NAME, user.getUserName());
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new UserTaskReminderException(ErrorCode.DUPLICATE_EMAIL, user.getEmail());
        }

        User entity = new User();
        entity.setId(UUID.randomUUID());
        entity.setUserName(user.getUserName());
        entity.setEmail(user.getEmail());
        entity.setFirstName(user.getFirstName());
        entity.setLastName(user.getLastName());
        entity.setRoles(user.getRoles() == null ? new ArrayList<>() : new ArrayList<>(user.getRoles()));
        entity.setCreatedDate(LocalDateTime.now(clock));
        entity.setUpdatedDate(entity.getCreatedDate());

        User saved = userRepository.save(entity);
        log.info("Created user {} ({})", saved.getUserName(), saved.getId());
        return mapper.map(saved, UserDto.class);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto get(UUID id) {
        return mapper.map(findOrThrow(id), UserDto.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return userRepository.findAll().stream()
                .map(user -> mapper.map(user, UserDto.class))
                .toList();
    }

    /**
     * Partial update: only non-null fields on the incoming DTO are applied.
     * Server-owned fields (id, createdDate) are never taken from the request.
     */
    @Override
    @Transactional
    public UserDto update(UUID id, UserDto user) {
        User existing = findOrThrow(id);

        if (user.getUserName() != null && !user.getUserName().equals(existing.getUserName())) {
            if (userRepository.existsByUserName(user.getUserName())) {
                throw new UserTaskReminderException(ErrorCode.DUPLICATE_USER_NAME, user.getUserName());
            }
            existing.setUserName(user.getUserName());
        }
        if (user.getEmail() != null && !user.getEmail().equals(existing.getEmail())) {
            if (userRepository.existsByEmail(user.getEmail())) {
                throw new UserTaskReminderException(ErrorCode.DUPLICATE_EMAIL, user.getEmail());
            }
            existing.setEmail(user.getEmail());
        }
        if (user.getFirstName() != null) {
            existing.setFirstName(user.getFirstName());
        }
        if (user.getLastName() != null) {
            existing.setLastName(user.getLastName());
        }
        if (user.getRoles() != null) {
            existing.setRoles(new ArrayList<>(user.getRoles()));
        }
        existing.setUpdatedDate(LocalDateTime.now(clock));

        return mapper.map(userRepository.save(existing), UserDto.class);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        userRepository.delete(findOrThrow(id));
        log.info("Deleted user {}", id);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserTaskReminderException(ErrorCode.USER_NOT_FOUND, id));
    }
}
