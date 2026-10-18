package com.agencydesk.auth;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agencydesk.auth.SessionRepository.MembershipView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record SelectRequest(@NotNull Long membershipId) {}

    @PostMapping("/login")
    public AuthService.LoginResult login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req.email(), req.password());
    }

    @GetMapping("/memberships")
    public List<MembershipView> memberships(@RequestAttribute(AuthInterceptor.USER_ID) long userId) {
        return auth.memberships(userId);
    }

    @PostMapping("/select")
    public MembershipView select(@RequestAttribute(AuthInterceptor.TOKEN) String token,
                                 @RequestAttribute(AuthInterceptor.USER_ID) long userId,
                                 @Valid @RequestBody SelectRequest req) {
        return auth.select(token, userId, req.membershipId());
    }

    @GetMapping("/me")
    public Actor me(Actor actor) {
        return actor;
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestAttribute(AuthInterceptor.TOKEN) String token) {
        auth.logout(token);
        return Map.of("status", "logged out");
    }
}
