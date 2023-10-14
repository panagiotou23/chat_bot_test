package com.example.demo.test;

import lombok.Data;

import java.util.List;

@Data
public class TempData {
    private String title;
    private List<TempParagraph> paragraphs;
}
