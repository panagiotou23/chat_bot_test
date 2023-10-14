package com.example.demo.test;

import lombok.Data;

import java.util.List;

@Data
public class TempQa {
    private String question;
    private String id;
    private List<TempAnswer> answers;
    private Boolean is_impossible;
}
