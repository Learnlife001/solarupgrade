package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.service.UserService;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Resolves the signed-in account from the security principal. Controllers that
 * need the domain {@link User} rather than just a username go through here.
 */
@Component
public class CurrentUserSupport {

    private final UserService userService;

    public CurrentUserSupport(UserService userService) {
        this.userService = userService;
    }

    public User require(Principal principal) {
        if (principal == null) {
            throw new IllegalStateException("No authenticated user on a secured request");
        }
        return userService.getByEmail(principal.getName());
    }
}
