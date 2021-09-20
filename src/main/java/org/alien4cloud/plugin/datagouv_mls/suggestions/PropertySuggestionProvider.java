package org.alien4cloud.plugin.datagouv_mls.suggestions;

import alien4cloud.model.suggestion.Suggestion;
import alien4cloud.model.suggestion.SuggestionEntry;
import alien4cloud.model.suggestion.SuggestionPolicy;
import alien4cloud.model.suggestion.SuggestionRequestContext;
import alien4cloud.rest.utils.JsonUtil;
import alien4cloud.suggestions.IComplexSuggestionPluginProvider;
import alien4cloud.suggestions.services.SuggestionService;

import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConfiguration;
import org.alien4cloud.plugin.datagouv_mls.model.Entity;
import org.alien4cloud.plugin.datagouv_mls.model.SearchResults;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.List;

@Component("property-suggestion-provider")
@Slf4j
public class PropertySuggestionProvider implements IComplexSuggestionPluginProvider {

    @Inject
    private SuggestionsConfiguration configuration;    

    @Inject
    private DatagouvMLSConfiguration dgv_configuration;    

    private RestTemplate restTemplate = null;
    private final ObjectMapper mapper = new ObjectMapper();

    @Resource
    private SuggestionService suggestionService;

    @PostConstruct
    public void init() {
       initSuggestions();

       try {
          restTemplate = getRestTemplate();
       } catch (Exception e) {
          log.error ("Error creating restTemplate: {}", e.getMessage());
       }
    }

    /**
     * initialise rest without checking certificate
     **/
    private RestTemplate getRestTemplate() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        TrustStrategy acceptingTrustStrategy = new TrustStrategy() {
            @Override
            public boolean isTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                return true;
            }
        };
        SSLContext sslContext = org.apache.http.ssl.SSLContexts.custom().loadTrustMaterial(null,acceptingTrustStrategy).build();
        SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(csf).build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setHttpClient(httpClient);
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        return restTemplate;
    }

    private void initSuggestions() {
       for (String nodetype : configuration.getNodeTypes()) {
          SuggestionEntry suggestionEntry = new SuggestionEntry();
          suggestionEntry.setEsIndex("toscaelement");
          suggestionEntry.setEsType("nodetype");
          suggestionEntry.setTargetElementId(nodetype);
          suggestionEntry.setSuggestionPolicy(SuggestionPolicy.Strict);
          suggestionEntry.setSuggestionHookPlugin("alien4cloud-datagouv_mls-plugin:property-suggestion-provider");
          for(String propIter : configuration.getProperties()) { 
              suggestionEntry.setTargetProperty(propIter);
              log.info("Adding suggestion entry {}", suggestionEntry.getId());
              try {
                suggestionService.createSuggestionEntry(suggestionEntry);
              } catch (Exception e) {
                log.error("Something wrong in suggestion configuration, ignoring " + suggestionEntry.getId(), e);
              }
          }
          
       }
    }

    /**
     * This method will be called each time the user will input something, so it could be a good idea to cache.
     */
    @SneakyThrows
    @Override
    public Collection<Suggestion> getSuggestions(String input, SuggestionRequestContext context) {
        log.debug("Context is {}", context);
        if (context != null) {
            log.debug("User is {}", context.getUser().getUsername());
        }
        Collection<Suggestion> result = Lists.newArrayList();

        if (StringUtils.isBlank(configuration.getUrl())) {
           log.debug ("No URL configured, using default values");
           return configuration.getDefaultSuggestions();
        }

        log.debug ("Trying connection to {}", configuration.getUrl());

        String auth = dgv_configuration.getApplicationDeleteCredentials();
        HttpHeaders headers = new HttpHeaders();
        byte[] encodedAuth = Base64.getEncoder().encode( 
             auth.getBytes(Charset.forName("US-ASCII")) );
        headers.set("Authorization", "Basic " + new String(encodedAuth));       

        try {
           HttpEntity request = new HttpEntity (headers);
           ResponseEntity<SearchResults> hresult = restTemplate.exchange(configuration.getUrl(), HttpMethod.GET, request, SearchResults.class);
           try {
              log.debug ("RESPONSE: {}", mapper.writeValueAsString(hresult.getBody()));
           } catch (Exception e) {}
           SearchResults sresult = hresult.getBody();
           if (sresult.getSearchResults() == null) {
              log.warn ("Response contains no search results");
              log.debug ("No entities, using default values");
              return configuration.getDefaultSuggestions();
           }
           List<Entity> entities = sresult.getSearchResults().getEntities();
           if ((entities == null) || (entities.size() == 0)) {
              log.warn ("Response contains no entities");
              log.debug ("No entities, using default values");
              return configuration.getDefaultSuggestions();
           }
           for (Entity entity : entities) {
               if ((entity.getAttributes() == null) || (entity.getAttributes().getQualifiedName() == null)) {
                  log.warn ("Response contains an entity with no qualified name, ignore it");
                  continue;
               }
               result.add(new Suggestion(entity.getAttributes().getQualifiedName(), null));
           }
        } catch (HttpClientErrorException he) {
           log.error ("HTTP error {}", he.getStatusCode());
           log.debug ("HTTP error, using default values");
           return configuration.getDefaultSuggestions();
        } catch (HttpServerErrorException he) {
           log.error ("HTTP error {}", he.getStatusCode());
           log.debug ("HTTP error, using default values");
           return configuration.getDefaultSuggestions();
        } catch (ResourceAccessException re) {
           log.error  ("Cannot send request: {}", re.getMessage());
           log.debug ("Error, using default values");
           return configuration.getDefaultSuggestions();
        }

        return result;
    }
}
