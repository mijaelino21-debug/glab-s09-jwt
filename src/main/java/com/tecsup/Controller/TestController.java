package com.tecsup.Controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/all")
    public String allAccess() {
        return "Public Content.";
    }

    @GetMapping("/user")
    public String userAccess(@RequestParam(required = false) String token) {
        return "User Content.";
    }

    @GetMapping("/admin")
    public String adminAccess() {
        return "Admin Content.";
    }

    @GetMapping("/mod")
    public String modAccess() {
        return "Moderator Content.";
    }
}