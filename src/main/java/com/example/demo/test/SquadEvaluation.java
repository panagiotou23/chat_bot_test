package com.example.demo.test;

import lombok.Data;

import java.util.List;

@Data
public class SquadEvaluation {
    private String version;
    private List<TempData> data;
}
