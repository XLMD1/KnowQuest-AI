package com.zhiven.qa.controller;

import com.zhiven.auth.entity.User;
import com.zhiven.auth.service.UserService;
import com.zhiven.qa.service.RAGService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
@RequestMapping("/qa")
public class QAController {

    private final RAGService ragService;
    private final UserService userService;

    public QAController(RAGService ragService, UserService userService) {
        this.ragService = ragService;
        this.userService = userService;
    }

    @GetMapping
    public String qaPage() {
        return "qa";
    }

    @PostMapping("/ask")
    public String ask(@RequestParam String question,
                      Principal principal,
                      Model model) {
        User user = userService.findByUsername(principal.getName());
        RAGService.Answer answer = ragService.ask(question, user);
        model.addAttribute("question", question);
        model.addAttribute("answer", answer.content());
        model.addAttribute("sources", answer.sources());
        return "fragments/qa-result";
    }
}
