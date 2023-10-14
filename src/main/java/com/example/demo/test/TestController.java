package com.example.demo.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.thesis.qnabot.api.embedding.adapter.out.dto.pinecone.PineconeFindKNearestRequestDto;
import com.thesis.qnabot.api.embedding.application.ChatBotService;
import com.thesis.qnabot.api.embedding.domain.Embedding;
import com.thesis.qnabot.api.embedding.domain.enums.*;
import com.thesis.qnabot.api.embedding.domain.request.QueryCompletionModelRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@RestController
@Slf4j
public class TestController {

    private final ChatBotService chatBotService;

    public TestController(final ChatBotService chatBotService) {
        this.chatBotService = chatBotService;
        this.chatBotService.setEmbeddingModel(EmbeddingModel.OPEN_AI);
        this.chatBotService.setEmbeddingApiKey("api-key");

        this.chatBotService.setCompletionModel(CompletionModel.OPEN_AI);
        this.chatBotService.setCompletionApiKey("api-key");

        this.chatBotService.setVectorDatabaseModel(VectorDatabaseModel.PINECONE);
        this.chatBotService.setVectorDatabaseApiKey("api-key");

        this.chatBotService.setChunkModel(ChunkModel.SENTENCES);
        this.chatBotService.setChunkSize(7);
        this.chatBotService.setChunkOverlap(5);

        this.chatBotService.setKnnAlgorithm(KnnAlgorithm.COSINE);
    }

    @PostMapping("/open-ai/embedding")
    public void getEmbedding(
            @RequestParam("file") MultipartFile file
    ) {
        chatBotService.createEmbeddings(file);
    }

    @GetMapping("/open-ai/embedding")
    public List<Embedding> findKNearest(
            @RequestParam String query,
            @RequestParam int k
    ) {
        return chatBotService.findKNearest(query, k);
    }


    @PostMapping("/open-ai/query-completion-model")
    public String response(@RequestBody QueryCompletionModelRequest request) {
        return chatBotService.query(request);
    }

    @PostMapping("/evaluate-squad")
    public String evaluate(
            @RequestBody SquadEvaluation squadEvaluation
    ) {

        if (databaseExists()) {
            try {
                log.info("Deleting Vectorized Database");
                chatBotService.deleteAllEmbeddings();
            } catch (Exception e) {
                log.error("Did not delete the existing DB", e);
            }
            while (databaseExists()) {
//                log.info("Database still here");
                sleep(500);
            }
            log.info("Deleted Vectorized Database");
        }

        AtomicInteger openAiWins = new AtomicInteger();
        AtomicInteger openAiMisses = new AtomicInteger();

        squadEvaluation.getData().forEach(data ->
                data.getParagraphs().forEach(paragraph -> {

                    log.info("Creating Vectorized Database");
                    chatBotService.createDatabase();
                    while (!databaseExists()) {
//                        log.info("Database not ready");
                        sleep(500);
                    }
                    log.info("Created Vectorized Database");

                    while (!indexIsReadyForRequest()) {
//                        log.info("Index is not ready for requests");
                        sleep(500);
                    }
                    log.info("Index ready for requests");
                    chatBotService.createEmbeddings(paragraph.getContext());

                    while (!indexIsParsedTheEmbeddings()) {
//                        log.info("Index hasn't parsed the embeddings yet");
                        sleep(500);
                    }
                    log.info("Index parsed the embeddings");
                    sleep(1000);
                    paragraph.getQas().forEach(qa -> {
                        log.info("Question: " + qa.getQuestion());
                        final var answer = chatBotService.query(
                                QueryCompletionModelRequest.builder()
                                        .k(5)
                                        .query(qa.getQuestion())
                                        .build()
                        );
                        log.info("Answer: " + answer);
                        log.info("Given Answers: " +
                                String.join(" || ", qa.getAnswers().stream()
                                        .map(TempAnswer::getText)
                                        .collect(Collectors.toSet()))
                        );
                        if (qa.getIs_impossible()) {
                            if (answer.equalsIgnoreCase("I don't know the answer.")) {
                                openAiWins.addAndGet(1);
                            } else {
                                openAiMisses.addAndGet(1);
                            }

                        } else {
                            if (
                                    qa.getAnswers().stream().anyMatch(givenAnswer ->
                                            answer.contains(givenAnswer.getText()) || givenAnswer.getText().contains(answer)
                                    )
                            ) {
                                openAiWins.addAndGet(1);
                            } else {
                                openAiMisses.addAndGet(1);
                            }
                        }

                        log.info("");
                        log.info("Current wins: " + openAiWins);
                        log.info("Current misses: " + openAiMisses);
                        log.info("");
                        log.info("");

                        sleep(2000);

                    });

                    log.info("Deleting Vectorized Database");
                    chatBotService.deleteAllEmbeddings();
                    while (databaseExists()) {
//                        log.info("Database still here");
                        sleep(500);
                    }
                    log.info("Deleted Vectorized Database");

                }));

        return "OpenAi Wins: " + openAiWins + "\nOpenAi Misses: " + openAiMisses;
    }

    private boolean indexIsParsedTheEmbeddings() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Api-Key", chatBotService.getVectorDatabaseApiKey());
        headers.add("accept", "application/json");
        headers.add("Content-Type", "application/json");
        final var url = "https://thesis-83dacea.svc.gcp-starter.pinecone.io/query";

        final var body = PineconeFindKNearestRequestDto.builder()
                .vector(new ArrayList<>(
                        Collections.nCopies(chatBotService.getEmbeddingModel().getEmbeddingSize(), 0.)
                ))
                .topK(1)
                .build();

        final var restTemplate = new RestTemplate();

        final var response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class
        ).getBody();
        return !response.get("matches").isEmpty();

    }

    private boolean indexIsReadyForRequest() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Api-Key", chatBotService.getVectorDatabaseApiKey());
            headers.add("accept", "application/json");
            headers.add("Content-Type", "application/json");
            final var url = "https://thesis-83dacea.svc.gcp-starter.pinecone.io/describe_index_stats";

            final var restTemplate = new RestTemplate();

            final var response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    JsonNode.class
            ).getBody();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean databaseExists() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Api-Key", chatBotService.getVectorDatabaseApiKey());
        headers.add("accept", "application/json");
        headers.add("Content-Type", "application/json");
        final var url = "https://controller.gcp-starter.pinecone.io/databases";

        final var restTemplate = new RestTemplate();

        final var response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode[].class
        ).getBody();
        return response.length != 0;
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
