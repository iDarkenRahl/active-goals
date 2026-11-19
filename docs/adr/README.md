# Architecture Decision Records (ADRs)

Use individual ADRs for *significant, approved* technical decisions likely to affect future development. Normal bug fixes, minor refactors, and one-off implementation details usually do not need ADRs.

## Naming
Use a sequential filename such as `0001-short-descriptive-title.md`, after checking existing ADR files. Never reuse an existing number or overwrite a record.

Numbers 0003 and 0004 remain unused; the next new ADR uses 0007.

## Decision procedure
1. Check `docs/DECISIONS.md` if present, existing ADRs, the actual `GLOSSARY.md`, and relevant code for related decisions.
2. Confirm a decision was actually approved; otherwise mark it **Proposed**, never imply approval.
3. Create an ADR using `TEMPLATE.md`, citing concrete evidence and rationale when known.
4. When a decision changes, create a new ADR, mark the old one **Superseded**, and cross-link them. Preserve earlier rationale and content.
5. Keep `docs/codex/ARCHITECTURE.md` as a concise description of the current architecture; link to relevant ADRs instead of repeating lengthy justifications.

If `docs/DECISIONS.md` exists, retain it as historical context. Do not automatically convert its entries into approved ADRs: some are observations rather than historically approved decisions.

## Glossary consistency
Before proposing or accepting a decision, check affected glossary definitions, including behavioral requirements and avoided terms. Flag conflicts and require explicit approval before changing established meanings or behavior. If approved, coordinate the corresponding glossary and ADR updates; keep the definition in the glossary and the reasoning here. Do not create ADRs simply because glossary terms exist or infer historical motivations from definitions. Reuse any established local ADR naming conventions.

