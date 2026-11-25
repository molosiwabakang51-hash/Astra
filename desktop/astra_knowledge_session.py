"""Session-local contextual knowledge conversations.

Explicit English follow-up patterns reuse the last successful draft and root
question. All evidence behind the prior chain must still be active. Arbitrary
new questions reset context; failed turns do not become conversational facts.
No chat or prior draft is written to disk.
"""
from copy import deepcopy
import re

from astra_evidence import clean, MAX_QUERY
from astra_orchestrator import MAX_CONTEXT_RECEIPTS

FOLLOWUP = re.compile(
    r"^(?:(?:please )?(?:explain (?:that|this|it|your (?:previous|last) answer)|"
    r"elaborate(?: on (?:that|this|it))?|expand on (?:that|this|it)|"
    r"(?:give|show|provide)(?: me)? (?:an|another|a practical) example(?: of (?:that|this|it))?|"
    r"simplify(?: (?:that|this|it|your (?:previous|last) answer))?|"
    r"summarize (?:that|this|it|your (?:previous|last) answer)|"
    r"why(?: is that)?|how does (?:that|this|it) work|"
    r"what (?:does (?:that|this|it) mean|are the implications))"
    r"(?:\s*[?.!]|\s*:\s*.+)?)$", re.I)
RESET = re.compile(r"(?:reset|clear) (?:the )?(?:conversation|knowledge context)[?.!]?", re.I)
AMBIGUOUS = re.compile(r"^(?:what about|what if|and then|compare (?:that|this|it)|"
                       r"how about|tell me more about (?:that|this|it))\b", re.I)


class KnowledgeSession:
    def __init__(self, orchestrator):
        self.orchestrator = orchestrator
        self.reset()

    def reset(self):
        self._context = None

    def execute(self, question, *, approve_models=False, review=False,
                use_vector=False, approve_embeddings=False, embed=None, model_sha=None):
        question = clean(question, MAX_QUERY, "question")
        if type(review) is not bool or type(use_vector) is not bool:
            raise ValueError("Invalid conversation settings.")
        if RESET.fullmatch(question):
            self.reset()
            return {"status": "knowledge_context_cleared"}
        if approve_models is not True:
            raise ValueError("Explicit model approval required for knowledge conversations.")
        followup = bool(FOLLOWUP.fullmatch(" ".join(question.split())))
        if not followup and AMBIGUOUS.search(question):
            return {"status": "clarification_required",
                    "clarification": "State the topic or an explicit supported reference to the previous answer."}
        if followup and self._context is None:
            return {"status": "clarification_required",
                    "clarification": "Ask a complete knowledge question before using a follow-up."}
        if not followup:
            self.reset()
        context = deepcopy(self._context) if followup else None
        request = {"kind": "answer", "question": question, "review": review,
                   "use_vector": use_vector}
        result = self.orchestrator.execute(
            request, approve_models=True, approve_embeddings=approve_embeddings,
            embed=embed, model_sha=model_sha, conversation=context)
        status = result["status"]
        if status in ("evidence_changed_retry", "context_budget_exceeded"):
            # The previous draft can no longer be safely reused.
            self.reset()
        if status == "unverified_evidence_grounded_draft":
            receipts = result["evidence_receipts"]
            if not 1 <= len(receipts) <= MAX_CONTEXT_RECEIPTS:
                self.reset()
                raise ValueError("Invalid returned evidence receipts.")
            self._context = {
                "root_question": context["root_question"] if context else question,
                "previous_question": question,
                "previous_draft": result["answer"],
                "receipts": dict(receipts),
            }
        # Do not expose internal receipt digests as public citations.
        public = {key: value for key, value in result.items() if key != "evidence_receipts"}
        return dict(public, contextual_followup=followup)
