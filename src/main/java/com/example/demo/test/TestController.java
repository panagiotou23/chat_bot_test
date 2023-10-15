package com.example.demo.test;

import com.example.demo.test.dto.SquadEvaluation;
import com.example.demo.test.dto.SquadAnswer;
import com.example.demo.test.dto.SquadParagraph;
import com.example.demo.test.dto.SquadQa;
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

import java.util.*;
import java.util.stream.Collectors;

@RestController
@Slf4j
public class TestController {

    private final ChatBotService chatBotService;
    private final ApiKeysConfig apiKeysConfig;

    public TestController(
            final ChatBotService chatBotService,
            final ApiKeysConfig apiKeysConfig
    ) {
        this.apiKeysConfig = apiKeysConfig;
        this.chatBotService = chatBotService;
        this.chatBotService.setEmbeddingModel(EmbeddingModel.OPEN_AI);
        this.chatBotService.setEmbeddingApiKey(apiKeysConfig.getOpenAiKey());

        this.chatBotService.setCompletionModel(CompletionModel.OPEN_AI);
        this.chatBotService.setCompletionApiKey(apiKeysConfig.getOpenAiKey());

        this.chatBotService.setVectorDatabaseModel(VectorDatabaseModel.PINECONE);
        this.chatBotService.setVectorDatabaseApiKey(apiKeysConfig.getPineconeKey());

        this.chatBotService.setChunkModel(ChunkModel.SENTENCES);
        this.chatBotService.setChunkSize(15);
        this.chatBotService.setChunkOverlap(2);

        this.chatBotService.setKnnAlgorithm(KnnAlgorithm.COSINE);
    }

    @PostMapping("/embedding")
    public void saveEmbedding(@RequestParam String input) {
        chatBotService.createEmbeddings("thesis", input);
    }

    @GetMapping("/open-ai/embedding")
    public List<Embedding> findKNearest(
            @RequestParam String indexName,
            @RequestParam String query,
            @RequestParam int k
    ) {
        return chatBotService.findKNearest(indexName, query, k);
    }


    @PostMapping("/open-ai/query-completion-model")
    public String response(@RequestBody QueryCompletionModelRequest request) {
        return chatBotService.query(request);
    }

    @PostMapping("/evaluate-squad")
    public String evaluate(
            @RequestBody SquadEvaluation squadEvaluation
    ) {

        final var activeIndexes = getStillActiveIndexes();
        if (!activeIndexes.isEmpty()) {
            try {
                activeIndexes.forEach(indexName -> {
                    log.info("Deleting Vectorized Database " + indexName);
                    chatBotService.deleteAllEmbeddings(indexName);
                });
            } catch (Exception e) {
                log.error("Did not delete the existing DB", e);
            }
            while (!getStillActiveIndexes().isEmpty()) {
                sleep(200);
            }
            log.info("Deleted Vectorized Database");
        }

        List<ParagraphScore> paragraphScores = new ArrayList<>();

        squadEvaluation.getData().forEach(data ->
                data.getParagraphs().forEach(paragraph ->
                        paragraphScores.addAll(evaluateParagraph(paragraph))
                ));


        final var result = new StringBuilder();

        result.append("Embedding Model,")
                .append("Completion Model,")
                .append("KNN Algorithm,")
                .append("Chunking Model,")
                .append("Wins,")
                .append("Misses")
                .append("\n");
        for (final var embeddingModel : EmbeddingModel.values()) {
            for (final var completionModel : CompletionModel.values()) {
                for (final var knnAlgorithm : KnnAlgorithm.values()) {
                    for (final var chunkModel : ChunkModel.values()) {
                        final var wins = paragraphScores.stream()
                                .filter(s ->
                                        s.getEmbeddingModel().equals(embeddingModel) &&
                                                s.getKnnAlgorithm().equals(knnAlgorithm) &&
                                                s.getChunkModel().equals(chunkModel) &&
                                                s.getCompletionModel().equals(completionModel)
                                ).mapToDouble(ParagraphScore::getWins)
                                .sum();
                        final var misses = paragraphScores.stream()
                                .filter(s ->
                                        s.getEmbeddingModel().equals(embeddingModel) &&
                                                s.getKnnAlgorithm().equals(knnAlgorithm) &&
                                                s.getChunkModel().equals(chunkModel) &&
                                                s.getCompletionModel().equals(completionModel)
                                ).mapToDouble(ParagraphScore::getMisses)
                                .sum();

                        result.append(embeddingModel)
                                .append(",")
                                .append(completionModel)
                                .append(",")
                                .append(knnAlgorithm)
                                .append(",")
                                .append(chunkModel)
                                .append(",")
                                .append(wins)
                                .append(",")
                                .append(misses)
                                .append("\n");
                    }
                }
            }
        }
        log.info(paragraphScores.toString());
        return result.toString();
    }

