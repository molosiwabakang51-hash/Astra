"""Full answer reference rehearsal. Public, fixed, separate splits."""
from qwen_retention_anchor import TRAIN, DEV, SYSTEM, encode, digest

TRAIN_ANSWERS=(
    "Pacific Ocean","Celsius","children","sides","late","Na","Tokyo","Mercury",
    "milk","wrote","livre","4","days","pound sterling","distant objects","gas exchange",
    "fast","100","January","grams","H2O","Rome","angles","stingy","better","32",
    "honey","Sun","swam","thermometer","10","rojo",
)
DEV_ANSWERS=(
    "Jupiter","Ottawa","Fe","deep","mice","taught","sides","60","eau","barometer",
    "absorb water and minerals","careful","9","October","yen","carbon dioxide",
)

def references(tokenizer, training):
    prompts,answers=(TRAIN,TRAIN_ANSWERS) if training else (DEV,DEV_ANSWERS)
    if len(prompts)!=len(answers) or len(set(TRAIN+DEV))!=len(TRAIN)+len(DEV):
        raise ValueError("Invalid reference split")
    prefixes=encode(tokenizer,prompts)
    rows=[]
    eos=tokenizer.eos_token_id
    if eos is None:
        raise ValueError("Missing EOS")
    for prefix,answer in zip(prefixes,answers):
        end=tokenizer.encode(answer,add_special_tokens=False)
        if not end or eos in end or len(end)>32:
            raise ValueError("Invalid reference answer")
        end=end+[eos]
        rows.append((prefix+end,[-100]*len(prefix)+end,prefix))
    return rows
