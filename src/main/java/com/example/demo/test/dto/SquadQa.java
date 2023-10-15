package com.example.demo.test.dto;

import lombok.Data;

import java.util.List;

@Data
public class SquadQa {
    private String question;
    private String id;
    private List<SquadAnswer> answers;
    private Boolean is_impossible;
}
