#!/usr/bin/env python3
"""M8-B5 static architecture guardrails; standard library only."""
from __future__ import annotations

import csv
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict, deque
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
ANNOTATION = re.compile(r"@(?:org\.junit\.jupiter\.api\.)?(Test|ParameterizedTest|RepeatedTest|TestFactory)\b")
FRAMEWORK_NEUTRAL = {
    "supply-chain-agent-domain", "supply-chain-agent-memory", "supply-chain-agent-core",
    "supply-chain-agent-simulator", "supply-chain-agent-actions",
    "supply-chain-agent-mechanisms", "supply-chain-agent-planning",
}
EXPECTED_INVENTORY = {
    "supply-chain-agent-domain": (53, 4, 14, 4, 0),
    "supply-chain-agent-memory": (52, 7, 44, 6, 1),
    "supply-chain-agent-core": (4, 0, 0, 0, 0),
    "supply-chain-agent-simulator": (5, 2, 20, 1, 1),
    "supply-chain-agent-actions": (58, 5, 28, 4, 1),
    "supply-chain-agent-mechanisms": (166, 12, 125, 12, 0),
    "supply-chain-agent-planning": (54, 10, 43, 9, 1),
    "supply-chain-agent-runtime": (38, 11, 87, 11, 0),
    "supply-chain-agent-infrastructure-postgres": (43, 15, 57, 13, 2),
    "supply-chain-agent-app": (2, 0, 0, 0, 0),
    "supply-chain-agent-evaluation": (94, 18, 62, 18, 0),
}
failures: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)

def parse_pom(path: Path):
    try:
        return ET.parse(path).getroot()
    except ET.ParseError as exc:
        failures.append(f"Invalid POM XML {path.relative_to(ROOT)}: {exc}")
        return None

def artifact_dependencies(module: str) -> set[tuple[str, str]]:
    pom = parse_pom(ROOT / module / "pom.xml")
    if pom is None:
        return set()
    deps = pom.find("m:dependencies", NS)
    result = set()
    if deps is not None:
        for dep in deps.findall("m:dependency", NS):
            group = dep.findtext("m:groupId", default="", namespaces=NS)
            artifact = dep.findtext("m:artifactId", default="", namespaces=NS)
            result.add((group, artifact))
    return result

root_pom = parse_pom(ROOT / "pom.xml")
modules = []
if root_pom is not None:
    modules_node = root_pom.find("m:modules", NS)
    modules = [x.text.strip() for x in modules_node.findall("m:module", NS) if x.text]
require(len(modules) == 11, f"Expected 11 reactor modules, found {len(modules)}")
require(set(modules) == set(EXPECTED_INVENTORY), "Root reactor module list differs from the registered 11-module architecture")

# Verify internal dependency direction is acyclic.
graph: dict[str, set[str]] = {m: set() for m in modules}
for module in modules:
    deps = artifact_dependencies(module)
    for group, artifact in deps:
        if group == "dev.supplychain.agent" and artifact in graph:
            graph[module].add(artifact)
indegree = {m: 0 for m in modules}
for source, targets in graph.items():
    for target in targets:
        indegree[target] += 1
queue = deque(sorted(m for m, degree in indegree.items() if degree == 0))
visited = []
while queue:
    node = queue.popleft()
    visited.append(node)
    for target in sorted(graph[node]):
        indegree[target] -= 1
        if indegree[target] == 0:
            queue.append(target)
require(len(visited) == len(modules), "Internal Maven module dependency graph contains a cycle")

# Every source module must declare direct framework artifacts it imports.
for module in modules:
    deps = artifact_dependencies(module)
    source_roots = [ROOT / module / "src/main/java", ROOT / module / "src/test/java"]
    source_files = [p for source_root in source_roots if source_root.exists() for p in source_root.rglob("*.java")]
    text = "\n".join(p.read_text(encoding="utf-8", errors="replace") for p in source_files)
    imports_langgraph = bool(re.search(r"\borg\.bsc\.langgraph4j\.", text))
    imports_langchain = bool(re.search(r"\bdev\.langchain4j\.", text))
    uses_pg_saver = "PostgresSaver" in text or "langgraph4j.checkpoint.postgres" in text
    if imports_langgraph:
        require(("org.bsc.langgraph4j", "langgraph4j-core") in deps,
                f"{module} imports LangGraph4j but does not declare direct langgraph4j-core dependency")
    if imports_langchain:
        require(("dev.langchain4j", "langchain4j") in deps,
                f"{module} imports LangChain4j but does not declare direct langchain4j dependency")
    if uses_pg_saver:
        require(("org.bsc.langgraph4j", "langgraph4j-postgres-saver") in deps,
                f"{module} uses PostgresSaver but does not declare direct langgraph4j-postgres-saver dependency")
    if module in FRAMEWORK_NEUTRAL:
        require(not imports_langgraph and not imports_langchain,
                f"Framework-neutral module {module} imports a framework API")

