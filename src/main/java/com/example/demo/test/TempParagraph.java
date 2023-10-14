package com.example.demo.test;

import lombok.Data;

import java.util.List;

@Data
public class TempParagraph {
    List<TempQa> qas;
    String context;
}
