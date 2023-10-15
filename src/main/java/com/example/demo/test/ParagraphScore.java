package com.example.demo.test;

import com.thesis.qnabot.api.embedding.domain.enums.ChunkModel;
import com.thesis.qnabot.api.embedding.domain.enums.CompletionModel;
import com.thesis.qnabot.api.embedding.domain.enums.EmbeddingModel;
import com.thesis.qnabot.api.embedding.domain.enums.KnnAlgorithm;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParagraphScore {

    private Integer wins;

    private Integer misses;

    private String paragraphContext;

    private EmbeddingModel embeddingModel;

    private CompletionModel completionModel;

    private KnnAlgorithm knnAlgorithm;

    private ChunkModel chunkModel;

    public void countWin() {
        this.wins++;
    }

    public void countMiss() {
        this.misses++;
    }
}
