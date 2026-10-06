package com.pharmacy.controller;

import com.pharmacy.entity.Role;
import com.pharmacy.entity.User;
import com.pharmacy.repository.UserRepository;
import com.pharmacy.service.BusinessException;
import com.pharmacy.util.Fmt;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/users")
public class UserController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Fmt fmt;

    public UserController(UserRepository users, PasswordEncoder encoder, Fmt fmt) {
        this.users = users;
        this.encoder = encoder;
        this.fmt = fmt;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", users.findAll());
        return "users/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("roles", Role.values());
        return "users/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("user", users.findById(id).orElseThrow(() -> new BusinessException("error.notFound")));
        model.addAttribute("roles", Role.values());
        return "users/form";
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id, @RequestParam String username,
                       @RequestParam(required = false) String fullName, @RequestParam Role role,
                       @RequestParam(required = false) String password,
                       @RequestParam(defaultValue = "false") boolean active,
                       Authentication auth, RedirectAttributes ra) {
        User u;
        if (id == null) {
            if (users.existsByUsername(username.trim())) throw new BusinessException("error.duplicateUsername");
            if (!StringUtils.hasText(password)) throw new BusinessException("error.passwordRequired");
            u = new User();
        } else {
            u = users.findById(id).orElseThrow(() -> new BusinessException("error.notFound"));
            if (!u.getUsername().equals(username.trim()) && users.existsByUsername(username.trim()))
                throw new BusinessException("error.duplicateUsername");
            // Never lock yourself out.
            if (u.getUsername().equals(auth.getName()) && (!active || role != Role.ADMIN))
                throw new BusinessException("error.cannotDemoteSelf");
        }
        u.setUsername(username.trim());
        u.setFullName(fullName);
        u.setRole(role);
        u.setActive(active);
        if (StringUtils.hasText(password)) {
            if (password.length() < 4) throw new BusinessException("error.passwordShort");
            u.setPassword(encoder.encode(password));
        }
        users.save(u);
        ra.addFlashAttribute("success", fmt.msg("common.saved"));
        return "redirect:/users";
    }
}
