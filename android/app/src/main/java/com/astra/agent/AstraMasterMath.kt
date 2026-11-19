package com.astra.agent

/** Original offline mathematics workshops; worked examples are verified identities. */
internal object AstraMasterMath {
    val units: List<AstraMasterclass.Unit> = listOf(
        AstraMasterclass.Unit(
            "Algebraic manipulation and equation checking", "Intermediate",
            "Algebra transforms quantities using rules that preserve equality. Collect like terms, distribute multiplication, and isolate variables through reversible operations. Dividing by an expression that could equal zero can discard or invent solutions; squaring both sides may introduce roots because it removes sign information. A reliable solution names the domain before transformation and substitutes every candidate into the original equation afterward. Factoring is useful but not mandatory; exact fractions often preserve more information than early decimal approximations. These principles also apply to financial formulas, unit conversions, budget models and risk equations.",
            "Solve 3(x−2)+4 = 2x+1: expand to 3x−2 = 2x+1, hence x=3. Check by substitution: left side 3(1)+4=7 and right side 2(3)+1=7. The equality holds.",
            "Solve 2(3x−1)=4x+10 and (x²−9)/(x−3)=6 separately. For the second, state why x=3 is excluded even though the numerator factors. Verify candidate roots in the original expressions, not just the simplified forms.",
            "Which operation can create an extraneous solution without a final check?",
            listOf("Adding a constant to both sides", "Squaring both sides", "Subtracting the same variable from both sides"), 'B',
            "Squaring destroys sign information; equal squares do not require equal starting expressions."
        ),
        AstraMasterclass.Unit(
            "Functions, domains and inverse operations", "Intermediate",
            "A function maps each permitted input to one output. The domain is the set of allowed inputs, and the range is the resulting set of outputs. Composing functions requires applying the inner function first, while an inverse reverses a mapping only when it is one-to-one over its domain. Real square roots require nonnegative radicands, denominators cannot be zero, and real logarithms require positive arguments. Models that ignore domains can return numerical answers that are economically or mathematically meaningless. Always write the units of inputs and outputs when they represent quantities.",
            "For f(x)=2x+3 and g(x)=x², f(g(2))=f(4)=11, but g(f(2))=g(7)=49. These differ because composition order matters. For h(x)=√(x−1), the real domain is x≥1.",
            "Find domains of 1/(x−4), ln(x+2), and √(5−x), then evaluate f(g(2)) and g(f(2)) for the functions above. Describe how a calculator should signal a domain error instead of fabricating a result.",
            "Which value is f(g(2)) if f(x)=2x+3 and g(x)=x²?",
            listOf("11", "49", "7"), 'A',
            "g(2)=4 and f(4)=11; composition applies the inner function first."
        ),
        AstraMasterclass.Unit(
            "Exponents, logarithms and growth rates", "Intermediate",
            "Exponential functions describe repeated proportional changes, while logarithms reverse exponentiation for positive inputs. Logarithm identities convert multiplication into addition and powers into products. A log return ln(Pt/Pprevious) requires positive values and differs from an arithmetic percentage return. When measuring growth across multiple intervals, geometric growth is not the simple average of annual percentages; the correct compound factor multiplies (1+r) across periods. Constant-rate models are convenient educational assumptions but rarely capture actual volatile asset prices. Rates and time units must be aligned.",
            "A value grows from P100 to P121 in two years. The constant annual growth rate solves 100(1+r)²=121, giving r=10%. Its two-year log change is ln(1.21)≈0.19062; average annual log change ≈0.09531.",
            "Solve 500(1.06)^t=750 for t using ln(1.5)/ln(1.06), then calculate the cumulative return from +20% followed by −10%. Contrast arithmetic average, geometric average and total compound factor, explaining why they differ.",
            "How do you solve A(1+r)^t=B for t when the inputs are valid?",
            listOf("(B−A)/r", "ln(B/A)/ln(1+r)", "ln(A+B)"), 'B',
            "Divide by A, take logarithms and solve t ln(1+r)=ln(B/A)."
        ),
        AstraMasterclass.Unit(
            "Quadratics, roots and validity", "Intermediate",
            "Quadratic equations ax²+bx+c=0 with a≠0 can be solved by factoring, completing the square or the quadratic formula. The discriminant b²−4ac determines whether there are two, one or no distinct real roots. Higher-degree or transcendental equations may require numerical approximation with stated tolerances. Mathematical solutions are not necessarily valid model outputs: a negative time, probability over one or amount outside a budget could violate application constraints. Check both the algebra and the domain. Avoid converting exact radicals into rounded decimals before verification.",
            "For x²−5x+6=0, factor (x−2)(x−3)=0: x=2 or x=3. For x²+4x+5=0, the discriminant is 16−20=−4, hence no real roots. The absence of real roots does not mean the polynomial itself is undefined.",
            "Solve 2x²−7x+3=0 by factoring and the quadratic formula. Construct a continuous function with a root between 1 and 2, demonstrate two steps of bisection and state when your approximation would stop.",
            "What discriminant indicates no real roots for a quadratic?",
            listOf("A negative discriminant", "A positive leading coefficient", "A negative constant"), 'A',
            "Negative b²−4ac requires the square root of a negative number in the quadratic formula."
        ),
        AstraMasterclass.Unit(
            "Linear systems and matrix reasoning", "Advanced",
            "A system of linear equations describes several unknowns constrained by multiple relationships. It can be expressed as Ax=b and solved by substitution, elimination or matrix decomposition. A square coefficient matrix that is singular lacks a unique inverse; this signals redundant equations, inconsistency or infinitely many solutions depending on the constants. Matrices appear in economics, computer graphics, statistics and asset-allocation constraints. Multiplication order matters. With real-world data, numerical conditioning and measurement error affect solution stability even when a system is technically invertible.",
            "Solve x+y=10 and 2x+y=14 by subtracting the first equation from the second: x=4, then y=6. The matrix [[1,1],[2,1]] is invertible. Replacing the second equation with 2x+2y=20 removes independent information.",
            "Create three equations for cash, bonds and equities from a total capital constraint and two exposure rules. Solve the system, check whether any holdings are negative, and interpret a negative amount under no-short-selling assumptions.",
            "What can prevent a square linear system from having a unique solution?",
            listOf("A singular coefficient matrix", "Any decimal coefficients", "An odd number of equations"), 'A',
            "Dependent rows can make the coefficient matrix singular and eliminate uniqueness."
        ),
        AstraMasterclass.Unit(
            "Limits and derivatives from first principles", "Intermediate",
            "A limit describes how a function behaves as an input approaches a point, including cases in which the function is not defined at that point. Continuity requires the function value and limiting value to agree. A derivative is the limit of a difference quotient and measures local sensitivity to small changes. Derivatives may not exist at sharp corners or discontinuities. In applied finance, duration and delta are related sensitivity ideas, but a local linear approximation can fail under large moves. Distinguish an exact formula from a small-change approximation.",
            "For f(x)=x², [f(3+h)−f(3)]/h equals [(3+h)²−9]/h =6+h for h≠0. Taking the limit h→0 gives f'(3)=6. A change from x=3 to 3.01 is estimated by 6×0.01=0.06, close to the exact change 0.0601.",
            "Derive f'(x) for f(x)=x²+2x using a difference quotient. Evaluate at x=4, compare the approximate change to the exact function change from 4 to 4.1 and identify the approximation error.",
            "What does f'(x) represent at a differentiable point?",
            listOf("The local rate of change of f with respect to x", "A guarantee of global increase", "The probability of x being positive"), 'A',
            "The derivative gives a local slope and first-order sensitivity."
        ),
        AstraMasterclass.Unit(
            "Differentiation, optimization and elasticity", "Advanced",
            "Product, quotient and chain rules extend derivatives to composite functions. A stationary point occurs when a derivative is zero, but it might be a maximum, minimum or flat point; boundary constraints also matter. Second derivatives describe curvature and can help classify stationary points. Elasticity measures proportional sensitivity, such as price elasticity of demand (dQ/dP)×P/Q. In economic problems, maximizing revenue is different from maximizing profit, and variable feasibility limits should be explicitly enforced. Analytical optima need not be implementable in whole units.",
            "Let revenue be R(q)=50q−q² for nonnegative quantity q. R'(q)=50−2q, so the stationary quantity is 25. R''(q)=−2 shows a local maximum for this concave quadratic. Revenue is 625 at that quantity before costs.",
            "Define π(q)=80q−2q²−100 and find its stationary quantity, second derivative and profit. Compare with q=0 and q=10, then introduce a capacity limit of 15 and identify the feasible optimum.",
            "A derivative equal to zero is sufficient proof of:",
            listOf("A global maximum", "Neither maximum nor minimum on its own", "A guaranteed profitable business"), 'B',
            "Stationary points need curvature, boundary and feasibility analysis."
        ),
        AstraMasterclass.Unit(
            "Integration and accumulated quantities", "Advanced",
            "A definite integral aggregates continuously varying quantities as signed area. The fundamental theorem of calculus links integration to antiderivatives when the relevant conditions hold. Integrating a rate over time yields total change, while a probability density must integrate to one over its support to represent a valid continuous distribution. Density values themselves may exceed one; probabilities require area over an interval. In economics, total cost can be reconstructed from marginal cost plus an initial fixed cost. Specify integration limits, units and whether a result can be negative.",
            "If marginal cost is MC(q)=10+2q, the variable cost of producing from q=0 to q=5 is ∫₀⁵(10+2q)dq = [10q+q²]₀⁵ = 75 currency units. An additional fixed cost would need to be added separately to obtain total cost.",
            "Integrate marginal cost MC(q)=4+3q from q=0 to q=10. Then calculate the probability that a uniformly distributed variable on [0,2] is between 0.5 and 1.5 using density 1/2. Explain the role of the limits.",
            "Integrating a marginal cost function over a production interval returns:",
            listOf("An incremental total cost over that interval", "The interest rate by definition", "The guaranteed market selling price"), 'A',
            "The integral accumulates cost per unit over the chosen quantity range."
        ),
        AstraMasterclass.Unit(
            "Sequences, series and annuities", "Advanced",
            "A sequence is an ordered set of terms; a series sums terms. Arithmetic sequences have constant differences and geometric sequences have constant ratios. Finite geometric sums are useful for regular payment streams and compound savings. Infinite geometric sums converge only when the common ratio has magnitude below one. Present values of ordinary annuities use a periodic rate and payment count matched to payment timing. An annuity due pays at the beginning of each period rather than the end. A correct model states whether payments are fixed, growing, indexed or uncertain.",
            "For three end-of-year payments of P100 discounted at 10%, PV =100/1.1 +100/1.1²+100/1.1³≈P248.69. The annuity-due version begins immediately, so PV is the ordinary-annuity result multiplied by 1.1 under the same timing convention.",
            "Compute the present value of five year-end P500 payments at 6%, both by summing discounts and by annuity formula. Repeat for payments at each year's start and explain the valuation difference.",
            "Which condition ensures an infinite geometric series with ratio r converges?",
            listOf("r is a positive integer", "The absolute value of r is less than one", "r is greater than one"), 'B',
            "Geometric terms must shrink sufficiently for the partial sums to converge."
        ),
        AstraMasterclass.Unit(
            "Counting, combinatorics and conditional probability", "Advanced",
            "Permutations count arrangements where order matters; combinations count selections where order does not. For n distinct objects and r selected without replacement, combinations are n!/[r!(n−r)!]. Probability depends on the sample space and the conditioning information. Bayes' theorem connects prior probabilities, likelihoods and updated beliefs; the base rate can dramatically change the meaning of a positive test. Independence and mutually exclusive outcomes are different concepts. A tiny positive probability per event can become important when repeated many times. State assumptions before applying formulas.",
            "Choose two committee members from five candidates without regard to order: 5C2=5×4/2=10. If an event has 1% base rate and a test detects 90% of true cases while falsely flagging 10% of noncases, a positive result does not imply 90% probability of the event.",
            "Calculate 6P3 and 6C3, then use Bayes' theorem with prior 5%, sensitivity 80% and false positive rate 20% to estimate P(event | positive). Compare your answer with the naive 80%.",
            "How many unordered pairs can be selected from five distinct objects?",
            listOf("10", "20", "5"), 'A',
            "Combination 5C2 counts unordered selections: 5×4/2=10."
        ),
        AstraMasterclass.Unit(
            "Descriptive statistics and estimation", "Advanced",
            "A data sample can be summarized by mean, median, variance, standard deviation and quantiles, but each statistic answers a different question. Outliers disproportionately affect the mean and variance. Sample variance typically divides by n−1 under common estimation assumptions, whereas population variance divides by n. A confidence interval depends on a sampling model and does not mean there is a fixed probability the true parameter changes after the interval is computed. Distribution shape, dependence and sample selection affect reliability. Distinguish descriptive facts from population inference.",
            "Sample [1,2,3,4,20] has mean 6 and median 3, reflecting how one high observation distorts the mean. Reporting only the mean hides the typical central value in this small skewed sample; neither mean nor median describes its full spread.",
            "Compute mean, median, range and sample variance for [2,4,4,5,15]. Remove the largest point and compare results. State why it would be unethical to discard the observation simply to improve a reported investment performance result.",
            "Which summary measure is usually less sensitive to an extreme observation?",
            listOf("The median", "The arithmetic mean", "The sample maximum"), 'A',
            "The median depends mainly on order rather than magnitudes of extreme values."
        ),
        AstraMasterclass.Unit(
            "Regression, correlation and diagnostics", "Advanced",
            "Correlation measures standardized linear association; it does not establish causation. Ordinary least squares fits model coefficients by minimizing squared residuals under a chosen specification. Predictions can fail if relationships change, omitted factors confound results, or training observations are not representative. A high R² does not prove economic usefulness or correctly measured uncertainty. Residual patterns, heteroskedasticity, autocorrelation, leverage points and multicollinearity all deserve checks. Data used to choose a specification should be separated from untouched evaluation data wherever possible.",
            "An illustrative sample has x values 1,2,3 and y values 2,4,6. The least-squares slope is 2 and intercept 0, but this perfect fit from three points alone does not prove x causes y or that future observations will follow y=2x.",
            "Fit a line to five invented x,y observations, compute residuals and R², then alter one data point substantially. Compare slope and model errors. Write a sentence explaining what evidence would be necessary to test a causal claim.",
            "Which conclusion follows reliably from a correlation of +0.9?",
            listOf("One variable directly causes the other", "There is a strong positive linear association in the observed data", "Their relationship will remain stable forever"), 'B',
            "Correlation captures association in a sample, not causality or future stability."
        ),
        AstraMasterclass.Unit(
            "Numerical methods, error and simulation", "Advanced",
            "Numerical algorithms approximate solutions when symbolic expressions are difficult. Bisection requires a continuous function with opposite signs at interval endpoints; Newton's method uses derivatives and may fail when slopes are tiny or starting points are poor. Computational errors arise from finite-precision arithmetic, rounding and stopping criteria. Monte Carlo simulation samples from explicitly chosen distributions; its output inherits model assumptions rather than discovering future truths. Reproducibility requires fixed seed handling, input disclosures, independent validation and convergence checks.",
            "To approximate √2, solve f(x)=x²−2=0 on [1,2]. Bisection tests midpoint 1.5, whose square exceeds two, then reduces the interval to [1,1.5]; the next midpoint 1.25 is below the root. This guarantees narrowing under continuity.",
            "Write pseudocode for bisection on f(x)=x³−x−2 over [1,2]. Stop when the interval width is below 0.001. Compare with Newton iteration starting at x=1.5 and explain why a simulation's output should include model assumptions and a reproducible random seed.",
            "Which method needs an interval whose endpoint function values have opposite signs?",
            listOf("Bisection method", "Simple linear regression", "Summation by parts"), 'A',
            "The sign change and continuity establish a bracket for a root."
        ),
        AstraMasterclass.Unit(
            "Differential equations and dynamical systems", "Advanced",
            "A differential equation specifies a relationship between a function and one or more derivatives. A basic growth model dX/dt=kX has solution X(t)=X(0)e^(kt), but constant proportional growth cannot describe every economic or biological process. Feedback, saturation and thresholds require different models, such as logistic growth. Initial conditions determine the particular solution of an ordinary differential equation, and numerical approximations can accumulate error. The mathematical expression should be evaluated against observable mechanisms rather than treated as a prophecy.",
            "For dN/dt=0.03N and N(0)=1000, solution N(t)=1000e^(0.03t). At t=10, N≈1349.9. If the quantity is a population in a finite habitat, the exponential model can eventually become unrealistic because available resources are limited.",
            "Solve dX/dt=−0.2X with X(0)=500 and compute X(5). Then propose an alternative model with a finite carrying capacity, explaining why its long-term trajectory differs from unlimited exponential growth.",
            "What is the solution to dX/dt=kX with initial X(0)=X0?",
            listOf("X0+k t", "X0 e^(kt)", "k/X0"), 'B',
            "A function proportional to its own derivative is exponential with the specified initial condition."
        )
    )
}
