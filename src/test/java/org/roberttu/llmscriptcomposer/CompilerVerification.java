package org.roberttu.llmscriptcomposer;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dependency-free regression verification for introspection, validation, and script emission. */
public final class CompilerVerification {
  private CompilerVerification() {
  }

  /**
   * Generates representative Auto Repair scripts and fails on missing semantics.
   *
   * @param args unused command-line arguments
   */
  public static void main(String[] args) {
    PresetCatalog catalog = new PresetCatalog();
    verifyCompleteCatalog(catalog);
    Map<String, Object> preset = catalog.get("auto-repair");
    Map<String, Object> answers = new LinkedHashMap<>();
    answers.put("records", preset.get("concepts"));
    answers.put("workflows", List.of(
        "Create and maintain records",
        "Search, filter and sort",
        "Schedule people or resources",
        "Assign and track work",
        "Manage inventory or availability",
        "Generate reports"));
    answers.put("platform", List.of("Plain Java web application"));
    answers.put("persistence", List.of("File-backed local storage"));
    answers.put("security", List.of("No login for the first prototype"));
    answers.put("priorities", List.of("Accurate records", "Reporting and visibility"));
    answers.put("quality", List.of(
        "Application starts successfully",
        "Relationships preserve referential integrity",
        "Automated tests pass"));

    Map<String, Object> project = new LinkedHashMap<>();
    project.put("name", "Auto Repair Verification");
    project.put("answers", answers);

    SpecificationCompiler compiler = new SpecificationCompiler();
    Map<String, Object> specification = compiler.buildSpecification(project, preset);
    String gemini = new String(compiler.emit(specification, "GEMINI"), StandardCharsets.UTF_8);
    require(gemini, "EXPLICIT_LLM_IMPLEMENTATION_SPECIFICATION_V1");
    require(gemini, "domainLayer");
    require(gemini, "applicationLayer");
    require(gemini, "infrastructureLayer");
    require(gemini, "DECIMAL_19_2");
    require(gemini, "DATE_TIME_ISO_8601");
    require(gemini, "Work Order.id");
    require(gemini, "RESTRICT");
    require(gemini, "/work-orders");
    require(gemini, "/inventory");
    require(gemini, "/reports");
    require(gemini, "PERSIST_ATOMICALLY");
    require(gemini, "\"danglingEntityReferences\":0");
    reject(gemini, "defined above");
    reject(gemini, "same as before");

    String deepSeek = new String(compiler.emit(specification, "DEEPSEEK"), StandardCharsets.UTF_8);
    require(deepSeek, "EXPERIMENTAL_NOT_RECOMMENDED_FOR_COMPLETE_APPLICATION_GENERATION");
    require(deepSeek, "STOP_AFTER_ONE_FAILED_FUNDAMENTAL_ARCHITECTURE_CORRECTION");
    require(deepSeek, "REASSESS_WITH_MATERIALLY_NEWER_MODEL_VERSION");

    String portable = new String(compiler.emit(specification, "PORTABLE"), StandardCharsets.UTF_8);
    require(portable, "PORTABLE_LLM_AST_V2");
    require(portable, "losslessReconstruction");
    require(portable, "semanticValidation");

    verifyConsulting(catalog, compiler);
    verifyAutoBody(catalog, compiler);
    System.out.println("PASS 100 semantic-role presets, workflow compatibility, Consulting Firm, Auto Body, and target profiles");
  }

