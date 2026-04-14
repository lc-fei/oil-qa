package org.example.springboot.controller;

import org.example.springboot.entity.Result;
import org.example.springboot.security.AuthContext;
import org.example.springboot.security.UserPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        UserPrincipal principal = AuthContext.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "管理端鉴权已生效");
        data.put("currentUser", principal.getUsername());
        data.put("roles", principal.getRoles());
        return Result.success(data);
    }
}
