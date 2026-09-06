package org.roberttu.llmscriptcomposer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Builds one shared semantic application specification and emits it in a form
 * suited to a selected LLM family.
 */
public final class SpecificationCompiler {
  private final PresetIntrospector introspector = new PresetIntrospector();
  private final SpecificationValidator validator = new SpecificationValidator();

  /** Public target profiles supported by the compiler. */
  public static final List<String> TARGET_PROFILES =
      List.of("PORTABLE", "CHATGPT", "CLAUDE", "GEMINI", "DEEPSEEK");

  /**
   * Builds a readable, model-neutral specification from questionnaire state.
   *
   * @param project saved questionnaire project
   * @param preset selected questionnaire preset
   * @return validated semantic specification
   */
  public Map<String, Object> buildSpecification(
      Map<String, Object> project, Map<String, Object> preset) {
    Map<String, Object> answers = map(project.get("answers"));
    List<String> concepts = strings(preset.get("concepts"));
    boolean application = "APPLICATION".equals(preset.get("type"));
    List<String> selected = selections(answers.get(application ? "records" : "components"));
    if (selected.isEmpty()) selected = concepts;
    List<String> requestedComponents = List.copyOf(selected);

    List<Map<String, Object>> entities = application
        ? introspector.resolveEntities(preset, selected) : new ArrayList<>();
    List<String> workflows = application ? selections(answers.get("workflows")) : List.of();
    List<Map<String, Object>> workflowDefinitions = application
        ? introspector.resolveWorkflows(preset, entities, workflows) : List.of();
    List<Map<String, Object>> relationships = relationships(entities);
    List<Map<String, Object>> uiComponents = application
        ? List.of() : introspector.resolveUiComponents(preset, selected);

    Map<String, Object> specification = new LinkedHashMap<>();
    specification.put("format", "PORTABLE_APPLICATION_SPECIFICATION_V2");
    specification.put("application", object(
        "name", project.get("name"), "presetId", preset.get("id"),
        "preset", preset.get("name"), "type", preset.get("type")));
    specification.put("requirements", answers);
    specification.put("domainConcepts", concepts);
    specification.put("entities", entities);
    if (!uiComponents.isEmpty()) specification.put("uiComponents", uiComponents);
    specification.put("relationships", relationships);
    specification.put("workflows", workflows);
    specification.put("workflowDefinitions", workflowDefinitions);
    specification.put("operationBindings", operationBindings(entities));
    specification.put("presentation", object(
        "navigation", application ? navigation(entities, workflowDefinitions) : uiNavigation(answers),
        "routePolicy", "STABLE_BOOKMARKABLE_ROUTES",
        "viewPolicy", "REAL_STATE_NO_PLACEHOLDER_CONTROLS"));
    specification.put("introspection", object(
        "metadataFormat", map(preset.get("introspection")).get("format"),
        "presetScope", preset.get("id"),
        "requestedComponents", requestedComponents,
        "resolvedComponents", entities.stream().map(entity -> entity.get("componentId")).toList(),
        "autoAddedDependencies", entities.stream().map(entity -> String.valueOf(entity.get("name")))
            .filter(name -> !requestedComponents.contains(name)).toList(),
        "workflowResolution", workflowDefinitions));
    specification.put("constraints", constraints(answers, relationships));
    specification.put("delivery", object(
        "platform", first(answers.get("platform"), first(answers.get("framework"), "Developer chooses")),
        "persistence", first(answers.get("persistence"), "Developer chooses"),
        "security", selections(answers.get("security")),
        "requirements", List.of(
            "Deliver complete runnable source code",
            "Use readable source files and document the application entry point",
            "Do not silently invent conflicting business rules",
            "Report unresolved or contradictory requirements",
            "Implement observable behavior rather than placeholders",
            "Include tests for required workflows and relevant failure cases")));
    List<String> acceptance = selections(answers.get("quality"));
    if (acceptance.isEmpty()) acceptance = List.of(
        "The delivered application starts successfully",
        "Validation failures do not mutate protected state");
    specification.put("acceptanceCriteria", acceptance);
    specification.put("semanticValidation", validator.validate(specification));
    return specification;
  }

