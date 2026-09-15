package com.vns.healthcare.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private static final List<String> AVAILABLE_PERMISSIONS = Arrays.asList(
            "dashboard",
            "employees",
            "customers",
            "attendance",
            "salary",
            "admin"
    );

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminController(RoleRepository roleRepository,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping({"", "/users"})
    public String users(Model model) {
        model.addAttribute("page", "admin");
        model.addAttribute("users", userRepository.findAll());
        return "admin/users";
    }

    @GetMapping("/users/new")
    public String newUserForm(Model model) {
        UserForm form = new UserForm();
        form.setEnabled(true);
        model.addAttribute("page", "admin");
        model.addAttribute("userForm", form);
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("accessModules", AVAILABLE_PERMISSIONS);
        return "admin/user-form";
    }

    @GetMapping("/users/{id}/edit")
    public String editUserForm(@PathVariable Long id, Model model) {
        AppUser user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        UserForm form = new UserForm();
        form.setUsername(user.getUsername());
        form.setEnabled(user.isEnabled());
        List<Long> roleIds = new ArrayList<Long>();
        for (Role role : user.getRoles()) {
            roleIds.add(role.getId());
        }
        form.setRoleIds(roleIds);

        List<String> readPermissions = new ArrayList<String>();
        List<String> writePermissions = new ArrayList<String>();
        for (String permission : user.getPermissions()) {
            if (permission == null || permission.trim().isEmpty()) {
                continue;
            }
            String normalized = permission.trim().toLowerCase();
            if (normalized.endsWith(":read")) {
                readPermissions.add(normalized.substring(0, normalized.lastIndexOf(":read")));
            } else if (normalized.endsWith(":write")) {
                writePermissions.add(normalized.substring(0, normalized.lastIndexOf(":write")));
            }
        }
        form.setReadPermissions(readPermissions);
        form.setWritePermissions(writePermissions);

        model.addAttribute("page", "admin");
        model.addAttribute("userForm", form);
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("accessModules", AVAILABLE_PERMISSIONS);
        model.addAttribute("userId", id);
        return "admin/user-form";
    }

    @PostMapping("/users")
    public String saveUser(@ModelAttribute("userForm") UserForm form,
                           RedirectAttributes redirectAttributes) {
        String username = form.getUsername() == null ? "" : form.getUsername().trim();
        if (userRepository.existsByUsername(username)) {
            log.warn("Attempt to create duplicate user [{}]", username);
            redirectAttributes.addFlashAttribute("error", "Username already exists.");
            return "redirect:/admin/users/new";
        }

        AppUser appUser = new AppUser();
        appUser.setUsername(username);
        appUser.setPassword(passwordEncoder.encode(form.getPassword()));
        appUser.setEnabled(form.isEnabled());

        List<Role> roles = roleRepository.findAllById(form.getRoleIds());
        appUser.setRoles(new HashSet<Role>(roles));
        appUser.setPermissions(new HashSet<String>(buildPermissions(form)));
        userRepository.save(appUser);
        log.info("Created user [{}] with roles [{}] and permissions [{}]", username, roles.size(), appUser.getPermissions());

        redirectAttributes.addFlashAttribute("success", "User created successfully.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}")
    public String updateUser(@PathVariable Long id,
                             @ModelAttribute("userForm") UserForm form,
                             RedirectAttributes redirectAttributes) {
        AppUser user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        String requested = form.getUsername() == null ? "" : form.getUsername().trim();
        if (!requested.isEmpty() && !requested.equalsIgnoreCase(user.getUsername())
                && userRepository.existsByUsername(requested)) {
            log.warn("Attempt to update user [{}] to duplicate username [{}]", user.getUsername(), requested);
            redirectAttributes.addFlashAttribute("error", "Username already exists.");
            return "redirect:/admin/users/" + id + "/edit";
        }

        user.setUsername(requested);
        user.setEnabled(form.isEnabled());
        if (form.getPassword() != null && !form.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        user.setRoles(new HashSet<Role>(roleRepository.findAllById(form.getRoleIds())));
        user.setPermissions(new HashSet<String>(buildPermissions(form)));
        userRepository.save(user);
        log.info("Updated user [{}]", user.getUsername());

        redirectAttributes.addFlashAttribute("success", "User updated successfully.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        AppUser user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            log.warn("Blocked deletion of protected admin user [{}]", user.getUsername());
            redirectAttributes.addFlashAttribute("error", "Default admin user cannot be deleted.");
            return "redirect:/admin/users";
        }
        userRepository.delete(user);
        log.info("Deleted user [{}]", user.getUsername());
        redirectAttributes.addFlashAttribute("success", "User deleted successfully.");
        return "redirect:/admin/users";
    }

    @GetMapping("/roles")
    public String roles(Model model) {
        model.addAttribute("page", "admin");
        model.addAttribute("roles", roleRepository.findAll());
        return "admin/roles";
    }

    @GetMapping("/roles/new")
    public String newRoleForm(Model model) {
        RoleForm form = new RoleForm();
        model.addAttribute("page", "admin");
        model.addAttribute("roleForm", form);
        model.addAttribute("permissions", AVAILABLE_PERMISSIONS);
        return "admin/role-form";
    }

    @PostMapping("/roles")
    public String saveRole(@ModelAttribute("roleForm") RoleForm form,
                           RedirectAttributes redirectAttributes) {
        String normalized = form.getName() == null ? "" : form.getName().trim();
        if (normalized.isEmpty()) {
            log.warn("Attempted role save with empty name");
            redirectAttributes.addFlashAttribute("error", "Role name is required.");
            return "redirect:/admin/roles/new";
        }

        Role existing = roleRepository.findByName(normalized.toUpperCase())
                .orElseGet(() -> new Role());
        existing.setName(normalized.toUpperCase());
        existing.setDescription(form.getDescription());

        Set<String> permissions = new HashSet<String>();
        if (form.getPermissions() != null) {
            for (String permission : form.getPermissions()) {
                if (permission != null && !permission.trim().isEmpty()) {
                    permissions.add(permission.trim().toLowerCase());
                }
            }
        }
        existing.setPermissions(permissions);
        roleRepository.save(existing);
        log.info("Saved role [{}] with {} permissions", existing.getName(), permissions.size());

        redirectAttributes.addFlashAttribute("success", "Role saved successfully.");
        return "redirect:/admin/roles";
    }

    @GetMapping("/roles/{id}/edit")
    public String editRole(@PathVariable Long id, Model model) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Role not found"));
        RoleForm form = new RoleForm();
        form.setName(role.getName());
        form.setDescription(role.getDescription());
        form.setPermissions(new ArrayList<String>(role.getPermissions()));

        model.addAttribute("page", "admin");
        model.addAttribute("roleForm", form);
        model.addAttribute("permissions", AVAILABLE_PERMISSIONS);
        model.addAttribute("roleId", id);
        return "admin/role-form";
    }

    @PostMapping("/roles/{id}/edit")
    public String updateRole(@PathVariable Long id,
                             @ModelAttribute("roleForm") RoleForm form,
                             RedirectAttributes redirectAttributes) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Role not found"));
        role.setName(form.getName().trim().toUpperCase());
        role.setDescription(form.getDescription());

        Set<String> permissions = new HashSet<String>();
        for (String permission : form.getPermissions()) {
            if (permission != null && !permission.trim().isEmpty()) {
                permissions.add(permission.trim().toLowerCase());
            }
        }
        role.setPermissions(permissions);
        roleRepository.save(role);
        log.info("Updated role [{}] with {} permissions", role.getName(), permissions.size());

        redirectAttributes.addFlashAttribute("success", "Role updated successfully.");
        return "redirect:/admin/roles";
    }

    private List<String> buildPermissions(UserForm userForm) {
        Set<String> permissions = new HashSet<String>();
        if (userForm.getReadPermissions() != null) {
            for (String module : userForm.getReadPermissions()) {
                if (module != null && !module.trim().isEmpty()) {
                    permissions.add(module.trim().toLowerCase() + ":read");
                }
            }
        }
        if (userForm.getWritePermissions() != null) {
            for (String module : userForm.getWritePermissions()) {
                if (module != null && !module.trim().isEmpty()) {
                    permissions.add(module.trim().toLowerCase() + ":write");
                }
            }
        }
        return new ArrayList<String>(permissions);
    }
}
