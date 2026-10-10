package com.sanproject.service_planner.controller;

import com.sanproject.service_planner.dto.AuthResponse;
import com.sanproject.service_planner.dto.ChangePasswordRequest;
import com.sanproject.service_planner.dto.ConfirmAccountDeletionRequest;
import com.sanproject.service_planner.dto.ForgotPasswordRequest;
import com.sanproject.service_planner.dto.LoginRequest;
import com.sanproject.service_planner.dto.MessageResponse;
import com.sanproject.service_planner.dto.RegisterRequest;
import com.sanproject.service_planner.dto.ResendVerificationRequest;
import com.sanproject.service_planner.dto.ResetPasswordRequest;
import com.sanproject.service_planner.dto.VerifyEmailRequest;
import com.sanproject.service_planner.security.SecurityConfig;
import com.sanproject.service_planner.service.AuthService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public auth endpoints (permitAll in SecurityConfig) plus authenticated change-password.
 * Successful login returns AuthResponse { token, role, id, name } for the React session.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.loginClient(request);
    }

    @PostMapping("/employee-login")
    public AuthResponse employeeLogin(@Valid @RequestBody LoginRequest request) {
        return authService.loginEmployee(request);
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return authService.requestPasswordReset(request);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return authService.verifyEmail(request);
    }

    @PostMapping("/resend-verification")
    public MessageResponse resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        return authService.resendVerification(request);
    }

    // Requires a valid JWT; not covered by the public /auth login/register matchers.
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
    }

    @PostMapping("/request-account-deletion")
    public MessageResponse requestAccountDeletion() {
        return authService.requestAccountDeletion();
    }

    @PostMapping("/confirm-account-deletion")
    public MessageResponse confirmAccountDeletion(@Valid @RequestBody ConfirmAccountDeletionRequest request) {
        return authService.confirmAccountDeletion(request);
    }
}