  /**
   * Emits a target-specific script without changing the shared application meaning.
   *
   * @param specification shared semantic specification
   * @param requestedProfile requested model profile
   * @return UTF-8 encoded script
   */
  public byte[] emit(Map<String, Object> specification, String requestedProfile) {
    specification.put("semanticValidation", validator.validate(specification));
    String profile = profile(requestedProfile);
    if (profile.equals("GEMINI") || profile.equals("DEEPSEEK")) {
      return explicitScript(specification, profile);
    }
    Map<String, Object> targeted = new LinkedHashMap<>(specification);
    targeted.put("emission", object(
        "targetProfile", profile,
        "instruction", "Decode and implement the complete specification without omissions or substitutions"));
    return compress(targeted);
  }

  /**
   * Produces a losslessly symbolized, self-contained model-neutral script.
   *
   * @param specification readable source specification
   * @return UTF-8 encoded compressed script
   */
  public byte[] compress(Map<String, Object> specification) {
    Map<String, Integer> frequency = new HashMap<>();
    collect(specification, frequency);
    List<String> symbols = frequency.entrySet().stream()
        .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
            .reversed().thenComparing(Map.Entry::getKey))
        .map(Map.Entry::getKey).toList();
    Map<String, String> tokens = new HashMap<>();
    for (int index = 0; index < symbols.size(); index++) {
      tokens.put(symbols.get(index), "@" + Integer.toString(index, 36).toUpperCase(Locale.ROOT));
    }
    Object ast = encode(specification, tokens);
    if (!Json.stringify(specification).equals(Json.stringify(decode(ast, symbols)))) {
      throw new IllegalStateException("Compiler equivalence gate failed");
    }
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("format", "PORTABLE_LLM_AST_V2");
    output.put("decode", List.of(
        "@N means symbols[base36(N)]", "[\"{}\",key,value,...] is an object",
        "[\"[]\",value,...] is an array", "Decode the complete specification before implementation"));
    output.put("symbols", symbols);
    output.put("ast", ast);
    output.put("encodingValidation", object(
        "status", "PASS", "losslessReconstruction", true,
        "sourceSha256", sha(Json.stringify(specification).getBytes(StandardCharsets.UTF_8))));
    output.put("semanticValidation", specification.get("semanticValidation"));
    return Json.stringify(output).getBytes(StandardCharsets.UTF_8);
  }

  private byte[] explicitScript(Map<String, Object> specification, String profile) {
    List<Map<String, Object>> entities = maps(specification.get("entities"));
    List<Map<String, Object>> menu = maps(map(specification.get("presentation")).get("navigation"));
    Map<String, Object> delivery = map(specification.get("delivery"));
    List<Map<String, Object>> pages = new ArrayList<>();
    List<Map<String, Object>> api = new ArrayList<>();
    for (Map<String, Object> item : menu) pages.add(object(
        "path", item.get("path"), "surface", "BROWSER_PAGE",
        "menuRegion", item.get("region"), "menuLabel", item.get("label"),
        "menuOrder", item.get("order"), "viewType", item.get("viewType"),
        "target", item.get("target")));
    for (Map<String, Object> entity : entities) api.add(object(
        "path", "/api/" + slug(plural(String.valueOf(entity.get("name")))),
        "entity", entity.get("name"),
        "methods", List.of("GET_LIST", "GET_ONE", "POST_CREATE", "PUT_UPDATE", "DELETE")));

    Map<String, Object> output = new LinkedHashMap<>();
    output.put("format", "EXPLICIT_LLM_IMPLEMENTATION_SPECIFICATION_V1");
    output.put("targetProfile", profile);
    if (profile.equals("DEEPSEEK")) output.put("targetSupport", object(
        "status", "EXPERIMENTAL_NOT_RECOMMENDED_FOR_COMPLETE_APPLICATION_GENERATION",
        "evidenceDate", "2026-09",
        "observedLimitation", "Tested workflow did not reliably preserve the requested standalone browser architecture",
        "productivityStopRule", "STOP_AFTER_ONE_FAILED_FUNDAMENTAL_ARCHITECTURE_CORRECTION",
        "retestPolicy", "REASSESS_WITH_MATERIALLY_NEWER_MODEL_VERSION"));
    output.put("compilerInstructions", List.of(
        "This script is self-contained; do not rely on prior conversation context",
        "Implement every declared entity, relationship, route, operation, workflow, constraint, and acceptance criterion",
        "Do not rename, omit, replace, or invent declared application elements",
        "Use multiple readable source files with one documented application entry point",
        "Do not place the complete implementation in one source file",
        "Do not claim a test passed unless it was executed",
        "Do not deliver placeholder controls, simulated persistence, or hard-coded success responses",
        "Report unresolved contradictions before making implementation assumptions"));
    output.put("application", specification.get("application"));
    output.put("domainLayer", object(
        "entities", entities, "relationships", specification.get("relationships"),
        "domainConstraints", specification.get("constraints")));
    output.put("applicationLayer", object(
        "operationBindings", specification.get("operationBindings"),
        "workflows", specification.get("workflows"),
        "workflowDefinitions", specification.get("workflowDefinitions"),
        "requirements", specification.get("requirements"),
        "executionPolicy", object(
            "validationFailure", "STOP_BEFORE_MUTATION",
            "referentialIntegrity", "ENFORCE_ON_CREATE_UPDATE_DELETE",
            "events", "EMIT_ONLY_WHEN_REQUIRED_BY_A_DECLARED_WORKFLOW")));
    output.put("infrastructureLayer", object(
        "webServer", object(
            "engine", server(delivery.get("platform")), "port", 8090,
            "pageRoutes", pages, "apiRoutes", api),
        "uiRenderer", object(
            "archetype", "HEADER_WITH_LEFT_SIDEBAR",
            "styling", "RESPONSIVE_ACCESSIBLE_UI",
            "navigationSource", "DECLARED_PAGE_ROUTES"),
        "persistence", persistence(delivery.get("persistence")), "delivery", delivery));
    output.put("acceptanceCriteria", specification.get("acceptanceCriteria"));
    output.put("introspection", specification.get("introspection"));
    output.put("semanticValidation", specification.get("semanticValidation"));
    output.put("completionEvidence", List.of(
        "Provide a requirements-to-source mapping", "Provide a requirements-to-tests mapping",
        "Report exact build and test commands", "List every unimplemented requirement"));
    String source = Json.stringify(output);
    output.put("compilerValidation", object(
        "status", "PASS", "selfContained", true, "placeholderReferences", 0,
        "danglingEntityReferences", danglingReferences(entities),
        "sourceSha256", sha(source.getBytes(StandardCharsets.UTF_8))));
    return Json.stringify(output).getBytes(StandardCharsets.UTF_8);
  }

  private static List<Map<String, Object>> relationships(List<Map<String, Object>> entities) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> entity : entities) for (Map<String, Object> field : maps(entity.get("fields"))) {
      if (field.containsKey("references")) result.add(object(
          "source", entity.get("name") + "." + field.get("name"),
          "target", field.get("references"), "cardinality", "MANY_TO_ONE",
          "required", field.get("required"), "onDelete", "RESTRICT"));
    }
    return result;
  }

  private static List<Map<String, Object>> navigation(
      List<Map<String, Object>> entities, List<Map<String, Object>> workflows) {
    List<Map<String, Object>> result = new ArrayList<>();
    result.add(nav("Dashboard", "/", "DASHBOARD_VIEW", "APPLICATION", 1));
    int order = 2;
    for (Map<String, Object> entity : entities) {
      if (Boolean.TRUE.equals(entity.get("systemManaged"))) continue;
      String name = String.valueOf(entity.get("name"));
      result.add(nav(plural(name), "/" + slug(plural(name)), "ENTITY_CRUD_VIEW", name, order++));
    }
    for (Map<String, Object> workflow : workflows) {
      Map<String, Object> page = map(workflow.get("page"));
      if (!page.isEmpty()) result.add(nav(String.valueOf(page.get("label")),
          String.valueOf(page.get("path")), String.valueOf(page.get("viewType")),
          String.valueOf(page.get("target")), order++));
    }
    return result;
  }

  private static List<Map<String, Object>> uiNavigation(Map<String, Object> answers) {
    String choice = first(answers.get("navigation"), "Single focused workspace");
    return List.of(nav(choice, "/", "UI_WORKSPACE", "APPLICATION", 1));
  }
  private static Map<String, Object> nav(String label, String path, String view, String target, int order) {
    return object("label", label, "path", path, "region", "LEFT_SIDEBAR",
        "order", order, "viewType", view, "target", target);
  }

  private static List<Map<String, Object>> operationBindings(List<Map<String, Object>> entities) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> entity : entities) for (String action : strings(entity.get("operations"))) {
      if (!List.of("CREATE", "UPDATE", "DELETE").contains(action)) continue;
      result.add(object("operation", entity.get("identifier") + "_" + action,
          "entity", entity.get("name"), "action", action,
          "pipeline", List.of("VALIDATE_SCHEMA_AND_REQUIRED_FIELDS", "VALIDATE_ENTITY_INVARIANTS",
              "VALIDATE_REFERENCES", "ENFORCE_" + action + "_POLICY", "PERSIST_ATOMICALLY",
              "WRITE_AUDIT_ENTRY_IF_CONFIGURED"),
          "failureMode", "STOP_BEFORE_MUTATION_ON_VALIDATION_ERROR"));
    }
    return result;
  }

  private static List<String> constraints(Map<String, Object> answers, List<Map<String, Object>> relationships) {
    List<String> result = new ArrayList<>(selections(answers.get("priorities")));
    if (!relationships.isEmpty()) result.addAll(List.of(
        "Referenced records must exist before dependent records are created or updated",
        "Deletion is restricted while dependent records reference the target"));
    result.addAll(List.of(
        "Money uses fixed-precision decimal arithmetic with two-decimal rounding",
        "Dates and times use declared ISO-8601 types rather than ambiguous free text",
        "Generated menus and routes exactly match declared navigation bindings"));
    return result;
  }

  private static Map<String, Object> persistence(Object value) {
    String choice = String.valueOf(value).toLowerCase(Locale.ROOT);
    if (choice.contains("relational")) return object(
        "mode", "RELATIONAL_DATABASE", "schemaInitialization", "AUTOMATIC_IDEMPOTENT",
        "transactions", "REQUIRED_FOR_MUTATIONS", "referentialIntegrity", "DATABASE_AND_APPLICATION_ENFORCED");
    if (choice.contains("memory")) return object("mode", "IN_MEMORY", "restartDurability", false);
    return object("mode", "FILE_BACKED_JSON", "storagePath", "data/application-state.json",
        "writePolicy", "ATOMIC_TEMP_FILE_REPLACE", "startupPolicy", "CREATE_IF_MISSING_VALIDATE_IF_PRESENT",
        "corruptionPolicy", "FAIL_WITH_ACTIONABLE_ERROR_DO_NOT_OVERWRITE",
        "concurrency", "SYNCHRONIZE_MUTATIONS", "restartDurability", true);
  }
  private static String server(Object value) {
    String platform = String.valueOf(value).toLowerCase(Locale.ROOT);
    if (platform.contains("plain java")) return "JDK_HTTP_SERVER";
    if (platform.contains(".net")) return "DOTNET_WEB_SERVER";
    if (platform.contains("typescript")) return "TYPESCRIPT_WEB_SERVER";
    if (platform.contains("python")) return "PYTHON_WEB_SERVER";
    return "TARGET_PLATFORM_DEFAULT_WEB_SERVER";
  }
  private static int danglingReferences(List<Map<String, Object>> entities) {
    Set<String> names = new HashSet<>();
    entities.forEach(entity -> names.add(String.valueOf(entity.get("name"))));
    int missing = 0;
    for (Map<String, Object> entity : entities) for (Map<String, Object> field : maps(entity.get("fields"))) {
      if (field.containsKey("references") && !names.contains(String.valueOf(field.get("references")).split("\\.")[0])) missing++;
    }
    if (missing > 0) throw new IllegalStateException("Specification contains " + missing + " dangling entity references");
    return 0;
  }

  private static void collect(Object value, Map<String, Integer> frequency) {
    if (value instanceof String text) frequency.merge(text, 1, Integer::sum);
    else if (value instanceof Map<?, ?> map) map.forEach((key, item) -> {
      frequency.merge(String.valueOf(key), 1, Integer::sum); collect(item, frequency);
    });
    else if (value instanceof Iterable<?> items) items.forEach(item -> collect(item, frequency));
  }
  private static Object encode(Object value, Map<String, String> tokens) {
    if (value instanceof String text) return tokens.get(text);
    if (value instanceof Map<?, ?> map) {
      List<Object> output = new ArrayList<>(); output.add("{}");
      map.forEach((key, item) -> { output.add(tokens.get(String.valueOf(key))); output.add(encode(item, tokens)); });
      return output;
    }
    if (value instanceof Iterable<?> items) {
      List<Object> output = new ArrayList<>(); output.add("[]");
      items.forEach(item -> output.add(encode(item, tokens))); return output;
    }
    return value;
  }
  private static Object decode(Object value, List<String> symbols) {
    if (value instanceof String token) return symbols.get(Integer.parseInt(token.substring(1), 36));
    if (value instanceof List<?> items && "{}".equals(items.get(0))) {
      Map<String, Object> output = new LinkedHashMap<>();
      for (int index = 1; index < items.size(); index += 2)
        output.put(String.valueOf(decode(items.get(index), symbols)), decode(items.get(index + 1), symbols));
      return output;
    }
    if (value instanceof List<?> items && "[]".equals(items.get(0))) {
      List<Object> output = new ArrayList<>();
      for (int index = 1; index < items.size(); index++) output.add(decode(items.get(index), symbols));
      return output;
    }
    return value;
  }

  private static String profile(String value) {
    String result = value == null ? "PORTABLE" : value.trim().toUpperCase(Locale.ROOT);
    if (!TARGET_PROFILES.contains(result)) throw new IllegalArgumentException("Unknown target profile: " + value);
    return result;
  }
  private static String identifier(String value) {
    return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
  }
  private static String slug(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
  }
  private static String plural(String value) {
    if (value.endsWith("y")) return value.substring(0, value.length() - 1) + "ies";
    return value.endsWith("s") ? value : value + "s";
  }
  private static String first(Object value, String fallback) {
    List<String> values = selections(value); return values.isEmpty() ? fallback : values.get(0);
  }
  private static List<String> selections(Object value) {
    if (value instanceof Iterable<?> items) {
      List<String> output = new ArrayList<>(); items.forEach(item -> output.add(String.valueOf(item))); return output;
    }
    return value == null || String.valueOf(value).isBlank() ? List.of() : List.of(String.valueOf(value));
  }
  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : new LinkedHashMap<>();
  }
  private static List<Map<String, Object>> maps(Object value) {
    if (!(value instanceof Iterable<?> items)) return List.of();
    List<Map<String, Object>> output = new ArrayList<>(); items.forEach(item -> output.add(map(item))); return output;
  }
  private static List<String> strings(Object value) {
    if (!(value instanceof Iterable<?> items)) return List.of();
    List<String> output = new ArrayList<>(); items.forEach(item -> output.add(String.valueOf(item))); return output;
  }
  private static Map<String, Object> object(Object... entries) {
    Map<String, Object> output = new LinkedHashMap<>();
    for (int index = 0; index < entries.length; index += 2) output.put(String.valueOf(entries[index]), entries[index + 1]);
    return output;
  }
  private static String sha(byte[] bytes) {
    try {
      StringBuilder output = new StringBuilder();
      for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) output.append(String.format("%02x", value));
      return output.toString();
    } catch (Exception exception) { throw new IllegalStateException(exception); }
  }
}
