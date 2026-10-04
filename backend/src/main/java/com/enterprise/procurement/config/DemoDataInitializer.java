package com.enterprise.procurement.config;

import com.enterprise.procurement.admin.entity.SysUser;
import com.enterprise.procurement.admin.mapper.SysUserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Ensures the documented local demo accounts work after a fresh Docker database init. */
@Component
public class DemoDataInitializer implements CommandLineRunner {
    private final SysUserMapper users;
    private final PasswordEncoder encoder;
    public DemoDataInitializer(SysUserMapper users, PasswordEncoder encoder) { this.users = users; this.encoder = encoder; }
    @Override public void run(String... args) {
        String hash = encoder.encode("admin123");
        for (String username : new String[]{"admin", "employee", "manager", "purchaser", "warehouse"}) {
            SysUser user = users.findByUsername(username);
            if (user != null) { user.setPassword(hash); users.updateById(user); }
        }
    }
}
