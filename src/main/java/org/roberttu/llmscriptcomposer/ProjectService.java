package org.roberttu.llmscriptcomposer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
/**
 * Application service for questionnaire projects, script revisions, and experiment records.
 * It validates preset references and keeps generated revisions immutable.
 */
public final class ProjectService {
  private final ProjectRepository repository;
  private final PresetCatalog presets;
  private final SpecificationCompiler compiler=new SpecificationCompiler();
  /** Creates the service with its persistence implementation and preset catalog.
   * @param repository persistence implementation
   * @param presets questionnaire preset catalog
   */
  public ProjectService(ProjectRepository repository,PresetCatalog presets) {
    this.repository=repository;
    this.presets=presets;
  }
  /** Validates and saves a new or existing questionnaire project.
   * @param input submitted project record
   * @return persisted project
   */
  public Map<String,Object> saveProject(Map<String,Object> input) {
    Map<String,Object> p=new LinkedHashMap<>(input);
    String id=text(p.get("id"));
    if(id.isBlank())id=id();
    p.put("id",id);
    p.putIfAbsent("createdAt",Instant.now().toString());
    p.put("updatedAt",Instant.now().toString());
    p.putIfAbsent("targetProfile","PORTABLE");
    String targetProfile=text(p.get("targetProfile")).toUpperCase(Locale.ROOT);
    if(!SpecificationCompiler.TARGET_PROFILES.contains(targetProfile)) {
      throw new IllegalArgumentException("Unknown target profile: "+targetProfile);
    }
    p.put("targetProfile",targetProfile);
    presets.get(text(p.get("presetId")));
    return repository.saveProject(p);
  }
  /** Lists saved projects.
   * @return projects in display order
   */
  public List<Map<String,Object>> projects() {
    return repository.projects();
  }
  /** Loads one project or reports that it does not exist.
   * @param id project identifier
   * @return matching project
   */
  public Map<String,Object> project(String id) {
    return repository.project(id).orElseThrow(()->new NoSuchElementException("Project not found"));
  }
  /** Deletes a project and all of its dependent history.
   * @param id project identifier
   */
  public void deleteProject(String id) {
    if(!repository.deleteProject(id))throw new NoSuchElementException("Project not found");
  }
  /** Generates and persists the next immutable compressed-script revision.
   * @param projectId owning project identifier
   * @return complete saved revision
   */
  public Map<String,Object> generate(String projectId) {
    Map<String,Object> project=project(projectId);
    Map<String,Object> spec=compiler.buildSpecification(project,presets.get(text(project.get("presetId"))));
    String source=Json.stringify(spec);
    String targetProfile=text(project.get("targetProfile"));
    if(targetProfile.isBlank())targetProfile="PORTABLE";
    byte[] compressed=compiler.emit(spec,targetProfile);
    List<Map<String,Object>> old=repository.revisions(projectId);
    Map<String,Object> r=new LinkedHashMap<>();
    r.put("id",id());
    r.put("projectId",projectId);
    r.put("number",old.size()+1);
    r.put("createdAt",Instant.now().toString());
    r.put("sourceSha256",sha(source.getBytes(StandardCharsets.UTF_8)));
    r.put("sourceBytes",source.getBytes(StandardCharsets.UTF_8).length);
    r.put("compressedBytes",compressed.length);
    r.put("targetProfile",targetProfile);
    r.put("scriptFormat",("GEMINI".equals(targetProfile)||"DEEPSEEK".equals(targetProfile))
        ?"EXPLICIT_THREE_LAYER":"COMPRESSED_HIERARCHICAL");
    r.put("specification",spec);
    r.put("compressedScript",new String(compressed,StandardCharsets.UTF_8));
    return repository.saveRevision(r);
  }
  /** Lists revision metadata without returning the large script payloads.
   * @param id owning project identifier
   * @return lightweight revision summaries
   */
  public List<Map<String,Object>> revisions(String id) {
    return repository.revisions(id).stream().map(this::revisionSummary).toList();
  }
  /** Loads a complete revision including its downloadable script.
   * @param id revision identifier
   * @return complete revision
   */
  public Map<String,Object> revision(String id) {
    return repository.revision(id).orElseThrow(()->new NoSuchElementException("Revision not found"));
  }
  /** Records a developer's external LLM experiment for a project revision.
   * @param projectId owning project identifier
   * @param input submitted experiment fields
   * @return saved experiment
   */
  public Map<String,Object> saveExperiment(String projectId,Map<String,Object> input) {
    project(projectId);
    Map<String,Object> e=new LinkedHashMap<>(input);
    e.put("id",id());
    e.put("projectId",projectId);
    e.put("createdAt",Instant.now().toString());
    e.putIfAbsent("status","PLANNED");
    return repository.saveExperiment(e);
  }
  /** Lists the experiment history for a project.
   * @param id owning project identifier
   * @return matching experiments
   */
  public List<Map<String,Object>> experiments(String id) {
    return repository.experiments(id);
  }
  private Map<String,Object> revisionSummary(Map<String,Object> r) {
    Map<String,Object> o=new LinkedHashMap<>(r);
    o.remove("specification");
    o.remove("compressedScript");
    return o;
  }
  private static String id() {
    return UUID.randomUUID().toString();
  }
  private static String text(Object v) {
    return v==null?"":String.valueOf(v);
  }
  private static String sha(byte[]b) {
    try {
      StringBuilder s=new StringBuilder();
      for(byte x:MessageDigest.getInstance("SHA-256").digest(b))s.append(String.format("%02x",x));
      return s.toString();
    }
    catch(Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
