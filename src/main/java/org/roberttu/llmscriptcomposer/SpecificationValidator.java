package org.roberttu.llmscriptcomposer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Performs deterministic semantic-linking checks before any target script is emitted. */
public final class SpecificationValidator {
  /**
   * Validates references, preset scope, workflow support, operations, and navigation.
   *
   * @param specification complete model-neutral specification
   * @return machine-readable successful validation report
   * @throws IllegalStateException when any semantic defect is found
   */
  public Map<String, Object> validate(Map<String, Object> specification) {
    List<String> errors = new ArrayList<>();
    String presetId = String.valueOf(map(specification.get("application")).get("presetId"));
    String applicationType = String.valueOf(map(specification.get("application")).get("type"));
    List<Map<String, Object>> entities = maps(specification.get("entities"));
    Map<String, Set<String>> fields = new HashMap<>();
    Map<String, Set<String>> roles = new HashMap<>();
    for (Map<String, Object> entity : entities) {
      String name = text(entity.get("name"));
      if (name.isBlank()) errors.add("Entity without a name");
      if (fields.containsKey(name)) errors.add("Duplicate entity: " + name);
      Set<String> entityFields = new HashSet<>();
      for (Map<String, Object> field : maps(entity.get("fields"))) {
        String fieldName = text(field.get("name"));
        if (!entityFields.add(fieldName)) errors.add("Duplicate field: " + name + "." + fieldName);
      }
      fields.put(name, entityFields);
      roles.put(name, new HashSet<>(strings(entity.get("semanticRoles"))));
      if (!presetId.equals(text(entity.get("scopeKey")))) {
        errors.add("Cross-preset component in " + name + ": " + entity.get("scopeKey"));
      }
    }

    int dangling = 0;
    for (Map<String, Object> entity : entities) {
      String sourceEntity = text(entity.get("name"));
      for (Map<String, Object> field : maps(entity.get("fields"))) {
        String reference = text(field.get("references"));
        if (!reference.isBlank() && !resolves(reference, fields)) {
          dangling++;
          errors.add("Dangling field reference: " + sourceEntity + "." + field.get("name") + " -> " + reference);
        }
      }
    }
    for (Map<String, Object> relationship : maps(specification.get("relationships"))) {
      if (!resolves(text(relationship.get("source")), fields)) {
        dangling++;
        errors.add("Dangling relationship source: " + relationship.get("source"));
      }
      if (!resolves(text(relationship.get("target")), fields)) {
        dangling++;
        errors.add("Dangling relationship target: " + relationship.get("target"));
      }
    }

    for (Map<String, Object> binding : maps(specification.get("operationBindings"))) {
      String entity = text(binding.get("entity"));
      if (!fields.containsKey(entity)) errors.add("Operation targets unknown entity: " + entity);
    }

    Set<String> workflowIds = new HashSet<>();
    Set<String> workflowNames = new HashSet<>();
    int incompatibleWorkflowSubjects = 0;
    int incompatibleWorkflowActors = 0;
    for (Map<String, Object> workflow : maps(specification.get("workflowDefinitions"))) {
      workflowIds.add(text(workflow.get("id")));
      workflowNames.add(text(workflow.get("name")));
      List<String> components = strings(workflow.get("supportingComponents"));
      List<String> operations = strings(workflow.get("operations"));
      if (components.isEmpty()) errors.add("Workflow has no supporting component: " + workflow.get("name"));
      if (operations.isEmpty()) errors.add("Workflow has no operation: " + workflow.get("name"));
      if (text(workflow.get("acceptanceScenario")).isBlank()) {
        errors.add("Workflow has no acceptance scenario: " + workflow.get("name"));
      }
      for (String component : components) {
        if (!fields.containsKey(component)) errors.add(
            "Workflow " + workflow.get("name") + " cites unknown component " + component);
      }
      for (Map<String, Object> state : maps(workflow.get("stateBindings"))) {
        String entity = text(state.get("entity"));
        String field = text(state.get("field"));
        if ("ALL_ACTIVE_ENTITIES".equals(entity)) continue;
        if (!fields.containsKey(entity) || !fields.get(entity).contains(field)) {
          errors.add("Workflow state does not resolve: " + entity + "." + field);
        }
      }
      for (Map<String, Object> semantic : maps(workflow.get("semanticBindings"))) {
        String entity = text(semantic.get("entity"));
        String usage = text(semantic.get("usage"));
        List<String> accepted = strings(semantic.get("acceptedRoles"));
        Set<String> actual = roles.getOrDefault(entity, Set.of());
        boolean compatible = false;
        for (String role : accepted) if (actual.contains(role)) compatible = true;
        if (!compatible) {
          if ("ASSIGNEE".equals(usage)) incompatibleWorkflowActors++;
          else incompatibleWorkflowSubjects++;
          errors.add("Workflow " + workflow.get("name") + " uses " + entity + " as " + usage
              + " but requires one of " + accepted + " and found " + actual);
        }
      }
    }
    for (String selected : strings(specification.get("workflows"))) {
      if (!workflowNames.contains(selected)) errors.add("Selected workflow is unresolved: " + selected);
    }

    for (Map<String, Object> navigation : maps(map(specification.get("presentation")).get("navigation"))) {
      String target = text(navigation.get("target"));
      if (!"APPLICATION".equals(target) && !fields.containsKey(target) && !workflowIds.contains(target)) {
        errors.add("Navigation target does not resolve: " + target);
      }
    }

    if ("UI".equals(applicationType)) {
      List<Map<String, Object>> uiComponents = maps(specification.get("uiComponents"));
      if (uiComponents.isEmpty()) errors.add("UI specification has no selected UI components");
      for (Map<String, Object> component : uiComponents) {
        if (!presetId.equals(text(component.get("scopeKey")))) {
          errors.add("Cross-preset UI component: " + component.get("name"));
        }
      }
    }

    if (!errors.isEmpty()) {
      throw new IllegalStateException("Semantic validation failed: " + String.join("; ", errors));
    }
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("status", "PASS");
    report.put("presetScope", presetId);
    report.put("entitiesChecked", entities.size());
    report.put("workflowsChecked", workflowNames.size());
    report.put("danglingReferences", dangling);
    report.put("unsupportedWorkflows", 0);
    report.put("unresolvedNavigationTargets", 0);
    report.put("crossPresetArtifacts", 0);
    report.put("incompatibleWorkflowSubjects", incompatibleWorkflowSubjects);
    report.put("incompatibleWorkflowActors", incompatibleWorkflowActors);
    return report;
  }

  private static boolean resolves(String reference, Map<String, Set<String>> fields) {
    int separator = reference.lastIndexOf('.');
    if (separator < 1 || separator == reference.length() - 1) return false;
    String entity = reference.substring(0, separator);
    String field = reference.substring(separator + 1);
    return fields.containsKey(entity) && fields.get(entity).contains(field);
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
  }

  private static List<Map<String, Object>> maps(Object value) {
    if (!(value instanceof Iterable<?> items)) return List.of();
    List<Map<String, Object>> output = new ArrayList<>();
    for (Object item : items) output.add(map(item));
    return output;
  }

  private static List<String> strings(Object value) {
    if (!(value instanceof Iterable<?> items)) return List.of();
    List<String> output = new ArrayList<>();
    for (Object item : items) output.add(String.valueOf(item));
    return output;
  }
}
