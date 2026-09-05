"""Focused regression tests for the documentation audit failure modes."""

from __future__ import annotations

import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path

from audit_docs import Audit


class AuditDocsTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        (self.root / "docs").mkdir()
        (self.root / "examples").mkdir()
        (self.root / "src/main/java/com/lulan/shincolle/api").mkdir(parents=True)
        (self.root / "src/main/resources/META-INF").mkdir(parents=True)
        (self.root / "gradle.properties").write_text(
            "\n".join(
                (
                    "mod_id=shincolle_kai",
                    "mod_version=1.20.1-1.0.0",
                    "minecraft_version=1.20.1",
                    "forge_version=47.4.0",
                    "forge_version_range=[47,)",
                )
            ),
            encoding="utf-8",
        )
        (self.root / "CHANGELOG.md").write_text("# 1.20.1-1.0.0\n", encoding="utf-8")
        (self.root / "src/main/resources/META-INF/mods.toml").write_text(
            """[[dependencies.${ mod_id }]]
modId = "forge"
mandatory = true
[[dependencies.${ mod_id }]]
modId = "minecraft"
mandatory = true
[[dependencies.${ mod_id }]]
modId = "curios"
mandatory = false
[[dependencies.${ mod_id }]]
modId = "tconstruct"
mandatory = false
[[dependencies.${ mod_id }]]
modId = "kubejs"
mandatory = false
[[dependencies.${ mod_id }]]
modId = "jei"
mandatory = false
""",
            encoding="utf-8",
        )
        self.write_readme()
        (self.root / "docs/java_addon_api.md").write_text("# API\n", encoding="utf-8")
        (self.root / "docs/kubejs_integration.md").write_text("# KubeJS\n", encoding="utf-8")

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def write_readme(self, extra: str = "") -> None:
        headings = "\n".join(
            f"## {heading}"
            for pair in (
                ("概要", "Overview"),
                ("動作環境", "Requirements"),
                ("互換性", "Compatibility"),
                ("移行", "Migration from Reforge"),
                ("既知の問題", "Known Issues"),
                ("ドキュメント", "Documentation"),
            )
            for heading in pair
        )
        (self.root / "readme.md").write_text(
            f"""# ShinColle-kai

shincolle_kai 1.20.1 Forge 47.4.0 [47,) 1.20.1-1.0.0

Optional integrations (all work fine when absent):

| MOD | Note |
|---|---|
| Curios | optional |
| Tinkers' Construct | optional |
| KubeJS | optional |
| JEI | optional |

{headings}
{extra}
""",
            encoding="utf-8",
        )

    def findings(self) -> list[str]:
        audit = Audit(self.root)
        with redirect_stdout(StringIO()):
            audit.run(False)
        return [finding.message for finding in audit.findings]

    def test_broken_link_is_an_error(self) -> None:
        self.write_readme("[missing](missing.md)")
        self.assertIn("broken link: missing.md", self.findings())

    def test_metadata_mismatch_is_an_error(self) -> None:
        readme = self.root / "readme.md"
        readme.write_text(
            readme.read_text(encoding="utf-8").replace("1.20.1-1.0.0", "1.20.1-0.0.0"),
            encoding="utf-8",
        )
        self.assertIn("mod_version value '1.20.1-1.0.0' is missing from README", self.findings())

    def test_documented_missing_dependency_is_an_error(self) -> None:
        mods_toml = self.root / "src/main/resources/META-INF/mods.toml"
        mods_toml.write_text(mods_toml.read_text(encoding="utf-8").replace(
            "[[dependencies.${ mod_id }]]\nmodId = \"jei\"\nmandatory = false\n", ""
        ), encoding="utf-8")
        self.assertIn("dependency 'jei' is documented but absent from mods.toml", self.findings())

    def test_unmapped_optional_integration_is_an_error(self) -> None:
        readme = self.root / "readme.md"
        readme.write_text(
            readme.read_text(encoding="utf-8").replace(
                "| JEI | optional |", "| JEI | optional |\n| Unknown Integration | optional |"
            ),
            encoding="utf-8",
        )
        self.assertIn(
            "optional integration 'Unknown Integration' has no mods.toml id mapping in the documentation audit",
            self.findings(),
        )

    def test_traceability_markers_do_not_end_optional_mod_table(self) -> None:
        readme = self.root / "readme.md"
        text = readme.read_text(encoding="utf-8")
        text = text.replace(
            "| Curios | optional |\n| Tinkers' Construct | optional |",
            "| Curios | optional |\n"
            "<!-- traceability: readme.optional.curios end -->\n"
            "<!-- traceability: readme.optional.tinkers begin -->\n"
            "| Tinkers' Construct | optional |",
        )
        readme.write_text(text, encoding="utf-8")
        messages = self.findings()
        self.assertNotIn("optional dependency 'tconstruct' is absent from the README table", messages)
        self.assertNotIn("optional dependency 'kubejs' is absent from the README table", messages)
        self.assertNotIn("optional dependency 'jei' is absent from the README table", messages)

    def test_legacy_namespace_outside_migration_is_an_error(self) -> None:
        self.write_readme("shincolle:legacy_value")
        self.assertIn("legacy namespace 'shincolle:' remains in public documentation", self.findings())

    def test_legacy_namespace_in_japanese_migration_section_is_allowed(self) -> None:
        self.write_readme("## 移行\nshincolle:legacy_value")
        self.assertNotIn("legacy namespace 'shincolle:' remains in public documentation", self.findings())

    def test_missing_fully_qualified_api_type_is_an_error(self) -> None:
        (self.root / "docs/java_addon_api.md").write_text(
            "```java\ncom.lulan.shincolle.api.MissingPublicType value;\n```\n", encoding="utf-8"
        )
        self.assertIn(
            "documented public API type does not exist: com.lulan.shincolle.api.MissingPublicType",
            self.findings(),
        )

    def test_missing_short_public_api_type_is_an_error(self) -> None:
        (self.root / "docs/java_addon_api.md").write_text(
            "```java\nShipAttributeType.builder();\n```\n", encoding="utf-8"
        )
        self.assertIn(
            "documented public API type does not exist: "
            "com.lulan.shincolle.api.attribute.ShipAttributeType",
            self.findings(),
        )

    STALE_TASK_STATE = (
        "agent instruction references docs/development_status.md without marking it as the "
        "historical cutover snapshot; governance/tasks.json is the task-state authority"
    )

    def write_skill(self, body: str) -> None:
        skill = self.root / ".agents/skills/shincolle-task-workflow"
        skill.mkdir(parents=True, exist_ok=True)
        (skill / "SKILL.md").write_text(body, encoding="utf-8")

    def test_stale_task_state_instruction_is_an_error(self) -> None:
        self.write_skill("# Workflow\n\nUpdate `docs/development_status.md` before implementing.\n")
        self.assertIn(self.STALE_TASK_STATE, self.findings())

    def test_stale_root_agent_instruction_is_an_error(self) -> None:
        (self.root / "AGENTS.md").write_text(
            "Keep `docs/development_status.md` accurate.\n", encoding="utf-8"
        )
        self.assertIn(self.STALE_TASK_STATE, self.findings())

    def test_stale_agent_core_instruction_is_an_error(self) -> None:
        (self.root / "docs/agent_core.md").write_text(
            "# Agent Core Rules\n\nKeep `docs/development_status.md` accurate.\n", encoding="utf-8"
        )
        self.assertIn(self.STALE_TASK_STATE, self.findings())

    def test_historical_snapshot_reference_is_allowed(self) -> None:
        self.write_skill(
            "# Workflow\n\n`docs/development_status.md` is the historical cutover\n"
            "snapshot; `governance/tasks.json` is the authority.\n"
        )
        self.assertNotIn(self.STALE_TASK_STATE, self.findings())

    def test_explicit_prohibition_is_allowed(self) -> None:
        self.write_skill("# Workflow\n\nDo not update `docs/development_status.md`.\n")
        self.assertNotIn(self.STALE_TASK_STATE, self.findings())

    def test_historical_read_is_allowed(self) -> None:
        self.write_skill(
            "# Migration research\n\nRead `docs/development_status.md` only as a historical snapshot.\n"
        )
        self.assertNotIn(self.STALE_TASK_STATE, self.findings())

    def test_stale_directive_is_not_hidden_by_nearby_snapshot_text(self) -> None:
        self.write_skill(
            "# Workflow\n\nUpdate `docs/development_status.md` before implementing.\n"
            "It was originally created as a cutover snapshot.\n"
        )
        self.assertIn(self.STALE_TASK_STATE, self.findings())


if __name__ == "__main__":
    unittest.main()
