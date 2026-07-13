package com.llm.insight.common;

public class InsightConstants {

    private InsightConstants() {}

    // Step 类型常量（对应 IAgentRunner.STEP_XXX）
    public static final String STEP_RAG = "rag";
    public static final String STEP_TEMPLATE = "tpl";
    public static final String STEP_PARSER = "psr";
    public static final String STEP_VERIFIER = "vrf";
    public static final String STEP_ACTION = "act";
    public static final String STEP_END = "99999";

    public static String stepToLabel(String step) {
        return switch (step) {
            case STEP_RAG -> "RAG";
            case STEP_TEMPLATE -> "Template";
            case STEP_PARSER -> "Parser";
            case STEP_VERIFIER -> "Verifier";
            case STEP_ACTION -> "Action";
            case STEP_END -> "End";
            default -> step;
        };
    }

    public static int stepToNumber(String step) {
        return switch (step) {
            case STEP_RAG -> 1;
            case STEP_TEMPLATE -> 2;
            case STEP_PARSER -> 3;
            case STEP_VERIFIER -> 4;
            case STEP_ACTION -> 5;
            default -> 0;
        };
    }
}
