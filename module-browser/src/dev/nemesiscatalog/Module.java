package dev.nemesiscatalog;

import org.json.*;
final class Module {
    final String id,name,version,description,publisher,url,hash,compatibility;
    final long bytes;
    final JSONArray firmware;
    Module(JSONObject o,boolean test)throws Exception{
        id=o.getString("id");version=o.getString("version");Policy.id(id);Policy.version(version);
        if("module.browser".equals(id))throw new IllegalArgumentException("Browser self-update is not supported in this prototype");
        name=o.getString("name");description=o.getString("description");publisher=o.getString("publisher");compatibility=o.getString("compatibility");
        if(name.length()>100||description.length()>3000||publisher.length()>100||compatibility.length()>2000)throw new IllegalArgumentException("Catalog text exceeds limit");
        url=o.getString("downloadUrl");Policy.asset(url,test);hash=o.getString("sha256");bytes=o.getLong("bytes");
        if(!hash.matches("[0-9a-f]{64}")||bytes<1||bytes>Policy.MAX_DOWNLOAD)throw new IllegalArgumentException("Invalid package hash/size");
        firmware=o.getJSONArray("firmwarePrefixes");for(int i=0;i<firmware.length();i++)if(firmware.getString(i).length()<8)throw new IllegalArgumentException("Firmware prefix too broad");
    }
    boolean matches(String current)throws Exception{if(firmware.length()==0)return true;for(int i=0;i<firmware.length();i++)if(current.startsWith(firmware.getString(i)))return true;return false;}
    public String toString(){return name+"\n"+version+" • "+publisher;}
}
