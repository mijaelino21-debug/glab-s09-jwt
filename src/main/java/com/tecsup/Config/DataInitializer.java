package com.tecsup.config;

import com.tecsup.entity.Role;
import com.tecsup.entity.RoleType;
import com.tecsup.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;

    @Override
    public void run(String... args) throws Exception {
        String[] roleNames = {"ADMIN", "USER", "MODERATOR"};
        for (String roleName : roleNames) {
            if (!roleRepository.existsByName(RoleType.valueOf(roleName))) {
                roleRepository.save(new Role(roleName, RoleType.valueOf(roleName)));
            }
        }
    }
}