#!/usr/bin/env python3
"""Malformed completion-event evidence must fail closed."""
import copy
import scale_shot_evidence as evidence


def run():
    doc=evidence.json.loads(evidence.TARGET.read_text());rows=evidence.scenarios();generated=evidence.source(rows)
    evidence.check(doc,rows,generated)
    mutations=[]
    for kind, mutate in (
        ("missing invocation",lambda c:c["events"].remove(next(e for e in c["events"] if e["kind"]=="SCALE_SHOT_COMPLETE"))),
        ("duplicate invocation",lambda c:c["events"].append(next(e for e in c["events"] if e["kind"]=="SCALE_SHOT_COMPLETE"))),
        ("missing final stage state",lambda c:c["events"].remove(next(e for e in c["events"] if e["kind"]=="SCALE_SHOT_FINAL"))),
        ("invocation before last hit",lambda c:c["events"].insert(0,c["events"].pop(next(i for i,e in enumerate(c["events"]) if e["kind"]=="SCALE_SHOT_COMPLETE")))),
        ("stage changes between strikes",lambda c:next(e for e in c["events"] if e["kind"]=="BETWEEN")["values"].__setitem__(-2,5)),
    ):
        changed=copy.deepcopy(doc)
        target=next(c for c in changed["cases"] if c["scenario"]["id"]=="scale-shot-2-low")
        mutate(target)
        try:evidence.check(changed,rows,generated)
        except (AssertionError,StopIteration):mutations.append(kind)
        else:raise AssertionError("accepted malformed evidence: "+kind)
    print(f"Scale Shot completion evidence mutations refused: {len(mutations)}")


if __name__=="__main__":run()
