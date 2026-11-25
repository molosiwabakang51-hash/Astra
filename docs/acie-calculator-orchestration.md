# ACIE model-led calculator orchestration

Explicit owner command: **Solve with calculator: [self-contained arithmetic question]**.

Qwen3 1.7B gets one isolated planning inference (128-token maximum), selects a single arithmetic expression or NONE, and cannot invoke any other operation. A strict independent BigDecimal parser admits only explicit numeric literals from the owner question, + - * / and parentheses, 120 expression characters, 16 operands, depth eight, bounded intermediate precision and scale. Non-terminating division, division by zero, invented literals, malformed and incomplete plans are rejected. No arbitrary evaluation, scripts, file reads, mutation, network or automatic tools.

Successful evaluation is fed into a second isolated inference for synthesis; the app attaches the exact computed expression and value with a clear interpretation caveat. The calculation is exact but the choice of expression is not independently verified. The first proposal is not streamed or stored as dialogue. Model-only evaluation precedes and bypasses this route. Ordinary requests are unchanged. On invalid proposals the user receives an honest no-calculation message; no fabricated verified result. Current implementation intentionally supports only explicit numeric arithmetic, not general planning, percentages with unstated constants, arbitrary expressions or autonomous tool execution. Two native calls cost additional latency; handset testing is required.

Four JVM and two source regression methods. ACIE-078/079/080/082 remain pending integrated Android and actual model-selection/synthesis evaluation. This is assisted capability only; it cannot increase an unassisted model score. The complete ACIE ledger is updated in tracker #308 to avoid conflicting with #417's active CI.

## Follow-up validation

Privacy surface explicitly counts all 18 sanitized exception handlers, including both planning and synthesis MODEL_CHAT handlers. Plain numbers before terminal punctuation are recognized; comma-separated digit groups and ambiguous precision/notation are rejected rather than partially interpreted. Numeric membership is not proof of semantic correctness. Five JVM and two source regression methods now cover the implementation.
