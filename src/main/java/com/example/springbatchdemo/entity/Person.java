package com.example.springbatchdemo.entity;

import lombok.Data;

@Data
public class Person {

    private Long id;
    private String name;
    private int age;
    private int gender; // 1: 男性, 2: 女性

    public String getGenderLabel() {
        return gender == 1 ? "男性" : "女性";
    }
}