package com.talankin.anthropic.task11;

import com.anthropic.client.*;
import com.anthropic.client.okhttp.*;
import com.anthropic.core.*;
import com.anthropic.models.messages.*;
import io.github.cdimascio.dotenv.*;

import java.util.*;
import java.util.stream.*;

import static com.anthropic.models.messages.MessageParam.Role.USER;
import static com.anthropic.models.messages.StopReason.END_TURN;
import static com.anthropic.models.messages.StopReason.TOOL_USE;

public class Agent {
    private final Dotenv dotenv = Dotenv.load();
    private final String apiKey = dotenv.get("ANTHROPIC_API_KEY");
    private final AnthropicClient client = new AnthropicOkHttpClient.Builder().apiKey(apiKey).build();
    private final Calculator calculator = new Calculator();

    public String start() {
        // TODO: работу над Integer числами переделать на операции с плавающей точкой,
        //  т.к. агент не использует свой калькулятор вместо моего для подсчета процентов

        /*
            работает. здесь используются два тулза калькулятор + web поиск в агентском цикле
            и только проверка двух стоп-ризонов: end_turn & tool_use
            в калькуляторе заведомо отсутствует оператор '%',
            чтобы поймать ошибку внутри агентского цикла
        */

        final Tool webSearchTool = createWebSearchTool();
        final Tool calculatorTool = createCalculatorTool();
        final ToolChoiceAuto toolChoice = ToolChoiceAuto.builder()
                .disableParallelToolUse(true)
                .build();
        final String userPrompt = "на сколько процентов и в какую сторону изменилась численность населения Боржоми в 2025 году по сравнению с 1976?";

        System.out.println("start agent");

        int i = 0;
        int maxIterations = 20;
        String finalAnswer = null;
        List<MessageParam> messageHistory = new ArrayList<>();
        messageHistory.add(MessageParam.builder()
                .role(USER)
                .content(userPrompt)
                .build());

        while (i < maxIterations) {
            Message response = client.messages().create(MessageCreateParams.builder()
                    .model(Model.CLAUDE_SONNET_5)
                    .maxTokens(500)
                    .addTool(webSearchTool)
                    .addTool(calculatorTool)
                    .toolChoice(toolChoice)
                    .messages(messageHistory) // добавить всю историю одним вызовом
                    .build());

            messageHistory.add(response.toParam()); // добавить ответ ассистента в историю

            String stopReason = response.stopReason().map(StopReason::asString).orElse(null);
            System.out.println("stop_reason: " + stopReason);

            if (END_TURN.asString().equalsIgnoreCase(stopReason)
                    || !TOOL_USE.asString().equalsIgnoreCase(stopReason)) {
                final String text = parseAnswers(response);
                finalAnswer = text;
                System.out.println("response content: " + text);

                break;
            }

            ToolUseBlock toolUseBlock = response.content().stream()
                    .flatMap(block -> block.toolUse().stream())
                    .findFirst()
                    .orElse(null);

            String toolResult = "";
            boolean isError = false;
            if (toolUseBlock != null) {
                System.out.println("Claude called " + toolUseBlock.name() + " with " + toolUseBlock._input());
                if (toolUseBlock.name().equals("calculator")) {
                    try {
                        toolResult = calculate(toolUseBlock);
                    } catch (RuntimeException e) {
                        toolResult = e.getMessage();
                        isError = true;
                    }
                }

                if (toolUseBlock.name().equals("web_search")) {
                    toolResult = webSearch(toolUseBlock);
                } else {
                    toolResult = "unknown tool: " + toolUseBlock.name();
                    isError = true;
                    System.out.println(toolResult);
                }
                
                messageHistory.add(MessageParam.builder() // добавить ответ юзера(результат тула) в историю
                        .role(USER)
                        .contentOfBlockParams(List.of(ContentBlockParam.ofToolResult(
                                ToolResultBlockParam.builder()
                                        .toolUseId(toolUseBlock.id())
                                        .content(toolResult)
                                        .isError(isError)
                                        .build())))
                        .build());
            }

            i++;
            if (i == maxIterations) {
                System.out.println("max iterations reached, stop agent. seems like agent loop is infinite");
            }
        }

        return finalAnswer;
    }

    private String webSearch(ToolUseBlock toolUseBlock) {
        return "В 1976 году численность населения города Боржоми " +
                "вместе с подчиненными поселками района составляла около 18 тысяч человек " +
                "(по данным переписи 1979 года в самом городе Боржоми было 18 059 жителей). " +
                "К 2025 году население непосредственно самого города оценивается примерно в 14 тысяч человек " +
                "(по официальным данным Википедии на 2023 год зафиксировано 11 194 человека)";
    }

