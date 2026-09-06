package org.roberttu.llmscriptcomposer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Creates and resolves the public, deterministic metadata carried by questionnaire presets.
 * Definitions are scoped by preset so identically named records may have different domain meanings.
 */
public final class PresetIntrospector {
  /** Shared operational patterns offered by every application preset. */
  public static final List<String> APPLICATION_WORKFLOWS = List.of(
      "Create and maintain records",
      "Search, filter and sort",
      "Schedule people or resources",
      "Assign and track work",
      "Approve or reject requests",
      "Manage inventory or availability",
      "Generate reports",
      "Send notifications");

  /**
   * Builds an inspectable metadata contract for one preset.
   *
   * @param presetId stable preset identifier
   * @param presetName display name
   * @param type APPLICATION or UI
   * @param concepts preset component names
   * @return complete preset-scoped introspection contract
   */
  public Map<String, Object> describe(
      String presetId, String presetName, String type, List<String> concepts) {
    List<Map<String, Object>> components = new ArrayList<>();
    if ("UI".equals(type)) {
      for (String concept : concepts) components.add(uiComponent(presetId, concept));
      return object(
          "format", "INTROSPECTABLE_UI_PRESET_V1",
          "scopeKey", presetId,
          "components", components,
          "interactionStates", List.of(
              "LOADING", "EMPTY", "VALIDATION_ERROR", "SYSTEM_ERROR",
              "SUCCESS", "NO_PERMISSION", "OFFLINE"),
          "contracts", List.of(
              "RESPONSIVE_LAYOUT", "KEYBOARD_NAVIGATION", "ACCESSIBLE_NAMES",
              "STABLE_ROUTES", "NO_DEAD_CONTROLS", "OBSERVABLE_STATE_FEEDBACK"),
          "provenance", object("presetId", presetId, "presetName", presetName));
    }
    for (String concept : concepts) components.add(applicationComponent(presetId, concept));
    List<Map<String, Object>> workflows = new ArrayList<>();
    for (String workflow : APPLICATION_WORKFLOWS) workflows.add(workflowRecipe(workflow));
    return object(
        "format", "INTROSPECTABLE_APPLICATION_PRESET_V1",
        "scopeKey", presetId,
        "components", components,
        "workflowRecipes", workflows,
        "validationPolicy", object(
            "entityReferences", "MUST_RESOLVE_WITHIN_PRESET_SCOPE",
            "workflowSupport", "MUST_RESOLVE_TO_STATE_OPERATIONS_AND_ACCEPTANCE",
            "navigationTargets", "MUST_RESOLVE_TO_ENTITY_OR_WORKFLOW_CAPABILITY",
            "crossPresetArtifacts", "PROHIBITED"),
        "provenance", object("presetId", presetId, "presetName", presetName));
  }

  /**
   * Resolves selected application records and all referenced preset-scoped dependencies.
   *
   * @param preset active preset
   * @param requestedNames selected record names
   * @return mutable resolved entity definitions in dependency order
   */
  public List<Map<String, Object>> resolveEntities(
      Map<String, Object> preset, List<String> requestedNames) {
    Map<String, Object> metadata = map(preset.get("introspection"));
    Map<String, Map<String, Object>> definitions = new LinkedHashMap<>();
    for (Map<String, Object> component : maps(metadata.get("components"))) {
      definitions.put(String.valueOf(component.get("name")), component);
    }
    LinkedHashSet<String> resolved = new LinkedHashSet<>(requestedNames);
    boolean changed;
    do {
      changed = false;
      for (String name : List.copyOf(resolved)) {
        Map<String, Object> component = definitions.get(name);
        if (component == null) throw new IllegalArgumentException(
            "Preset " + preset.get("id") + " does not define component " + name);
        for (Map<String, Object> field : maps(component.get("fields"))) {
          String reference = String.valueOf(field.getOrDefault("references", ""));
          if (!reference.isBlank() && resolved.add(reference.split("\\.")[0])) changed = true;
        }
      }
    } while (changed);

    List<Map<String, Object>> entities = new ArrayList<>();
    for (String name : resolved) {
      Map<String, Object> component = definitions.get(name);
      if (component == null) throw new IllegalStateException(
          "Missing preset-scoped dependency " + name + " in " + preset.get("id"));
      entities.add(entityFrom(component));
    }
    return entities;
  }

