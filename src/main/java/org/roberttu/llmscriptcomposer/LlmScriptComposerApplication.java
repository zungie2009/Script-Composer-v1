package org.roberttu.llmscriptcomposer;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
/**
 * Application entry point for the LLM Script Composer web application.
 * It selects the configured repository, exposes the JSON API, and serves static assets.
 * In Spring Tool Suite (STS) or Eclipse, run this class using
 * {@code Run As > Java Application}.
 */
public final class LlmScriptComposerApplication {
  private final PresetCatalog presets=new PresetCatalog();
  private final ProjectService service;
  private LlmScriptComposerApplication(ProjectRepository repository) {
    service=new ProjectService(repository,presets);
  }
  /** Starts the web application using values from {@code composer.properties}.
   * @param args command-line arguments; currently unused
   * @throws Exception when configuration, repository, or HTTP startup fails
   */
  public static void main(String[]args)throws Exception {
    Properties p=new Properties();
    Path config=Path.of(System.getProperty("composer.config","composer.properties"));
    if(java.nio.file.Files.exists(config))try(InputStream in=java.nio.file.Files.newInputStream(config)) {
      p.load(in);
    }
    String mode=p.getProperty("storage.mode","FILE").toUpperCase(Locale.ROOT);
    ProjectRepository repo="DATABASE".equals(mode)?new JdbcProjectRepository(p.getProperty("storage.jdbc.driver","org.h2.Driver"),p.getProperty("storage.jdbc.url","jdbc:h2:file:./data/composer"),p.getProperty("storage.jdbc.user","sa"),p.getProperty("storage.jdbc.password","")):new FileProjectRepository(Path.of(p.getProperty("storage.file.directory","./data/projects")));
    String host=p.getProperty("server.host","127.0.0.1");
    int port=Integer.parseInt(p.getProperty("server.port","8090"));
    HttpServer server=HttpServer.create(new InetSocketAddress(host,port),0);
    LlmScriptComposerApplication app=new LlmScriptComposerApplication(repo);
    server.createContext("/",app::handle);
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
    server.start();
    System.out.println("LLM Script Composer started at http://"+host+":"+port+" using "+mode+" storage");
    new java.util.concurrent.CountDownLatch(1).await();
  }
  private void handle(HttpExchange x) {
    try {
      String path=x.getRequestURI().getPath(),method=x.getRequestMethod();
      if(path.equals("/api/presets")&&method.equals("GET")) {
        json(x,200,presets.all());
        return;
      }
      if(path.equals("/api/projects")&&method.equals("GET")) {
        json(x,200,service.projects());
        return;
      }
      if(path.equals("/api/projects")&&method.equals("POST")) {
        json(x,200,service.saveProject(body(x)));
        return;
      }
      String[] s=path.split("/");
      if(s.length>=4&&s[1].equals("api")&&s[2].equals("projects")) {
        String id=s[3];
        if(s.length==4&&method.equals("GET")) {
          json(x,200,service.project(id));
          return;
        }
        if(s.length==4&&method.equals("DELETE")) {
          service.deleteProject(id);
          json(x,200,Map.of("deleted",true,"projectId",id));
          return;
        }
        if(s.length==5&&s[4].equals("generate")&&method.equals("POST")) {
          json(x,201,service.generate(id));
          return;
        }
        if(s.length==5&&s[4].equals("revisions")&&method.equals("GET")) {
          json(x,200,service.revisions(id));
          return;
        }
        if(s.length==5&&s[4].equals("experiments")&&method.equals("GET")) {
          json(x,200,service.experiments(id));
          return;
        }
        if(s.length==5&&s[4].equals("experiments")&&method.equals("POST")) {
          json(x,201,service.saveExperiment(id,body(x)));
          return;
        }
      }
      if(s.length==4&&s[1].equals("api")&&s[2].equals("revisions")&&method.equals("GET")) {
        json(x,200,service.revision(s[3]));
        return;
      }
      if(s.length==5&&s[1].equals("api")&&s[2].equals("revisions")&&s[4].equals("download")&&method.equals("GET")) {
        Map<String,Object>r=service.revision(s[3]);
        download(x,String.valueOf(r.get("compressedScript")),"llm-script-revision-"+r.get("number")+".jsonc");
        return;
      }
      staticFile(x,path);
    }
    catch(NoSuchElementException e) {
      error(x,404,e.getMessage());
    }
    catch(IllegalArgumentException e) {
      error(x,400,e.getMessage());
    }
    catch(Exception e) {
      e.printStackTrace();
      error(x,500,e.getMessage()==null?"Unexpected error":e.getMessage());
    }
  }
  @SuppressWarnings("unchecked")private Map<String,Object> body(HttpExchange x)throws IOException {
    String text=new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
    Object v=Json.parse(text);
    if(!(v instanceof Map<?,?>))throw new IllegalArgumentException("JSON object required");
    return(Map<String,Object>)v;
  }
  private void staticFile(HttpExchange x,String path)throws IOException {
    if(path.equals("/"))path="/index.html";
    if(path.contains("..")) {
      error(x,400,"Invalid path");
      return;
    }
    try(InputStream in=LlmScriptComposerApplication.class.getResourceAsStream("/static"+path)) {
      if(in==null) {
        error(x,404,"Not found");
        return;
      }
      byte[]b=in.readAllBytes();
      String type=path.endsWith(".html")?"text/html; charset=utf-8":path.endsWith(".css")?"text/css; charset=utf-8":path.endsWith(".js")?"application/javascript; charset=utf-8":"application/octet-stream";
      send(x,200,type,b);
    }
  }
  private void json(HttpExchange x,int status,Object value)throws IOException {
    send(x,status,"application/json; charset=utf-8",Json.stringify(value).getBytes(StandardCharsets.UTF_8));
  }
  private void error(HttpExchange x,int status,String message) {
    try {
      json(x,status,Map.of("error",message));
    }
    catch(Exception ignored) {
    }
  }
  private void download(HttpExchange x,String text,String name)throws IOException {
    x.getResponseHeaders().set("Content-Disposition","attachment; filename=\""+name+"\"");
    send(x,200,"application/json; charset=utf-8",text.getBytes(StandardCharsets.UTF_8));
  }
  private void send(HttpExchange x,int status,String type,byte[]body)throws IOException {
    x.getResponseHeaders().set("Content-Type",type);
    x.getResponseHeaders().set("Cache-Control","no-store");
    x.sendResponseHeaders(status,body.length);
    try(OutputStream out=x.getResponseBody()) {
      out.write(body);
    }
  }
}
