package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.dto.RegistrationForm;
import com.shoppingapp.shoppingwebapp.security.PasswordPolicy;
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
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

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
        // Length is checked by the annotation; this is the rest of the policy,
        // which needs the email to spot a password built out of it.
        String weak = PasswordPolicy.reject(registrationForm.getPassword(), registrationForm.getEmail());
        if (weak != null && !bindingResult.hasFieldErrors("password")) {
            bindingResult.rejectValue("password", "password.weak", weak);
        }
        if (bindingResult.hasErrors()) {
            return "register";
        }

        userService.register(registrationForm);
        // Carried so the code form can prefill the address the user just typed.
        return "redirect:/verify?email=" + UriUtils.encodeQueryParam(
                registrationForm.getEmail().trim().toLowerCase(), StandardCharsets.UTF_8);
    }

    /**
     * The code entry form. Reached straight after registering, and linked from
     * a refused sign-in.
     */
    @GetMapping("/verify")
    public String verifyForm(@RequestParam(required = false) String email, Model model) {
        model.addAttribute("email", email == null ? "" : email);
        return "verify";
    }

    @PostMapping("/verify")
    public String verify(@RequestParam String email,
                         @RequestParam String code,
                         RedirectAttributes redirectAttributes) {
        if (userService.verify(email, code).isPresent()) {
            redirectAttributes.addFlashAttribute("message", "Email confirmed. You can sign in now.");
            return "redirect:/login";
        }
        // Wrong, expired and exhausted codes all say the same thing, so the
        // form cannot be used to work out which addresses have accounts.
        redirectAttributes.addFlashAttribute("error",
                "That code is not valid. It may have expired, or been entered incorrectly too many times. "
                        + "Request a new one below.");
        redirectAttributes.addAttribute("email", email);
        return "redirect:/verify";
    }

    @PostMapping("/resend-verification")
    public String resendVerification(@RequestParam String email, RedirectAttributes redirectAttributes) {
        userService.resendVerification(email);
        // Always the same answer, whether or not the address is registered.
        redirectAttributes.addFlashAttribute("message",
                "If that address has an account awaiting confirmation, a new code is on its way.");
        redirectAttributes.addAttribute("email", email);
        return "redirect:/verify";
    }
}