  /**
   * Resolves selected UI components from their preset-scoped metadata.
   *
   * @param preset active UI preset
   * @param requestedNames selected UI component names
   * @return resolved preset-scoped UI component definitions
   */
  public List<Map<String, Object>> resolveUiComponents(
      Map<String, Object> preset, List<String> requestedNames) {
    Map<String, Object> metadata = map(preset.get("introspection"));
    Map<String, Map<String, Object>> definitions = new LinkedHashMap<>();
    for (Map<String, Object> component : maps(metadata.get("components"))) {
      definitions.put(String.valueOf(component.get("name")), component);
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (String name : requestedNames) {
      Map<String, Object> component = definitions.get(name);
      if (component == null) throw new IllegalArgumentException(
          "UI preset " + preset.get("id") + " does not define component " + name);
      result.add(new LinkedHashMap<>(component));
    }
    return result;
  }

  /**
   * Resolves selected workflow labels to real state, operations, presentation surfaces, and tests.
   * Supporting fields are added only from these declared shared recipes.
   *
   * @param preset active application preset
   * @param entities resolved active entities
   * @param selectedWorkflows selected workflow labels
   * @return resolved workflow definitions
   */
  public List<Map<String, Object>> resolveWorkflows(
      Map<String, Object> preset,
      List<Map<String, Object>> entities,
      List<String> selectedWorkflows) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (String workflow : selectedWorkflows) {
      switch (workflow) {
        case "Create and maintain records" -> result.add(crudWorkflow(entities));
        case "Search, filter and sort" -> result.add(searchWorkflow(entities));
        case "Schedule people or resources" -> result.add(scheduleWorkflow(entities));
        case "Assign and track work" -> result.add(assignmentWorkflow(entities));
        case "Approve or reject requests" -> result.add(approvalWorkflow(entities));
        case "Manage inventory or availability" -> result.add(inventoryWorkflow(entities));
        case "Generate reports" -> result.add(reportingWorkflow(entities));
        case "Send notifications" -> result.add(notificationWorkflow(preset, entities));
        default -> throw new IllegalArgumentException("Unsupported workflow selection: " + workflow);
      }
    }
    return result;
  }

  private Map<String, Object> applicationComponent(String presetId, String name) {
    List<Map<String, Object>> fields = fields(presetId, name);
    return object(
        "id", presetId.toUpperCase(Locale.ROOT).replace('-', '_') + "__" + identifier(name),
        "scopeKey", presetId,
        "kind", "ENTITY_COMPONENT",
        "name", name,
        "identifier", identifier(name),
        "semanticRoles", semanticRoles(presetId, name),
        "fields", fields,
        "capabilities", List.of("CREATE", "READ", "UPDATE", "DELETE", "LIST", "SEARCH", "FILTER", "PAGINATE"),
        "provenance", object("presetId", presetId, "definition", "PRESET_SCOPED_COMPONENT"));
  }

  private Map<String, Object> uiComponent(String presetId, String name) {
    return object(
        "id", presetId.toUpperCase(Locale.ROOT).replace('-', '_') + "__" + identifier(name),
        "scopeKey", presetId,
        "kind", "UI_COMPONENT",
        "name", name,
        "inputs", List.of("MODEL", "VIEW_STATE", "USER_ACTION"),
        "outputs", List.of("RENDERED_CONTENT", "SEMANTIC_EVENT"),
        "states", List.of("DEFAULT", "LOADING", "EMPTY", "ERROR", "SUCCESS", "DISABLED"),
        "constraints", List.of("KEYBOARD_ACCESSIBLE", "RESPONSIVE", "NO_DEAD_CONTROL"),
        "provenance", object("presetId", presetId, "definition", "PRESET_SCOPED_UI_COMPONENT"));
  }

