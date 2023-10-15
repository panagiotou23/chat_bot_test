package com.example.demo.test.dto;

import lombok.Data;

import java.util.List;

@Data
public class SquadParagraph {
    List<SquadQa> qas;
    String context;
}