    private String calculate(ToolUseBlock toolUseBlock) {
        final Map<String, JsonValue> inputValues = ((JsonObject) toolUseBlock._input()).values();
        final Integer number1 = inputValues.get("number1").convert(Integer.class);
        final Integer number2 = inputValues.get("number2").convert(Integer.class);
        final String operation = inputValues.get("operation").convert(String.class);

        return calculator.calculate(number1, number2, operation);
    }

    private Tool createCalculatorTool() {
        return Tool.builder()
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
                                        "enum", List.of("+", "-", "*", "/", "%"),
                                        "description", "the operation to apply to number1 and number2: "
                                                + "'+' addition, '-' subtraction, '*' multiplication, "
                                                + "'/' integer division (truncated, 2/3 = 0), "
                                                + "'%' remainder of integer division (modulo), not percent"
                                )
                        )))
                        .required(List.of("number1", "number2", "operation"))
                        .build())
                .build();
    }

    private Tool createWebSearchTool() {
        return Tool.builder()
                .name("web_search")
                .description("Find information on the web.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(JsonValue.from(Map.of(
                                "query", Map.of(
                                        "type", "string",
                                        "description", "search query to find information on the web"
                                ))))
                        .required(List.of("query"))
                        .build())
                .build();
    }

    private String parseAnswers(Message response) {
        final List<String> answers = response.content().stream()
                .map(ContentBlock::text)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(TextBlock::text)
                .toList();

        return String.join(System.lineSeparator(), answers);
    }

    private String start4() {
        // работает. здесь только калькулятор в агентском цикле
        // и только проверка двух стоп-ризонов: end_turn & tool_use.
        // в калькуляторе заведомо отсутствует оператор '%',
        // чтобы поймать ошибку внутри агентского цикла
        final Tool calculatorTool = createCalculatorTool();
        final ToolChoiceAuto toolChoice = ToolChoiceAuto.builder()
                .disableParallelToolUse(true)
                .build();
        final String userPrompt = "какой будет остаток от деления 2 и 3?";

        System.out.println("start agent");


        int i = 0;
        int maxIterations = 10;
        List<MessageParam> messageHistory = new ArrayList<>();
        messageHistory.add(MessageParam.builder()
                .role(USER)
                .content(userPrompt)
                .build());

        while (i < maxIterations) {
            Message response = client.messages().create(MessageCreateParams.builder()
                    .model(Model.CLAUDE_SONNET_5)
                    .maxTokens(500)
                    .addTool(calculatorTool)
                    .toolChoice(toolChoice)
                    .messages(messageHistory) // добавить всю историю одним вызовом
                    .build());

            messageHistory.add(response.toParam()); // добавить ответ ассистента в историю

            String stopReason = response.stopReason().map(StopReason::asString).orElse(null);
            if (END_TURN.asString().equalsIgnoreCase(stopReason)
                    || !TOOL_USE.asString().equalsIgnoreCase(stopReason)) {
                final String text = parseAnswers(response);
                System.out.println("stop_reason: " + stopReason);
                System.out.println("response content: " + text);

                break;
            }

            ToolUseBlock toolUseBlock = response.content().stream()
                    .flatMap(block -> block.toolUse().stream())
                    .findFirst()
                    .orElse(null);

            System.out.println("stop_reason: " + stopReason);

            String calculatedResult;
            boolean isError = false;
            if (toolUseBlock != null) {
                System.out.println("Claude called " + toolUseBlock.name() + " with " + toolUseBlock._input());
                try {
                    calculatedResult = calculate(toolUseBlock);
                } catch (RuntimeException e) {
                    calculatedResult = e.getMessage();
                    isError = true;
                }
                messageHistory.add(MessageParam.builder() // добавить ответ юзера(результат тула) в историю
                        .role(USER)
                        .contentOfBlockParams(List.of(ContentBlockParam.ofToolResult(
                                ToolResultBlockParam.builder()
                                        .toolUseId(toolUseBlock.id())
                                        .content(calculatedResult)
                                        .isError(isError)
                                        .build())))
                        .build());
            }

            i++;
        }

        return "";
    }

    private String start3() {
        // не работает. не происходит накопления опыта использования тула. поэтому бесконечный цикл
        final Tool calculatorTool = createCalculatorTool();
        final ToolChoiceAuto toolChoice = ToolChoiceAuto.builder()
                .disableParallelToolUse(true)
                .build();
        final String userPrompt = "какой будет остаток от деления 2 и 3?";

        System.out.println("start agent");


        int i = 0;
        int maxIterations = 10;

        Message response = null;
        ToolUseBlock toolUseBlock = null;
        String stopReason = null;
        String calculatedResult = null;
        boolean isError = false;
        while (i < maxIterations) {

            if ("end_turn".equalsIgnoreCase(stopReason)) {
                System.out.println("stop_reason: " + stopReason + " response content: " + response.content());
                break;
            }


            if (i > 0) {
                response = client.messages().create(MessageCreateParams.builder()
                        .model(Model.CLAUDE_SONNET_5)
                        .maxTokens(500)
                        .addTool(calculatorTool)
                        .toolChoice(toolChoice)
                        .addUserMessage(userPrompt)
                        .addMessage(response)
                        .addUserMessageOfBlockParams(List.of(ContentBlockParam.ofToolResult(
                                ToolResultBlockParam.builder()
                                        .toolUseId(toolUseBlock.id())
                                        .content(calculatedResult)
                                        .isError(isError)
                                        .build())))
                        .build());
                isError = false;
            } else {
                response = client.messages().create(MessageCreateParams.builder()
                        .model(Model.CLAUDE_SONNET_5)
                        .maxTokens(1000)
                        .addTool(calculatorTool)
                        .toolChoice(toolChoice)
                        .addUserMessage(userPrompt)
                        .build());
            }

            stopReason = response.stopReason().map(StopReason::asString).orElse(null);

            toolUseBlock = response.content().stream()
                    .flatMap(block -> block.toolUse().stream())
                    .findFirst()
                    .orElse(null);

            System.out.println("stop_reason: " + stopReason);
            System.out.println("Claude called " + toolUseBlock.name() + " with " + toolUseBlock._input());

            try {
                calculatedResult = calculate(toolUseBlock);
            } catch (RuntimeException e) {
                calculatedResult = e.getMessage();
                isError = true;
            } finally {
                i++;
            }
        }

        return "";
    }

    private String start2() {
        // работает. сингл операция без агентского цикла
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
                                        "enum", List.of("+", "-", "*", "/", "%"),
                                        "description", "the operation to apply to number1 and number2: "
                                                + "'+' addition, '-' subtraction, '*' multiplication, "
                                                + "'/' integer division (truncated, 2/3 = 0), "
                                                + "'%' remainder of integer division (modulo), not percent"
                                )
                        )))
                        .required(List.of("number1", "number2", "operation"))
                        .build())
                .build();

        // Ask for at most one tool call per turn.
        ToolChoiceAuto toolChoice = ToolChoiceAuto.builder()
                .disableParallelToolUse(true)
                .build();

        String userPrompt = "какой будет остаток от деления 2 и 3?";

        // Claude replies with a tool_use block naming the tool and its arguments.
        Message responseToolUse = client.messages().create(MessageCreateParams.builder()
                .model(Model.CLAUDE_SONNET_5)
                .maxTokens(1000)
                .addTool(calculatorTool)
                .toolChoice(toolChoice)
                .addUserMessage(userPrompt)
                .build());

        ToolUseBlock toolUseBlock1 = responseToolUse.content().stream()
                .flatMap(block -> block.toolUse().stream())
                .findFirst()
                .orElseThrow();

        final String stopReason1 = responseToolUse.stopReason().map(StopReason::asString).orElse(null);
        System.out.println("  stop_reason1:" + stopReason1);
        System.out.println("Claude called " + toolUseBlock1.name() + " with " + toolUseBlock1._input());


        boolean isError = false;
        String calculated;
        try {
            calculated = calculate(toolUseBlock1);
        } catch (RuntimeException e) {
            calculated = e.getMessage();
            isError = true;
        }




        Message calculatedResponse = client.messages().create(MessageCreateParams.builder()
                        .model(Model.CLAUDE_SONNET_5)
                        .maxTokens(1000)
                        .addTool(calculatorTool)
                        .toolChoice(toolChoice)
                        .addUserMessage(userPrompt)
                        .addMessage(responseToolUse)
                        .addUserMessageOfBlockParams(List.of(ContentBlockParam.ofToolResult(
                                ToolResultBlockParam.builder()
                                        .toolUseId(toolUseBlock1.id())
                                        .content(calculated)
                                        .isError(isError)
                                        .build())))
                        .build());

        final String stopReason2 = calculatedResponse.stopReason().map(StopReason::asString).orElse(null);
        ToolUseBlock toolUseBlock2 = calculatedResponse.content().stream()
                .flatMap(block -> block.toolUse().stream())
                .findFirst()
                .orElseThrow();

        System.out.println("  stop_reason2:" + stopReason2);
        System.out.println("Claude called " + toolUseBlock2.name() + " with " + toolUseBlock2._input());

        // Claude uses the result to answer the original question.
        calculatedResponse.content().stream()
                .flatMap(block -> block.text().stream())
                .forEach(textBlock -> System.out.println(textBlock.text()));

        return "";
    }

    private String start1() {
        // работает. сингл операция без агентского цикла
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

        return "stop agent, result:" + " i:" + i + " stopReason:" + stopReason;
    }

}
