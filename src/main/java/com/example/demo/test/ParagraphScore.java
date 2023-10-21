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

    private int chunkSize;

    private int chunkOverlap;

    private int k;

    public void countWin() {
        this.wins++;
    }

    public void countMiss() {
        this.misses++;
    }

    public boolean hasEqualValues(
            EmbeddingModel embeddingModel,
            CompletionModel completionModel,
            KnnAlgorithm knnAlgorithm,
            ChunkModel chunkModel,
            Integer chunkSize,
            Integer chunkOverlap,
            Integer k
    ) {
        return this.embeddingModel.equals(embeddingModel) &&
                this.knnAlgorithm.equals(knnAlgorithm) &&
                this.chunkModel.equals(chunkModel) &&
                this.completionModel.equals(completionModel) &&
                this.chunkSize == chunkSize &&
                this.chunkOverlap == chunkOverlap &&
                this.k == k;
    }
}
