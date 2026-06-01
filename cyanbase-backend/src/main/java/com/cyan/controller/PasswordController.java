package com.cyan.controller;

import com.cyan.common.R;
import com.cyan.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordController {

    private final JwtUtil jwtUtil;

    /**
     * 生成密码哈希（仅用于测试）
     */
    @GetMapping("/genPass")
    public R<String> genPass(@RequestParam String password) {
        String encoded = jwtUtil.encodePassword(password);
        return R.ok(encoded);
    }
}