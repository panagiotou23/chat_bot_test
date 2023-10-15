package com.example.demo.test;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class ApiKeysConfig {
    @Value("${open-ai-key}")
    private String openAiKey;

    @Value("${npl-cloud-key}")
    private String nplCloudKey;

    @Value("${pinecone-key}")
    private String pineconeKey;

}
