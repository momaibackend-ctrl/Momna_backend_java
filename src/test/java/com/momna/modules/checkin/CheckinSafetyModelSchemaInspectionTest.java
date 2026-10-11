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
   try(var zis=new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
    ZipEntry entry;
    while((entry=zis.getNextEntry())!=null){
     if(!entry.getName().endsWith("daily_checkin_weighted_model_v3.json"))continue;
     var root=new ObjectMapper().readTree(zis);
     System.out.println("SAFETY_MODEL_KEYS="+root.properties().stream().map(java.util.Map.Entry::getKey).toList());
     for(String k:List.of("safety_route_catalog","item_safety_routes","safety_clarification_catalog","safety_clarifications","safety_rules")) {
       JsonNode v=root.path(k);
       System.out.println("SAFETY_MODEL_SECTION_"+k+"="+v.toString().substring(0,Math.min(v.toString().length(),9500)));
     }
     return;
    }
   }
   throw new AssertionError("Model entry not found");
 }
}
