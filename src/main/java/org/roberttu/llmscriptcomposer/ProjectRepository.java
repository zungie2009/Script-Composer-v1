package org.roberttu.llmscriptcomposer;
import java.util.*;
/**
 * Persistence contract for projects, immutable script revisions, and LLM experiments.
 * Implementations may store the same public records in files or through JDBC.
 */
public interface ProjectRepository  {
  /** Saves a new or existing questionnaire project.
   * @param project complete project record
   * @return saved project record
   */
  Map<String,Object> saveProject(Map<String,Object> project);
  /** Finds one project by its stable identifier.
   * @param id project identifier
   * @return matching project, when present
   */
  Optional<Map<String,Object>> project(String id);
  /** Lists every saved project in display order.
   * @return saved projects
   */
  List<Map<String,Object>> projects();
  /** Deletes a project and all revisions and experiments that belong to it.
   * @param id project identifier
   * @return {@code true} when a project was deleted
   */
  boolean deleteProject(String id);
  /** Saves an immutable generated-script revision.
   * @param revision complete revision record
   * @return saved revision record
   */
  Map<String,Object> saveRevision(Map<String,Object> revision);
  /** Lists generated revisions belonging to a project.
   * @param projectId owning project identifier
   * @return matching revisions
   */
  List<Map<String,Object>> revisions(String projectId);
  /** Finds one generated revision by its stable identifier.
   * @param id revision identifier
   * @return matching revision, when present
   */
  Optional<Map<String,Object>> revision(String id);
  /** Saves a manually recorded external LLM experiment.
   * @param experiment complete experiment record
   * @return saved experiment record
   */
  Map<String,Object> saveExperiment(Map<String,Object> experiment);
  /** Lists external LLM experiments belonging to a project.
   * @param projectId owning project identifier
   * @return matching experiments
   */
  List<Map<String,Object>> experiments(String projectId);
}
