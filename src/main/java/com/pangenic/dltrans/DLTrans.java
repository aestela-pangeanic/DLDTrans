/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.pangenic.dltrans;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.nio.charset.StandardCharsets;
import org.apache.http.HttpEntity;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
//import org.codehaus.jettison.json.JSONObject;


import java.util.List;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.apache.commons.io.Charsets;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author A
 */
public class DLTrans {
    static long secsToReport = 60;
    
    static String hostname = "";
    static String port = "";
    
    static DeepLService dl;
    static long lastReportTime=0;
    
    public static void main(String[] args) throws Exception {
        
        try {
            hostname=args[0];
            port=args[1];
        } catch (Exception ee){
            System.out.println("parms failed");
            hostname="localhost";
            port="8086";
            //return;
        }
        dl = new DeepLService();
        
            new Thread(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Monitor Thread started");
                    while (true) {
                        try {
                            Thread.sleep(1000*5);
                        } catch (Exception eee) {; }    
                        System.out.println("Monitor Tick");
                        long now = System.currentTimeMillis();
                        if (now-lastReportTime>secsToReport*1000) {
                            System.out.println("Report Time!");
                            lastReportTime = now;
                            String translated ="KO";
                            try {
                                translated = dl.translate("en", "es","OK");
                            } catch (Exception ee){
                                 translated ="KO";
                            }
                            System.out.println("Translate check returns: " + translated);
                            
                            if (translated==null){
                                translated ="KO";
                            }
                            
                            String payload="{\"hostname\":\""+hostname+"\""
                                + ",\"langpair\":\"*\""
                                + ",\"port\":\"" + port + "\""
                                + ",\"status\":\"" + translated + "\"}";
                
                
                            JSONObject jsonObj = new JSONObject(payload.toString());    
                
                            CloseableHttpClient client = null;

                            final HttpParams httpParams = new BasicHttpParams();
                            HttpConnectionParams.setConnectionTimeout(httpParams, 1000*10);
                            client = new DefaultHttpClient(httpParams);                

                            HttpPost httpPost = new HttpPost("http://prod.pangeamt.com:8080//NexRelay/v1/statusmt");

                            HttpEntity entity = EntityBuilder.create().
                                    setContentType(ContentType.APPLICATION_JSON).
                                    setContentEncoding(StandardCharsets.UTF_8.name()).
                                    setText(payload).build();
                            httpPost.setEntity(entity);

                            try {    
                                System.out.println("Reporting: " + payload);
                                client.execute(httpPost);
                            } catch (Exception ee) {
                                System.out.println("Exception reporting: " + ee);
                            }    
                            
                            
                        }
                    }
                }
            }).start();
            
            
        
        int puerto=new Integer(port).intValue();
        HttpServer server = HttpServer.create(new InetSocketAddress(puerto), 0);
        server.createContext("/info", new InfoHandler());
        server.createContext("/get", new GetHandler());
        server.createContext("/translate", new MyHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("The server is running at port "+ puerto);
        /*
        Document doc = new Document("Hola. Soy el jefe. Tengo 12.4 euros y el Sr.Gonzalez 12. Mr. Bloom came at 12.4");
        List<Sentence> sentences = doc.sentences();
        sentences.stream().forEach(System.out::println);
        */
    }    
    
    static class MyHandler implements HttpHandler {
    public void handle(HttpExchange t) throws IOException {
        String encoding = "UTF-8";
        t.getResponseHeaders().set("Content-Type", "text/html; charset=" + encoding);

        System.out.println("POST /translate endpoint called");
        StringBuilder response = new StringBuilder();
        InputStreamReader isr =  new InputStreamReader(t.getRequestBody(),"utf-8");
        BufferedReader br = new BufferedReader(isr);

        int b;
        StringBuilder buf = new StringBuilder(512);
        while ((b = br.read()) != -1) {
            buf.append((char) b);
        }

        br.close();
        isr.close();
        System.out.println("Json received: " + buf.toString());    
        // The resulting string is: buf.toString()
        // and the number of BYTES (not utf-8 characters) from the body is: buf.length()
        JSONObject j0 = new JSONObject(buf.toString());
        String text = j0.getString("text");
        String src = j0.getString("src");
        String tgt = j0.getString("tgt");
        System.out.println("Text to translate: " + text);    

        JSONObject result = new JSONObject(); 
            
        String translated = dl.translate(src, tgt, text);  
        
        int errorcode = 0;
        if (translated==null)
            errorcode = -1;
        
        result.put("source", text);
        result.put("target", translated);
        result.put("error", errorcode);
      
        response.append(result);
        System.out.println("Returning: "+ response.toString());
        
        byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
        t.sendResponseHeaders(200, bytes.length);
        OutputStream os = t.getResponseBody();
        os.write(bytes);
        os.close();
        
        lastReportTime = System.currentTimeMillis();
        /*
        t.sendResponseHeaders(200, response.toString().length());
        Writer out = new OutputStreamWriter(t.getResponseBody(), encoding);
        
        
        out.write(response.toString());
        out.close();
        */
        //SentenceSplitter.writeResponse(t, response.toString());
        
    }
}
    
    // http://localhost:8000/info
  static class InfoHandler implements HttpHandler {
    public void handle(HttpExchange httpExchange) throws IOException {
      String response = "Use /get?hello=word&foo=bar to see how to handle url parameters";
      DLTrans.writeResponse(httpExchange, response.toString());
    }
  }

  
  static class GetHandler implements HttpHandler {
    public void handle(HttpExchange httpExchange) throws IOException {
        System.out.println("GET endpoint called");
        String encoding = "UTF-8";
        httpExchange.getResponseHeaders().set("Content-Type", "text/html; charset=" + encoding);
        
        
        
        System.out.println("trace 1: " + httpExchange.getRequestURI());
        
        
        
        StringBuilder response = new StringBuilder();

        System.out.println("trace 2");
        
        Map <String,String> parms = null ;
        try {
            System.out.println("> " +httpExchange.getRequestURI().getQuery());
            parms = DLTrans.queryToMap(httpExchange.getRequestURI().getQuery());
        } catch (Exception ee) {
            System.out.println("Exception " + ee);
        }
        System.out.println("trace 3");
        
        String text =parms.get("text");
        String src =parms.get("src");
        String tgt =parms.get("tgt");
        System.out.println("Retrieved parms " + src + "/" + tgt + "/" + text);

        String translated = dl.translate(src, tgt, text);  
        
        System.out.println("Translated: " + translated);
        
        int errorcode = 0;
        if (translated==null)
            errorcode = -1;
        
        JSONObject result = new JSONObject(); 
        
        result.put("source", text);
        result.put("target", translated);
        result.put("error", errorcode);
      
        response.append(result);
        System.out.println("Returning: "+ response.toString());
        
        byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
        httpExchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = httpExchange.getResponseBody();
        os.write(bytes);
        os.close();
        
        lastReportTime = System.currentTimeMillis();
    }
  }
  
  public static void writeResponse(HttpExchange httpExchange, String response) throws IOException {
    httpExchange.sendResponseHeaders(200, response.length());
    OutputStream os = httpExchange.getResponseBody();
    os.write(response.getBytes(Charsets.UTF_8));
    os.close();
  }
 
  /**
   * returns the url parameters in a map
   * @param query
   * @return map
   */
  public static Map<String, String> queryToMap(String query){
    Map<String, String> result = new HashMap<String, String>();
    for (String param : query.split("&")) {
        String pair[] = param.split("=");
        if (pair.length>1) {
            result.put(pair[0], pair[1]);
        }else{
            result.put(pair[0], "");
        }
    }
    return result;
  }
  
  

