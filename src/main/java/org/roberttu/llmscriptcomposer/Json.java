package org.roberttu.llmscriptcomposer;
import java.util.*;
/**
 * Small dependency-free JSON reader and writer used by the standalone server.
 * Parsed objects retain insertion order through {@link LinkedHashMap}.
 */
public final class Json  {
  private Json() {
  }
  /** Parses one complete JSON document.
   * @param text JSON source text
   * @return parsed value tree
   */
  public static Object parse(String text) {
    return new Parser(text).parse();
  }
  /** Serializes supported Java values as compact JSON.
   * @param value value tree to serialize
   * @return compact JSON text
   */
  public static String stringify(Object value) {
    StringBuilder out=new StringBuilder();
    write(value,out);
    return out.toString();
  }
  private static void write(Object v,StringBuilder b) {
    if(v==null) {
      b.append("null");
      return;
    }
    if(v instanceof String s) {
      b.append('"');
      for(char c:s.toCharArray()) {
        switch(c) {
          case '"'->b.append("\\\"");
          case '\\'->b.append("\\\\");
          case '\n'->b.append("\\n");
          case '\r'->b.append("\\r");
          case '\t'->b.append("\\t");
          default-> {
            if(c<32)b.append(String.format("\\u%04x",(int)c));
            else b.append(c);
          }
        }
      }
      b.append('"');
      return;
    }
    if(v instanceof Number||v instanceof Boolean) {
      b.append(v);
      return;
    }
    if(v instanceof Map<?,?> m) {
      b.append('{');
      int i=0;
      for(var e:m.entrySet()) {
        if(i++>0)b.append(',');
        write(String.valueOf(e.getKey()),b);
        b.append(':');
        write(e.getValue(),b);
      }
      b.append('}');
      return;
    }
    if(v instanceof Iterable<?> a) {
      b.append('[');
      int i=0;
      for(Object x:a) {
        if(i++>0)b.append(',');
        write(x,b);
      }
      b.append(']');
      return;
    }
    write(String.valueOf(v),b);
  }
  private static final class Parser {
    private final String s;
    private int p;
    Parser(String s) {
      this.s=s;
    }
    Object parse() {
      Object v=value();
      ws();
      if(p!=s.length())throw new IllegalArgumentException("Unexpected JSON at "+p);
      return v;
    }
    private Object value() {
      ws();
      if(p>=s.length())throw new IllegalArgumentException("Unexpected end of JSON");
      char c=s.charAt(p);
      if(c=='{')return object();
      if(c=='[')return array();
      if(c=='"')return string();
      if(s.startsWith("true",p)) {
        p+=4;
        return true;
      }
      if(s.startsWith("false",p)) {
        p+=5;
        return false;
      }
      if(s.startsWith("null",p)) {
        p+=4;
        return null;
      }
      return number();
    }
    private Map<String,Object> object() {
      p++;
      Map<String,Object> m=new LinkedHashMap<>();
      ws();
      if(take('}'))return m;
      do {
        ws();
        String k=string();
        ws();
        need(':');
        m.put(k,value());
        ws();
      }
      while(take(','));
      need('}');
      return m;
    }
    private List<Object> array() {
      p++;
      List<Object>a=new ArrayList<>();
      ws();
      if(take(']'))return a;
      do {
        a.add(value());
        ws();
      }
      while(take(','));
      need(']');
      return a;
    }
    private String string() {
      need('"');
      StringBuilder b=new StringBuilder();
      while(p<s.length()) {
        char c=s.charAt(p++);
        if(c=='"')return b.toString();
        if(c=='\\') {
          char e=s.charAt(p++);
          switch(e) {
            case '"','\\','/'->b.append(e);
            case 'b'->b.append('\b');
            case 'f'->b.append('\f');
            case 'n'->b.append('\n');
            case 'r'->b.append('\r');
            case 't'->b.append('\t');
            case 'u'-> {
              b.append((char)Integer.parseInt(s.substring(p,p+4),16));
              p+=4;
            }
            default->throw new IllegalArgumentException("Bad escape");
          }
        }
        else b.append(c);
      }
      throw new IllegalArgumentException("Unclosed string");
    }
    private Number number() {
      int a=p;
      while(p<s.length()&&"-+0123456789.eE".indexOf(s.charAt(p))>=0)p++;
      String n=s.substring(a,p);
      if(n.contains(".")||n.contains("e")||n.contains("E"))return Double.valueOf(n);
      return Long.valueOf(n);
    }
    private void ws() {
      while(p<s.length()&&Character.isWhitespace(s.charAt(p)))p++;
    }
    private boolean take(char c) {
      if(p<s.length()&&s.charAt(p)==c) {
        p++;
        return true;
      }
      return false;
    }
    private void need(char c) {
      if(!take(c))throw new IllegalArgumentException("Expected "+c+" at "+p);
    }
  }
}
