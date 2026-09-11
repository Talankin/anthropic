package com.talankin.anthropic.task11;

public class Calculator {
    public String calculate(Integer a, Integer b, String operation) throws RuntimeException {
        if (a == null || b == null || operation == null || operation.isBlank()) {
            return "Invalid input. Please provide two numbers and an operation.";
        }

        Integer result = switch (operation) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> a / b;
            default -> throw new RuntimeException("Unknown operation. Supported operations: +, -, *, /");
        };

        return result.toString();
    }
}
