package service.real_time_alert.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class RagChatController {

    private static final Logger LOG = LoggerFactory.getLogger(RagChatController.class);

    private final ChatClient chatClient;

    public RagChatController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        You are the HydroWatch Water Quality AI Assistant.
                        Answer the user's question accurately using ONLY the provided context.
                        If the answer is not contained in the context, state that the official documents do not contain that information.
                        """)
                // The advisor searches the vector store and adds the matching chunks
                // to the prompt automatically for every request.
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().topK(3).build())
                        .build())
                .build();
    }

    @PostMapping
    public ResponseEntity<String> askQuestion(@RequestBody Map<String, String> request) {
        String userQuestion = request.get("question");
        if (userQuestion == null || userQuestion.isBlank()) {
            return ResponseEntity.badRequest().body("Field 'question' is required.");
        }

        try {
            String answer = chatClient.prompt()
                    .user(userQuestion)
                    .call()
                    .content();
            return ResponseEntity.ok(answer);
        } catch (Exception e) {
            LOG.error("Chat call failed", e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    /**
     * Beans are kept in a separate nested configuration class to avoid a circular
     * dependency (the controller needs the VectorStore bean).
     * The vector store is in-memory: documents are embedded again on every startup.
     */
    @Configuration
    static class RagConfig {

        private static final Logger LOG = LoggerFactory.getLogger(RagConfig.class);

        @Bean
        public VectorStore vectorStore(EmbeddingModel embeddingModel) {
            return SimpleVectorStore.builder(embeddingModel).build();
        }

        @Bean
        public CommandLineRunner initializeVectorStore(VectorStore store) {
            return args -> {
                LOG.info("Initializing vector store from resources/docs/...");
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                Resource[] resources = resolver.getResources("classpath:docs/*.txt");

                List<Document> allDocuments = new ArrayList<>();
                TokenTextSplitter splitter = TokenTextSplitter.builder().build();

                for (Resource resource : resources) {
                    String filename = resource.getFilename();
                    if (filename == null) continue;

                    LOG.info("Processing text document: {}", filename);
                    TextReader textReader = new TextReader(resource);
                    allDocuments.addAll(splitter.apply(textReader.get()));
                }

                if (!allDocuments.isEmpty()) {
                    store.add(allDocuments);
                    LOG.info("Successfully vectorized and stored {} document chunks.", allDocuments.size());
                } else {
                    LOG.warn("No text documents found in classpath:docs/.");
                }
            };
        }
    }
}