# The tracked test inventory must stay synchronized with the source tree.
inventory_path = ROOT / "docs/phase8/M8-TEST-INVENTORY.csv"
rows = {}
with inventory_path.open(newline="", encoding="utf-8") as handle:
    for row in csv.DictReader(handle):
        rows[row["module"]] = row
require(set(rows) == set(EXPECTED_INVENTORY), "Test inventory modules differ from the root Maven reactor")
for module, expected in EXPECTED_INVENTORY.items():
    if module not in modules or module not in rows:
        continue
    base = ROOT / module
    prod = list((base / "src/main/java").rglob("*.java")) if (base / "src/main/java").exists() else []
    tests = list((base / "src/test/java").rglob("*.java")) if (base / "src/test/java").exists() else []
    annotated = [p for p in tests if ANNOTATION.search(p.read_text(encoding="utf-8", errors="replace"))]
    annotation_count = sum(len(ANNOTATION.findall(p.read_text(encoding="utf-8", errors="replace"))) for p in tests)
    actual = (len(prod), len(tests), annotation_count, len(annotated), len(tests) - len(annotated))
    require(actual == expected, f"{module} source inventory mismatch: expected {expected}, found {actual}")
    row = rows[module]
    fields = ("production_java_files", "test_java_sources", "annotated_test_methods_or_factories",
              "test_classes_with_test_annotations", "support_fixture_or_helper_sources")
    recorded = tuple(int(row[field]) for field in fields)
    require(actual == recorded, f"{module} inventory CSV mismatch: computed {actual}, recorded {recorded}")

# Both workflow copies are kept in sync; the active GitHub file owns the job.
active = ROOT / ".github/workflows/ci.yml"
mirror = ROOT / "workflows/ci.yml"
require(active.exists() and mirror.exists(), "Both active and mirror CI workflow files must exist")
if active.exists() and mirror.exists():
    require(active.read_bytes() == mirror.read_bytes(), "workflows/ci.yml must be byte-identical to .github/workflows/ci.yml")
    workflow = active.read_text(encoding="utf-8")
    require("verify-m8-b5-architecture.py" in workflow, "Active CI workflow does not run the M8-B5 architecture guardrail")
    require("verify-m8-mechanism-experimental-evidence.py" in workflow,
            "Active CI workflow does not run the M8 mechanism-training and independent-evidence guardrail")
    require("-Dphase0.postgres=true clean install" in workflow, "Active CI workflow must clean-install the fully tested reactor before generating B2 outputs")
    require("M8ComparisonCli" in workflow and "target/m8-b2-results/**" in workflow,
            "Active CI workflow must generate and upload the three M8-B2 result artifacts")
    require("Record M8-B2 artifact provenance and checksums" in workflow and "RUN-PROVENANCE.txt" in workflow,
            "Active CI workflow must record source revision, run URL and B2 artifact checksums")
    require("Verify M8-B4-D lifecycle source in this checkout" not in workflow,
            "Temporary B4-D troubleshooting assertion must be removed after B4-D acceptance")
    require("verify-m8-r2-evidence.py" in workflow and "M8R2OutcomeFeedbackEvidenceCli" in workflow,
            "Active CI must run R2 guardrails and emit the R2 outcome-feedback evidence artifact")
    require("github/codeql-action/init@v4" in workflow and "github/codeql-action/analyze@v4" in workflow,
            "Active CI must produce CodeQL source-analysis evidence")
    require("aquasecurity/trivy-action@v0.36.0" in workflow and "trivy-fs.sarif" in workflow,
            "Active CI must produce Trivy filesystem SCA/secret/misconfiguration evidence")

if failures:
    print("M8-B5 architecture verification: FAILED", file=sys.stderr)
    for failure in failures:
        print(f" - {failure}", file=sys.stderr)
    raise SystemExit(1)

total_production = sum(value[0] for value in EXPECTED_INVENTORY.values())
total_test_sources = sum(value[1] for value in EXPECTED_INVENTORY.values())
total_annotations = sum(value[2] for value in EXPECTED_INVENTORY.values())
total_annotated_sources = sum(value[3] for value in EXPECTED_INVENTORY.values())
total_helpers = sum(value[4] for value in EXPECTED_INVENTORY.values())
print(f"M8-B5 architecture verification: PASS ({len(modules)} modules; acyclic internal dependency graph)")
print("Direct framework dependencies: PASS (all imports declared by their owning modules)")
print("Framework-neutral module boundary: PASS")
print(f"Source/test inventory: PASS ({total_production} production Java files; {total_test_sources} test sources; {total_annotations} test annotations/factories; {total_annotated_sources} annotated test sources; {total_helpers} helpers)")
print("CI workflow mirror and M8-B2 artifact capture: PASS")
