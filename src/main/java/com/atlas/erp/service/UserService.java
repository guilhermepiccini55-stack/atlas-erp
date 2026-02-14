package com.atlas.erp.service;

import com.atlas.erp.dto.CreateUserRequest;
import com.atlas.erp.dto.UpdateUserRequest;
import com.atlas.erp.dto.UserDTO;
import com.atlas.erp.entity.User;
import com.atlas.erp.exception.ResourceNotFoundException;
import com.atlas.erp.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserDTO createUser(CreateUserRequest request) {
    User user = new User();
    user.setName(request.getName());
    user.setEmail(request.getEmail());
    user.setPassword(request.getPassword());

    User saved = userRepository.save(user);
    return toDTO(saved);
}


    public List<UserDTO> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return toDTO(user);
    }

    public UserDTO updateUser(Long id, UpdateUserRequest request) {

    User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    user.setName(request.getName());
    user.setEmail(request.getEmail());

    User updated = userRepository.save(user);
    return toDTO(updated);
    }

    public void deleteUser(Long id) {

    User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    userRepository.delete(user);
}

    private UserDTO toDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }
}
