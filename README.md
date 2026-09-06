# LLM Script Composer v0.8.0

LLM Script Composer is a plain Java, questionnaire-driven web application for creating, validating, compressing, versioning, and tracking portable software specifications for implementation by an LLM.

The prototype contains 100 editable presets: 80 application presets and 20 UI-development presets. It does not call an AI service and does not generate an assembled application. Developers retain control of which model receives the downloaded script.

## Run

Requirements: Java 21.

```text
java -jar target/llm-script-composer.jar
```

Open `http://127.0.0.1:8090`.

## Run in STS or Eclipse

1. Select **File > Import > Existing Maven Projects**.
2. Select the folder containing `pom.xml` and complete the import.
3. Open `src/main/java/org/roberttu/llmscriptcomposer/LlmScriptComposerApplication.java`.
4. Select **Run As > Java Application**.
5. Open `http://127.0.0.1:8090` in a browser.

`LlmScriptComposerApplication` is the clearly named application entry point in the root package. The executable JAR manifest uses the same class.

## Persistence

File mode is dependency-free and enabled by default in `composer.properties`.

Database mode uses the standard JDBC API. The included Maven configuration declares H2 as the default runtime driver. Build with Maven or place a compatible JDBC driver on the runtime classpath, then enable the DATABASE properties in `composer.properties`.

## Features

- Searchable application and UI preset catalog
- Structured contracts for all 80 application and 20 UI-development presets
- Preset-scoped requirements that prevent unrelated business concepts from entering a project
- Pre-generation checks for incomplete, inconsistent, or unsupported requirements
- Guided choice-card questionnaire with one focused decision per page
- Previous/Next navigation, progress rail, validation, review, and correction
- Stable, bookmarkable project and question-step URLs
- Automatic downstream-answer invalidation after an earlier answer changes
- Save and resume from the last valid workflow stage
- Neutral semantic HTML metadata for future introspection
- Project-card actions for opening the questionnaire, opening the Tracker, or deleting the complete project history
- Expanded offline Help guide covering the complete project, questionnaire, generation, model handoff, tracking, persistence, IDE, and troubleshooting workflow
- Readable, conventionally formatted Java source with JavaDoc on the public API
- Clearly named root-package application entry point for STS and Eclipse
- File or JDBC repository implementation
- Immutable generated revisions with SHA-256 checksums
- Compact, model-neutral JSONC export
- Shared semantic specification with Portable, ChatGPT, Claude, Gemini, and DeepSeek target profiles
- Explicit Gemini/DeepSeek three-layer output with entity schemas, relationships, routes, operation pipelines, persistence rules, and completion evidence
- Clearly labeled experimental DeepSeek profile with a productivity stop rule based on September 2026 testing
- LLM Experiment Tracker for manual cross-model evaluation
- No embedded model API, framework, Node.js, or application server

## Public scope

This repository uses conventional public software concepts: presets, questions, requirements, entities, workflows, constraints, specifications, revisions, and experiments. It is intentionally independent from any proprietary composition or runtime-adaptation system.

Research and commercial work involving more advanced application architecture and dynamic behavior is maintained separately. Contact Robert Tu Consulting for additional information.

## Target maturity

The DeepSeek profile is retained for periodic compatibility testing, but it is currently experimental and is not recommended for complete application generation. In September 2026 testing, the evaluated workflow did not reliably preserve the distinction between a console program, a Servlet deployment, and the requested standalone browser application. This is a dated test result and should be reassessed when materially newer model versions become available.

## Specification quality

The Composer checks generated specifications for completeness and consistency before download. The checks are designed to catch missing records, invalid relationships, unsupported workflows, unresolved pages, and requirements accidentally carried over from an unrelated preset. The public application explains the reported problem and expected correction without documenting the compiler's implementation method.

## License

Apache License 2.0. See `LICENSE`.
