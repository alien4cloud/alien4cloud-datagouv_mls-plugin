package org.alien4cloud.plugin.datagouv_mls.suggestions;

import alien4cloud.model.suggestion.Suggestion;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@Setter
@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "datagouv_mls.suggestions")
public class SuggestionsConfiguration {
   private String url;

   private List<String> nodeTypes = Stream.of("artemis.bec.pub.BecKubeContainer",
                                            "artemis.bec.pub.BecKubeConfigurableContainer",
                                            "artemis.bec.pub.BecCustomJavaSpark3Job",
                                            "artemis.bec.pub.BecPythonSpark3Job").collect(Collectors.toList());

   private String property = "batchuser";

   private String[] defaultValues = {"user1", "user2"};

   private List<Suggestion> defaultSuggestions = null;

   public List<Suggestion> getDefaultSuggestions() {
      if (defaultSuggestions == null) {
         defaultSuggestions = Stream.of(defaultValues).map(this::addSuggestion).collect(Collectors.toList());
      }
      return defaultSuggestions;
   }

    private Suggestion addSuggestion(String val) {
       return new Suggestion(val, null);
    }

    private String archive = "artemis.bec.pub";
}
