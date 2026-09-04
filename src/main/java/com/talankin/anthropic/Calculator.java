package com.talankin.anthropic;

import java.util.function.*;

public class Calculator {
    public int calculate(int a, int b, String operation) {
        return switch (operation) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> a / b;
            default -> throw new IllegalArgumentException("Unknown operation");
        };
    }
}