    private List<ParagraphScore> evaluateParagraph(SquadParagraph paragraph) {
        final var scores = new ArrayList<ParagraphScore>();

        for (final var embeddingModel : EmbeddingModel.values()) {
            for (final var knnAlgorithm : KnnAlgorithm.values()) {
                for (final var chunkModel : ChunkModel.values()) {

                    chatBotService.setEmbeddingModel(embeddingModel);
                    chatBotService.setKnnAlgorithm(knnAlgorithm);
                    chatBotService.setChunkModel(chunkModel);
                    if (embeddingModel.equals(EmbeddingModel.OPEN_AI)) {
                        chatBotService.setEmbeddingApiKey(apiKeysConfig.getOpenAiKey());
                    } else {
                        chatBotService.setEmbeddingApiKey(apiKeysConfig.getNplCloudKey());
                    }


                    final var indexName = "thesis-" +
                            embeddingModel.getStringValue() + "-" +
                            knnAlgorithm.getStringValue() + "-" +
                            chunkModel.getStringValue();
                    createIndex(indexName, paragraph.getContext());


                    for (final var completionModel : CompletionModel.values()) {

                        chatBotService.setCompletionModel(completionModel);
                        if (completionModel.equals(CompletionModel.OPEN_AI)) {
                            chatBotService.setCompletionApiKey(apiKeysConfig.getOpenAiKey());
                        } else {
                            chatBotService.setCompletionApiKey(apiKeysConfig.getNplCloudKey());
                        }

                        final var score = ParagraphScore.builder()
                                .wins(0)
                                .misses(0)
                                .paragraphContext(paragraph.getContext())
                                .embeddingModel(embeddingModel)
                                .completionModel(completionModel)
                                .knnAlgorithm(knnAlgorithm)
                                .chunkModel(chunkModel)
                                .build();
                        paragraph.getQas().forEach(qa ->
                                evaluateQa(indexName, qa, score)
                        );

                        scores.add(score);
                    }

                    deleteIndex(indexName);
                }
            }
        }
        return scores;
    }

    private void evaluateQa(String indexName, SquadQa qa, ParagraphScore score) {
        log.info("Question: " + qa.getQuestion());
        final var answer = chatBotService.query(
                QueryCompletionModelRequest.builder()
                        .indexName(indexName)
                        .k(5)
                        .query(qa.getQuestion())
                        .build()
        );
        log.info("Answer: " + answer);
        log.info("Given Answers: " +
                String.join(" || ", qa.getAnswers().stream()
                        .map(SquadAnswer::getText)
                        .collect(Collectors.toSet()))
        );
        if (qa.getIs_impossible()) {
            if (answer.equalsIgnoreCase("I don't know the answer.")) {
                score.countWin();
            } else {
                score.countMiss();
            }

        } else {
            if (
                    qa.getAnswers().stream().anyMatch(givenAnswer ->
                            answer.contains(givenAnswer.getText()) || givenAnswer.getText().contains(answer)
                    )
            ) {
                score.countWin();
            } else {
                score.countMiss();
            }
        }

        log.info("");
        log.info("Current wins: " + score.getWins());
        log.info("Current misses: " + score.getMisses());
        log.info("");
        log.info("");
    }

    private void createIndex(String indexName, String context) {
        log.info("Creating Vectorized Database " + indexName);
        chatBotService.createDatabase(indexName);
        while (!getStillActiveIndexes().contains(indexName)) {
            sleep(500);
        }
        log.info("Created Vectorized Database");

        while (!indexIsReadyForRequest(indexName)) {
            sleep(500);
        }
        log.info("Index ready for requests");
        sleep(500);

        boolean embeddingsStored = false;
        int retryAttempts = 0;
        while (!embeddingsStored || retryAttempts > 10) {
            try {
                chatBotService.createEmbeddings(indexName, context);
                embeddingsStored = true;
            } catch (Exception e) {
                log.warn("Could not save embeddings. Retry attempt: " + retryAttempts++);
                sleep(200);
            }
        }
        if (!embeddingsStored) {
            throw new RuntimeException("Could not save embeddings");
        }

        while (!indexHasParsedTheEmbeddings(indexName)) {
            sleep(500);
        }
        log.info("Index parsed the embeddings");
    }

    private void deleteIndex(String indexName) {
        log.info("Deleting Vectorized Database " + indexName);
        chatBotService.deleteAllEmbeddings(indexName);
        while (getStillActiveIndexes().contains(indexName)) {
            sleep(500);
        }
        log.info("Deleted Vectorized Database " + indexName);
    }

    private boolean indexHasParsedTheEmbeddings(String indexName) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Api-Key", chatBotService.getVectorDatabaseApiKey());
        headers.add("accept", "application/json");
        headers.add("Content-Type", "application/json");
        final var url = "https://" + indexName + "-63159e9.svc.eu-west4-gcp.pinecone.io/query";

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

    private boolean indexIsReadyForRequest(String indexName) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Api-Key", chatBotService.getVectorDatabaseApiKey());
            headers.add("accept", "application/json");
            headers.add("Content-Type", "application/json");
            final var url = "https://" + indexName + "-63159e9.svc.eu-west4-gcp.pinecone.io/describe_index_stats";

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

    private List<String> getStillActiveIndexes() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Api-Key", chatBotService.getVectorDatabaseApiKey());
        headers.add("accept", "application/json");
        headers.add("Content-Type", "application/json");
        final var url = "https://controller.eu-west4-gcp.pinecone.io/databases";

        final var restTemplate = new RestTemplate();

        final var response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String[].class
        ).getBody();

        if (response == null) {
            return Collections.emptyList();
        }
        return List.of(response);
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
