#!/usr/bin/env python3
"""Audit repository documentation that can be checked without Markdown extensions.

The checks intentionally cover facts with a single repository source of truth.  They
do not judge prose quality or attempt to infer whether translations mean the same
thing.  Run without arguments in CI; --check-external is deliberately opt-in.
"""

from __future__ import annotations

import argparse
import re
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path, PurePosixPath


MARKDOWN_LINK = re.compile(r"!?\[[^\]]*\]\(([^)]+)\)")
FQCN = re.compile(r"\b(com\.lulan\.shincolle(?:\.[a-z][a-z0-9_]*)*\.[A-Z][A-Za-z0-9_]*)\b")
CODE_BLOCK = re.compile(r"```[^\n]*\n(.*?)```", re.DOTALL)
OLD_NAMESPACE = re.compile(r"(?<![A-Za-z0-9_])shincolle:")

INTERNAL_NAMESPACE_EXCLUSIONS = {
    PurePosixPath("docs/collaboration.md"),
    PurePosixPath("docs/development_status.md"),
}
INTERNAL_NAMESPACE_PREFIXES = (
    PurePosixPath("docs/specs"),
    PurePosixPath("docs/prompts"),
    PurePosixPath("docs/changes"),
    PurePosixPath("session-logs"),
)
ROOT_RELATIVE_LINK_PREFIXES = ("src/", "examples/", ".github/", "gradle/", "config/")

# governance/tasks.json is the sole Task State authority.  Agent instructions may still
# point at the pre-cutover snapshot, but only where the surrounding lines say it is
# historical; an unmarked mention reads as "keep this file current" and revives the
# workflow the cutover replaced.
TASK_STATE_INSTRUCTION_FILES = (PurePosixPath("AGENTS.md"), PurePosixPath("docs/agent_core.md"))
TASK_STATE_INSTRUCTION_ROOTS = (PurePosixPath(".agents/skills"),)
CUTOVER_SNAPSHOT = "docs/development_status.md"
CUTOVER_SNAPSHOT_MARKERS = ("read-only", "read only", "historical", "cutover", "snapshot", "凍結")
TASK_STATE_MUTATION_WORDS = ("update", "edit", "write", "maintain", "synchronize", "keep")
TASK_STATE_PROHIBITIONS = ("do not", "don't", "never", "must not", "更新しない", "更新してはならない")
RELEASE_CHECKLIST_LEGACY_NAMESPACE = "- [ ] 旧namespace `shincolle:` が公開ドキュメントに残っていない"

README_OPTIONAL_MODS = {
    "Curios": "curios",
    "Tinkers' Construct": "tconstruct",
    "KubeJS": "kubejs",
    "JEI": "jei",
}
# Code examples intentionally omit imports to stay readable. Keep the public API
# names used by those examples explicit so deleting a documented source class is
# still detected; deriving this map only from existing sources would hide deletion.
DOCUMENTED_PUBLIC_API_TYPES = {
    "CoreShipAttributes": "com.lulan.shincolle.api.attribute.CoreShipAttributes",
    "ShipAttributeCombiners": "com.lulan.shincolle.api.attribute.ShipAttributeCombiners",
    "ShipAttributeDisplayFormat": "com.lulan.shincolle.api.attribute.ShipAttributeDisplayFormat",
    "ShipAttributeRegistries": "com.lulan.shincolle.api.attribute.ShipAttributeRegistries",
    "ShipAttributeType": "com.lulan.shincolle.api.attribute.ShipAttributeType",
    "ShipAttributeValues": "com.lulan.shincolle.api.attribute.ShipAttributeValues",
    "IShipEquipment": "com.lulan.shincolle.api.equipment.IShipEquipment",
    "ResolvedShipEquipment": "com.lulan.shincolle.api.equipment.ResolvedShipEquipment",
    "ShipAttackEffect": "com.lulan.shincolle.api.equipment.ShipAttackEffect",
    "ShipEquipmentContext": "com.lulan.shincolle.api.equipment.ShipEquipmentContext",
    "PlayerOwnedShip": "com.lulan.shincolle.api.ship.PlayerOwnedShip",
}
REQUIRED_BILINGUAL_SECTIONS = (
    ("概要", "Overview"),
    ("動作環境", "Requirements"),
    ("互換性", "Compatibility"),
    ("移行", "Migration from Reforge"),
    ("既知の問題", "Known Issues"),
    ("ドキュメント", "Documentation"),
)


