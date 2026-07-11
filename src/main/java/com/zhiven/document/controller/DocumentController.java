package com.zhiven.document.controller;

import com.zhiven.auth.entity.User;
import com.zhiven.auth.service.UserService;
import com.zhiven.document.entity.Document;
import com.zhiven.document.repository.DocumentRepository;
import com.zhiven.document.service.DocIngestionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/docs")
public class DocumentController {

    private final DocIngestionService ingestionService;
    private final DocumentRepository documentRepository;
    private final UserService userService;

    public DocumentController(DocIngestionService ingestionService,
                              DocumentRepository documentRepository,
                              UserService userService) {
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model, Principal principal) {
        User user = userService.findByUsername(principal.getName());
        List<Document> docs = documentRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        model.addAttribute("documents", docs);
        return "docs";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         Principal principal,
                         RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "请选择文件");
            return "redirect:/docs";
        }
        try {
            User user = userService.findByUsername(principal.getName());
            ingestionService.ingest(file, user);
            redirectAttributes.addFlashAttribute("success", "上传成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/docs";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal,
                         RedirectAttributes redirectAttributes) {
        User user = userService.findByUsername(principal.getName());
        documentRepository.findById(id).ifPresentOrElse(doc -> {
            if (!doc.getUser().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "无权删除");
                return;
            }
            documentRepository.delete(doc);
            redirectAttributes.addFlashAttribute("success", "删除成功");
        }, () -> redirectAttributes.addFlashAttribute("error", "文档不存在"));
        return "redirect:/docs";
    }
}