  private List<Map<String, Object>> fields(String presetId, String name) {
    if ("consulting".equals(presetId)) return consultingFields(name);
    if ("auto-repair".equals(presetId)) return autoRepairFields(name);
    if ("auto-body".equals(presetId)) return autoBodyFields(name);
    String id = identifier(name);
    return switch (id) {
      case "CUSTOMER", "CLIENT", "GUEST", "PATIENT", "MEMBER", "TENANT", "OWNER",
          "GUARDIAN", "STUDENT", "LEARNER", "DONOR", "SELLER", "BUYER", "LEAD" ->
          List.of(id(), field("name", "STRING", true), field("email", "EMAIL", false),
              field("phone", "PHONE", false));
      case "VEHICLE" -> List.of(id(), field("make", "STRING", true), field("model", "STRING", true),
          special("year", "INTEGER", true, "VALID_MODEL_YEAR"), special("vin", "STRING", false, "UNIQUE"));
      case "APPOINTMENT", "RESERVATION", "BOOKING", "LESSON", "VISIT", "SESSION", "EVENT" ->
          List.of(id(), field("name", "STRING", true), field("scheduledAt", "DATE_TIME_ISO_8601", true),
              enumeration("status", "SCHEDULED", "COMPLETED", "CANCELLED"));
      case "PRODUCT", "PART", "SUPPLY", "MATERIAL", "INGREDIENT", "INVENTORY_ITEM",
          "INVENTORY_LOT", "EQUIPMENT", "CHEMICAL" ->
          List.of(id(), field("name", "STRING", true), money("unitPrice", "NON_NEGATIVE"),
              special("stockQuantity", "INTEGER", true, "NON_NEGATIVE"),
              special("reorderThreshold", "INTEGER", false, "NON_NEGATIVE"));
      case "PAYMENT" -> List.of(id(), money("amount", "NON_NEGATIVE"),
          field("receivedDate", "DATE_ISO_8601", true),
          enumeration("status", "PENDING", "COMPLETED", "REFUNDED"));
      case "INVOICE" -> List.of(id(), field("subjectId", "STRING", true),
          money("totalAmount", "NON_NEGATIVE"),
          enumeration("paymentStatus", "UNPAID", "PARTIALLY_PAID", "PAID", "REFUNDED"),
          field("issuedDate", "DATE_ISO_8601", true));
      default -> List.of(id(), field("name", "STRING", true));
    };
  }

