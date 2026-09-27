package com.vaultchain.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultchain.exception.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class AssetFingerprints {
    private final ObjectMapper json;
    public AssetFingerprints(ObjectMapper json){this.json=json;}
    public record Result(String sha256,String phash,Map<String,Object> metadata) {}
    public Result compute(byte[] bytes,String mime,boolean withVisual){
        Map<String,Object> raw;
        if("image/png".equals(mime))raw=pngMetadata(bytes);
        else if("image/jpeg".equals(mime))raw=Map.of();
        else throw new ApiException(500,"Failed to extract image metadata");
        Object width=raw.get("ImageWidth"),height=raw.get("ImageHeight");
        Long count=width instanceof Number w&&height instanceof Number h?w.longValue()*h.longValue():null;
        List<String> patterns=count==null?List.of():List.of("resolution-available");
        Map<String,Object> metaJson=new LinkedHashMap<>(raw);metaJson.put("pixelCount",count);metaJson.put("patterns",patterns);
        Map<String,Object> metadata=new LinkedHashMap<>();metadata.put("width",width);metadata.put("height",height);
        metadata.put("pixelCount",count);metadata.put("patterns",patterns);metadata.put("camera",null);
        metadata.put("location",null);metadata.put("createdDate",null);metadata.put("metadataJson",metaJson);
        String sha;
        try{Map<String,Object> payload=new LinkedHashMap<>();payload.put("metadata",ordered(metadata));payload.put("asset",Map.of());
            MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(bytes);
            digest.update(json.writeValueAsBytes(payload));sha=HexFormat.of().formatHex(digest.digest());}
        catch(Exception failure){throw new IllegalStateException(failure);}
        String visual=null;
        if(withVisual){try{BufferedImage image=ImageIO.read(new ByteArrayInputStream(bytes));
                if(image==null)throw new IllegalStateException("decoder unavailable");visual=BlockHash.hash(image);}
            catch(Exception failure){throw new ApiException(500,"Failed to generate perceptual hash");}}
        return new Result(sha,visual,metadata);
    }
    private static Object ordered(Object value){
        if(value instanceof Map<?,?> map){Map<String,Object> sorted=new TreeMap<>();
            map.forEach((k,v)->sorted.put(String.valueOf(k),ordered(v)));return sorted;}
        if(value instanceof List<?> list)return list.stream().map(AssetFingerprints::ordered).toList();return value;
    }
    private static Map<String,Object> pngMetadata(byte[] bytes){
        try(DataInputStream input=new DataInputStream(new ByteArrayInputStream(bytes))){
            byte[] signature=new byte[8];input.readFully(signature);
            if(!Arrays.equals(signature,new byte[]{(byte)137,80,78,71,13,10,26,10}))throw new Exception();
            Map<String,Object> result=new LinkedHashMap<>();
            while(input.available()>=12){int length=input.readInt();byte[] typeBytes=new byte[4];input.readFully(typeBytes);
                String type=new String(typeBytes,StandardCharsets.US_ASCII);
                if(length<0||length>20_000_000||length>input.available()-4)throw new Exception();
                byte[] data=new byte[length];input.readFully(data);input.readInt();
                if(type.equals("IHDR")){if(data.length<13)throw new Exception();
                    java.nio.ByteBuffer header=java.nio.ByteBuffer.wrap(data);int width=header.getInt(),height=header.getInt();
                    result.put("ImageWidth",width);result.put("ImageHeight",height);result.put("BitDepth",header.get()&255);
                    int color=header.get()&255;result.put("ColorType",switch(color){case 0->"Grayscale";case 2->"RGB";case 3->"Palette";case 4->"Grayscale with Alpha";case 6->"RGB with Alpha";default->"Unknown";});
                    result.put("Compression","Deflate/Inflate");result.put("Filter","Adaptive");
                    result.put("Interlace",(header.get(12)&255)==0?"Noninterlaced":"Adam7 Interlace");
                }else if(type.equals("tEXt")){int split=0;while(split<data.length&&data[split]!=0)split++;
                    if(split<data.length){String key=new String(data,0,split,StandardCharsets.ISO_8859_1);
                        String text=new String(data,split+1,data.length-split-1,StandardCharsets.ISO_8859_1);result.put(key,text);}}
                else if(type.equals("IEND"))break;
            }
            if(!result.containsKey("ImageWidth"))throw new Exception();return result;
        }catch(Exception failure){throw new ApiException(500,"Failed to extract image metadata");}
    }
}