static class DeepLService  {
        private static final int maxCperDay = 100000;
        private static final int maxCperMonth = 1000000;

        static String BASE_URL = "https://www2.deepl.com/jsonrpc";


        static String  JSONRPC_VERSION = "2.0";
        static String DEEPL_METHOD = "LMT_handle_jobs";

        
        static boolean started = false;
        static int charsDay=0;
        static int charsMonth=0;
        static int charsHour=0;
        
        
        DeepLService(){
            //constructor
            long now = System.currentTimeMillis();
        }
        

        public String translate(String src, String tgt, String line)  {
            String resp = null;
            
            int counter = 0;
            for (int i = 0; i < line.length(); i++) {
            if (Character.isLetter(line.charAt(i)))
                counter++;
                }
            if (counter==0){
                System.out.println("DeepL returns with invariant: [" + line + "]");      
                return(line);
            }
            
            
            CloseableHttpResponse response=null;
            CloseableHttpClient client = null;
            long now = System.currentTimeMillis();
            src=src.toUpperCase();
            tgt=tgt.toUpperCase();
            
            if (line.trim().length()==0)
               return line;     
            try {
                System.out.println("Trying to translate with DeepL: " + line + " ("+src + " -> " + tgt +")");      
                // Using the access token to build the appid for the request url
                
                String payload="{\"jsonrpc\":\"2.0\",\"method\":\"LMT_handle_jobs\","
                        + "\"params\":{\"jobs\":[{\"kind\":\"default\", \"quality\":\"fast\", "
                        + "\"raw_en_sentence\":"
                        + "\"" + line  + "\""
                        + "}],\"lang\":{\"user_preferred_langs\":["
                        + "\"" + src + "\""
                        + ","
                        + "\"" + tgt + "\""
                        + "],\"source_lang_user_selected\":"
                        + "\"" + src + "\""
                        + ",\"target_lang\":"
                        + "\"" + tgt + "\""
                        + "}, \"priority\":-1},\"id\":0}";
                
                
                JSONObject jsonObj = new JSONObject(payload.toString());    
                
                //System.out.println("request: " + payload); 
                //System.out.println("====");

                //client = HttpClients.createDefault();
                
            // set the connection timeout value to 30 seconds (30000 milliseconds)
                final HttpParams httpParams = new BasicHttpParams();
                HttpConnectionParams.setConnectionTimeout(httpParams, 1000*10);
                client = new DefaultHttpClient(httpParams);                
                
                HttpPost httpPost = new HttpPost(BASE_URL);
                
                HttpEntity entity = EntityBuilder.create().
                        setContentType(ContentType.APPLICATION_JSON).
                        setContentEncoding(StandardCharsets.UTF_8.name()).
                        setText(payload).build();
                httpPost.setEntity(entity);
        
                
                response = client.execute(httpPost);
                
                
                int status = response.getStatusLine().getStatusCode();
                
                
                if (status==200) {
                    charsDay+=line.length();
                    charsMonth+=line.length();
                    charsHour+=line.length();
                    try {
                        
                        HttpEntity httpEntity = response.getEntity();
                        if (httpEntity != null) {
                            String resultString = EntityUtils.toString(httpEntity); 
                             // parsing JSON and convert String to JSON Object
                            JSONObject result = new JSONObject(resultString); 
                            System.out.println("DLTrans answer: " + result);
                            JSONObject result2 = result.getJSONObject("result");
                            //System.out.println("result: " + result2);
                            JSONObject result3 = result2.getJSONArray("translations").getJSONObject(0);
                            JSONObject r4 = result3.getJSONArray("beams").getJSONObject(0);
                            resp=r4.getString("postprocessed_sentence");
                            //System.out.println("out: " + resp);
                        }
                    } catch (Exception ee) {
                        System.out.println("Excpt sending: " + ee);
                        resp = null;

                    }
                } else {
                    System.out.println("DeepL REST answered code: " + status + " > " + response.toString()) ;
                    resp = null;
                }    
                

            } catch (Exception ee) {
                System.out.println("Excpt: " + ee);
                resp = null;
            }
            finally {
                    //IOUtils.closeQuietly(response);
            }            
            
            if (resp != null){
                System.out.println("DeepL Translation OK");
            } else {
                System.out.println("DeepL Translation failed");
                resp = null;
            }

            return(resp);
        }

        
        

        static public boolean canTranslate(String src, String tgt) {
            boolean srcOK=false;
            boolean tgtOK=false;
            if (src.equals("es") || src.equals("en") || src.equals("de") || src.equals("fr") || src.equals("it") || src.equals("nl") || src.equals("pl")) {
                srcOK=true;
            }
            if (tgt.equals("es") || tgt.equals("en") || tgt.equals("de") || tgt.equals("fr") || tgt.equals("it") || tgt.equals("nl") || tgt.equals("pl")) {
                tgtOK=true;
            }
            if (srcOK && tgtOK)
                return true;
            
            return false;
        }
        
        
    
    
    }
}