@dataclass(frozen=True)
class Finding:
    severity: str
    path: PurePosixPath
    line: int
    message: str

    def format(self) -> str:
        return f"{self.severity} {self.path}:{self.line}  {self.message}"


class Audit:
    def __init__(self, root: Path) -> None:
        self.root = root.resolve()
        self.findings: list[Finding] = []

    def add(self, severity: str, path: PurePosixPath, line: int, message: str) -> None:
        self.findings.append(Finding(severity, path, line, message))

    def line_for_offset(self, text: str, offset: int) -> int:
        return text.count("\n", 0, offset) + 1

    def read_text(self, relative: PurePosixPath) -> str:
        return (self.root / relative).read_text(encoding="utf-8")

    def markdown_files(self) -> list[PurePosixPath]:
        files: list[PurePosixPath] = []
        for relative_root in (PurePosixPath("docs"), PurePosixPath("examples")):
            absolute_root = self.root / relative_root
            if absolute_root.exists():
                files.extend(
                    PurePosixPath(path.relative_to(self.root).as_posix())
                    for path in absolute_root.rglob("*.md")
                )
        readme = PurePosixPath("readme.md")
        if (self.root / readme).exists():
            files.append(readme)
        return sorted(set(files), key=lambda path: path.as_posix())

    def instruction_files(self) -> list[PurePosixPath]:
        files = [path for path in TASK_STATE_INSTRUCTION_FILES if (self.root / path).exists()]
        for relative_root in TASK_STATE_INSTRUCTION_ROOTS:
            absolute_root = self.root / relative_root
            if absolute_root.exists():
                files.extend(
                    PurePosixPath(path.relative_to(self.root).as_posix())
                    for path in absolute_root.rglob("*.md")
                )
        return sorted(set(files), key=lambda path: path.as_posix())

    def check_task_state_authority(self) -> None:
        for path in self.instruction_files():
            lines = self.read_text(path).splitlines()
            for index, line in enumerate(lines):
                if CUTOVER_SNAPSHOT not in line:
                    continue
                lowered = line.lower()
                prohibits_state_use = (
                    any(prohibition in lowered for prohibition in TASK_STATE_PROHIBITIONS)
                    and any(word in lowered for word in TASK_STATE_MUTATION_WORDS)
                    and "do not forget" not in lowered
                )
                if prohibits_state_use:
                    continue
                directs_state_use = any(word in lowered for word in TASK_STATE_MUTATION_WORDS)
                window = " ".join(lines[max(index - 1, 0):index + 2]).lower()
                if not directs_state_use and any(marker in window for marker in CUTOVER_SNAPSHOT_MARKERS):
                    continue
                self.add(
                    "ERROR", path, index + 1,
                    f"agent instruction references {CUTOVER_SNAPSHOT} without marking it as the "
                    "historical cutover snapshot; governance/tasks.json is the task-state authority",
                )

    def check_metadata(self) -> None:
        properties: dict[str, str] = {}
        for line in self.read_text(PurePosixPath("gradle.properties")).splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                properties[key.strip()] = value.strip()

        readme_path = PurePosixPath("readme.md")
        changelog_path = PurePosixPath("CHANGELOG.md")
        readme = self.read_text(readme_path)
        changelog = self.read_text(changelog_path)
        expected = {
            "mod_id": properties["mod_id"],
            "mod_version": properties["mod_version"],
            "minecraft_version": properties["minecraft_version"],
            "forge_version": properties["forge_version"],
            "forge_version_range": properties["forge_version_range"],
        }

        required_readme_values = (
            ("mod_id", expected["mod_id"]),
            ("mod_version", expected["mod_version"]),
            ("minecraft_version", expected["minecraft_version"]),
            ("forge_version", expected["forge_version"]),
            ("forge_version_range", expected["forge_version_range"]),
        )
        for key, value in required_readme_values:
            if value not in readme:
                self.add("ERROR", readme_path, 1, f"{key} value '{value}' is missing from README")

        if expected["mod_version"] not in changelog:
            self.add(
                "ERROR",
                changelog_path,
                1,
                f"mod_version '{expected['mod_version']}' is missing from CHANGELOG",
            )

        public_docs = [
            path for path in self.markdown_files()
            if path == readme_path or self.is_public_namespace_document(path)
        ]
        old_id = "shincolle:"
        for path in public_docs:
            text = self.read_text(path)
            for match in OLD_NAMESPACE.finditer(text):
                line = self.line_for_offset(text, match.start())
                if self.is_allowed_legacy_namespace(path, text, match.start()):
                    continue
                self.add("ERROR", path, line, f"legacy namespace '{old_id}' remains in public documentation")

    def is_public_namespace_document(self, path: PurePosixPath) -> bool:
        if path in INTERNAL_NAMESPACE_EXCLUSIONS:
            return False
        return not any(path.is_relative_to(prefix) for prefix in INTERNAL_NAMESPACE_PREFIXES)

    def in_readme_migration_section(self, text: str, offset: int) -> bool:
        heading_start = text.rfind("\n## ", 0, offset)
        if heading_start < 0:
            heading_start = 0
        else:
            heading_start += 1
        heading_end = text.find("\n", heading_start + 1)
        heading = text[heading_start: heading_end if heading_end >= 0 else len(text)]
        return heading in {"## 移行", "## Reforge からの移行", "## Migration from Reforge"}

    def is_allowed_legacy_namespace(self, path: PurePosixPath, text: str, offset: int) -> bool:
        if path == PurePosixPath("readme.md"):
            return self.in_readme_migration_section(text, offset)
        if path == PurePosixPath("docs/release_checklist.md"):
            line_start = text.rfind("\n", 0, offset) + 1
            line_end = text.find("\n", offset)
            line = text[line_start: line_end if line_end >= 0 else len(text)]
            return line == RELEASE_CHECKLIST_LEGACY_NAMESPACE
        return False

    def check_dependencies(self) -> None:
        mods_toml = self.read_text(PurePosixPath("src/main/resources/META-INF/mods.toml"))
        dependencies: dict[str, bool] = {}
        for block in re.split(r"(?=^\[\[dependencies\.)", mods_toml, flags=re.MULTILINE):
            mod_match = re.search(r'^modId\s*=\s*"([^"]+)"', block, re.MULTILINE)
            mandatory_match = re.search(r"^mandatory\s*=\s*(true|false)", block, re.MULTILINE)
            if mod_match and mandatory_match:
                dependencies[mod_match.group(1)] = mandatory_match.group(1) == "true"

        readme_path = PurePosixPath("readme.md")
        readme = self.read_text(readme_path)
        table_labels = self.optional_mod_table_labels(readme)
        for label, line in table_labels.items():
            if label not in README_OPTIONAL_MODS:
                self.add(
                    "ERROR",
                    readme_path,
                    line,
                    f"optional integration '{label}' has no mods.toml id mapping in the documentation audit",
                )
        documented = {
            mod_id for label, mod_id in README_OPTIONAL_MODS.items() if label in table_labels
        }
        optional_dependencies = {mod_id for mod_id, mandatory in dependencies.items() if not mandatory}

        for mod_id in sorted(documented - set(dependencies)):
            line = self.find_line(readme, next(label for label, value in README_OPTIONAL_MODS.items() if value == mod_id))
            self.add("ERROR", readme_path, line, f"dependency '{mod_id}' is documented but absent from mods.toml")
        for mod_id in sorted(optional_dependencies - documented):
            self.add("ERROR", readme_path, 1, f"optional dependency '{mod_id}' is absent from the README table")
        for mod_id, mandatory in sorted(dependencies.items()):
            if mandatory and mod_id not in {"forge", "minecraft"}:
                self.add("ERROR", readme_path, 1, f"mandatory dependency '{mod_id}' has no README requirement mapping")

    def optional_mod_table_labels(self, readme: str) -> dict[str, int]:
        labels: dict[str, int] = {}
        in_optional_table = False
        for line_number, line in enumerate(readme.splitlines(), start=1):
            if line.startswith(("任意で連携するMOD", "Optional integrations")):
                in_optional_table = True
                continue
            if not in_optional_table:
                continue
            if not line.strip():
                continue
            if line.strip().startswith("<!-- traceability:"):
                continue
            if not line.startswith("|"):
                in_optional_table = False
                continue
            cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
            if not cells or cells[0] in {"MOD", "Mod"} or set(cells[0]) <= {"-", ":"}:
                continue
            labels.setdefault(cells[0], line_number)
        return labels

    def check_links(self) -> None:
        for path in self.markdown_files():
            text = self.read_text(path)
            for match in MARKDOWN_LINK.finditer(text):
                raw_target = match.group(1).strip().strip("<>")
                line = self.line_for_offset(text, match.start(1))
                if self.is_external_or_anchor(raw_target):
                    continue
                target = raw_target.split("#", 1)[0].split("?", 1)[0]
                if not target:
                    continue
                if target.startswith("original_source/"):
                    self.add("WARN", path, line, "link targets original_source/, which is local-only and not public")
                    continue
                base = self.root if target.startswith(ROOT_RELATIVE_LINK_PREFIXES) else self.root / path.parent
                target_path = (base / Path(target)).resolve()
                try:
                    target_path.relative_to(self.root)
                except ValueError:
                    self.add("ERROR", path, line, f"link escapes repository: {raw_target}")
                    continue
                if not target_path.exists():
                    self.add("ERROR", path, line, f"broken link: {raw_target}")

    @staticmethod
    def is_external_or_anchor(target: str) -> bool:
        lower = target.lower()
        return target.startswith("#") or lower.startswith(("http://", "https://", "mailto:", "tel:", "data:"))

    def check_public_api_types(self) -> None:
        docs = (PurePosixPath("docs/java_addon_api.md"), PurePosixPath("docs/kubejs_integration.md"))
        referenced: dict[str, tuple[PurePosixPath, int]] = {}
        for path in docs:
            text = self.read_text(path)
            for match in FQCN.finditer(text):
                referenced.setdefault(match.group(1), (path, self.line_for_offset(text, match.start())))
            for block in CODE_BLOCK.finditer(text):
                for short_name, fqcn in DOCUMENTED_PUBLIC_API_TYPES.items():
                    if re.search(rf"\b{re.escape(short_name)}\b", block.group(1)):
                        referenced.setdefault(fqcn, (path, self.line_for_offset(text, block.start(1))))

        for fqcn, (path, line) in sorted(referenced.items()):
            source = self.root / "src/main/java" / Path(*fqcn.split(".")).with_suffix(".java")
            if not source.exists():
                self.add("ERROR", path, line, f"documented public API type does not exist: {fqcn}")

    def check_bilingual_sections(self) -> None:
        path = PurePosixPath("readme.md")
        text = self.read_text(path)
        headings = set(re.findall(r"^##\s+(.+?)\s*$", text, re.MULTILINE))
        for japanese, english in REQUIRED_BILINGUAL_SECTIONS:
            if japanese not in headings:
                self.add("WARN", path, 1, f"Japanese section is missing: {japanese}")
            if english not in headings:
                self.add("WARN", path, 1, f"English section is missing: {english}")

    def check_external_urls(self) -> None:
        seen: set[str] = set()
        for path in self.markdown_files():
            text = self.read_text(path)
            for match in MARKDOWN_LINK.finditer(text):
                url = match.group(1).strip().strip("<>")
                if not url.startswith(("http://", "https://")) or url in seen:
                    continue
                seen.add(url)
                request = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "ShinColle-kai-doc-audit"})
                try:
                    with urllib.request.urlopen(request, timeout=10) as response:
                        if response.status >= 400:
                            raise urllib.error.HTTPError(url, response.status, "HTTP error", response.headers, None)
                except (OSError, urllib.error.HTTPError) as error:
                    self.add("WARN", path, self.line_for_offset(text, match.start(1)), f"external URL check failed for {url}: {error}")

    @staticmethod
    def find_line(text: str, needle: str) -> int:
        index = text.find(needle)
        return text.count("\n", 0, index) + 1 if index >= 0 else 1

    def run(self, check_external: bool) -> int:
        self.check_metadata()
        self.check_dependencies()
        self.check_links()
        self.check_public_api_types()
        self.check_bilingual_sections()
        self.check_task_state_authority()
        if check_external:
            self.check_external_urls()

        for finding in self.findings:
            print(finding.format())
        errors = sum(finding.severity == "ERROR" for finding in self.findings)
        warnings = sum(finding.severity == "WARN" for finding in self.findings)
        print(f"\n{errors} errors, {warnings} warnings")
        return 1 if errors else 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Audit ShinColle-kai repository documentation.")
    parser.add_argument("--check-external", action="store_true", help="warn about unreachable external Markdown URLs")
    parser.add_argument("--root", type=Path, help="repository root; intended for isolated audit tests")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.root if args.root else Path(__file__).resolve().parents[1]
    return Audit(root).run(args.check_external)


if __name__ == "__main__":
    sys.exit(main())