  private static void verifyCompleteCatalog(PresetCatalog catalog) {
    int applications = 0;
    int interfaces = 0;
    SpecificationCompiler compiler = new SpecificationCompiler();
    for (Map<String, Object> preset : catalog.all()) {
      Map<String, Object> introspection = map(preset.get("introspection"));
      require(String.valueOf(introspection.get("format")), "INTROSPECTABLE_");
      List<?> components = (List<?>) introspection.get("components");
      if (components.size() != ((List<?>) preset.get("concepts")).size()) {
        throw new AssertionError("Incomplete preset introspection: " + preset.get("id"));
      }
      Map<String, Object> answers = standardAnswers(preset);
      Map<String, Object> project = new LinkedHashMap<>();
      project.put("name", preset.get("name") + " Verification");
      project.put("answers", answers);
      Map<String, Object> specification;
      try {
        specification = compiler.buildSpecification(project, preset);
      } catch (RuntimeException exception) {
        throw new AssertionError("Preset failed semantic resolution: " + preset.get("id"), exception);
      }
      require(String.valueOf(specification.get("semanticValidation")), "status=PASS");
      if ("APPLICATION".equals(preset.get("type"))) {
        applications++;
        for (Object item : components) {
          Map<String, Object> component = map(item);
          if (((List<?>) component.get("semanticRoles")).isEmpty()) {
            throw new AssertionError("Component has no semantic role: " + component.get("id"));
          }
        }
        if (((List<?>) specification.get("workflowDefinitions")).size()
            != PresetIntrospector.APPLICATION_WORKFLOWS.size()) {
          throw new AssertionError("Incomplete workflow resolution: " + preset.get("id"));
        }
      } else {
        interfaces++;
        if (((List<?>) specification.get("uiComponents")).isEmpty()) {
          throw new AssertionError("No resolved UI components: " + preset.get("id"));
        }
      }
    }
    if (applications != 80 || interfaces != 20) {
      throw new AssertionError("Unexpected preset distribution: " + applications + "/" + interfaces);
    }
  }

  private static void verifyConsulting(PresetCatalog catalog, SpecificationCompiler compiler) {
    Map<String, Object> preset = catalog.get("consulting");
    Map<String, Object> project = new LinkedHashMap<>();
    project.put("name", "Consulting Firm Verification");
    project.put("answers", standardAnswers(preset));
    Map<String, Object> specification = compiler.buildSpecification(project, preset);
    String source = Json.stringify(specification);
    require(source, "engagementId");
    require(source, "Engagement.id");
    require(source, "clientId");
    require(source, "Client.id");
    require(source, "approvalStatus");
    require(source, "RESOURCE_AVAILABILITY");
    require(source, "Notification");
    require(source, "\"unsupportedWorkflows\":0");
    reject(source, "Work Order.id");
    reject(source, "Customer.id");
    reject(source, "workOrderId");
    reject(source, "customerId");

    Map<String, Object> dependencyProject = new LinkedHashMap<>();
    dependencyProject.put("name", "Consulting Invoice Dependency Verification");
    Map<String, Object> dependencyAnswers = standardAnswers(preset);
    dependencyAnswers.put("records", List.of("Invoice"));
    dependencyProject.put("answers", dependencyAnswers);
    String dependencySource = Json.stringify(compiler.buildSpecification(dependencyProject, preset));
    require(dependencySource, "autoAddedDependencies");
    require(dependencySource, "Engagement");
    require(dependencySource, "Client");

    String claude = new String(compiler.emit(specification, "CLAUDE"), StandardCharsets.UTF_8);
    require(claude, "PORTABLE_LLM_AST_V2");
    require(claude, "engagementId");
    require(claude, "semanticValidation");
    require(claude, "encodingValidation");
    reject(claude, "Work Order.id");

    Map<String, Object> broken = compiler.buildSpecification(project, preset);
    for (Map<String, Object> entity : maps(broken.get("entities"))) {
      if (!"Invoice".equals(entity.get("name"))) continue;
      for (Map<String, Object> field : maps(entity.get("fields"))) {
        if ("engagementId".equals(field.get("name"))) field.put("references", "Missing.id");
      }
    }
    try {
      compiler.emit(broken, "CLAUDE");
      throw new AssertionError("Semantic gate accepted a dangling reference");
    } catch (IllegalStateException expected) {
      require(expected.getMessage(), "Dangling field reference");
    }
  }

