package com.scout24.ha.meross.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class MerossHttpClient {
    public static final String _SECRET = "23x17ahWarFH6w29";
    public static final String _MEROSS_URL = "https://iot.meross.com";
    public static final String _LOGIN_URL = _MEROSS_URL + "/v1/Auth/Login";
    public static final String _LOG_URL = _MEROSS_URL + "/v1/log/user";
    public static final String _DEV_LIST = _MEROSS_URL + "/v1/Device/devList";
    private static ObjectMapper mapper = new ObjectMapper();

    @Getter
    private String token;
    @Getter
    private String key;
    @Getter
    private long userId;

    @Getter
    private String email;
    private String password;
    @Getter
    private boolean authenticated;

    public MerossHttpClient(String userEmail, String password) {
        this.email = userEmail;
        this.password = password;
    }
    private Object authenticatedPost(String url, Map<String,String> paramsData) throws RestException {
        try {
            String nonce = UUID.randomUUID().toString();
            long timeMillis = System.currentTimeMillis();
            String loginParams = this.encodeParams(paramsData);

            //Generate the md5-hash (called signature)
            String datatosign = _SECRET + timeMillis + nonce + loginParams;
            String md5hash = DigestUtils.md5DigestAsHex(datatosign.getBytes());
            Map<String, String> headersMap = ImmutableMap.of("Authorization", "Basic " + token,
                    "vender", "Meross",
                    "AppVersion", "1.3.0",
                    "AppLanguage", "EN",
                    "User-Agent", "okhttp/3.6.0");

            Map<String, String> payload = ImmutableMap.of(
                    "params", loginParams,
                    "sign", md5hash,
                    "timestamp", timeMillis + "",
                    "nonce", nonce);
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAll(headersMap);


            HttpEntity<String> entityReq = new HttpEntity<>(mapper.writeValueAsString(payload), headers);

            RestTemplate template = new RestTemplate();

            ResponseEntity<Map> respEntity = template
                    .exchange(url, HttpMethod.POST, entityReq, Map.class);

            //Save returned value
            Map<String, ?> jsondata = respEntity.getBody();
            System.out.println("jsondata = \n" + mapper.writeValueAsString(jsondata));

            if (jsondata != null && jsondata.containsKey("info") &&
                    !jsondata.get("info").equals("Success")) {
                throw new RestException("Error authenticating on API Server!");
            }
            return jsondata.get("data");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String encodeParams(Map<String, String> params_data) throws JsonProcessingException {
        return Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(params_data));
    }

    private boolean login() throws RestException {
        Map<String, String> data = ImmutableMap.of("email", this.email, "password", this.password);
        Map responseData = (Map) this.authenticatedPost(_LOGIN_URL, data);
        this.token = (String) responseData.get("token");
        this.key = (String) responseData.get("key");
        this.userId = Long.parseLong((String) responseData.get("userid"));
        String userEmail = (String) responseData.get("email");
        this.authenticated = true;
        this.log();
        return true;
    }

    private void log(){
        Map<String, String> data = Maps.newLinkedHashMap();
        data.put( "extra", "");
        data.put( "model", "Android,Android SDK built for x86_64");
        data.put("system","Android");
        data.put("uuid","493dd9174941ed58waitForOpenWifi");
        data.put( "vendor", "Meross");
        data.put("version", "6.0");
        try {
            this.authenticatedPost(_LOG_URL, data);
        } catch (RestException e) {
            log.error(e.getMessage(), e);
        }
    }

    private List<Device> listDevices() throws RestException {
        if (!this.authenticated && !this.login())
            throw new RestException("Not connected, please authenticate first");
        final List<Map> map = (List<Map>) this.authenticatedPost(_DEV_LIST, ImmutableMap.of());
        return mapper.convertValue(map, new TypeReference<List<Device>>(){});
    }


    public List<AttachedDevice> listSupportedDevices(boolean onlineOnly){
        ArrayList<AttachedDevice> supportedDevices = Lists.newArrayList();
        try {
            for (Device device : listDevices()) {
                if (onlineOnly && device.getOnlineStatus() != 1) {
                    continue;
                }
                AttachedDevice wrapped = wrapIt(this.token, this.key, this.userId, device);
                supportedDevices.add(wrapped);
            }
        } catch (RestException e) {
            log.error(e.getMessage(), e);
        }
        return supportedDevices;
    }

    public Map<String,AttachedDevice> mapSupportedDevices(boolean onlineOnly){
        Map<String, AttachedDevice> supportedDevices = Maps.newLinkedHashMap();
        try {
            for (Device device : listDevices()) {
                if (onlineOnly && device.getOnlineStatus() != 1) {
                    continue;
                }
                AttachedDevice wrapped = wrapIt(this.token, this.key, this.userId, device);
                supportedDevices.put(device.getUuid(), wrapped);
            }
        } catch (RestException e) {
            log.error(e.getMessage(), e);
        }
        return supportedDevices;
    }

    private AttachedDevice wrapIt(String token, String key, long userid, Device device) {
        return new AttachedDevice(device, token, key, userid);
    }


}
