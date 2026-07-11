package com.zhiven.document.service;

import com.zhiven.auth.entity.User;
import com.zhiven.document.entity.Document;
import com.zhiven.document.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class DocIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocIngestionService.class);
    private static final List<String> ALLOWED_EXTENSIONS = List.of("pdf", "docx", "xlsx", "pptx", "txt", "md");

    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;

    public DocIngestionService(VectorStore vectorStore,
                               DocumentRepository documentRepository) {
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
    }

    public Document ingest(MultipartFile file, User user) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new RuntimeException("不支持的文件格式: " + extension);
        }

        Document doc = new Document(user, originalFilename, extension, file.getSize());
        doc.setStatus("INDEXING");
        documentRepository.save(doc);

        try {
            DocumentReader reader = new TikaDocumentReader(
                    new InputStreamResource(file.getInputStream()));
            List<org.springframework.ai.document.Document> rawDocs = reader.get();

            TokenTextSplitter splitter = new TokenTextSplitter(800, 100, 10, 5000, true);
            List<org.springframework.ai.document.Document> chunks = splitter.apply(rawDocs);

            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).getMetadata().put("doc_id", doc.getId());
                chunks.get(i).getMetadata().put("chunk_index", i);
                chunks.get(i).getMetadata().put("filename", originalFilename);
                chunks.get(i).getMetadata().put("user_id", user.getId());
            }

            vectorStore.add(chunks);

            doc.setChunkCount(chunks.size());
            doc.setStatus("READY");
            log.info("文档上链完成: {} ({} 块)", originalFilename, chunks.size());
        } catch (Exception e) {
            doc.setStatus("FAILED");
            log.error("文档处理失败: {}", originalFilename, e);
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }

        return documentRepository.save(doc);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
