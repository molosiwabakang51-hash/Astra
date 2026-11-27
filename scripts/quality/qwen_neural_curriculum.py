"""Public synthetic curriculum. No chat histories or independent assessment items."""
from dataclasses import dataclass, asdict
import hashlib
import json
import random
import re

@dataclass(frozen=True)
class Example:
    domain: str
    question: str
    rationale: str
    answer: str

    @property
    def completion(self):
        return "Reason: " + self.rationale + "\nAnswer: " + self.answer

DOMAINS = ("quantity", "deduction", "pattern", "correction")

def curriculum(train=True, count=32):
    if type(count) is not int or count < 4 or count > 256 or count % 4:
        raise ValueError("Use a multiple of four, 4..256")
    rng = random.Random(745231 if train else 983761)
    out = []
    for i in range(count//4):
        # Different value intervals, nouns, wording, and event order in transfer.
        a = rng.randrange(30, 90) if train else rng.randrange(200, 400)
        b,c = rng.sample(range(4, 25), 2)
        if train:
            q = f"A box holds {a} counters. Add {b}, then remove {c}. How many counters remain?"
            r = f"Add {b} to {a} to get {a+b}; subtract {c} to get {a+b-c}."
        else:
            q = f"A warehouse has {a} parcels. After shipping {c} parcels, it receives {b} more. Give the ending count."
            r = f"{a}-{c}+{b}={a-c+b}."
        out.append(Example("quantity",q,r,str(a+b-c)))
        # Enumerate two independent truth variables. Conditions and targets
        # differ across splits; the training examples never contain dev wording.
        kind = i % 3
        if train:
            p = ("All teal items are heavy. This item is teal.",
                 "All teal items are heavy. This item is not heavy.",
                 "All teal items are heavy. This item is heavy.")[kind]
            q = p + " Is this item teal? Answer YES, NO or UNKNOWN."
            ans = ("YES","NO","UNKNOWN")[kind]
            r = ("Teal is explicitly given.","If teal implies heavy, not heavy excludes teal.",
                 "Heavy does not establish teal.")[kind]
            # Unique object labels prevent accidental duplicate training prompts.
            q = f"Object T{i}: " + q
        else:
            p = ("Whenever an alarm is armed, its light is on. The light is off.",
                 "Whenever an alarm is armed, its light is on. The alarm is armed.",
                 "Whenever an alarm is armed, its light is on. The light is on.")[kind]
            q = f"Device V{i}: " + p + (" Is the light on?" if kind == 1 else " Is the alarm armed?") + " Answer YES, NO or UNKNOWN."
            ans = ("NO","YES","UNKNOWN")[kind]
            r = ("An armed alarm must have its light on.","Armed implies light on.",
                 "The light can be on even when not armed.")[kind]
        out.append(Example("deduction",q,r,ans))
        start = rng.randrange(2,20) if train else rng.randrange(40,80)
        delta = rng.randrange(2,10)
        if train:
            seq = [start+j*delta for j in range(4)]
            target = seq[-1]+delta
            q = f"Continue the constant-difference sequence: {', '.join(map(str,seq))}. Give the next number."
            r = f"Each step adds {delta}; {seq[-1]}+{delta}={target}."
        else:
            seq = [start-j*delta for j in range(4)]
            target = seq[-1]-delta
            q = f"A sequence decreases by the same amount at each step: {', '.join(map(str,seq))}. What comes next?"
            r = f"Each step subtracts {delta}; {seq[-1]}-{delta}={target}."
        out.append(Example("pattern",q,r,str(target)))
        # Corrections include missing/withdrawn information, not just arithmetic.
        old = rng.randrange(10,50) if train else rng.randrange(100,200)
        new = old+rng.randrange(2,17)
        if train:
            q = f"I said the reserve was {old} units. Correction: it is {new} units." if i%2==0 else f"I said the reserve was {old} units. I withdraw that figure; no replacement is established."
            q += " What is the current reserve? Give a number or UNKNOWN."
        else:
            q = f"The earlier stock count was {old}. A later confirmed count supersedes it: {new}." if i%2==0 else f"The previous stock count was {old}. That count is invalid and no new one has been confirmed."
            q += " State the current stock count, or UNKNOWN."
        r = "The latest confirmed value replaces the old one." if i%2==0 else "The earlier value is withdrawn and no replacement is known."
        out.append(Example("correction",q,r,str(new) if i%2==0 else "UNKNOWN"))
    if len({e.question for e in out}) != len(out):
        raise RuntimeError("Duplicate curriculum question")
    rng.shuffle(out)
    return tuple(out)

def controls():
    return (
        Example("control","What is the capital of France? Give its name.","Paris is the capital.","Paris"),
        Example("control","Name the planet on which humans live.","Humans live on Earth.","Earth"),
        Example("control","What is the opposite of hot? Give one word.","The antonym is cold.","cold"),
        Example("control","How many days are in a standard week?","A standard week has seven days.","7"),
    )

def digest(examples):
    return hashlib.sha256(json.dumps([asdict(e) for e in examples],sort_keys=True,
                                    ensure_ascii=True,separators=(",",":")).encode()).hexdigest()

def grade(text, expected):
    """Do not mine arbitrary numbers or accept a rationale as proof."""
    if not isinstance(text,str):
        return False
    match=re.fullmatch(r"Reason: [^\r\n]+\nAnswer: ([^\r\n]+)",text.strip())
    # Also allow an unadorned exact answer; report format separately.
    answer=match.group(1).strip() if match else text.strip()
    return answer.casefold()==expected.casefold()

def formatted(text):
    return bool(isinstance(text,str) and re.fullmatch(r"Reason: [^\r\n]+\nAnswer: [^\r\n]+",text.strip()))

def token_example(tokenizer, example, maximum=256):
    """Exact generation-prefix masking. Never train on system/user tokens."""
    messages=[{"role":"system","content":"Answer the question accurately. Give one short reasoning sentence, then a final answer on a new line as Answer: VALUE."},
              {"role":"user","content":example.question}]
    prefix=tokenizer.apply_chat_template(messages,tokenize=True,add_generation_prompt=True,enable_thinking=False)
    suffix=tokenizer.encode(example.completion,add_special_tokens=False)+[tokenizer.eos_token_id]
    if not prefix or not suffix or tokenizer.eos_token_id is None or len(prefix)+len(suffix)>maximum:
        raise ValueError("Example outside token budget; no truncation")
    return prefix+suffix,[-100]*len(prefix)+suffix,prefix
