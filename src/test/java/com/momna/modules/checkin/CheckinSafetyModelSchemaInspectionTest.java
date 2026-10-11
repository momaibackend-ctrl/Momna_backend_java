package com.momna.modules.checkin;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
class CheckinSafetyModelSchemaInspectionTest {
 @Test void listSourceSafetySchema() throws Exception {
   var in=getClass().getResourceAsStream("/checkin/momna_checkin_weighted_model_files.zip.b64");
   byte[] zip=Base64.getDecoder().decode(new String(in.readAllBytes(),StandardCharsets.UTF_8).replaceAll("\\s+",""));
   var path=java.nio.file.Files.createTempFile("safety-model-", ".zip");
   java.nio.file.Files.write(path,zip);
   try(var zf=new ZipFile(path.toFile())) {
    var es=zf.entries();
    while(es.hasMoreElements()){
     var entry=es.nextElement();
     if(!entry.getName().endsWith("daily_checkin_weighted_model_v3.json"))continue;
     var root=new ObjectMapper().readTree(zf.getInputStream(entry));
     System.out.println("SAFETY_MODEL_KEYS="+root.properties().stream().map(java.util.Map.Entry::getKey).toList());
     for(String k:List.of("safety_route_catalog","item_safety_routes","safety_clarification_catalog","safety_clarifications","safety_rules")) {
       JsonNode v=root.path(k);
       System.out.println("SAFETY_MODEL_SECTION_"+k+"="+v.toString().substring(0,Math.min(v.toString().length(),9500)));
     }
     return;
    }
   }finally{java.nio.file.Files.deleteIfExists(path);}
   throw new AssertionError("Model entry not found");
 }
}
