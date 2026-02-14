package com.atlas.erp.controller;

import com.atlas.erp.dto.CreateUserRequest;
import com.atlas.erp.dto.UpdateUserRequest;
import com.atlas.erp.dto.UserDTO;
import com.atlas.erp.service.UserService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDTO create(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }

    @GetMapping
    public List<UserDTO> list() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public UserDTO getById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @PutMapping("/{id}")
public UserDTO update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserRequest request) {

    return userService.updateUser(id, request);
}

@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void delete(@PathVariable Long id) {
    userService.deleteUser(id);
}

}
