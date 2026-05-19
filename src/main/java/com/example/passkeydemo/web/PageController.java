package com.example.passkeydemo.web;

import com.example.passkeydemo.model.AppUser;
import com.example.passkeydemo.service.AppUserService;
import com.example.passkeydemo.service.PasskeyService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final AppUserService appUserService;
    private final PasskeyService passkeyService;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/passkeys")
    public String passkeysPage(Model model, Principal principal) {
        AppUser user = appUserService.findByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());
        model.addAttribute("displayName", user.getDisplayName());
        model.addAttribute("passkeys", passkeyService.listByUserId(user.getId()));
        model.addAttribute("count", passkeyService.countByUserId(user.getId()));
        model.addAttribute("max", PasskeyService.MAX_PASSKEYS_PER_USER);
        return "passkeys";
    }
}
