package com.zhiven.agent.controller;

import com.zhiven.agent.entity.AgentTask;
import com.zhiven.agent.service.AgentService;
import com.zhiven.auth.entity.User;
import com.zhiven.auth.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
@RequestMapping("/agent")
public class AgentController {

    private final AgentService agentService;
    private final UserService userService;

    public AgentController(AgentService agentService, UserService userService) {
        this.agentService = agentService;
        this.userService = userService;
    }

    @GetMapping
    public String agentPage() {
        return "agent";
    }

    @PostMapping("/submit")
    public String submit(@RequestParam String task,
                         Principal principal,
                         Model model) {
        User user = userService.findByUsername(principal.getName());
        AgentTask result = agentService.execute(task, user);
        model.addAttribute("status", result.getStatus());
        model.addAttribute("result", result.getResult());
        return "fragments/agent-result";
    }
}
