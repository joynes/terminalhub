#!/usr/bin/env python3
"""Summarize JaCoCo's complete app report, including zero-covered source files."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def summarize(path):
    report = ET.parse(path).getroot()
    counts = {c.attrib["type"]: {k: int(c.attrib[k]) for k in ("missed", "covered")}
              for c in report.findall("counter")}
    if "LINE" not in counts or sum(counts["LINE"].values()) == 0:
        raise ValueError("Coverage report has no executable lines")
    for counter in counts.values():
        total = counter["missed"] + counter["covered"]
        counter["percent"] = 100 * counter["covered"] / total if total else None
    sources = []
    for package in report.findall("package"):
        for source in package.findall("sourcefile"):
            line_count = next((c for c in source.findall("counter") if c.attrib["type"] == "LINE"), None)
            if line_count is None:
                continue
            missed, covered = (int(line_count.attrib[k]) for k in ("missed", "covered"))
            sources.append({"file": package.attrib["name"] + "/" + source.attrib["name"],
                            "missed": missed, "covered": covered,
                            "percent": 100 * covered / (missed + covered) if missed + covered else None})
    return {"counters": counts, "sources": sorted(sources, key=lambda s: (-s["missed"], s["file"]))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("--json", type=Path)
    parser.add_argument("--minimum", type=float)
    args = parser.parse_args()
    result = summarize(args.report)
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(result, indent=2) + "\n")
    print("## App coverage\n")
    for name in ("LINE", "BRANCH"):
        counter = result["counters"].get(name)
        if counter and counter["percent"] is not None:
            print(f"{name.title()}: **{counter['percent']:.2f}%** "
                  f"({counter['covered']}/{counter['covered'] + counter['missed']}).\n")
    print("| Source | Missed lines | Coverage |\n|---|---:|---:|")
    for source in result["sources"][:30]:
        print(f"| {source['file']} | {source['missed']} | {source['percent']:.2f}% |")
    if args.minimum is not None and result["counters"]["LINE"]["percent"] < args.minimum:
        raise SystemExit(f"Line coverage is below the required {args.minimum:g}%")


if __name__ == "__main__":
    main()
