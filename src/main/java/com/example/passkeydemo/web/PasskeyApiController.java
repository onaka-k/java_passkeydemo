package com.example.passkeydemo.web;

import com.example.passkeydemo.model.AppUser;
import com.example.passkeydemo.model.PasskeyCredential;
import com.example.passkeydemo.service.AppUserService;
import com.example.passkeydemo.service.PasskeyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Validated
public class PasskeyApiController {

    private static final String SESSION_REG_CHALLENGE = "reg_challenge";
    private static final String SESSION_LOGIN_CHALLENGE = "login_challenge";
    private static final String SESSION_LOGIN_USERNAME = "login_username";

    private final AppUserService appUserService;
    private final PasskeyService passkeyService;
    private final ObjectMapper objectMapper;

    @GetMapping("/passkeys/list")
    public List<Map<String, Object>> list(Principal principal) {
        AppUser user = appUserService.findByUsername(principal.getName());
        return passkeyService.listByUserId(user.getId()).stream().map(credential -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", credential.getId());
            item.put("label", credential.getLabel());
            item.put("createdAt", credential.getCreatedAt());
            item.put("updatedAt", credential.getUpdatedAt());
            return item;
        }).toList();
    }

    @GetMapping("/passkeys/register/options")
    public ResponseEntity<?> registrationOptions(Principal principal, HttpSession session) {
        AppUser user = appUserService.findByUsername(principal.getName());
        long count = passkeyService.countByUserId(user.getId());
        if (count >= PasskeyService.MAX_PASSKEYS_PER_USER) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Passkey registration limit reached (5)."));
        }

        String challenge = randomBase64Url(32);
        session.setAttribute(SESSION_REG_CHALLENGE, challenge);

        List<Map<String, Object>> excludeCredentials = passkeyService.listByUserId(user.getId()).stream()
            .map(c -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("type", "public-key");
                item.put("id", c.getCredentialId());
                return item;
            })
            .toList();

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("challenge", challenge);
        options.put("rp", Map.of("name", "Java Passkey Demo", "id", "localhost"));
        options.put("user", Map.of(
                "id", base64Url(user.getUsername().getBytes(StandardCharsets.UTF_8)),
                "name", user.getUsername(),
                "displayName", user.getDisplayName()));
        options.put("pubKeyCredParams", List.of(
                Map.of("type", "public-key", "alg", -7),
                Map.of("type", "public-key", "alg", -257)));
        options.put("timeout", 60000);
        options.put("attestation", "none");
        options.put("authenticatorSelection", Map.of("residentKey", "preferred", "userVerification", "preferred"));
        options.put("excludeCredentials", excludeCredentials);

        return ResponseEntity.ok(options);
    }

    @PostMapping("/passkeys/register/finish")
    public ResponseEntity<?> finishRegistration(@RequestBody RegistrationFinishRequest request, Principal principal, HttpSession session) {
        AppUser user = appUserService.findByUsername(principal.getName());
        String expectedChallenge = (String) session.getAttribute(SESSION_REG_CHALLENGE);
        if (expectedChallenge == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Registration session has expired."));
        }

        if (!challengeMatches(request.clientDataJSON(), expectedChallenge)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Challenge mismatch."));
        }

        try {
            passkeyService.register(
                    user,
                    request.credentialId(),
                    request.label(),
                    request.transports(),
                    request.userHandle(),
                    request.payloadJson());
        } catch (PasskeyService.PasskeyLimitExceededException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Passkey registration limit reached (5)."));
        }

        session.removeAttribute(SESSION_REG_CHALLENGE);
        return ResponseEntity.ok(Map.of("message", "Passkey registered."));
    }

    @DeleteMapping("/passkeys/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Principal principal) {
        AppUser user = appUserService.findByUsername(principal.getName());
        passkeyService.deleteById(user.getId(), id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    @PostMapping("/login/passkey/options")
    public ResponseEntity<?> passkeyLoginOptions(@RequestBody PasskeyLoginOptionsRequest request, HttpSession session) {
        AppUser user = appUserService.findByUsername(request.username());
        List<PasskeyCredential> credentials = passkeyService.listByUserId(user.getId());
        if (credentials.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "No passkeys are registered for this ID."));
        }

        String challenge = randomBase64Url(32);
        session.setAttribute(SESSION_LOGIN_CHALLENGE, challenge);
        session.setAttribute(SESSION_LOGIN_USERNAME, user.getUsername());

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("challenge", challenge);
        options.put("timeout", 60000);
        options.put("rpId", "localhost");
        options.put("userVerification", "preferred");
        options.put("allowCredentials", credentials.stream()
                .map(c -> Map.of("type", "public-key", "id", c.getCredentialId()))
                .toList());

        return ResponseEntity.ok(options);
    }

    @PostMapping("/login/passkey")
    public ResponseEntity<?> passkeyLogin(@RequestBody PasskeyLoginRequest request, HttpServletRequest httpRequest, HttpSession session) {
        String expectedChallenge = (String) session.getAttribute(SESSION_LOGIN_CHALLENGE);
        String expectedUsername = (String) session.getAttribute(SESSION_LOGIN_USERNAME);

        if (expectedChallenge == null || expectedUsername == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Login session has expired."));
        }

        if (!expectedUsername.equals(request.username())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "ID mismatch."));
        }

        if (!challengeMatches(request.clientDataJSON(), expectedChallenge)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Challenge mismatch."));
        }

        PasskeyCredential credential = passkeyService.findByCredentialId(request.credentialId());
        if (!credential.getUser().getUsername().equals(request.username())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Credential does not belong to this ID."));
        }

        // NOTE: This demo validates challenge + ownership only. Production requires full signature verification.
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                request.username(),
                null,
                AuthorityUtils.createAuthorityList("ROLE_USER"));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        httpRequest.getSession(true).setAttribute("SPRING_SECURITY_CONTEXT", context);

        passkeyService.touchSignIn(request.credentialId());
        session.removeAttribute(SESSION_LOGIN_CHALLENGE);
        session.removeAttribute(SESSION_LOGIN_USERNAME);

        return ResponseEntity.ok(Map.of("message", "Logged in", "redirect", "/passkeys", "timestamp", Instant.now().toString()));
    }

    private boolean challengeMatches(String clientDataJsonBase64Url, String expectedChallenge) {
        try {
            byte[] clientDataBytes = Base64.getUrlDecoder().decode(padBase64(clientDataJsonBase64Url));
            JsonNode node = objectMapper.readTree(clientDataBytes);
            String actualChallenge = node.path("challenge").asText();
            return expectedChallenge.equals(actualChallenge);
        } catch (Exception ex) {
            return false;
        }
    }

    private String randomBase64Url(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return base64Url(bytes);
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String padBase64(String value) {
        int mod = value.length() % 4;
        if (mod == 0) {
            return value;
        }
        return value + "=".repeat(4 - mod);
    }

    public record RegistrationFinishRequest(
            @NotBlank String credentialId,
            @NotBlank String clientDataJSON,
            String transports,
            String userHandle,
            String label,
            String payloadJson) {
    }

    public record PasskeyLoginOptionsRequest(@NotBlank String username) {
    }

    public record PasskeyLoginRequest(
            @NotBlank String username,
            @NotBlank String credentialId,
            @NotBlank String clientDataJSON,
            String authenticatorData,
            String signature,
            String userHandle) {
    }
}