  private static void verifyAutoBody(PresetCatalog catalog, SpecificationCompiler compiler) {
    Map<String, Object> preset = catalog.get("auto-body");
    Map<String, Object> project = new LinkedHashMap<>();
    project.put("name", "Auto Body Semantic Role Verification");
    project.put("answers", standardAnswers(preset));
    Map<String, Object> specification = compiler.buildSpecification(project, preset);
    Map<String, Object> customer = entity(specification, "Customer");
    Map<String, Object> repairJob = entity(specification, "Repair Job");
    require(String.valueOf(repairJob.get("semanticRoles")), "SCHEDULE_PRIORITY");
    require(String.valueOf(repairJob.get("semanticRoles")), "ASSIGNMENT_PRIORITY");
    requireField(repairJob, "scheduledStart");
    requireField(repairJob, "scheduledEnd");
    requireField(repairJob, "scheduleStatus");
    requireField(repairJob, "assignedTechnicianId");
    requireField(repairJob, "workStatus");
    rejectField(customer, "scheduledStart");
    rejectField(customer, "scheduledEnd");
    rejectField(customer, "scheduleStatus");
    rejectField(customer, "assignedToId");
    rejectField(customer, "workStatus");

    String source = Json.stringify(specification);
    require(source, "\"incompatibleWorkflowSubjects\":0");
    require(source, "\"incompatibleWorkflowActors\":0");
    require(source, "\"supportingComponents\":[\"Repair Job\"]");
    require(source, "assignedTechnicianId");

    String claude = new String(compiler.emit(specification, "CLAUDE"), StandardCharsets.UTF_8);
    require(claude, "Repair Job");
    require(claude, "SCHEDULE_PRIORITY");
    require(claude, "assignedTechnicianId");

    Map<String, Object> broken = compiler.buildSpecification(project, preset);
    for (Map<String, Object> workflow : maps(broken.get("workflowDefinitions"))) {
      if (!"RESOURCE_SCHEDULING".equals(workflow.get("id"))) continue;
      maps(workflow.get("semanticBindings")).get(0).put("entity", "Customer");
    }
    try {
      compiler.emit(broken, "CLAUDE");
      throw new AssertionError("Semantic gate accepted Customer as the scheduling subject");
    } catch (IllegalStateException expected) {
      require(expected.getMessage(), "uses Customer as SUBJECT");
    }
  }

  private static Map<String, Object> entity(Map<String, Object> specification, String name) {
    for (Map<String, Object> entity : maps(specification.get("entities"))) {
      if (name.equals(entity.get("name"))) return entity;
    }
    throw new AssertionError("Missing entity: " + name);
  }

  private static void requireField(Map<String, Object> entity, String name) {
    if (!hasField(entity, name)) throw new AssertionError("Missing field " + entity.get("name") + "." + name);
  }

  private static void rejectField(Map<String, Object> entity, String name) {
    if (hasField(entity, name)) throw new AssertionError("Unexpected field " + entity.get("name") + "." + name);
  }

  private static boolean hasField(Map<String, Object> entity, String name) {
    for (Map<String, Object> field : maps(entity.get("fields"))) {
      if (name.equals(field.get("name"))) return true;
    }
    return false;
  }

  private static Map<String, Object> standardAnswers(Map<String, Object> preset) {
    Map<String, Object> answers = new LinkedHashMap<>();
    if ("APPLICATION".equals(preset.get("type"))) {
      answers.put("records", preset.get("concepts"));
      answers.put("workflows", PresetIntrospector.APPLICATION_WORKFLOWS);
      answers.put("platform", List.of("Plain Java web application"));
      answers.put("persistence", List.of("File-backed local storage"));
      answers.put("security", List.of("Administrator login"));
    } else {
      answers.put("components", preset.get("concepts"));
      answers.put("navigation", List.of("Left sidebar"));
      answers.put("framework", List.of("HTML, CSS and vanilla JavaScript"));
    }
    answers.put("priorities", List.of("Accurate records", "Reporting and visibility"));
    answers.put("quality", List.of("Application starts successfully", "Automated tests pass"));
    return answers;
  }

  private static void require(String script, String expected) {
    if (!script.contains(expected)) {
      throw new AssertionError("Missing expected script content: " + expected);
    }
  }

  private static void reject(String script, String prohibited) {
    if (script.toLowerCase().contains(prohibited.toLowerCase())) {
      throw new AssertionError("Prohibited contextual placeholder: " + prohibited);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> maps(Object value) {
    return (List<Map<String, Object>>) value;
  }
}
