"""Adaptive local inference budget, shared across one request.

Only the trusted caller chooses modes and approves model use. Model output never
extends a budget. Timing is an estimate, not a quality score or OS deadline.
Optional specialist inference requires enough remaining time for its estimated
cost. Every native call receives a reduced timeout and retains RAM admission.
"""
from dataclasses import dataclass
import math
import time

MODES = {"fast": 1, "balanced": 2, "deep": 4}
ROLES = ("primary", "critic")
MAX_SAMPLES = 12
DEFAULT_ESTIMATE = 30


class BudgetExhausted(RuntimeError):
    """No additional inference is permitted within this request."""


@dataclass(frozen=True)
class Policy:
    mode: str = "balanced"
    seconds: int = 180

    def __post_init__(self):
        if not isinstance(self.mode, str) or self.mode not in MODES:
            raise ValueError("Unsupported inference mode.")
        if type(self.seconds) is not int or not 1 <= self.seconds <= 900:
            raise ValueError("Request budget must be 1–900 seconds.")

    @property
    def max_calls(self):
        return MODES[self.mode]


class LatencyProfile:
    """Bounded session-local duration samples; never stores prompts or answers.

    Use one instance per exact runtime/configuration. Do not reuse after model
    changes. Failed calls contribute elapsed time, not a success/quality score.
    """

    def __init__(self):
        self._samples = {role: [] for role in ROLES}

    def record(self, role, elapsed):
        if role not in ROLES or type(elapsed) not in (int, float):
            raise ValueError("Invalid latency observation.")
        if not math.isfinite(elapsed) or not 0 <= elapsed <= 3600:
            raise ValueError("Latency outside supported measurement range.")
        values = self._samples[role]
        values.append(max(1.0, float(elapsed)))
        del values[:-MAX_SAMPLES]

    def estimate(self, role):
        if role not in ROLES:
            raise ValueError("Invalid model role.")
        values = sorted(self._samples[role])
        if not values:
            return DEFAULT_ESTIMATE
        # Conservative nearest-rank 90th percentile with a 25% margin.
        observed = values[math.ceil(len(values) * 0.9) - 1]
        return max(5, math.ceil(observed * 1.25))

    def summary(self):
        return {role: {"samples": len(self._samples[role]),
                       "estimated_seconds": self.estimate(role)}
                for role in ROLES}


class BudgetedRunner:
    """Wrap a timeout-aware local runner for one sequential request.

    A first primary call may proceed with a shorter timeout than its estimate.
    Subsequent calls are optional and admitted only when estimated affordable.
    This is not a hard wall-clock deadline: startup, cleanup and system
    scheduling can overrun it. Late responses are not accepted.
    """

    def __init__(self, runner, policy, profile=None, *, clock=time.monotonic,
                 approved=False):
        if approved is not True:
            raise ValueError("Explicit local model approval required.")
        if not callable(runner) or not isinstance(policy, Policy):
            raise ValueError("Invalid budgeted runner configuration.")
        if type(getattr(runner, "timeout", None)) is not int or not 1 <= runner.timeout <= 300:
            raise ValueError("Runner must expose a bounded configured timeout.")
        self.runner = runner
        self.policy = policy
        self.profile = profile if profile is not None else LatencyProfile()
        if not isinstance(self.profile, LatencyProfile):
            raise ValueError("Invalid latency profile.")
        self.clock = clock
        self.started = self._now()
        self.calls = 0
        self.events = []

    def _now(self):
        value = self.clock()
        if type(value) not in (int, float) or not math.isfinite(value):
            raise ValueError("Invalid monotonic clock.")
        return value

    def remaining(self):
        elapsed = self._now() - self.started
        if elapsed < 0:
            raise RuntimeError("Monotonic clock moved backwards.")
        return max(0.0, self.policy.seconds - elapsed)

    def __call__(self, role, prompt):
        if role not in ROLES:
            raise ValueError("Unsupported budgeted model role.")
        remaining = math.floor(self.remaining())
        estimate = self.profile.estimate(role)
        if self.calls >= self.policy.max_calls or remaining < 1 or (
            (self.calls > 0 or role == "critic") and min(remaining, self.runner.timeout) < estimate
        ):
            if len(self.events) < 2 * max(MODES.values()):
                self.events.append({"role": role, "status": "budget_skipped"})
            raise BudgetExhausted("Optional inference exceeds request budget.")
        timeout = min(self.runner.timeout, remaining)
        self.calls += 1
        started = self._now()
        status = "failed"
        try:
            value = self.runner(role, prompt, timeout_seconds=timeout)
            if self.remaining() <= 0:
                status = "deadline_exceeded"
                raise BudgetExhausted("Late model response discarded.")
            status = "returned"
            return value
        finally:
            elapsed = self._now() - started
            if 0 <= elapsed <= 3600:
                self.profile.record(role, elapsed)
            self.events.append({"role": role, "status": status,
                                "timeout_seconds": timeout,
                                "elapsed_seconds": round(max(0, elapsed), 3)})

    def report(self):
        return {"mode": self.policy.mode, "budget_seconds": self.policy.seconds,
                "max_model_calls": self.policy.max_calls, "model_calls": self.calls,
                "remaining_seconds": round(self.remaining(), 3),
                "events": [dict(event) for event in self.events],
                "latency_profile": self.profile.summary(),
                "scope": "Session-local timing; no factual quality or intelligence claim."}
