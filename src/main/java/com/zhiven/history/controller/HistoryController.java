package com.zhiven.history.controller;

import com.zhiven.auth.entity.User;
import com.zhiven.auth.service.UserService;
import com.zhiven.qa.entity.QaHistory;
import com.zhiven.qa.repository.QaHistoryRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.List;

@Controller
public class HistoryController {

    private final QaHistoryRepository qaHistoryRepository;
    private final UserService userService;

    public HistoryController(QaHistoryRepository qaHistoryRepository, UserService userService) {
        this.qaHistoryRepository = qaHistoryRepository;
        this.userService = userService;
    }

    @GetMapping("/history")
    public String history(Principal principal, Model model) {
        User user = userService.findByUsername(principal.getName());
        List<QaHistory> histories = qaHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        model.addAttribute("histories", histories);
        return "history";
    }
}
