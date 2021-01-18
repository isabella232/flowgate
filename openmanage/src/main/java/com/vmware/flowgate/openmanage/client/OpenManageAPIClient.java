/**
 * Copyright 2021 VMware, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
*/
package com.vmware.flowgate.openmanage.client;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.vmware.flowgate.client.RestTemplateBuilder;
import com.vmware.flowgate.common.exception.WormholeException;
import com.vmware.flowgate.common.model.FacilitySoftwareConfig;
import com.vmware.flowgate.openmanage.datamodel.AuthInfo;
import com.vmware.flowgate.openmanage.datamodel.Chassis;
import com.vmware.flowgate.openmanage.datamodel.DeviceType;
import com.vmware.flowgate.openmanage.datamodel.DevicesResult;
import com.vmware.flowgate.openmanage.datamodel.Server;

public class OpenManageAPIClient {

   private static final String GetDviceUri = "/api/DeviceService/Devices?$filter=Type eq %s&$skip=%s&$top=%s";
   private static final String SessionUri = "/api/SessionService/Sessions";
   private static final String LogOutUri = "/api/SessionService/Actions/SessionService.Logoff";
   private static final String APISessionType = "API";
   private RestTemplate restTemplate;
   private String serviceEndPoint;
   private AuthInfo authInfo;
   private String token;
   private static Map<Class<?>,Integer> deviceTypeMap = new HashMap<Class<?>,Integer>();
   private String AuthHeader = "x-auth-token";
   static {
      deviceTypeMap.put(Server.class, DeviceType.SERVER.getValue());
      deviceTypeMap.put(Chassis.class, DeviceType.CHASSIS.getValue());
      deviceTypeMap = Collections.unmodifiableMap(deviceTypeMap);
   }

   public OpenManageAPIClient(FacilitySoftwareConfig config) {
      this.serviceEndPoint = config.getServerURL();
      this.authInfo = new AuthInfo(config.getUserName(),config.getPassword(), APISessionType);
      try {
         this.restTemplate =
               RestTemplateBuilder.buildTemplate(config.isVerifyCert(), 60000);
      } catch (Exception e) {
         throw new WormholeException(e.getMessage(), e.getCause());
      }
   }

   public String getServiceEndPoint() {
      return serviceEndPoint;
   }

   private HttpHeaders buildHeaders() {
      HttpHeaders headers = RestTemplateBuilder.getDefaultHeader();
      headers.add(AuthHeader, this.token);
      return headers;
   }

   private HttpEntity<String> getDefaultEntity() {
      return new HttpEntity<String>(buildHeaders());
   }

   public void getToken() {
      HttpEntity<Object> postEntity = new HttpEntity<Object>(this.authInfo, RestTemplateBuilder.getDefaultHeader());
      ResponseEntity<Void> entity = this.restTemplate.exchange(getServiceEndPoint() + SessionUri,
            HttpMethod.POST, postEntity , Void.class);
      if(entity.getStatusCode().is2xxSuccessful() &&
            !entity.getHeaders().get(AuthHeader).isEmpty()) {
         this.token = entity.getHeaders().get(AuthHeader).get(0);
      }
   }

   public void logOut() {
      this.restTemplate.exchange(getServiceEndPoint() + LogOutUri,
            HttpMethod.POST, getDefaultEntity() , Void.class);
   }

   public <T> DevicesResult<T> getDevices(int skip, int limit, Class<T> type) {
      String url = getServiceEndPoint()
            + String.format(GetDviceUri, deviceTypeMap.get(type), skip, limit);
      UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
      URI uri = builder.build().encode().toUri();
      ResolvableType resolvableType = ResolvableType.forClassWithGenerics(DevicesResult.class, type);
      ParameterizedTypeReference<DevicesResult<T>> typeRef = ParameterizedTypeReference.forType(resolvableType.getType());
      ResponseEntity<DevicesResult<T>> entity = this.restTemplate.exchange(uri, HttpMethod.GET,
            getDefaultEntity(), typeRef);
      DevicesResult<T> result = null;
      if (entity.hasBody()) {
         result = entity.getBody();
      }
      return result;
   }

}