  private List<Map<String, Object>> autoBodyFields(String name) {
    return switch (identifier(name)) {
      case "CUSTOMER" -> List.of(id(), field("name", "STRING", true),
          field("email", "EMAIL", false), field("phone", "PHONE", true),
          field("address", "STRING", false));
      case "VEHICLE" -> List.of(id(), reference("customerId", "Customer.id", true),
          special("vin", "STRING", true, "UNIQUE"), field("make", "STRING", true),
          field("model", "STRING", true), special("year", "INTEGER", true, "VALID_MODEL_YEAR"),
          field("color", "STRING", false));
      case "ESTIMATE" -> List.of(id(), reference("customerId", "Customer.id", true),
          reference("vehicleId", "Vehicle.id", true), money("estimatedAmount", "NON_NEGATIVE"),
          enumeration("status", "DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "EXPIRED"));
      case "REPAIR_JOB" -> List.of(id(), reference("customerId", "Customer.id", true),
          reference("vehicleId", "Vehicle.id", true), reference("estimateId", "Estimate.id", false),
          reference("assignedTechnicianId", "Technician.id", false), field("description", "STRING", true),
          enumeration("status", "PLANNED", "SCHEDULED", "IN_PROGRESS", "QUALITY_CHECK", "COMPLETED", "CANCELLED"));
      case "TECHNICIAN" -> List.of(id(), field("name", "STRING", true),
          field("specialty", "STRING", false), field("available", "BOOLEAN", true));
      case "PART" -> List.of(id(), special("sku", "STRING", true, "UNIQUE"),
          field("name", "STRING", true), money("unitPrice", "NON_NEGATIVE"),
          special("stockQuantity", "INTEGER", true, "NON_NEGATIVE"),
          special("reorderThreshold", "INTEGER", false, "NON_NEGATIVE"));
      case "INSURANCE_CLAIM" -> List.of(id(), reference("customerId", "Customer.id", true),
          reference("vehicleId", "Vehicle.id", true), reference("repairJobId", "Repair Job.id", false),
          field("claimNumber", "STRING", true), field("insurer", "STRING", true),
          enumeration("status", "OPEN", "SUBMITTED", "APPROVED", "DENIED", "SETTLED"));
      default -> List.of(id(), field("name", "STRING", true));
    };
  }

  private List<String> semanticRoles(String presetId, String name) {
    String id = identifier(name);
    LinkedHashSet<String> roles = new LinkedHashSet<>();
    roles.add("BUSINESS_RECORD");
    if (containsAny(id, "CUSTOMER", "CLIENT", "GUEST", "PATIENT", "MEMBER", "TENANT",
        "OWNER", "GUARDIAN", "STUDENT", "LEARNER", "DONOR", "SELLER", "BUYER", "LEAD")) {
      roles.add("PARTY");
      roles.add("CONTACT");
    }
    if (containsAny(id, "EMPLOYEE", "TECHNICIAN", "CONSULTANT", "CREW", "DRIVER", "PRACTITIONER",
        "ATTORNEY", "TRANSLATOR", "CAREGIVER", "INSTRUCTOR", "TEACHER", "THERAPIST", "STYLIST",
        "BARBER", "GROOMER", "TRAINER", "TUTOR", "DETAILER", "BARISTA", "CRAFTSPERSON",
        "INSTALLER", "SUBCONTRACTOR", "COURIER", "AGENT")) {
      roles.add("WORK_RESOURCE");
      roles.add("AVAILABILITY_RESOURCE");
    }
    if (containsAny(id, "ROOM", "TABLE", "DESK", "UNIT", "PROPERTY", "VEHICLE", "TRUCK",
        "SERVICE_BAY", "POOL", "SITE", "CLASS", "COURSE", "PLAN", "LISTING")) {
      roles.add("AVAILABILITY_RESOURCE");
    }
    if (containsAny(id, "APPOINTMENT", "RESERVATION", "BOOKING", "SCHEDULE", "LESSON", "CLASS",
        "VISIT", "SESSION", "EVENT", "DEADLINE", "ROUTE", "STAY", "RENTAL")) {
      roles.add("SCHEDULE_ENTRY");
      roles.add("SCHEDULE_SUBJECT");
      roles.add("ASSIGNMENT_SUBJECT");
      roles.add("TRACKABLE");
      roles.add("AVAILABILITY_RESOURCE");
    }
    if (containsAny(id, "JOB", "WORK_ORDER", "PROJECT", "TASK", "ENGAGEMENT", "DELIVERABLE",
        "SERVICE_TICKET", "SERVICE_CALL", "REPAIR", "MOVE", "LOAD", "SHIPMENT", "DELIVERY",
        "ORDER", "SALE", "CLAIM", "REQUEST", "ISSUE", "INCIDENT", "VIOLATION", "INSPECTION",
        "VISIT", "ROUTE",
        "ENCOUNTER", "PRODUCTION_BATCH", "RECURRING_WORK")) {
      roles.add("WORK_ITEM");
      roles.add("SCHEDULE_SUBJECT");
      roles.add("ASSIGNMENT_SUBJECT");
      roles.add("TRACKABLE");
    }
    if (containsAny(id, "ESTIMATE", "REQUEST", "CLAIM", "DOCUMENT", "PURCHASE_ORDER", "DELIVERABLE",
        "INVOICE", "PROJECT", "APPRAISAL", "PRESCRIPTION")) {
      roles.add("APPROVAL_SUBJECT");
    }
    if (containsAny(id, "INVENTORY", "PART", "SUPPLY", "MATERIAL", "INGREDIENT", "PRODUCT",
        "EQUIPMENT", "CHEMICAL", "ITEM", "PERISHABLE_LOT")) {
      roles.add("INVENTORY_SUBJECT");
    }
    if ("consulting".equals(presetId) && "ENGAGEMENT".equals(id)) roles.add("SCHEDULE_PRIORITY");
    if ("consulting".equals(presetId) && "DELIVERABLE".equals(id)) roles.add("ASSIGNMENT_PRIORITY");
    if ("auto-body".equals(presetId) && "REPAIR_JOB".equals(id)) {
      roles.add("SCHEDULE_PRIORITY");
      roles.add("ASSIGNMENT_PRIORITY");
    }
    return List.copyOf(roles);
  }

  private List<Map<String, Object>> autoRepairFields(String name) {
    return switch (identifier(name)) {
      case "CUSTOMER" -> List.of(id(), field("name", "STRING", true),
          special("email", "EMAIL", true, "UNIQUE"), field("phone", "PHONE", true),
          field("address", "STRING", false));
      case "VEHICLE" -> List.of(id(), reference("customerId", "Customer.id", true),
          special("vin", "STRING", true, "UNIQUE"), field("make", "STRING", true),
          field("model", "STRING", true), special("year", "INTEGER", true, "VALID_MODEL_YEAR"),
          special("licensePlate", "STRING", true, "UNIQUE"));
      case "WORK_ORDER" -> List.of(id(), reference("vehicleId", "Vehicle.id", true),
          reference("customerId", "Customer.id", true),
          reference("assignedTechnicianId", "Technician.id", false),
          enumeration("status", "PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED"),
          field("serviceType", "STRING", true), money("laborAmount", "NON_NEGATIVE"),
          field("lineItems", "LIST<WORK_ORDER_LINE_ITEM>", false),
          money("totalAmount", "DERIVED_PARTS_PLUS_LABOR"));
      case "TECHNICIAN" -> List.of(id(), field("name", "STRING", true),
          field("specialty", "STRING", true), field("available", "BOOLEAN", true));
      case "APPOINTMENT" -> List.of(id(), reference("customerId", "Customer.id", true),
          reference("vehicleId", "Vehicle.id", true), reference("technicianId", "Technician.id", false),
          field("scheduledAt", "DATE_TIME_ISO_8601", true), field("serviceType", "STRING", true),
          enumeration("status", "SCHEDULED", "IN_SERVICE", "COMPLETED", "CANCELLED", "NO_SHOW"));
      case "PART" -> List.of(id(), special("sku", "STRING", true, "UNIQUE"),
          field("name", "STRING", true), money("unitPrice", "NON_NEGATIVE"),
          special("stockQuantity", "INTEGER", true, "NON_NEGATIVE"),
          special("reorderThreshold", "INTEGER", true, "NON_NEGATIVE"));
      case "ESTIMATE" -> List.of(id(), reference("customerId", "Customer.id", true),
          reference("vehicleId", "Vehicle.id", true), field("lineItems", "LIST<ESTIMATE_LINE_ITEM>", false),
          money("laborAmount", "NON_NEGATIVE"), money("estimatedAmount", "DERIVED_PARTS_PLUS_LABOR"),
          enumeration("status", "DRAFT", "APPROVED", "REJECTED", "EXPIRED"));
      case "INVOICE" -> List.of(id(), reference("workOrderId", "Work Order.id", true),
          reference("customerId", "Customer.id", true), money("totalAmount", "NON_NEGATIVE"),
          enumeration("paymentStatus", "UNPAID", "PARTIALLY_PAID", "PAID", "REFUNDED"),
          field("issuedDate", "DATE_ISO_8601", true));
      default -> List.of(id(), field("name", "STRING", true));
    };
  }

  private List<Map<String, Object>> consultingFields(String name) {
    return switch (identifier(name)) {
      case "CLIENT" -> List.of(id(), field("name", "STRING", true), field("organization", "STRING", false),
          field("email", "EMAIL", true), field("phone", "PHONE", false));
      case "CONSULTANT" -> List.of(id(), field("name", "STRING", true), field("email", "EMAIL", true),
          field("specialty", "STRING", false), money("hourlyRate", "NON_NEGATIVE"),
          field("active", "BOOLEAN", true));
      case "ENGAGEMENT" -> List.of(id(), reference("clientId", "Client.id", true),
          reference("leadConsultantId", "Consultant.id", false), field("name", "STRING", true),
          enumeration("status", "PLANNED", "ACTIVE", "ON_HOLD", "COMPLETED", "CANCELLED"),
          field("startDate", "DATE_ISO_8601", true), field("endDate", "DATE_ISO_8601", false),
          money("budget", "NON_NEGATIVE"));
      case "DELIVERABLE" -> List.of(id(), reference("engagementId", "Engagement.id", true),
          reference("assignedConsultantId", "Consultant.id", false), field("title", "STRING", true),
          field("dueDate", "DATE_ISO_8601", false),
          enumeration("status", "PLANNED", "IN_PROGRESS", "SUBMITTED", "ACCEPTED", "CANCELLED"));
      case "TIME_ENTRY" -> List.of(id(), reference("engagementId", "Engagement.id", true),
          reference("consultantId", "Consultant.id", true), field("workDate", "DATE_ISO_8601", true),
          special("hours", "DECIMAL_7_2", true, "POSITIVE"), field("description", "STRING", true),
          field("billable", "BOOLEAN", true));
      case "INVOICE" -> List.of(id(), reference("engagementId", "Engagement.id", true),
          reference("clientId", "Client.id", true), special("invoiceNumber", "STRING", true, "UNIQUE"),
          money("totalAmount", "NON_NEGATIVE"),
          enumeration("paymentStatus", "UNPAID", "PARTIALLY_PAID", "PAID", "REFUNDED"),
          field("issuedDate", "DATE_ISO_8601", true), field("dueDate", "DATE_ISO_8601", true));
      default -> List.of(id(), field("name", "STRING", true));
    };
  }

  private Map<String, Object> workflowRecipe(String name) {
    return object(
        "id", identifier(name),
        "name", name,
        "requires", List.of("SUPPORTING_COMPONENT", "STATE_BINDING", "OPERATION", "ACCEPTANCE_SCENARIO"),
        "resolutionPolicy", "RESOLVE_WITHIN_ACTIVE_PRESET_OR_FAIL");
  }

  private Map<String, Object> crudWorkflow(List<Map<String, Object>> entities) {
    return workflow("RECORD_MANAGEMENT", "Create and maintain records", names(entities), List.of(),
        List.of("CREATE", "READ", "UPDATE", "DELETE"), null,
        "Created and changed records persist and invalid mutations are rejected");
  }

  private Map<String, Object> searchWorkflow(List<Map<String, Object>> entities) {
    return workflow("SEARCH_FILTER_SORT", "Search, filter and sort", names(entities), List.of(),
        List.of("SEARCH", "FILTER", "SORT", "PAGINATE"), null,
        "Search and filters operate on persisted records and return deterministic results");
  }

  private Map<String, Object> scheduleWorkflow(List<Map<String, Object>> entities) {
    Map<String, Object> anchor = requireRole(entities, "Schedule people or resources",
        "SCHEDULE_PRIORITY", "SCHEDULE_ENTRY", "SCHEDULE_SUBJECT", "WORK_RESOURCE");
    addField(anchor, field("scheduledStart", "DATE_TIME_ISO_8601", false));
    addField(anchor, field("scheduledEnd", "DATE_TIME_ISO_8601", false));
    addField(anchor, enumeration("scheduleStatus", "UNSCHEDULED", "SCHEDULED", "COMPLETED", "CANCELLED"));
    return workflow("RESOURCE_SCHEDULING", "Schedule people or resources", List.of(name(anchor)),
        List.of(binding(anchor, "scheduledStart"), binding(anchor, "scheduledEnd"), binding(anchor, "scheduleStatus")),
        List.of("SCHEDULE", "RESCHEDULE", "CANCEL_SCHEDULE"),
        page("Schedule", "/schedule", "RESOURCE_SCHEDULING"),
        "Scheduling changes real state and rejects invalid date ranges",
        List.of(semanticBinding("SUBJECT", anchor, "SCHEDULE_SUBJECT", "SCHEDULE_PRIORITY", "SCHEDULE_ENTRY", "WORK_RESOURCE")));
  }

  private Map<String, Object> assignmentWorkflow(List<Map<String, Object>> entities) {
    Map<String, Object> anchor = requireRole(entities, "Assign and track work",
        "ASSIGNMENT_PRIORITY", "ASSIGNMENT_SUBJECT");
    Map<String, Object> actor = byRole(entities, "WORK_RESOURCE");
    String assignmentField;
    if (actor == null) {
      assignmentField = "assignedTo";
      addField(anchor, field(assignmentField, "STRING", false));
    } else {
      assignmentField = referenceField(anchor, name(actor) + ".id");
      if (assignmentField == null) {
        assignmentField = "assignedToId";
        addField(anchor, reference(assignmentField, name(actor) + ".id", false));
      }
    }
    addField(anchor, enumeration("workStatus", "UNASSIGNED", "ASSIGNED", "IN_PROGRESS", "COMPLETED"));
    List<Map<String, Object>> semantics = new ArrayList<>();
    semantics.add(semanticBinding("SUBJECT", anchor, "ASSIGNMENT_SUBJECT", "ASSIGNMENT_PRIORITY"));
    if (actor != null) semantics.add(semanticBinding("ASSIGNEE", actor, "WORK_RESOURCE"));
    return workflow("WORK_ASSIGNMENT", "Assign and track work", List.of(name(anchor)),
        List.of(binding(anchor, assignmentField), binding(anchor, "workStatus")),
        List.of("ASSIGN", "REASSIGN", "CHANGE_WORK_STATUS"),
        page("Assignments", "/assignments", "WORK_ASSIGNMENT"),
        "Assignment and work status changes persist and remain traceable", semantics);
  }

  private Map<String, Object> approvalWorkflow(List<Map<String, Object>> entities) {
    Map<String, Object> anchor = requireRole(entities, "Approve or reject requests",
        "APPROVAL_SUBJECT", "ASSIGNMENT_SUBJECT");
    addField(anchor, enumeration("approvalStatus", "PENDING", "APPROVED", "REJECTED"));
    addField(anchor, field("approvalComment", "STRING", false));
    return workflow("APPROVAL", "Approve or reject requests", List.of(name(anchor)),
        List.of(binding(anchor, "approvalStatus"), binding(anchor, "approvalComment")),
        List.of("APPROVE", "REJECT"), page("Approvals", "/approvals", "APPROVAL"),
        "Approval transitions are persisted, audited, and restricted to valid prior states",
        List.of(semanticBinding("SUBJECT", anchor, "APPROVAL_SUBJECT", "ASSIGNMENT_SUBJECT")));
  }

  private Map<String, Object> inventoryWorkflow(List<Map<String, Object>> entities) {
    Map<String, Object> stock = byRole(entities, "INVENTORY_SUBJECT");
    if (stock != null) {
      addField(stock, special("stockQuantity", "INTEGER", true, "NON_NEGATIVE"));
      addField(stock, special("reorderThreshold", "INTEGER", false, "NON_NEGATIVE"));
      return workflow("INVENTORY_TRACKING", "Manage inventory or availability", List.of(name(stock)),
          List.of(binding(stock, "stockQuantity"), binding(stock, "reorderThreshold")),
          List.of("ADJUST_STOCK", "VIEW_LOW_STOCK"),
          page("Inventory", "/inventory", "INVENTORY_TRACKING"),
          "Inventory adjustments persist and stock cannot become negative",
          List.of(semanticBinding("SUBJECT", stock, "INVENTORY_SUBJECT")));
    }
    Map<String, Object> resource = requireRole(entities, "Manage inventory or availability",
        "AVAILABILITY_RESOURCE", "WORK_RESOURCE");
    addField(resource, field("available", "BOOLEAN", true));
    addField(resource, special("capacityPerDay", "INTEGER", false, "NON_NEGATIVE"));
    return workflow("RESOURCE_AVAILABILITY", "Manage inventory or availability", List.of(name(resource)),
        List.of(binding(resource, "available"), binding(resource, "capacityPerDay")),
        List.of("SET_AVAILABILITY", "VIEW_CAPACITY"),
        page("Availability", "/availability", "RESOURCE_AVAILABILITY"),
        "Availability and capacity changes persist and influence scheduling choices",
        List.of(semanticBinding("SUBJECT", resource, "AVAILABILITY_RESOURCE", "WORK_RESOURCE")));
  }

  private Map<String, Object> reportingWorkflow(List<Map<String, Object>> entities) {
    return workflow("REPORTING", "Generate reports", names(entities),
        List.of(object("entity", "ALL_ACTIVE_ENTITIES", "field", "PERSISTED_RECORDS")),
        List.of("GENERATE_REPORT", "FILTER_REPORT"), page("Reports", "/reports", "REPORTING"),
        "Reports are calculated from current persisted records rather than hard-coded totals");
  }

  private Map<String, Object> notificationWorkflow(
      Map<String, Object> preset, List<Map<String, Object>> entities) {
    Map<String, Object> notification = find(entities, "Notification");
    if (notification == null) {
      notification = object(
          "name", "Notification", "identifier", "NOTIFICATION", "scopeKey", preset.get("id"),
          "componentId", String.valueOf(preset.get("id")).toUpperCase(Locale.ROOT).replace('-', '_') + "__NOTIFICATION",
          "systemManaged", true,
          "fields", new ArrayList<>(List.of(id(), field("subjectType", "STRING", true),
              field("subjectId", "STRING", true), field("message", "STRING", true),
              enumeration("status", "PENDING", "SENT", "FAILED"),
              field("createdAt", "DATE_TIME_ISO_8601", true))),
          "operations", List.of("CREATE", "READ", "LIST", "SEARCH", "FILTER", "PAGINATE"),
          "provenance", object("presetId", preset.get("id"), "definition", "SHARED_NOTIFICATION_RECIPE"));
      entities.add(notification);
    }
    return workflow("NOTIFICATIONS", "Send notifications", List.of("Notification"),
        List.of(binding(notification, "status"), binding(notification, "message")),
        List.of("QUEUE_NOTIFICATION", "MARK_SENT", "MARK_FAILED"),
        page("Notifications", "/notifications", "NOTIFICATIONS"),
        "Notification requests and delivery outcomes are stored as real state");
  }

  private Map<String, Object> workflow(
      String id, String name, List<String> components, List<Map<String, Object>> bindings,
      List<String> operations, Map<String, Object> page, String acceptance) {
    return workflow(id, name, components, bindings, operations, page, acceptance, List.of());
  }

  private Map<String, Object> workflow(
      String id, String name, List<String> components, List<Map<String, Object>> bindings,
      List<String> operations, Map<String, Object> page, String acceptance,
      List<Map<String, Object>> semanticBindings) {
    Map<String, Object> output = object(
        "id", id, "name", name, "status", "RESOLVED",
        "supportingComponents", components, "stateBindings", bindings,
        "operations", operations, "acceptanceScenario", acceptance,
        "semanticBindings", semanticBindings);
    if (page != null) output.put("page", page);
    return output;
  }

  private Map<String, Object> entityFrom(Map<String, Object> component) {
    return object(
        "name", component.get("name"), "identifier", component.get("identifier"),
        "scopeKey", component.get("scopeKey"), "componentId", component.get("id"),
        "semanticRoles", component.get("semanticRoles"),
        "fields", deepMaps(component.get("fields")),
        "operations", new ArrayList<>(strings(component.get("capabilities"))),
        "provenance", component.get("provenance"));
  }

  private static Map<String, Object> requireRole(
      List<Map<String, Object>> entities, String workflow, String... preferredRoles) {
    for (String role : preferredRoles) {
      Map<String, Object> found = byRole(entities, role);
      if (found != null) return found;
    }
    throw new IllegalStateException(
        "Workflow " + workflow + " has no component with a compatible semantic role");
  }

  private static Map<String, Object> byRole(List<Map<String, Object>> entities, String role) {
    for (Map<String, Object> entity : entities) {
      if (strings(entity.get("semanticRoles")).contains(role)) return entity;
    }
    return null;
  }

  private static Map<String, Object> semanticBinding(
      String usage, Map<String, Object> entity, String... acceptedRoles) {
    return object("usage", usage, "entity", name(entity), "acceptedRoles", List.of(acceptedRoles));
  }

  private static Map<String, Object> find(List<Map<String, Object>> entities, String name) {
    for (Map<String, Object> entity : entities) if (name.equals(entity.get("name"))) return entity;
    return null;
  }

  private static boolean containsAny(String value, String... fragments) {
    for (String fragment : fragments) if (value.contains(fragment)) return true;
    return false;
  }

  private static void addField(Map<String, Object> entity, Map<String, Object> field) {
    List<Map<String, Object>> fields = maps(entity.get("fields"));
    for (Map<String, Object> existing : fields) if (existing.get("name").equals(field.get("name"))) return;
    fields.add(field);
    entity.put("fields", fields);
  }

  private static String referenceField(Map<String, Object> entity, String target) {
    for (Map<String, Object> field : maps(entity.get("fields"))) {
      if (target.equals(field.get("references"))) return String.valueOf(field.get("name"));
    }
    return null;
  }

  private static String name(Map<String, Object> entity) {
    return String.valueOf(entity.get("name"));
  }

  private static List<String> names(List<Map<String, Object>> entities) {
    List<String> result = new ArrayList<>();
    for (Map<String, Object> entity : entities) result.add(name(entity));
    return result;
  }

  private static Map<String, Object> binding(Map<String, Object> entity, String field) {
    return object("entity", name(entity), "field", field);
  }

  private static Map<String, Object> page(String label, String path, String target) {
    return object("label", label, "path", path, "target", target, "viewType", "WORKFLOW_VIEW");
  }

  private static Map<String, Object> id() {
    Map<String, Object> result = special("id", "STRING", true, "GENERATED_STABLE_ID");
    result.put("primaryKey", true);
    return result;
  }

  private static Map<String, Object> field(String name, String type, boolean required) {
    return object("name", name, "type", type, "required", required);
  }

  private static Map<String, Object> special(String name, String type, boolean required, String constraint) {
    Map<String, Object> result = field(name, type, required);
    result.put("constraint", constraint);
    return result;
  }

  private static Map<String, Object> reference(String name, String target, boolean required) {
    Map<String, Object> result = field(name, "STRING", required);
    result.put("references", target);
    return result;
  }

  private static Map<String, Object> money(String name, String constraint) {
    return special(name, "DECIMAL_19_2", true, constraint);
  }

  private static Map<String, Object> enumeration(String name, String... values) {
    Map<String, Object> result = field(name, "ENUM", true);
    result.put("values", List.of(values));
    return result;
  }

  private static String identifier(String value) {
    return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : new LinkedHashMap<>();
  }

  private static List<Map<String, Object>> maps(Object value) {
    if (!(value instanceof Iterable<?> items)) return new ArrayList<>();
    List<Map<String, Object>> output = new ArrayList<>();
    for (Object item : items) output.add(map(item));
    return output;
  }

  private static List<Map<String, Object>> deepMaps(Object value) {
    List<Map<String, Object>> output = new ArrayList<>();
    for (Map<String, Object> item : maps(value)) {
      Map<String, Object> copy = new LinkedHashMap<>(item);
      if (item.get("values") instanceof Iterable<?>) copy.put("values", new ArrayList<>(strings(item.get("values"))));
      output.add(copy);
    }
    return output;
  }

  private static List<String> strings(Object value) {
    if (!(value instanceof Iterable<?> items)) return List.of();
    List<String> output = new ArrayList<>();
    for (Object item : items) output.add(String.valueOf(item));
    return output;
  }

  private static Map<String, Object> object(Object... entries) {
    Map<String, Object> output = new LinkedHashMap<>();
    for (int index = 0; index < entries.length; index += 2) {
      output.put(String.valueOf(entries[index]), entries[index + 1]);
    }
    return output;
  }
}
