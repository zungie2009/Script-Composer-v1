# Prototype verification

Verified on September 5, 2026.

- Java sources compile with the JDK compiler.
- Runnable JAR contains the web resources and correct main class.
- Root-package `LlmScriptComposerApplication` entry point and executable-JAR manifest agree.
- STS/Eclipse import and launch instructions identify the exact entry-point class.
- Preset catalog contains exactly 100 presets.
- Application presets: 80.
- UI-development presets: 20.
- All 100 presets expose complete preset-scoped introspection metadata: PASS.
- Every application component exposes at least one semantic role: PASS.
- All 80 application presets resolve the full shared workflow catalog: PASS.
- All 20 UI presets resolve selected UI component contracts: PASS.
- Guided preset questionnaire mechanics: PASS.
- One-question-per-page choice cards and minimum-selection validation: PASS.
- Previous/Next navigation and review/edit workflow: PASS.
- Bookmarkable project/step URL contract: PASS.
- Earlier-answer downstream invalidation: PASS.
- Declarative HTML workflow metadata and dynamic step attributes: PASS.
- Project save and reload through File storage: PASS.
- Immutable revision creation: PASS.
- Compressed JSONC download: PASS.
- Pre-download representation and specification validation gates: PASS.
- Semantic validation runs before every target-profile emission: PASS.
- Entity dependency closure and auto-added dependency provenance: PASS.
- Relationship source/target, operation entity, workflow state, navigation target, and preset-scope validation: PASS.
- Workflow subject and actor semantic-role compatibility validation: PASS.
- Portable, ChatGPT, Claude, Gemini, and experimental DeepSeek target-profile selection: PASS.
- Explicit Gemini/DeepSeek three-layer emission: PASS.
- DeepSeek experimental warning, dated evidence note, and productivity stop rule: PASS.
- Auto Repair typed schemas, relationships, routes, pipelines, persistence contract, and completion evidence: PASS.
- Consulting Invoice uses `engagementId -> Engagement.id` and `clientId -> Client.id`: PASS.
- Consulting output contains no `Work Order.id`, `Customer.id`, `workOrderId`, or `customerId`: PASS.
- Consulting scheduling, assignment, approval, availability, reporting, and notification workflows resolve to real declared state and operations: PASS.
- Deliberately corrupted Consulting relationship is blocked for Claude emission: PASS.
- Auto Body scheduling and assignment bind to `Repair Job`, not `Customer`: PASS.
- Auto Body assignment reuses `Repair Job.assignedTechnicianId -> Technician.id`: PASS.
- Auto Body Customer contains no scheduling, assignment, or work-status fields: PASS.
- Deliberately binding Auto Body scheduling to Customer is blocked for Claude emission: PASS.
- Explicit-script dangling-reference and contextual-placeholder gates: PASS.
- Experiment creation and history retrieval: PASS.
- Project-card Open, Tracker, and Delete action contracts: PASS.
- File repository cascade deletion of project, revisions, and experiments: PASS.
- JDBC project deletion is implemented as one transaction.
- Public Java types and operations include JavaDoc: PASS.
- Help contains no bundled White Paper link or compiler-implementation explanation: PASS.
- Expanded offline operating guide covers projects, questionnaires, profiles, generation, model handoff, tracking, persistence, IDE startup, and troubleshooting: PASS.
- Repository reopen/persistence check: PASS.
- Public-source terminology audit: PASS; no private project names, architectural vocabulary, or implementation identifiers detected.

JDBC mode is implemented through `java.sql` and configured for H2-compatible SQL. It requires the H2 runtime dependency declared by Maven and was not executed in the dependency-free JDK-only verification environment.
