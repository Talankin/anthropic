package com.talankin.anthropic;

import com.anthropic.client.*;
import com.anthropic.client.okhttp.*;
import com.anthropic.core.*;
import com.anthropic.models.messages.*;
import io.github.cdimascio.dotenv.*;

import java.util.*;
import java.util.function.*;

public class Agent {
    private final Dotenv dotenv = Dotenv.load();
    private final String apiKey = dotenv.get("ANTHROPIC_API_KEY");
    private final AnthropicClient client = new AnthropicOkHttpClient.Builder().apiKey(apiKey).build();
    private final Calculator calculator = new Calculator();


    public String start() {
        System.out.println("start agent");
        Tool calculatorTool = Tool.builder()
                .name("calculator")
                .description("Calculate two numbers.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(JsonValue.from(Map.of(
                                "number1", Map.of(
                                        "type", "integer",
                                        "description", "the first number to add"
                                ),
                                "number2", Map.of(
                                        "type", "integer",
                                        "description", "the second number to add"
                                ),
                                "operation", Map.of(
                                        "type", "string",
                                        "description", "the operation to calculate between two numbers. только 4 операции возможны: сложение:+, вычитание:-, умножение:*, деление:/"
                                )
                        )))
                        .required(List.of("number1", "number2", "operation"))
                        .build())
                .build();

        // Ask for at most one tool call per turn.
        ToolChoiceAuto toolChoice = ToolChoiceAuto.builder()
                .disableParallelToolUse(true)
                .build();

        String userPrompt = "сколько будет если сложить 2 и 3?";

// Claude replies with a tool_use block naming the tool and its arguments.
        Message response = client.messages().create(MessageCreateParams.builder()
                .model(Model.CLAUDE_SONNET_5)
                .maxTokens(1000)
                .addTool(calculatorTool)
                .toolChoice(toolChoice)
                .addUserMessage(userPrompt)
                .build());
        ToolUseBlock toolUse = response.content().stream()
                .flatMap(block -> block.toolUse().stream())
                .findFirst()
                .orElseThrow();

        final String stopReason = response.stopReason().map(StopReason::asString).orElse(null);
        System.out.println("Claude called " + toolUse.name() + " with " + toolUse._input() + "stop_reson:" + stopReason);

//        final int calculate = calculator.calculate(1, 2, "+");


        return "";
    }

    public String start1() {
        System.out.println("start agent");

        final MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_SONNET_5)
                .maxTokens(100)
                .addTool(WebSearchTool20260209.builder().build())
                .addUserMessage("What's the latest on the Mars rover?")
                .build();
        Message response = client.messages().create(params);
        final String stopReason = response.stopReason().map(StopReason::asString).orElse(null);
        System.out.println(response.content());
        int i = 0;

//        while (i < 1000) {
//            i++;
//        }

        return "stop agent, result:" + " i:" + i + " stopReason:" + stopReason;
    }



}
