package org.roberttu.llmscriptcomposer;
import java.sql.*;
import java.util.*;
/**
 * JDBC repository that stores portable JSON payloads in a small relational table.
 * The default statements use H2-compatible SQL and keep project deletion transactional.
 */
public final class JdbcProjectRepository implements ProjectRepository {
  private final String url,user,password;
  /** Creates the repository, loads the configured driver, and initializes its schema.
   * @param driver JDBC driver class name
   * @param url JDBC connection URL
   * @param user database user
   * @param password database password
   */
  public JdbcProjectRepository(String driver,String url,String user,String password) {
    try {
      if(driver!=null&&!driver.isBlank())Class.forName(driver);
      this.url=url;
      this.user=user;
      this.password=password;
      schema();
    }
    catch(Exception e) {
      throw new IllegalStateException("JDBC storage initialization failed. Add the JDBC driver to the classpath and verify configuration.",e);
    }
  }
  private Connection connection()throws SQLException {
    return DriverManager.getConnection(url,user,password);
  }
  private void schema()throws SQLException {
    try(Connection c=connection();Statement s=c.createStatement()) {
      s.executeUpdate("CREATE TABLE IF NOT EXISTS lsc_record (record_type VARCHAR(32) NOT NULL, record_id VARCHAR(80) PRIMARY KEY, project_id VARCHAR(80), created_at VARCHAR(50), payload CLOB NOT NULL)");
    }
  }
  /** {@inheritDoc} */
  public Map<String,Object> saveProject(Map<String,Object> p) {
    return save("PROJECT",p);
  }
  /** {@inheritDoc} */
  public Optional<Map<String,Object>> project(String id) {
    return one("PROJECT",id);
  }
  /** {@inheritDoc} */
  public List<Map<String,Object>> projects() {
    return list("PROJECT",null);
  }
  /** {@inheritDoc} */
  public boolean deleteProject(String id) {
    try(Connection c=connection()) {
      c.setAutoCommit(false);
      try(PreparedStatement children=c.prepareStatement("DELETE FROM lsc_record WHERE project_id=? AND record_type IN ('REVISION','EXPERIMENT')");PreparedStatement project=c.prepareStatement("DELETE FROM lsc_record WHERE record_type='PROJECT' AND record_id=?")) {
        children.setString(1,id);
        children.executeUpdate();
        project.setString(1,id);
        boolean deleted=project.executeUpdate()>0;
        c.commit();
        return deleted;
      }
      catch(Exception e) {
        c.rollback();
        throw e;
      }
    }
    catch(Exception e) {
      throw new IllegalStateException("JDBC project deletion failed",e);
    }
  }
  /** {@inheritDoc} */
  public Map<String,Object> saveRevision(Map<String,Object> r) {
    return save("REVISION",r);
  }
  /** {@inheritDoc} */
  public List<Map<String,Object>> revisions(String id) {
    return list("REVISION",id);
  }
  /** {@inheritDoc} */
  public Optional<Map<String,Object>> revision(String id) {
    return one("REVISION",id);
  }
  /** {@inheritDoc} */
  public Map<String,Object> saveExperiment(Map<String,Object> e) {
    return save("EXPERIMENT",e);
  }
  /** {@inheritDoc} */
  public List<Map<String,Object>> experiments(String id) {
    return list("EXPERIMENT",id);
  }
  private Map<String,Object> save(String type,Map<String,Object> item) {
    String sql="MERGE INTO lsc_record (record_type,record_id,project_id,created_at,payload) KEY(record_id) VALUES (?,?,?,?,?)";
    try(Connection c=connection();PreparedStatement p=c.prepareStatement(sql)) {
      p.setString(1,type);
      p.setString(2,String.valueOf(item.get("id")));
      p.setString(3,String.valueOf(item.getOrDefault("projectId",item.get("id"))));
      p.setString(4,String.valueOf(item.getOrDefault("createdAt","")));
      p.setString(5,Json.stringify(item));
      p.executeUpdate();
      return item;
    }
    catch(SQLException e) {
      throw new IllegalStateException("JDBC save failed; this implementation expects H2-compatible MERGE syntax",e);
    }
  }
  @SuppressWarnings("unchecked")private Optional<Map<String,Object>> one(String type,String id) {
    try(Connection c=connection();PreparedStatement p=c.prepareStatement("SELECT payload FROM lsc_record WHERE record_type=? AND record_id=?")) {
      p.setString(1,type);
      p.setString(2,id);
      try(ResultSet r=p.executeQuery()) {
        return r.next()?Optional.of((Map<String,Object>)Json.parse(r.getString(1))):Optional.empty();
      }
    }
    catch(SQLException e) {
      throw new IllegalStateException(e);
    }
  }
  @SuppressWarnings("unchecked")private List<Map<String,Object>> list(String type,String projectId) {
    String sql="SELECT payload FROM lsc_record WHERE record_type=?"+(projectId==null?"":" AND project_id=?")+" ORDER BY created_at DESC";
    List<Map<String,Object>> out=new ArrayList<>();
    try(Connection c=connection();PreparedStatement p=c.prepareStatement(sql)) {
      p.setString(1,type);
      if(projectId!=null)p.setString(2,projectId);
      try(ResultSet r=p.executeQuery()) {
        while(r.next())out.add((Map<String,Object>)Json.parse(r.getString(1)));
      }
      return out;
    }
    catch(SQLException e) {
      throw new IllegalStateException(e);
    }
  }
}
