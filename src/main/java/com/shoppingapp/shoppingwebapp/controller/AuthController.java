package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.dto.RegistrationForm;
import com.shoppingapp.shoppingwebapp.model.User;
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
import java.util.Optional;

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

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "forgot-password";
    }

    /**
     * Answers the same whether or not the address has an account.
     *
     * <p>"No account with that email" would turn this form into a way to test
     * addresses against the customer list, which matters more here than the
     * small kindness of telling someone they typed it wrong.
     */
    @PostMapping("/forgot-password")
    public String requestPasswordReset(@RequestParam String email, RedirectAttributes redirectAttributes) {
        userService.requestPasswordReset(email);
        redirectAttributes.addFlashAttribute("message",
                "If that address has an account, a reset link is on its way. "
                        + "It expires in 30 minutes.");
        return "redirect:/login";
    }

    /**
     * The link's landing page. The token is checked before the form is drawn,
     * so a dead link says so instead of taking a new password and then refusing
     * it.
     */
    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam(required = false) String token, Model model) {
        if (userService.userForResetToken(token).isEmpty()) {
            model.addAttribute("expired", true);
            return "reset-password";
        }
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token,
                                @RequestParam String password,
                                @RequestParam String confirmPassword,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        model.addAttribute("token", token);

        // Resolved first so the password can be checked against this account's
        // address, and so a dead link is reported before anything else.
        Optional<User> account = userService.userForResetToken(token);
        if (account.isEmpty()) {
            model.addAttribute("expired", true);
            return "reset-password";
        }

        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match");
            return "reset-password";
        }
        // The same policy registration uses. Setting a password through a
        // reset is still setting a password; without this the reset form would
        // be the way round the rules.
        String weak = PasswordPolicy.reject(password, account.get().getEmail());
        if (weak != null) {
            model.addAttribute("error", weak);
            return "reset-password";
        }

        if (userService.resetPassword(token, password).isEmpty()) {
            // The token expired between the check above and here, or was used
            // by another tab. Rare, and better said than swallowed.
            model.addAttribute("expired", true);
            return "reset-password";
        }

        redirectAttributes.addFlashAttribute("message",
                "Your password has been changed. Sign in with it below.");
        return "redirect:/login";
    }
}
