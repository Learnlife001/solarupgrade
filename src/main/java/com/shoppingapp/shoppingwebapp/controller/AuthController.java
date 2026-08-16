package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.dto.RegistrationForm;
import com.shoppingapp.shoppingwebapp.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("registrationForm", new RegistrationForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm registrationForm,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (!registrationForm.passwordsMatch()) {
            bindingResult.rejectValue("confirmPassword", "passwords.mismatch", "Passwords do not match");
        }
        if (userService.emailTaken(registrationForm.getEmail())) {
            bindingResult.rejectValue("email", "email.taken", "An account with that email already exists");
        }
        if (bindingResult.hasErrors()) {
            return "register";
        }

        userService.register(registrationForm);
        redirectAttributes.addFlashAttribute("pendingEmail", registrationForm.getEmail().trim().toLowerCase());
        return "redirect:/register/check-email";
    }

    /**
     * Shown after registering. Deliberately a separate page rather than a flash
     * message on the sign-in form, because the next step is to go and read an
     * email, not to try signing in.
     */
    @GetMapping("/register/check-email")
    public String checkEmail() {
        return "check-email";
    }

    @GetMapping("/verify")
    public String verify(@RequestParam(required = false) String token, RedirectAttributes redirectAttributes) {
        return userService.verify(token)
                .map(user -> {
                    redirectAttributes.addFlashAttribute("message",
                            "Email confirmed. You can sign in now.");
                    return "redirect:/login";
                })
                .orElseGet(() -> {
                    // Unknown, already-used and expired tokens are reported the
                    // same way, so this cannot be used to probe for valid ones.
                    redirectAttributes.addFlashAttribute("error",
                            "That confirmation link is invalid or has expired. Request a new one below.");
                    return "redirect:/login?unverified";
                });
    }

    @PostMapping("/resend-verification")
    public String resendVerification(@RequestParam String email, RedirectAttributes redirectAttributes) {
        userService.resendVerification(email);
        // Always the same answer, whether or not the address is registered.
        redirectAttributes.addFlashAttribute("message",
                "If that address has an account awaiting confirmation, a new link is on its way.");
        return "redirect:/login";
    }
}
