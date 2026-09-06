package org.roberttu.llmscriptcomposer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
/**
 * Stores each public record as an individual UTF-8 JSON file.
 * This is the dependency-free default repository for local evaluation.
 */
public final class FileProjectRepository implements ProjectRepository {
  private final Path root;
  /** Creates a repository rooted at the supplied directory.
   * @param root directory that owns all repository files
   */
  public FileProjectRepository(Path root) {
    this.root=root;
    try {
      Files.createDirectories(root);
    }
    catch(Exception e) {
      throw new IllegalStateException(e);
    }
  }
  /** {@inheritDoc} */
  public Map<String,Object> saveProject(Map<String,Object> p) {
    return save("projects",p);
  }
  /** {@inheritDoc} */
  public Optional<Map<String,Object>> project(String id) {
    return read("projects",id);
  }
  /** {@inheritDoc} */
  public List<Map<String,Object>> projects() {
    return all("projects",null);
  }
  /** {@inheritDoc} */
  public boolean deleteProject(String id) {
    boolean existed=project(id).isPresent();
    try {
      Files.deleteIfExists(root.resolve("projects").resolve(id+".json"));
      deleteChildren("revisions",id);
      deleteChildren("experiments",id);
      return existed;
    }
    catch(Exception e) {
      throw new IllegalStateException("Cannot delete project",e);
    }
  }
  /** {@inheritDoc} */
  public Map<String,Object> saveRevision(Map<String,Object> r) {
    return save("revisions",r);
  }
  /** {@inheritDoc} */
  public List<Map<String,Object>> revisions(String id) {
    return all("revisions",id);
  }
  /** {@inheritDoc} */
  public Optional<Map<String,Object>> revision(String id) {
    return read("revisions",id);
  }
  /** {@inheritDoc} */
  public Map<String,Object> saveExperiment(Map<String,Object> e) {
    return save("experiments",e);
  }
  /** {@inheritDoc} */
  public List<Map<String,Object>> experiments(String id) {
    return all("experiments",id);
  }
  private Map<String,Object> save(String type,Map<String,Object> item) {
    try {
      Path d=root.resolve(type);
      Files.createDirectories(d);
      Files.writeString(d.resolve(item.get("id")+".json"),Json.stringify(item),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
      return item;
    }
    catch(Exception e) {
      throw new IllegalStateException("Cannot save "+type,e);
    }
  }
  @SuppressWarnings("unchecked")private Optional<Map<String,Object>> read(String type,String id) {
    try {
      Path f=root.resolve(type).resolve(id+".json");
      return Files.exists(f)?Optional.of((Map<String,Object>)Json.parse(Files.readString(f))):Optional.empty();
    }
    catch(Exception e) {
      throw new IllegalStateException(e);
    }
  }
  @SuppressWarnings("unchecked")private List<Map<String,Object>> all(String type,String projectId) {
    List<Map<String,Object>> out=new ArrayList<>();
    Path d=root.resolve(type);
    if(!Files.isDirectory(d))return out;
    try(var files=Files.list(d)) {
      files.filter(f->f.toString().endsWith(".json")).forEach(f-> {
        try {
          Map<String,Object> m=(Map<String,Object>)Json.parse(Files.readString(f));if(projectId==null||projectId.equals(m.get("projectId")))out.add(m);
        }
        catch(Exception e) {
          throw new IllegalStateException(e);
        }
      }
      );
    }
    catch(Exception e) {
      throw new IllegalStateException(e);
    }
    out.sort(Comparator.comparing((Map<String,Object> x)->String.valueOf(x.getOrDefault("createdAt",""))).reversed());
    return out;
  }
  private void deleteChildren(String type,String projectId)throws Exception {
    Path d=root.resolve(type);
    if(!Files.isDirectory(d))return;
    for(Map<String,Object> item:all(type,projectId))Files.deleteIfExists(d.resolve(item.get("id")+".json"));
  }
}
